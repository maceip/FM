package com.google.research.air.cosmo.context

import android.os.StrictMode
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.research.air.cosmo.security.DatabasePassphraseProvider
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

data class ContextEvent(
    val id: String,
    val source: Source,
    val timestampMs: Long,
    val text: String?,
    val blobRef: String?,
    val embedding: FloatArray?,
    val retentionClass: RetentionClass,
    val sourceMetadata: Map<String, String>
)

enum class Source { AUDIO, SCREEN, NOTIFICATION, CAMERA, INTERACTION, INTENT, LOCATION }

enum class RetentionClass { EPHEMERAL, SHORT, LONG }

sealed class InsertResult {
    data object Stored : InsertResult()

    data class Rejected(
        val reason: RejectReason
    ) : InsertResult()
}

enum class RejectReason { SOURCE_DISABLED, RETENTION_FULL, SCHEMA_INVALID }

data class ScoredEvent(
    val event: ContextEvent,
    val score: Float
)

data class ContextStoreStats(
    val countsBySource: Map<Source, Int>,
    val countsByRetention: Map<RetentionClass, Int>
)

interface ContextStore {
    suspend fun insert(event: ContextEvent): InsertResult

    suspend fun attachEmbedding(
        id: String,
        embedding: FloatArray
    )

    suspend fun queryByTime(
        range: LongRange,
        sources: Set<Source>? = null
    ): List<ContextEvent>

    suspend fun queryByText(
        query: String,
        limit: Int = 20
    ): List<ContextEvent>

    suspend fun queryByEmbedding(
        vec: FloatArray,
        limit: Int = 20
    ): List<ScoredEvent>

    suspend fun delete(id: String)

    suspend fun deleteRange(
        range: LongRange,
        sources: Set<Source>? = null
    ): Int

    suspend fun purgeSource(source: Source): Int

    fun events(): Flow<ContextEvent>

    suspend fun stats(): ContextStoreStats
}

@Entity(tableName = "context_events")
data class ContextEventEntity(
    @PrimaryKey val id: String,
    val source: Source,
    val timestampMs: Long,
    val text: String?,
    val blobRef: String?,
    val embedding: ByteArray?,
    val retentionClass: RetentionClass,
    val sourceMetadata: String
)

@Fts4
@Entity(tableName = "context_event_fts")
data class ContextEventFts(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val eventId: String,
    val text: String
)

data class SourceCount(
    val source: Source,
    val count: Int
)

data class RetentionCount(
    val retentionClass: RetentionClass,
    val count: Int
)

class ContextConverters {
    @TypeConverter fun sourceToString(value: Source): String = value.name

    @TypeConverter fun sourceFromString(value: String): Source = Source.valueOf(value)

    @TypeConverter fun retentionToString(value: RetentionClass): String = value.name

    @TypeConverter fun retentionFromString(value: String): RetentionClass = RetentionClass.valueOf(value)
}

@Dao
interface ContextEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entity: ContextEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(entity: ContextEventFts)

    @Transaction
    suspend fun insertWithFts(entity: ContextEventEntity) {
        insertEvent(entity)
        if (!entity.text.isNullOrBlank()) {
            insertFts(ContextEventFts(eventId = entity.id, text = entity.text))
        }
    }

    @Query("UPDATE context_events SET embedding = :embedding WHERE id = :id")
    suspend fun attachEmbedding(
        id: String,
        embedding: ByteArray
    )

    @Query("SELECT * FROM context_events WHERE timestampMs BETWEEN :start AND :end ORDER BY timestampMs ASC")
    suspend fun queryByTime(
        start: Long,
        end: Long
    ): List<ContextEventEntity>

    @Query("SELECT * FROM context_events WHERE timestampMs BETWEEN :start AND :end AND source IN (:sources) ORDER BY timestampMs ASC")
    suspend fun queryByTimeAndSources(
        start: Long,
        end: Long,
        sources: Set<Source>
    ): List<ContextEventEntity>

    @Query(
        "SELECT ce.* FROM context_events ce JOIN context_event_fts fts ON ce.id = fts.eventId WHERE context_event_fts MATCH :query ORDER BY ce.timestampMs DESC LIMIT :limit"
    )
    suspend fun queryByText(
        query: String,
        limit: Int
    ): List<ContextEventEntity>

    @Query("SELECT * FROM context_events WHERE embedding IS NOT NULL")
    suspend fun eventsWithEmbeddings(): List<ContextEventEntity>

    @Query("DELETE FROM context_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM context_event_fts WHERE eventId = :id")
    suspend fun deleteFts(id: String)

    @Transaction
    suspend fun deleteEverywhere(id: String) {
        delete(id)
        deleteFts(id)
    }

    @Query("DELETE FROM context_events WHERE timestampMs BETWEEN :start AND :end")
    suspend fun deleteEventsRange(
        start: Long,
        end: Long
    ): Int

    @Query("DELETE FROM context_event_fts WHERE eventId IN (SELECT id FROM context_events WHERE timestampMs BETWEEN :start AND :end)")
    suspend fun deleteFtsRange(
        start: Long,
        end: Long
    )

    @Transaction
    suspend fun deleteRange(
        start: Long,
        end: Long
    ): Int {
        deleteFtsRange(start, end)
        return deleteEventsRange(start, end)
    }

    @Query("DELETE FROM context_events WHERE timestampMs BETWEEN :start AND :end AND source IN (:sources)")
    suspend fun deleteEventsRangeWithSources(
        start: Long,
        end: Long,
        sources: Set<Source>
    ): Int

    @Query("DELETE FROM context_event_fts WHERE eventId IN (SELECT id FROM context_events WHERE timestampMs BETWEEN :start AND :end AND source IN (:sources))")
    suspend fun deleteFtsRangeWithSources(
        start: Long,
        end: Long,
        sources: Set<Source>
    )

    @Transaction
    suspend fun deleteRangeWithSources(
        start: Long,
        end: Long,
        sources: Set<Source>
    ): Int {
        deleteFtsRangeWithSources(start, end, sources)
        return deleteEventsRangeWithSources(start, end, sources)
    }

    @Query("DELETE FROM context_events WHERE source = :source")
    suspend fun purgeEventsSource(source: Source): Int

    @Query("DELETE FROM context_event_fts WHERE eventId IN (SELECT id FROM context_events WHERE source = :source)")
    suspend fun purgeFtsSource(source: Source)

    @Transaction
    suspend fun purgeSource(source: Source): Int {
        purgeFtsSource(source)
        return purgeEventsSource(source)
    }

    @Query("SELECT source, COUNT(*) as count FROM context_events GROUP BY source")
    suspend fun countsBySource(): List<SourceCount>

    @Query("SELECT retentionClass, COUNT(*) as count FROM context_events GROUP BY retentionClass")
    suspend fun countsByRetention(): List<RetentionCount>
}

@Database(entities = [ContextEventEntity::class, ContextEventFts::class], version = 1, exportSchema = false)
@TypeConverters(ContextConverters::class)
abstract class CosmoDatabase : RoomDatabase() {
    abstract fun contextEventDao(): ContextEventDao
}

@Singleton
class RoomContextStore
@Inject
constructor(
    private val dao: ContextEventDao,
    private val settings: CosmoSettingStore
) : ContextStore {
    private val inserts = MutableSharedFlow<ContextEvent>(extraBufferCapacity = 64)
    private val json = Json { encodeDefaults = true }

    override suspend fun insert(event: ContextEvent): InsertResult = withContext(Dispatchers.IO) {
        if (event.id.isBlank()) return@withContext InsertResult.Rejected(RejectReason.SCHEMA_INVALID)
        if (!isSourceEnabled(event.source)) return@withContext InsertResult.Rejected(RejectReason.SOURCE_DISABLED)
        dao.insertWithFts(event.toEntity(json))
        inserts.tryEmit(event)
        InsertResult.Stored
    }

    override suspend fun attachEmbedding(
        id: String,
        embedding: FloatArray
    ) = withContext(Dispatchers.IO) {
        dao.attachEmbedding(id, embedding.toBytes())
    }

    override suspend fun queryByTime(
        range: LongRange,
        sources: Set<Source>?
    ): List<ContextEvent> = withContext(Dispatchers.IO) {
        val rows =
            if (sources.isNullOrEmpty()) {
                dao.queryByTime(range.first, range.last)
            } else {
                dao.queryByTimeAndSources(range.first, range.last, sources)
            }
        rows.map { it.toEvent(json) }
    }

    override suspend fun queryByText(
        query: String,
        limit: Int
    ): List<ContextEvent> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else dao.queryByText(query.sanitizeFtsQuery(), limit).map { it.toEvent(json) }
    }

    override suspend fun queryByEmbedding(
        vec: FloatArray,
        limit: Int
    ): List<ScoredEvent> = withContext(Dispatchers.IO) {
        dao
            .eventsWithEmbeddings()
            .mapNotNull { entity -> entity.embedding?.toFloats()?.let { ScoredEvent(entity.toEvent(json), cosine(vec, it)) } }
            .sortedByDescending { it.score }
            .take(limit)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteEverywhere(id) }

    override suspend fun deleteRange(
        range: LongRange,
        sources: Set<Source>?
    ): Int = withContext(Dispatchers.IO) {
        if (sources.isNullOrEmpty()) {
            dao.deleteRange(
                range.first,
                range.last
            )
        } else {
            dao.deleteRangeWithSources(range.first, range.last, sources)
        }
    }

    override suspend fun purgeSource(source: Source): Int = withContext(Dispatchers.IO) { dao.purgeSource(source) }

    override fun events(): Flow<ContextEvent> = inserts

    override suspend fun stats(): ContextStoreStats = withContext(Dispatchers.IO) {
        ContextStoreStats(
            countsBySource = dao.countsBySource().associate { it.source to it.count },
            countsByRetention = dao.countsByRetention().associate { it.retentionClass to it.count }
        )
    }

    private fun isSourceEnabled(source: Source): Boolean = when (source) {
        Source.AUDIO -> settings.isAmbientAudioEnabled()
        Source.SCREEN -> settings.isScreenContextEnabled()
        Source.NOTIFICATION -> settings.isNotificationCaptureEnabled()
        Source.CAMERA -> settings.isCameraEnabled() || settings.isGlassesCameraEnabled()
        Source.INTERACTION -> settings.isScreenInteractionCaptureEnabled()
        Source.INTENT -> settings.isIntentExtractionEnabled()
        Source.LOCATION -> true
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ContextStoreBindings {
    @Binds abstract fun bindContextStore(impl: RoomContextStore): ContextStore
}

@Module
@InstallIn(SingletonComponent::class)
object ContextStoreModule {
    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext context: android.content.Context,
        passphraseProvider: DatabasePassphraseProvider
    ): CosmoDatabase {
        // net.zetetic:sqlcipher-android does not reliably auto-load libsqlcipher.so on every
        // device image — on Android 14+/MIUI we hit UnsatisfiedLinkError on
        // SQLiteConnection.nativeOpen even though the .so is bundled. Forcing the load before
        // the first Room operation fixes that. System.loadLibrary is idempotent.
        val previousPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        return try {
            System.loadLibrary("sqlcipher")
            Room
                .databaseBuilder(context, CosmoDatabase::class.java, "cosmo_context.db")
                .openHelperFactory(SupportOpenHelperFactory(passphraseProvider.passphrase()))
                .build()
        } finally {
            StrictMode.setThreadPolicy(previousPolicy)
        }
    }

    @Provides fun provideContextDao(database: CosmoDatabase): ContextEventDao = database.contextEventDao()
}

private fun ContextEvent.toEntity(json: Json): ContextEventEntity = ContextEventEntity(
    id = id,
    source = source,
    timestampMs = timestampMs,
    text = text,
    blobRef = blobRef,
    embedding = embedding?.toBytes(),
    retentionClass = retentionClass,
    sourceMetadata = json.encodeToString(sourceMetadata)
)

private fun ContextEventEntity.toEvent(json: Json): ContextEvent = ContextEvent(
    id = id,
    source = source,
    timestampMs = timestampMs,
    text = text,
    blobRef = blobRef,
    embedding = embedding?.toFloats(),
    retentionClass = retentionClass,
    sourceMetadata = runCatching { json.decodeFromString<Map<String, String>>(sourceMetadata) }.getOrDefault(emptyMap())
)

private fun FloatArray.toBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    forEach(buffer::putFloat)
    return buffer.array()
}

private fun ByteArray.toFloats(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.float }
}

private fun cosine(
    a: FloatArray,
    b: FloatArray
): Float {
    val n = minOf(a.size, b.size)
    if (n == 0) return 0f
    var dot = 0.0
    var aa = 0.0
    var bb = 0.0
    for (i in 0 until n) {
        dot += a[i] * b[i]
        aa += a[i] * a[i]
        bb += b[i] * b[i]
    }
    return if (aa == 0.0 || bb == 0.0) 0f else (dot / kotlin.math.sqrt(aa * bb)).toFloat()
}

private fun String.sanitizeFtsQuery(): String = trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .joinToString(" ") { it.replace("\"", "\"\"") + "*" }
