package br.com.wdc.shopping.nativeui.android.theme

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Material-style icons rendered via Canvas paths.
 * All icons are 24dp viewBox, rendered at requested size.
 */
object ShoppingIcons {

    fun localMall(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_LOCAL_MALL)

    fun shoppingCart(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_SHOPPING_CART)

    fun shoppingBag(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_SHOPPING_BAG)

    fun inventory(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_INVENTORY)

    fun arrowBack(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_ARROW_BACK)

    fun add(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_ADD)

    fun remove(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_REMOVE)

    fun addShoppingCart(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_ADD_SHOPPING_CART)

    fun chevronLeft(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_CHEVRON_LEFT)

    fun chevronRight(context: Context, sizeDp: Int, color: Int): Drawable =
        renderIcon(context, sizeDp, color, PATH_CHEVRON_RIGHT)

    private fun renderIcon(context: Context, sizeDp: Int, color: Int, pathData: String): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val path = parseSvgPath(pathData)
        val scale = sizePx / 24f
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        path.transform(matrix)
        canvas.drawPath(path, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun parseSvgPath(data: String): Path {
        val path = Path()
        val commands = Regex("[MmLlHhVvCcSsQqTtAaZz][^MmLlHhVvCcSsQqTtAaZz]*").findAll(data)
        var cx = 0f
        var cy = 0f
        var lastCx2 = 0f // last control point for S/s
        var lastCy2 = 0f
        var lastCmd = ' '
        for (match in commands) {
            val cmd = match.value[0]
            val args = Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)").findAll(match.value.substring(1)).map { it.value.toFloat() }.toList()
            when (cmd) {
                'M' -> { cx = args[0]; cy = args[1]; path.moveTo(cx, cy); var i = 2; while (i + 1 < args.size) { cx = args[i]; cy = args[i + 1]; path.lineTo(cx, cy); i += 2 } }
                'm' -> { cx += args[0]; cy += args[1]; path.moveTo(cx, cy); var i = 2; while (i + 1 < args.size) { cx += args[i]; cy += args[i + 1]; path.lineTo(cx, cy); i += 2 } }
                'L' -> { var i = 0; while (i + 1 < args.size) { cx = args[i]; cy = args[i + 1]; path.lineTo(cx, cy); i += 2 } }
                'l' -> { var i = 0; while (i + 1 < args.size) { cx += args[i]; cy += args[i + 1]; path.lineTo(cx, cy); i += 2 } }
                'H' -> { var i = 0; while (i < args.size) { cx = args[i]; path.lineTo(cx, cy); i++ } }
                'h' -> { var i = 0; while (i < args.size) { cx += args[i]; path.lineTo(cx, cy); i++ } }
                'V' -> { var i = 0; while (i < args.size) { cy = args[i]; path.lineTo(cx, cy); i++ } }
                'v' -> { var i = 0; while (i < args.size) { cy += args[i]; path.lineTo(cx, cy); i++ } }
                'C' -> { var i = 0; while (i + 5 < args.size) { lastCx2 = args[i + 2]; lastCy2 = args[i + 3]; path.cubicTo(args[i], args[i + 1], lastCx2, lastCy2, args[i + 4], args[i + 5]); cx = args[i + 4]; cy = args[i + 5]; i += 6 } }
                'c' -> { var i = 0; while (i + 5 < args.size) { lastCx2 = cx + args[i + 2]; lastCy2 = cy + args[i + 3]; path.cubicTo(cx + args[i], cy + args[i + 1], lastCx2, lastCy2, cx + args[i + 4], cy + args[i + 5]); cx += args[i + 4]; cy += args[i + 5]; i += 6 } }
                'S' -> { var i = 0; while (i + 3 < args.size) { val cx1 = if (lastCmd in "CcSs") 2 * cx - lastCx2 else cx; val cy1 = if (lastCmd in "CcSs") 2 * cy - lastCy2 else cy; lastCx2 = args[i]; lastCy2 = args[i + 1]; path.cubicTo(cx1, cy1, lastCx2, lastCy2, args[i + 2], args[i + 3]); cx = args[i + 2]; cy = args[i + 3]; i += 4 } }
                's' -> { var i = 0; while (i + 3 < args.size) { val cx1 = if (lastCmd in "CcSs") 2 * cx - lastCx2 else cx; val cy1 = if (lastCmd in "CcSs") 2 * cy - lastCy2 else cy; lastCx2 = cx + args[i]; lastCy2 = cy + args[i + 1]; path.cubicTo(cx1, cy1, lastCx2, lastCy2, cx + args[i + 2], cy + args[i + 3]); cx += args[i + 2]; cy += args[i + 3]; i += 4 } }
                'Q' -> { var i = 0; while (i + 3 < args.size) { path.quadTo(args[i], args[i + 1], args[i + 2], args[i + 3]); cx = args[i + 2]; cy = args[i + 3]; i += 4 } }
                'q' -> { var i = 0; while (i + 3 < args.size) { path.quadTo(cx + args[i], cy + args[i + 1], cx + args[i + 2], cy + args[i + 3]); cx += args[i + 2]; cy += args[i + 3]; i += 4 } }
                'Z', 'z' -> path.close()
            }
            lastCmd = cmd
        }
        return path
    }

    // Material Design icon SVG paths (24x24 viewBox)
    private const val PATH_LOCAL_MALL = "M19 6h-2c0-2.76-2.24-5-5-5S7 3.24 7 6H5c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zM12 3c1.66 0 3 1.34 3 3H9c0-1.66 1.34-3 3-3zm7 17H5V8h14v12zM12 12c-1.66 0-3-1.34-3-3H7c0 2.76 2.24 5 5 5s5-2.24 5-5h-2c0 1.66-1.34 3-3 3z"
    private const val PATH_SHOPPING_CART = "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"
    private const val PATH_SHOPPING_BAG = "M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 .55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"
    private const val PATH_INVENTORY = "M20 2H4c-1 0-2 .9-2 2v3.01c0 .72.43 1.34 1 1.69V20c0 1.1 1.1 2 2 2h14c.9 0 2-.9 2-2V8.7c.57-.35 1-.97 1-1.69V4c0-1.1-1-2-2-2zm-5 12H9v-2h6v2zm5-7H4V4h16v3z"
    private const val PATH_ARROW_BACK = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
    private const val PATH_ADD = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    private const val PATH_REMOVE = "M19 13H5v-2h14v2z"
    private const val PATH_ADD_SHOPPING_CART = "M11 9h2V6h3V4h-3V1h-2v3H8v2h3v3zm-4 9c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2zm-9.83-3.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25z"
    private const val PATH_CHEVRON_LEFT = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"
    private const val PATH_CHEVRON_RIGHT = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"
}
