package com.google.research.air.cosmo.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.research.air.cosmo.contentcapture.ScreenObservation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun interface AccessibilityEventListener {
    fun onAccessibilityEvent(event: AccessibilityEvent)
}

interface AccessibilityEventSource {
    val connected: StateFlow<Boolean>

    fun addListener(listener: AccessibilityEventListener)

    fun removeListener(listener: AccessibilityEventListener)

    fun activeScreenObservation(): ScreenObservation?
}

@Singleton
class ConnectedAccessibilityServiceProvider
@Inject
constructor() : AccessibilityEventSource {
    private val listeners = CopyOnWriteArraySet<AccessibilityEventListener>()
    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected

    @Volatile private var service: CosmoAccessibilityService? = null

    fun attach(service: CosmoAccessibilityService) {
        this.service = service
        _connected.value = true
    }

    fun detach(service: CosmoAccessibilityService) {
        if (this.service === service) {
            this.service = null
            _connected.value = false
        }
    }

    override fun addListener(listener: AccessibilityEventListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AccessibilityEventListener) {
        listeners.remove(listener)
    }

    fun dispatch(event: AccessibilityEvent) {
        listeners.forEach { it.onAccessibilityEvent(event) }
    }

    override fun activeScreenObservation(): ScreenObservation? {
        val root = service?.rootInActiveWindow ?: return null
        val text = root.collectText().joinToString("\n")
        return ScreenObservation(
            timestampMillis = System.currentTimeMillis(),
            packageName = root.packageName?.toString().orEmpty(),
            text = text,
            viewHierarchy = root.flatten()
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityModule {
    @Binds abstract fun bindAccessibilityEventSource(impl: ConnectedAccessibilityServiceProvider): AccessibilityEventSource
}

@AndroidEntryPoint
class CosmoAccessibilityService : AccessibilityService() {
    @Inject lateinit var provider: ConnectedAccessibilityServiceProvider

    override fun onServiceConnected() {
        provider.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        provider.dispatch(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        provider.detach(this)
        super.onDestroy()
    }
}

private fun AccessibilityNodeInfo.collectText(into: MutableList<String> = mutableListOf()): List<String> {
    text?.toString()?.takeIf { it.isNotBlank() }?.let(into::add)
    contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(into::add)
    for (i in 0 until childCount) getChild(i)?.collectText(into)
    return into
}

private fun AccessibilityNodeInfo.flatten(depth: Int = 0): String {
    val own = "${" ".repeat(
        depth * 2
    )}${className?.toString().orEmpty()} ${viewIdResourceName.orEmpty()} ${text?.toString().orEmpty()} ${contentDescription?.toString().orEmpty()}"
    val children = (0 until childCount).mapNotNull { getChild(it)?.flatten(depth + 1) }
    return (listOf(own) + children).joinToString("\n")
}
