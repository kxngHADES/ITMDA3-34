package com.swiftq.staff.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.swiftq.staff.R

// A LinearLayout that stops growing past app:maxWidth, so screens stay
// readable on tablets and in landscape while filling small phones.
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var maxWidth: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.MaxWidthLinearLayout,
            defStyleAttr,
            0
        ).apply {
            maxWidth = getDimensionPixelSize(
                R.styleable.MaxWidthLinearLayout_maxWidth,
                0
            )
            recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)

        val constrainedSpec = if (maxWidth in 1 until availableWidth) {
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY)
        } else {
            widthMeasureSpec
        }

        super.onMeasure(constrainedSpec, heightMeasureSpec)
    }
}

// Pads a view by the system bars (and keyboard) on top of its own padding,
// because targetSdk 35+ always draws edge to edge.
fun View.applySystemBarInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = true,
    includeIme: Boolean = false
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        var types = androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()

        if (includeIme) {
            types = types or androidx.core.view.WindowInsetsCompat.Type.ime()
        }

        val bars = insets.getInsets(types)

        view.setPadding(
            initialLeft + if (horizontal) bars.left else 0,
            initialTop + if (top) bars.top else 0,
            initialRight + if (horizontal) bars.right else 0,
            initialBottom + if (bottom) bars.bottom else 0
        )

        insets
    }
}
