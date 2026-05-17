package com.google.research.air.cosmo.presence.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

class AndroidPresenceOverlayWindowHost(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onEvent: (PresenceOverlayEvent) -> Unit
) : PresenceOverlayWindowHost {
    private var composeView: ComposeView? = null
    private var attached: Boolean = false

    override fun show(state: PresenceOverlayUiState): Result<Unit> =
        runCatching {
            val view = ensureView(state)
            if (!attached) {
                windowManager.addView(view, layoutParamsFor(state.route))
                attached = true
            } else {
                windowManager.updateViewLayout(view, layoutParamsFor(state.route))
            }
        }

    override fun update(state: PresenceOverlayUiState): Result<Unit> =
        runCatching {
            val view = ensureView(state)
            if (attached) {
                windowManager.updateViewLayout(view, layoutParamsFor(state.route))
            } else {
                windowManager.addView(view, layoutParamsFor(state.route))
                attached = true
            }
        }

    override fun hide(): Result<Unit> =
        runCatching {
            val view = composeView ?: return@runCatching
            if (attached) {
                windowManager.removeViewImmediate(view)
            }
            attached = false
            view.disposeComposition()
            composeView = null
        }

    private fun ensureView(state: PresenceOverlayUiState): ComposeView {
        val existing = composeView
        if (existing != null) {
            existing.setOverlayContent(state)
            return existing
        }
        return ComposeView(context).also { view ->
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.isFocusable = PresenceOverlayFocusPolicy.acceptsBackKey(state.route)
            view.isFocusableInTouchMode = PresenceOverlayFocusPolicy.acceptsBackKey(state.route)
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onEvent(PresenceOverlayEvent.Back)
                    true
                } else {
                    false
                }
            }
            view.setOverlayContent(state)
            composeView = view
        }
    }

    private fun ComposeView.setOverlayContent(state: PresenceOverlayUiState) {
        isFocusable = PresenceOverlayFocusPolicy.acceptsBackKey(state.route)
        isFocusableInTouchMode = PresenceOverlayFocusPolicy.acceptsBackKey(state.route)
        if (PresenceOverlayFocusPolicy.acceptsBackKey(state.route)) requestFocus()
        setContent {
            PresenceOverlayContent(uiState = state, onEvent = onEvent)
        }
    }

    private fun layoutParamsFor(route: PresenceOverlayRoute): WindowManager.LayoutParams {
        val focusFlag =
            if (PresenceOverlayFocusPolicy.acceptsBackKey(route)) {
                0
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                focusFlag,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "CosmoPresenceOverlay"
        }
    }
}
