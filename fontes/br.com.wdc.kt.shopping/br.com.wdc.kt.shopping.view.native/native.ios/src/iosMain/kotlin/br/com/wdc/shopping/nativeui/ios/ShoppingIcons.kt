package br.com.wdc.shopping.nativeui.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.*
import platform.UIKit.*

/**
 * Material Design icon SVG paths rendered as UIImage for iOS.
 * All icons use a 24x24 viewBox (standard MD icon size).
 */
@OptIn(ExperimentalForeignApi::class)
object ShoppingIcons {

    // Material Design icon paths (24x24 viewBox)
    private const val PATH_LOCAL_MALL =
        "M19 6h-2c0-2.76-2.24-5-5-5S7 3.24 7 6H5c-1.1 0-1.99.9-1.99 2L3 20c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-7-3c1.66 0 3 1.34 3 3H9c0-1.66 1.34-3 3-3zm0 10c-2.76 0-5-2.24-5-5h2c0 1.66 1.34 3 3 3s3-1.34 3-3h2c0 2.76-2.24 5-5 5z"

    private const val PATH_SHOPPING_CART =
        "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"

    private const val PATH_SHOPPING_BAG =
        "M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 .55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"

    private const val PATH_INVENTORY2 =
        "M20 2H4c-1 0-2 1-2 2v3.01c0 .72.43 1.34 1 1.69V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1-1-2-2-2zm-5 12H9v-2h6v2zm5-7H4V4h16v3z"

    private const val PATH_ARROW_BACK =
        "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"

    private const val PATH_ADD =
        "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"

    private const val PATH_REMOVE =
        "M19 13H5v-2h14v2z"

    private const val PATH_RECEIPT =
        "M18 17H6v-2h12v2zm0-4H6v-2h12v2zm0-4H6V7h12v2zM3 22l1.5-1.5L6 22l1.5-1.5L9 22l1.5-1.5L12 22l1.5-1.5L15 22l1.5-1.5L18 22l1.5-1.5L21 22V2l-1.5 1.5L18 2l-1.5 1.5L15 2l-1.5 1.5L12 2l-1.5 1.5L9 2 7.5 3.5 6 2 4.5 3.5 3 2v20z"

    private const val PATH_ADD_SHOPPING_CART =
        "M11 9h2V6h3V4h-3V1h-2v3H8v2h3v3zm-4 9c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2zm-9.83-3.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.86-7.01L19.42 4h-.01l-1.1 2-2.76 5H8.53l-.13-.27L6.16 6l-.95-2-.94-2H1v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25z"

    private const val PATH_CHECK_CIRCLE =
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"

    private const val PATH_CHEVRON_LEFT =
        "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"

    private const val PATH_CHEVRON_RIGHT =
        "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"

    /** Logo icon (LocalMall - shopping bag) */
    fun localMall(size: Double, color: UIColor): UIImage = renderIcon(PATH_LOCAL_MALL, size, color)

    /** Cart icon (ShoppingCart) */
    fun shoppingCart(size: Double, color: UIColor): UIImage = renderIcon(PATH_SHOPPING_CART, size, color)

    /** Products tab icon (ShoppingBag) */
    fun shoppingBag(size: Double, color: UIColor): UIImage = renderIcon(PATH_SHOPPING_BAG, size, color)

    /** Purchases tab icon (Inventory2) */
    fun inventory(size: Double, color: UIColor): UIImage = renderIcon(PATH_INVENTORY2, size, color)

    /** Back arrow */
    fun arrowBack(size: Double, color: UIColor): UIImage = renderIcon(PATH_ARROW_BACK, size, color)

    /** Plus icon */
    fun add(size: Double, color: UIColor): UIImage = renderIcon(PATH_ADD, size, color)

    /** Minus icon */
    fun remove(size: Double, color: UIColor): UIImage = renderIcon(PATH_REMOVE, size, color)

    /** Receipt icon */
    fun receipt(size: Double, color: UIColor): UIImage = renderIcon(PATH_RECEIPT, size, color)

    /** Add to cart icon */
    fun addShoppingCart(size: Double, color: UIColor): UIImage = renderIcon(PATH_ADD_SHOPPING_CART, size, color)

    /** Check circle icon */
    fun checkCircle(size: Double, color: UIColor): UIImage = renderIcon(PATH_CHECK_CIRCLE, size, color)

    /** Chevron left */
    fun chevronLeft(size: Double, color: UIColor): UIImage = renderIcon(PATH_CHEVRON_LEFT, size, color)

    /** Chevron right */
    fun chevronRight(size: Double, color: UIColor): UIImage = renderIcon(PATH_CHEVRON_RIGHT, size, color)

    // --- Rendering ---

    private fun renderIcon(pathData: String, size: Double, color: UIColor): UIImage {
        val scale = size / 24.0
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 0.0)
        val ctx = UIGraphicsGetCurrentContext() ?: run {
            UIGraphicsEndImageContext()
            return UIImage()
        }

        CGContextScaleCTM(ctx, scale, scale)
        color.setFill()

        val path = parseSvgPath(pathData)
        path.fill()

        val image = UIGraphicsGetImageFromCurrentImageContext() ?: UIImage()
        UIGraphicsEndImageContext()
        return image.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
    }

    // --- SVG Path Parser ---

    private fun parseSvgPath(d: String): UIBezierPath {
        val path = UIBezierPath()
        val tokens = tokenize(d)
        var i = 0
        var curX = 0.0
        var curY = 0.0
        var lastCpX = 0.0
        var lastCpY = 0.0
        var lastCmd = ' '

        fun nextDouble(): Double {
            return if (i < tokens.size) tokens[i++].toDouble() else 0.0
        }

        while (i < tokens.size) {
            val token = tokens[i]
            val cmd = if (token.length == 1 && token[0].isLetter()) {
                i++
                token[0]
            } else {
                // Implicit repeat of last command
                if (lastCmd == 'M') 'L'
                else if (lastCmd == 'm') 'l'
                else lastCmd
            }

            when (cmd) {
                'M' -> {
                    curX = nextDouble(); curY = nextDouble()
                    path.moveToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'm' -> {
                    curX += nextDouble(); curY += nextDouble()
                    path.moveToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'L' -> {
                    curX = nextDouble(); curY = nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'l' -> {
                    curX += nextDouble(); curY += nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'H' -> {
                    curX = nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'h' -> {
                    curX += nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'V' -> {
                    curY = nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'v' -> {
                    curY += nextDouble()
                    path.addLineToPoint(CGPointMake(curX, curY))
                    lastCmd = cmd
                }
                'C' -> {
                    val x1 = nextDouble(); val y1 = nextDouble()
                    val x2 = nextDouble(); val y2 = nextDouble()
                    val x = nextDouble(); val y = nextDouble()
                    path.addCurveToPoint(
                        CGPointMake(x, y),
                        controlPoint1 = CGPointMake(x1, y1),
                        controlPoint2 = CGPointMake(x2, y2)
                    )
                    lastCpX = x2; lastCpY = y2
                    curX = x; curY = y
                    lastCmd = cmd
                }
                'c' -> {
                    val x1 = curX + nextDouble(); val y1 = curY + nextDouble()
                    val x2 = curX + nextDouble(); val y2 = curY + nextDouble()
                    val x = curX + nextDouble(); val y = curY + nextDouble()
                    path.addCurveToPoint(
                        CGPointMake(x, y),
                        controlPoint1 = CGPointMake(x1, y1),
                        controlPoint2 = CGPointMake(x2, y2)
                    )
                    lastCpX = x2; lastCpY = y2
                    curX = x; curY = y
                    lastCmd = cmd
                }
                'S' -> {
                    val rx = 2.0 * curX - lastCpX
                    val ry = 2.0 * curY - lastCpY
                    val x2 = nextDouble(); val y2 = nextDouble()
                    val x = nextDouble(); val y = nextDouble()
                    path.addCurveToPoint(
                        CGPointMake(x, y),
                        controlPoint1 = CGPointMake(rx, ry),
                        controlPoint2 = CGPointMake(x2, y2)
                    )
                    lastCpX = x2; lastCpY = y2
                    curX = x; curY = y
                    lastCmd = cmd
                }
                's' -> {
                    val rx = 2.0 * curX - lastCpX
                    val ry = 2.0 * curY - lastCpY
                    val x2 = curX + nextDouble(); val y2 = curY + nextDouble()
                    val x = curX + nextDouble(); val y = curY + nextDouble()
                    path.addCurveToPoint(
                        CGPointMake(x, y),
                        controlPoint1 = CGPointMake(rx, ry),
                        controlPoint2 = CGPointMake(x2, y2)
                    )
                    lastCpX = x2; lastCpY = y2
                    curX = x; curY = y
                    lastCmd = cmd
                }
                'Z', 'z' -> {
                    path.closePath()
                    lastCmd = cmd
                }
            }
        }
        return path
    }

    private fun tokenize(d: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < d.length) {
            val ch = d[i]
            when {
                ch.isWhitespace() || ch == ',' -> i++
                ch.isLetter() -> {
                    tokens.add(ch.toString())
                    i++
                }
                else -> {
                    // Number (may start with - or .)
                    val start = i
                    if (ch == '-' || ch == '+') i++
                    var hasDot = false
                    while (i < d.length) {
                        val c = d[i]
                        if (c == '.' && !hasDot) { hasDot = true; i++ }
                        else if (c.isDigit()) i++
                        else if (c == 'e' || c == 'E') {
                            i++
                            if (i < d.length && (d[i] == '+' || d[i] == '-')) i++
                        }
                        else break
                    }
                    if (i > start) tokens.add(d.substring(start, i))
                    else i++ // skip unexpected char
                }
            }
        }
        return tokens
    }
}
