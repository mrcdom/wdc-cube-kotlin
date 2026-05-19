package br.com.wdc.shopping.nativeui.ios.toolkit

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIView

/**
 * A simple flow layout view that arranges children in a responsive grid
 * using Auto Layout constraints.
 *
 * Calculates the number of columns based on available width and [minChildWidth].
 * Children are stretched equally to fill the row.
 * Children must have internal constraints that define their height
 * (e.g., a pinned vStack with fixed-height subviews).
 *
 * This is a lightweight alternative to UICollectionView, suitable for a small
 * number of children managed via ListSlot (no virtualization).
 */
@OptIn(ExperimentalForeignApi::class)
class FlowLayoutView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    /** Minimum width (in points) for each child. Columns = floor(availableWidth / minChildWidth). */
    var minChildWidth: Double = 160.0

    /** Horizontal spacing between children in points. */
    var horizontalSpacing: Double = 12.0

    /** Vertical spacing between rows in points. */
    var verticalSpacing: Double = 12.0

    private var layoutConstraints = mutableListOf<NSLayoutConstraint>()
    private var lastLayoutWidth: Double = -1.0
    private var lastChildCount: Int = -1

    init {
        translatesAutoresizingMaskIntoConstraints = false
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val currentWidth = bounds.useContents { size.width }
        val children = visibleChildren()
        if (currentWidth > 0.0 && (currentWidth != lastLayoutWidth || children.size != lastChildCount)) {
            lastLayoutWidth = currentWidth
            lastChildCount = children.size
            rebuildConstraints(currentWidth, children)
        }
    }

    override fun intrinsicContentSize(): CValue<CGSize> {
        return CGSizeMake(-1.0, -1.0) // Height determined by constraints
    }

    private fun rebuildConstraints(availableWidth: Double, children: List<UIView>) {
        // Remove old layout constraints
        NSLayoutConstraint.deactivateConstraints(layoutConstraints)
        layoutConstraints.clear()

        if (children.isEmpty()) return

        val columns = calculateColumns(availableWidth)
        val childWidth = calculateChildWidth(availableWidth, columns)

        val newConstraints = mutableListOf<NSLayoutConstraint>()

        // Track the bottom anchors of each row for vertical chaining
        var rowStartIndex = 0
        var col = 0
        var prevRowBottomAnchor = topAnchor // Start from FlowLayoutView's top

        var rowChildren = mutableListOf<UIView>()

        for ((index, child) in children.withIndex()) {
            child.translatesAutoresizingMaskIntoConstraints = false
            rowChildren.add(child)
            col++

            val isEndOfRow = (col >= columns) || (index == children.size - 1)
            if (isEndOfRow) {
                // Layout this row
                val isFirstRow = (prevRowBottomAnchor === topAnchor)
                val topOffset = if (isFirstRow) 0.0 else verticalSpacing

                for ((colIdx, rowChild) in rowChildren.withIndex()) {
                    // Width constraint
                    newConstraints.add(rowChild.widthAnchor.constraintEqualToConstant(childWidth))
                    // Top constraint
                    newConstraints.add(rowChild.topAnchor.constraintEqualToAnchor(prevRowBottomAnchor, topOffset))
                    // Leading constraint
                    val leadingOffset = colIdx * (childWidth + horizontalSpacing)
                    newConstraints.add(rowChild.leadingAnchor.constraintEqualToAnchor(leadingAnchor, leadingOffset))
                }

                // Use the first child of this row as the bottom reference for the next row
                // (all children in a row should have similar height; pin bottom to tallest via greaterThanOrEqual)
                // For simplicity, chain from each child's bottom for the last row
                prevRowBottomAnchor = rowChildren[0].bottomAnchor

                // Reset for next row
                rowChildren = mutableListOf()
                col = 0
            }
        }

        // Pin the bottom of the last row to FlowLayoutView's bottom
        // Use the bottom of ALL children in the last full/partial row
        val lastRowStart = children.size - ((children.size % columns).let { if (it == 0) columns else it })
        for (i in lastRowStart until children.size) {
            newConstraints.add(children[i].bottomAnchor.constraintLessThanOrEqualToAnchor(bottomAnchor))
        }
        // At least one child pins bottom with equality to define height
        if (children.isNotEmpty()) {
            val bottomPin = children.last().bottomAnchor.constraintEqualToAnchor(bottomAnchor)
            bottomPin.priority = 999.0f
            newConstraints.add(bottomPin)
        }

        layoutConstraints = newConstraints.toMutableList()
        NSLayoutConstraint.activateConstraints(layoutConstraints)
    }

    private fun calculateColumns(availableWidth: Double): Int {
        if (minChildWidth <= 0.0) return 1
        var cols = 1
        while ((cols + 1) * minChildWidth + cols * horizontalSpacing <= availableWidth) {
            cols++
        }
        return cols
    }

    private fun calculateChildWidth(availableWidth: Double, columns: Int): Double {
        val totalSpacing = (columns - 1) * horizontalSpacing
        return (availableWidth - totalSpacing) / columns
    }

    private fun visibleChildren(): List<UIView> {
        val result = mutableListOf<UIView>()
        val count = subviews.size
        for (i in 0 until count) {
            val child = subviews[i] as UIView
            if (!child.isHidden()) result.add(child)
        }
        return result
    }
}
