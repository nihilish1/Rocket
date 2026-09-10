package com.example.rocketwallpaper

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.random.Random

/**
 * All grid, speed, and size settings are configurable from the app's
 * Settings screen (see RocketSettings / SettingsActivity) and are read live
 * here, so changes take effect immediately without reinstalling anything.
 *
 * Flight behavior: the rocket only ever makes a single straight pass at a
 * time -- either purely horizontal or purely vertical -- starting fully
 * off-screen on one edge and ending fully off-screen on the far edge. It
 * never turns mid-flight. Each new pass alternates between horizontal and
 * vertical, matching a row or column of the configured grid.
 */
class RocketWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RocketEngine()

    private inner class RocketEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { drawFrame() }
        private var visible = false

        private val rocketBitmap = BitmapFactory.decodeResource(resources, R.drawable.rocket)

        // The sprite's own resting heading, in degrees, measured clockwise
        // from due east (0 = pointing right, 90 = pointing down). The
        // supplied artwork points up-and-to-the-right, roughly -45 degrees.
        private val spriteBaseHeadingDeg = -45f

        private var screenW = 0
        private var screenH = 0
        private var cellW = 0f
        private var cellH = 0f
        private var gridTop = 0f
        private var gridLeft = 0f

        // Live settings, refreshed from RocketSettings.
        private var columns = RocketSettings.DEFAULT_COLUMNS
        private var rows = RocketSettings.DEFAULT_ROWS
        private var topMarginFraction = RocketSettings.DEFAULT_TOP_MARGIN_PCT / 100f
        private var bottomMarginFraction = RocketSettings.DEFAULT_BOTTOM_MARGIN_PCT / 100f
        private var sideMarginFraction = RocketSettings.DEFAULT_SIDE_MARGIN_PCT / 100f
        private var speedPxPerSec = RocketSettings.DEFAULT_SPEED.toFloat()
        private var scaleFactor = RocketSettings.DEFAULT_SCALE_PCT / 100f
        private var blockedCells: Set<Pair<Int, Int>> = emptySet()

        private var posX = 0f
        private var posY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private var headingDeg = 0f

        private var horizontalNext = true // alternates each pass
        private var lastFrameTimeNs = 0L

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshSettings()
            if (screenW > 0 && screenH > 0) {
                recomputeGrid()
                startNewPass()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            refreshSettings()
            RocketSettings.prefs(this@RocketWallpaperService)
                .registerOnSharedPreferenceChangeListener(prefsListener)
        }

        override fun onDestroy() {
            super.onDestroy()
            RocketSettings.prefs(this@RocketWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                lastFrameTimeNs = System.nanoTime()
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            screenW = width
            screenH = height
            refreshSettings()
            recomputeGrid()
            startNewPass()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        private fun refreshSettings() {
            val ctx = this@RocketWallpaperService
            columns = RocketSettings.getColumns(ctx)
            rows = RocketSettings.getRows(ctx)
            topMarginFraction = RocketSettings.getTopMarginPct(ctx) / 100f
            bottomMarginFraction = RocketSettings.getBottomMarginPct(ctx) / 100f
            sideMarginFraction = RocketSettings.getSideMarginPct(ctx) / 100f
            speedPxPerSec = RocketSettings.getSpeed(ctx).toFloat()
            scaleFactor = RocketSettings.getScalePct(ctx) / 100f
            blockedCells = RocketSettings.getBlockedCells(ctx)
        }

        private fun recomputeGrid() {
            gridTop = screenH * topMarginFraction
            val gridBottom = screenH * (1f - bottomMarginFraction)
            gridLeft = screenW * sideMarginFraction
            val gridRight = screenW * (1f - sideMarginFraction)
            cellW = (gridRight - gridLeft) / columns.toFloat()
            cellH = (gridBottom - gridTop) / rows.toFloat()
        }

        private fun cellCenterX(col: Int) = gridLeft + cellW * (col + 0.5f)
        private fun cellCenterY(row: Int) = gridTop + cellH * (row + 0.5f)

        private fun isFree(col: Int, row: Int): Boolean {
            if (col < 0 || col >= columns) return false
            if (row < 0 || row >= rows) return false
            return (col to row) !in blockedCells
        }

        /** Picks a row with no blocked cells if one exists, else the row with the fewest. */
        private fun pickRow(): Int {
            if (rows <= 0) return 0
            var bestRow = 0
            var bestBlocked = Int.MAX_VALUE
            val fullyFreeRows = mutableListOf<Int>()
            for (r in 0 until rows) {
                var blockedCount = 0
                for (c in 0 until columns) if (!isFree(c, r)) blockedCount++
                if (blockedCount == 0) fullyFreeRows.add(r)
                if (blockedCount < bestBlocked) {
                    bestBlocked = blockedCount
                    bestRow = r
                }
            }
            return if (fullyFreeRows.isNotEmpty()) fullyFreeRows[Random.nextInt(fullyFreeRows.size)] else bestRow
        }

        /** Picks a column with no blocked cells if one exists, else the column with the fewest. */
        private fun pickColumn(): Int {
            if (columns <= 0) return 0
            var bestCol = 0
            var bestBlocked = Int.MAX_VALUE
            val fullyFreeCols = mutableListOf<Int>()
            for (c in 0 until columns) {
                var blockedCount = 0
                for (r in 0 until rows) if (!isFree(c, r)) blockedCount++
                if (blockedCount == 0) fullyFreeCols.add(c)
                if (blockedCount < bestBlocked) {
                    bestBlocked = blockedCount
                    bestCol = c
                }
            }
            return if (fullyFreeCols.isNotEmpty()) fullyFreeCols[Random.nextInt(fullyFreeCols.size)] else bestCol
        }

        /** Starts a fresh straight pass: fully off-screen on one edge to fully off-screen on the opposite edge. */
        private fun startNewPass() {
            val doHorizontal = horizontalNext
            horizontalNext = !horizontalNext

            if (doHorizontal) {
                val row = pickRow()
                val y = cellCenterY(row)
                val margin = screenW * 0.15f
                val goingRight = Random.nextBoolean()
                if (goingRight) {
                    posX = -margin
                    targetX = screenW + margin
                } else {
                    posX = screenW + margin
                    targetX = -margin
                }
                posY = y
                targetY = y
                headingDeg = if (goingRight) 0f else 180f
            } else {
                val col = pickColumn()
                val x = cellCenterX(col)
                val margin = screenH * 0.15f
                val goingDown = Random.nextBoolean()
                if (goingDown) {
                    posY = -margin
                    targetY = screenH + margin
                } else {
                    posY = screenH + margin
                    targetY = -margin
                }
                posX = x
                targetX = x
                headingDeg = if (goingDown) 90f else -90f
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    update()
                    render(canvas)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
            handler.removeCallbacks(drawRunner)
            if (visible) handler.postDelayed(drawRunner, 16L)
        }

        private fun update() {
            val now = System.nanoTime()
            val dt = ((now - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameTimeNs = now

            val dx = targetX - posX
            val dy = targetY - posY
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val step = speedPxPerSec * dt

            if (dist <= step || dist < 0.5f) {
                posX = targetX
                posY = targetY
                startNewPass()
            } else {
                posX += dx / dist * step
                posY += dy / dist * step
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            val scale = (cellH.coerceAtMost(cellW) * 0.85f * scaleFactor) / rocketBitmap.height.toFloat()
            val matrix = Matrix()
            matrix.postTranslate(-rocketBitmap.width / 2f, -rocketBitmap.height / 2f)
            matrix.postScale(scale, scale)
            matrix.postRotate(headingDeg - spriteBaseHeadingDeg)
            matrix.postTranslate(posX, posY)

            canvas.drawBitmap(rocketBitmap, matrix, null)
        }
    }
}
