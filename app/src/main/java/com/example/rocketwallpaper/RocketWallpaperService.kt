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
 */
private enum class FlightMode { GRID, EXIT, ENTER }

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
        private var speedPxPerSec = RocketSettings.DEFAULT_SPEED.toFloat()
        private var scaleFactor = RocketSettings.DEFAULT_SCALE_PCT / 100f
        private var blockedCells: Set<Pair<Int, Int>> = emptySet()

        private var curCol = 0
        private var curRow = 0
        private var posX = 0f
        private var posY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private var headingDeg = 0f
        private var dirX = 0f
        private var dirY = 0f

        private var mode = FlightMode.GRID
        private var legsSinceExit = 0
        private var legsUntilExit = Random.nextInt(3, 7)

        private var horizontalTurnNext = true // alternates each leg
        private var lastFrameTimeNs = 0L

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshSettings()
            if (screenW > 0 && screenH > 0) {
                recomputeGrid()
                resetPathIfNeeded()
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
            val start = findRandomFreeCell()
            curCol = start.first
            curRow = start.second
            posX = cellCenterX(curCol)
            posY = cellCenterY(curRow)
            mode = FlightMode.GRID
            legsSinceExit = 0
            legsUntilExit = Random.nextInt(3, 7)
            pickNextLeg()
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
            speedPxPerSec = RocketSettings.getSpeed(ctx).toFloat()
            scaleFactor = RocketSettings.getScalePct(ctx) / 100f
            blockedCells = RocketSettings.getBlockedCells(ctx)
        }

        /** Re-picks a starting cell/leg if settings changed enough to invalidate the current one. */
        private fun resetPathIfNeeded() {
            if (!isFree(curCol, curRow)) {
                val start = findRandomFreeCell()
                curCol = start.first
                curRow = start.second
                posX = cellCenterX(curCol)
                posY = cellCenterY(curRow)
            }
            pickNextLeg()
        }

        private fun recomputeGrid() {
            gridTop = screenH * topMarginFraction
            val gridBottom = screenH * (1f - bottomMarginFraction)
            gridLeft = 0f
            cellW = screenW / columns.toFloat()
            cellH = (gridBottom - gridTop) / rows.toFloat()
        }

        private fun cellCenterX(col: Int) = gridLeft + cellW * (col + 0.5f)
        private fun cellCenterY(row: Int) = gridTop + cellH * (row + 0.5f)

        private fun isFree(col: Int, row: Int): Boolean {
            if (col < 0 || col >= columns) return false
            if (row < 0 || row >= rows) return false
            return (col to row) !in blockedCells
        }

        private fun findRandomFreeCell(): Pair<Int, Int> {
            val freeCells = mutableListOf<Pair<Int, Int>>()
            for (c in 0 until columns) {
                for (r in 0 until rows) {
                    if (isFree(c, r)) freeCells.add(c to r)
                }
            }
            if (freeCells.isEmpty()) return 0 to 0
            return freeCells[Random.nextInt(freeCells.size)]
        }

        /** How many consecutive free cells lie in one direction from the current cell. */
        private fun runLength(dCol: Int, dRow: Int): Int {
            var c = curCol + dCol
            var r = curRow + dRow
            var len = 0
            while (isFree(c, r)) {
                len++
                c += dCol
                r += dRow
            }
            return len
        }

        /** Picks the next straight leg, alternating horizontal/vertical travel. */
        private fun pickNextLeg() {
            val wantHorizontal = horizontalTurnNext
            horizontalTurnNext = !horizontalTurnNext

            val candidates = if (wantHorizontal) listOf(1 to 0, -1 to 0) else listOf(0 to 1, 0 to -1)

            var chosenDir: Pair<Int, Int>? = null
            var chosenLen = 0
            for (dir in candidates.shuffled()) {
                val len = runLength(dir.first, dir.second)
                if (len > 0) {
                    chosenDir = dir
                    chosenLen = len
                    break
                }
            }

            if (chosenDir == null) {
                val otherCandidates = if (wantHorizontal) listOf(0 to 1, 0 to -1) else listOf(1 to 0, -1 to 0)
                for (dir in otherCandidates.shuffled()) {
                    val len = runLength(dir.first, dir.second)
                    if (len > 0) {
                        chosenDir = dir
                        chosenLen = len
                        horizontalTurnNext = (dir.second != 0)
                        break
                    }
                }
            }

            if (chosenDir == null) {
                val cell = findRandomFreeCell()
                curCol = cell.first
                curRow = cell.second
                posX = cellCenterX(curCol)
                posY = cellCenterY(curRow)
                targetX = posX
                targetY = posY
                return
            }

            val legLen = 1 + Random.nextInt(chosenLen)
            curCol += chosenDir.first * legLen
            curRow += chosenDir.second * legLen
            targetX = cellCenterX(curCol)
            targetY = cellCenterY(curRow)
            val dx = targetX - posX
            val dy = targetY - posY
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.0001f)
            dirX = dx / dist
            dirY = dy / dist
            headingDeg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        /** Sends the rocket straight past the grid edge until it's fully off-screen. */
        private fun startExit() {
            mode = FlightMode.EXIT
            val farDistance = (screenW + screenH).toFloat()
            targetX = posX + dirX * farDistance
            targetY = posY + dirY * farDistance
        }

        /** Picks a random edge off-screen and flies back in to a free entry cell. */
        private fun startEnter() {
            mode = FlightMode.ENTER
            val margin = maxOf(screenW, screenH) * 0.25f
            var attempts = 0
            var entryCol: Int
            var entryRow: Int
            var enterHorizontal: Boolean

            do {
                when (Random.nextInt(4)) {
                    0 -> { // from the top
                        entryCol = Random.nextInt(columns); entryRow = 0
                        enterHorizontal = false
                        posX = cellCenterX(entryCol); posY = gridTop - margin
                    }
                    1 -> { // from the bottom
                        entryCol = Random.nextInt(columns); entryRow = rows - 1
                        enterHorizontal = false
                        posX = cellCenterX(entryCol); posY = (screenH * (1f - bottomMarginFraction)) + margin
                    }
                    2 -> { // from the left
                        entryCol = 0; entryRow = Random.nextInt(rows)
                        enterHorizontal = true
                        posX = gridLeft - margin; posY = cellCenterY(entryRow)
                    }
                    else -> { // from the right
                        entryCol = columns - 1; entryRow = Random.nextInt(rows)
                        enterHorizontal = true
                        posX = screenW + margin; posY = cellCenterY(entryRow)
                    }
                }
                attempts++
            } while (!isFree(entryCol, entryRow) && attempts < 20)

            curCol = entryCol
            curRow = entryRow
            targetX = cellCenterX(curCol)
            targetY = cellCenterY(curRow)
            val dx = targetX - posX
            val dy = targetY - posY
            val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.0001f)
            dirX = dx / dist
            dirY = dy / dist
            headingDeg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            // The entry leg itself used one axis, so the next grid leg should use the other.
            horizontalTurnNext = enterHorizontal
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
                when (mode) {
                    FlightMode.GRID -> {
                        legsSinceExit++
                        if (legsSinceExit >= legsUntilExit) {
                            startExit()
                        } else {
                            pickNextLeg()
                        }
                    }
                    FlightMode.EXIT -> {
                        startEnter()
                    }
                    FlightMode.ENTER -> {
                        mode = FlightMode.GRID
                        legsSinceExit = 0
                        legsUntilExit = Random.nextInt(3, 7)
                        pickNextLeg()
                    }
                }
            } else if (dist > 0f) {
                posX += dx / dist * step
                posY += dy / dist * step
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            val scale = (cellH.coerceAtMost(cellW) * 1.6f * scaleFactor) / rocketBitmap.height.toFloat()
            val matrix = Matrix()
            matrix.postTranslate(-rocketBitmap.width / 2f, -rocketBitmap.height / 2f)
            matrix.postScale(scale, scale)
            matrix.postRotate(headingDeg - spriteBaseHeadingDeg)
            matrix.postTranslate(posX, posY)

            canvas.drawBitmap(rocketBitmap, matrix, null)
        }
    }
}
