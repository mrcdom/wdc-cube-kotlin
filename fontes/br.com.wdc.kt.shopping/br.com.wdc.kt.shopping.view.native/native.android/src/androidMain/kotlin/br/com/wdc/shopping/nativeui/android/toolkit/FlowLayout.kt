package br.com.wdc.shopping.nativeui.android.toolkit

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * A ViewGroup that arranges children in a grid-like flow.
 * Calculates the number of columns based on available width and [minChildWidth].
 * Children are stretched equally to fill the row.
 */
class FlowLayout(context: Context) : ViewGroup(context) {

    /** Minimum width (in pixels) for each child. Columns = floor(availableWidth / minChildWidth). */
    var minChildWidth: Int = 180

    /** Horizontal spacing between children in pixels. */
    var horizontalSpacing: Int = 0

    /** Vertical spacing between rows in pixels. */
    var verticalSpacing: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val childCount = childCount

        if (childCount == 0 || availableWidth <= 0) {
            setMeasuredDimension(
                resolveSize(paddingLeft + paddingRight, widthMeasureSpec),
                resolveSize(paddingTop + paddingBottom, heightMeasureSpec)
            )
            return
        }

        // Calculate columns
        val columns = calculateColumns(availableWidth)
        val childWidth = calculateChildWidth(availableWidth, columns)

        var totalHeight = paddingTop + paddingBottom
        var rowHeight = 0
        var rowCount = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            val childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
            child.measure(childWidthSpec, childHeightSpec)

            val col = rowCount % columns
            if (col == 0 && rowCount > 0) {
                // Not the first item, but starting a new conceptual row count
            }

            rowHeight = maxOf(rowHeight, child.measuredHeight)

            if ((rowCount + 1) % columns == 0 || i == childCount - 1) {
                // End of row or last item
                totalHeight += rowHeight
                if (i < childCount - 1) {
                    totalHeight += verticalSpacing
                }
                rowHeight = 0
            }
            rowCount++
        }

        // Recalculate properly with row tracking
        totalHeight = paddingTop + paddingBottom
        var visibleIndex = 0
        var currentRowMaxHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            currentRowMaxHeight = maxOf(currentRowMaxHeight, child.measuredHeight)
            val col = visibleIndex % columns

            if (col == columns - 1 || i == lastVisibleIndex()) {
                totalHeight += currentRowMaxHeight
                if (i < lastVisibleIndex()) {
                    totalHeight += verticalSpacing
                }
                currentRowMaxHeight = 0
            }
            visibleIndex++
        }

        setMeasuredDimension(
            resolveSize(availableWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableWidth = r - l - paddingLeft - paddingRight
        val childCount = childCount

        if (childCount == 0 || availableWidth <= 0) return

        val columns = calculateColumns(availableWidth)
        val childWidth = calculateChildWidth(availableWidth, columns)

        var x = paddingLeft
        var y = paddingTop
        var col = 0
        var rowMaxHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            if (col >= columns) {
                // New row
                y += rowMaxHeight + verticalSpacing
                x = paddingLeft
                col = 0
                rowMaxHeight = 0
            }

            val childHeight = child.measuredHeight
            child.layout(x, y, x + childWidth, y + childHeight)
            rowMaxHeight = maxOf(rowMaxHeight, childHeight)

            x += childWidth + horizontalSpacing
            col++
        }
    }

    private fun calculateColumns(availableWidth: Int): Int {
        if (minChildWidth <= 0) return 1
        // Account for spacing: cols items need (cols-1) spacings
        // availableWidth >= cols * minChildWidth + (cols - 1) * horizontalSpacing
        var cols = 1
        while ((cols + 1) * minChildWidth + cols * horizontalSpacing <= availableWidth) {
            cols++
        }
        return cols
    }

    private fun calculateChildWidth(availableWidth: Int, columns: Int): Int {
        val totalSpacing = (columns - 1) * horizontalSpacing
        return (availableWidth - totalSpacing) / columns
    }

    private fun lastVisibleIndex(): Int {
        for (i in childCount - 1 downTo 0) {
            if (getChildAt(i).visibility != View.GONE) return i
        }
        return -1
    }
}
