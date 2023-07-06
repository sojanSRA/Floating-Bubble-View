package com.torrydo.floatingbubbleview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PointF
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.ImageView
import androidx.compose.ui.platform.ComposeView
import com.torrydo.floatingbubbleview.databinding.BubbleBinding

internal class FloatingBubbleView(
    private val builder: FloatingBubble.Builder,
) : BaseFloatingViewBinding<BubbleBinding>(
    context = builder.context,
    initializer = BubbleBinding.inflate(LayoutInflater.from(builder.context))
) {

    /**
     * store previous point for later usage, reset after finger down
     * */
    private val prevPoint = Point(0, 0)
    private val rawPointOnDown = PointF(0f, 0f)
    private val newPoint = Point(0, 0)

    private var halfScreenWidth = ScreenInfo.widthPx / 2

    private var orientation = -1

    init {

        orientation = if (ScreenInfo.heightPx >= ScreenInfo.widthPx) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }

        setupLayoutParams()
        setupBubbleProperties()
        customTouch()

    }

    private var isAnimatingToEdge = false
    fun animateIconToEdge(onFinished: (() -> Unit)? = null) {
        if (isAnimatingToEdge) return

        val bubbleWidthCompat = if (builder.bubbleView != null) {
            builder.bubbleView!!.width
        } else {
            width
        }

        isAnimatingToEdge = true
        val iconX = binding.root.getXYPointOnScreen().x

        val isOnTheLeftSide = iconX + bubbleWidthCompat / 2 < halfScreenWidth
        val startX: Int
        val endX: Int
        if (isOnTheLeftSide) {
            startX = iconX
            endX = 0
        } else {
            startX = iconX
            endX = ScreenInfo.widthPx - bubbleWidthCompat
        }

        AnimHelper.startSpringX(
            startValue = startX.toFloat(),
            finalPosition = endX.toFloat(),
            event = object : AnimHelper.Event {
                override fun onUpdate(float: Float) {
                    try {
                        windowParams.x = float.toInt()
                        update()
                    } catch (_: Exception) {
                    }
                }

                override fun onEnd() {
                    isAnimatingToEdge = false
                    onFinished?.invoke()
                }
            }
        )
    }

    // private func --------------------------------------------------------------------------------

    private fun setupBubbleProperties() {

        windowParams.apply {
            x = builder.startPoint.x
            y = builder.startPoint.y
        }

        if (builder.bubbleView != null) {

            binding.bubbleRoot.addView(builder.bubbleView)

            return
        }

        if (builder.composeLifecycleOwner != null) {

            builder.bubbleView = binding.bubbleRoot.findViewById<ComposeView>(R.id.view_compose).apply {
                setContent {
                    builder.composeView!!()
                }
                visibility = View.VISIBLE
            }

            builder.composeLifecycleOwner?.attachToDecorView(binding.bubbleRoot)

            return
        }

    }

    fun updateLocationUI(x: Float, y: Float) {
        val mIconDeltaX = x - rawPointOnDown.x
        val mIconDeltaY = y - rawPointOnDown.y

        newPoint.x = prevPoint.x + mIconDeltaX.toInt()
        newPoint.y = prevPoint.y + mIconDeltaY.toInt()

        //region prevent bubble Y point move outside the screen
        val safeTopY = 0
        val safeBottomY =
            ScreenInfo.heightPx - ScreenInfo.softNavBarHeightPx - ScreenInfo.statusBarHeightPx - height
        val isAboveStatusBar = newPoint.y < safeTopY
        val isUnderSoftNavBar = newPoint.y > safeBottomY
        if (isAboveStatusBar) {
            newPoint.y = safeTopY
        } else if (isUnderSoftNavBar) {
            if (ScreenInfo.isPortrait) {
                newPoint.y = safeBottomY
            } else if (newPoint.y - ScreenInfo.softNavBarHeightPx > safeBottomY) {
                newPoint.y = safeBottomY + (ScreenInfo.softNavBarHeightPx)
            }
        }
        //endregion

        windowParams.x = newPoint.x
        windowParams.y = newPoint.y
        update()
    }

    /**
     * set location without updating UI
     * */
    fun setLocation(x: Float, y: Float) {
        newPoint.x = x.toInt()
        newPoint.y = y.toInt()
    }

    fun rawLocationOnScreen(): Pair<Float, Float> {
        return Pair(newPoint.x.toFloat(), newPoint.y.toFloat())
    }

    /**
     * pass close bubble point
     * */
    fun animateTo(x: Float, y: Float) {
        AnimHelper.animateSpringPath(
            startX = newPoint.x.toFloat(),
            startY = newPoint.y.toFloat(),
            endX = x,
            endY = y,
            event = object : AnimHelper.Event {
                override fun onUpdatePoint(x: Float, y: Float) {

                    windowParams.x = x.toInt()
                    windowParams.y = y.toInt()

//                    builder.listener?.onMove(x.toFloat(), y.toFloat()) // don't call this line, it'll spam multiple MotionEvent.OnActionMove
                    update()

                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun customTouch() {
        fun onActionDown(motionEvent: MotionEvent) {
            prevPoint.x = windowParams.x
            prevPoint.y = windowParams.y

            rawPointOnDown.x = motionEvent.rawX
            rawPointOnDown.y = motionEvent.rawY

            builder.listener?.onDown(motionEvent.rawX, motionEvent.rawY)
        }

        fun onActionMove(motionEvent: MotionEvent) {
            builder.listener?.onMove(motionEvent.rawX, motionEvent.rawY)
        }

        fun onActionUp(motionEvent: MotionEvent) {
            builder.listener?.onUp(motionEvent.rawX, motionEvent.rawY)
        }

        // listen actions --------------------------------------------------------------------------

        val gestureDetector = GestureDetector(builder.context, object : SimpleOnGestureListener() {

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                builder.listener?.onClick()
                return super.onSingleTapUp(e)
            }

        })

        binding.bubbleRoot.apply {

            afterMeasured { updateGestureExclusion(builder.context) }

            setOnTouchListener { _, motionEvent ->

                gestureDetector.onTouchEvent(motionEvent)

                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> onActionDown(motionEvent)
                    MotionEvent.ACTION_MOVE -> onActionMove(motionEvent)
                    MotionEvent.ACTION_UP -> onActionUp(motionEvent)
                }

                return@setOnTouchListener true
            }
        }
    }

    override fun setupLayoutParams() {
        super.setupLayoutParams()

        windowParams.apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS


            builder.bubbleStyle?.let {
                windowAnimations = it
            }

        }
    }
}