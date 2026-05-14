package com.google.research.air.cosmo.memory

import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

data class MemoryEntry(
    val text: String,
    val tags: List<String>
)

data class SearchFilter(
    val tags: Set<String> = emptySet()
)

class MemoryStorageException(
    message: String
) : Exception(message)

interface MemoryStorage {
    suspend fun add(entry: MemoryEntry)

    suspend fun clear()

    suspend fun search(
        query: String,
        limit: Int,
        filter: SearchFilter = SearchFilter()
    ): List<MemoryEntry>
}

class ContextMemoryStorage
@Inject
constructor(
    private val contextStore: ContextStore
) : MemoryStorage {
    override suspend fun add(entry: MemoryEntry) {
        contextStore.insert(
            ContextEvent(
                id = UUID.randomUUID().toString(),
                source = Source.INTENT,
                timestampMs = System.currentTimeMillis(),
                text = entry.text,
                blobRef = null,
                embedding = null,
                retentionClass = RetentionClass.LONG,
                sourceMetadata = mapOf("tags" to entry.tags.joinToString(","))
            )
        )
    }

    override suspend fun clear() {
        contextStore.purgeSource(Source.INTENT)
    }

    override suspend fun search(
        query: String,
        limit: Int,
        filter: SearchFilter
    ): List<MemoryEntry> = contextStore.queryByText(query, limit).map {
        MemoryEntry(
            it.text.orEmpty(),
            it.sourceMetadata["tags"]
                ?.split(",")
                ?.filter(String::isNotBlank)
                .orEmpty()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {
    @Binds abstract fun bindMemory(impl: ContextMemoryStorage): MemoryStorage
}
