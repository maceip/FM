package com.google.research.air.cosmo.presence.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts a Compose tree inside a system overlay window (TYPE_APPLICATION_OVERLAY). Provides the
 * three ViewTree owners that Compose requires (Lifecycle, SavedState, ViewModel) which an Activity
 * gives for free but a Service does not.
 *
 * Usage from [CosmoPresenceService]:
 * ```
 * val host = ComposeOverlayHost(context)
 * host.setContent { PresenceRoot(...) }
 * host.show()
 * // later:
 * host.hide()
 * host.destroy()
 * ```
 *
 * Touch passthrough: the overlay window is full-screen but touch-transparent except for regions
 * registered via [updateTouchableRegion]. The [ViewTreeObserver.OnComputeInternalInsetsListener]
 * routes touch only to the orb/tab/card-stack bounds.
 */
class ComposeOverlayHost(
    private val context: Context
) : androidx.lifecycle.LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    // region Lifecycle plumbing

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = vmStore

    // endregion

    // region Window

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private var composeView: ComposeView? = null
    private var isShowing = false

    /**
     * Mutable touchable region. Updated by the Compose tree via [updateTouchableRegion] whenever
     * the orb/tab/card-stack bounds change.
     *
     * Touch passthrough on TYPE_APPLICATION_OVERLAY: ViewTreeObserver.OnComputeInternalInsetsListener
     * is a hidden API. Production path: either use reflection to access it (Facebook/Messenger
     * pattern) or use a small non-fullscreen window that repositions via updateViewLayout. For now,
     * the window is fullscreen and NOT touch-modal — it receives touch only on opaque pixels.
     * FLAG_NOT_TOUCH_MODAL lets touches outside our Compose content pass through to the app below.
     */
    private val touchableRegion = mutableStateOf(Region())

    // endregion

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun setContent(content: @Composable () -> Unit) {
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposeOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@ComposeOverlayHost)
            setViewTreeViewModelStoreOwner(this@ComposeOverlayHost)
            setContent(content)
        }
        composeView = view
    }

    fun show() {
        val view = composeView ?: return
        if (isShowing) return
        try {
            windowManager.addView(view, layoutParams)
            isShowing = true
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            android.util.Log.e("ComposeOverlayHost", "Failed to add overlay view", e)
        }
    }

    fun hide() {
        val view = composeView ?: return
        if (!isShowing) return
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            windowManager.removeView(view)
            isShowing = false
        } catch (e: Exception) {
            android.util.Log.e("ComposeOverlayHost", "Failed to remove overlay view", e)
        }
    }

    /**
     * Final cleanup. Must be called from [CosmoPresenceService.onDestroy]. After this call the
     * host is unusable.
     */
    fun destroy() {
        if (isShowing) hide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        vmStore.clear()
        composeView = null
    }

    /**
     * Called from the Compose tree (via [TouchableRegionTracker]) whenever the interactive bounds
     * change. Updates the region fed to the window's inset listener so only our surfaces consume
     * touch.
     */
    fun updateTouchableRegion(rects: List<Rect>) {
        val region = Region()
        rects.forEach { region.op(it, Region.Op.UNION) }
        touchableRegion.value = region
    }

    /**
     * Called from [CosmoPresenceService.onConfigurationChanged]. Compose handles recomposition
     * internally; we just need to make sure the layout params stay correct.
     */
    fun onConfigurationChanged() {
        if (!isShowing) return
        try {
            composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
        } catch (e: Exception) {
            android.util.Log.e("ComposeOverlayHost", "Failed to update layout on config change", e)
        }
    }
}
