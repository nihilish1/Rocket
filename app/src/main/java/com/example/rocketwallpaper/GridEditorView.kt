package com.example.rocketwallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * A mini visual copy of the wallpaper grid. Green cells are free, red cells
 * are "blocked" (a widget or anything off-grid sits there). Tap a cell to
 * toggle it. Hold this next to your real home screen and tap the cells that
 * match your widgets.
 */
class GridEditorView(context: Context) : View(context) {
    var columns = RocketSettings.DEFAULT_COLUMNS
    var rows = RocketSettings.DEFAULT_ROWS
    val blockedCells = mutableSetOf<Pair<Int, Int>>()
    var onChanged: ((Set<Pair<Int, Int>>) -> Unit)? = null

    private val linePaint = Paint().apply {
        color = Color.parseColor("#999999")
        strokeWidth = 2f
    }
    private val blockedPaint = Paint().apply {
        color = Color.parseColor("#E53935")
        alpha = 170
    }
    private val freePaint = Paint().apply {
        color = Color.parseColor("#43A047")
        alpha = 60
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 1.9f).toInt() // roughly a phone's aspect ratio
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (columns <= 0 || rows <= 0) return
        val cellW = width / columns.toFloat()
        val cellH = height / rows.toFloat()

        for (c in 0 until columns) {
            for (r in 0 until rows) {
                val left = c * cellW
                val top = r * cellH
                val paint = if ((c to r) in blockedCells) blockedPaint else freePaint
                canvas.drawRect(left, top, left + cellW, top + cellH, paint)
            }
        }
        for (c in 0..columns) {
            val x = c * cellW
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
        }
        for (r in 0..rows) {
            val y = r * cellH
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (columns <= 0 || rows <= 0) return true
            val cellW = width / columns.toFloat()
            val cellH = height / rows.toFloat()
            val c = (event.x / cellW).toInt().coerceIn(0, columns - 1)
            val r = (event.y / cellH).toInt().coerceIn(0, rows - 1)
            val key = c to r
            if (key in blockedCells) blockedCells.remove(key) else blockedCells.add(key)
            invalidate()
            onChanged?.invoke(blockedCells)
            return true
        }
        return super.onTouchEvent(event)
    }
}
