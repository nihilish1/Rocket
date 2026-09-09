package com.example.rocketwallpaper

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
 * ---------------------------------------------------------------------
 *  GRID CONFIG — edit these to match YOUR launcher and current layout.
 * ---------------------------------------------------------------------
 *  Android cannot read another app's (the launcher's) icon/widget
 *  positions, so instead of detecting them we give the rocket a virtual
 *  grid that mirrors your home screen grid. Set COLUMNS/ROWS to match
 *  your launcher's grid setting (check Launcher settings > Home screen
 *  grid — common values are 4x5, 4x6, 5x5, 5x6).
 *
 *  TOP_MARGIN / BOTTOM_MARGIN are fractions of screen height reserved
 *  for the status bar / search bar (top) and the dock (bottom) — the
 *  rocket will never enter these bands.
 *
 *  BLOCKED_CELLS lists any grid cell (col, row) — 0-indexed, row 0 is
 *  the top icon row — that's occupied by a widget or anything not on
 *  a normal icon slot. A 4x2 widget in the top-left corner, for
 *  example, would block (0,0) (1,0) (2,0) (3,0) (0,1) (1,1) (2,1) (3,1).
 *  Update this list any time you rearrange your home screen.
 * ---------------------------------------------------------------------
 */
object GridConfig {
    const val COLUMNS = 5
    const val ROWS = 6

    const val TOP_MARGIN_FRACTION = 0.08f     // status bar / search bar
    const val BOTTOM_MARGIN_FRACTION = 0.10f  // dock

    // Example: a 4x2 widget in the top-left. Replace with your real layout.
    val BLOCKED_CELLS: Set<Pair<Int, Int>> = setOf(
        0 to 0, 1 to 0, 2 to 0, 3 to 0,
        0 to 1, 1 to 1, 2 to 1, 3 to 1
    )
}

class RocketWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RocketEngine()

    private inner class RocketEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { drawFrame() }
        private var visible = false

        private val rocketBitmap = BitmapFactory.decodeResource(resources, R.drawable.rocket)

        // The sprite's own resting heading, in degrees, measured clockwise
        // from due east (0 = pointing right, 90 = pointing down). The
        // supplied artwork points up-and-to-the-right, i.e. up-and-right
        // is roughly -45 degrees. Tweak this if the rocket looks rotated
        // wrong on screen.
        private val spriteBaseHeadingDeg = -45f

        private var screenW = 0
        private var screenH = 0
        private var cellW = 0f
        private var cellH = 0f
        private var gridTop = 0f
        private var gridLeft = 0f

        private var curCol = 0
        private var curRow = 0
        private var posX = 0f
        private var posY = 0f
        private var targetX = 0f
        private var targetY = 0f
        private var headingDeg = 0f

        private var horizontalTurnNext = true // alternates each leg
        private val speedPxPerSec = 260f
        private var lastFrameTimeNs = 0L

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
            recomputeGrid()
            // (re)start the rocket in the middle of the free grid area
            val start = findRandomFreeCell()
            curCol = start.first
            curRow = start.second
            posX = cellCenterX(curCol)
            posY = cellCenterY(curRow)
            pickNextLeg()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        private fun recomputeGrid() {
            gridTop = screenH * GridConfig.TOP_MARGIN_FRACTION
            val gridBottom = screenH * (1f - GridConfig.BOTTOM_MARGIN_FRACTION)
            gridLeft = 0f
            cellW = screenW / GridConfig.COLUMNS.toFloat()
            cellH = (gridBottom - gridTop) / GridConfig.ROWS.toFloat()
        }

        private fun cellCenterX(col: Int) = gridLeft + cellW * (col + 0.5f)
        private fun cellCenterY(row: Int) = gridTop + cellH * (row + 0.5f)

        private fun isFree(col: Int, row: Int): Boolean {
            if (col < 0 || col >= GridConfig.COLUMNS) return false
            if (row < 0 || row >= GridConfig.ROWS) return false
            return (col to row) !in GridConfig.BLOCKED_CELLS
        }

        private fun findRandomFreeCell(): Pair<Int, Int> {
            val freeCells = mutableListOf<Pair<Int, Int>>()
            for (c in 0 until GridConfig.COLUMNS) {
                for (r in 0 until GridConfig.ROWS) {
                    if (isFree(c, r)) freeCells.add(c to r)
                }
            }
            if (freeCells.isEmpty()) return 0 to 0
            return freeCells[Random.nextInt(freeCells.size)]
        }

        /** Finds how many consecutive free cells lie in one direction from the current cell. */
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

            val candidates = if (wantHorizontal) {
                listOf(1 to 0, -1 to 0)
            } else {
                listOf(0 to 1, 0 to -1)
            }

            var chosenDir: Pair<Int, Int>? = null
            var chosenLen = 0
            val shuffled = candidates.shuffled()
            for (dir in shuffled) {
                val len = runLength(dir.first, dir.second)
                if (len > 0) {
                    chosenDir = dir
                    chosenLen = len
                    break
                }
            }

            // Nothing free on the preferred axis — try the other axis instead.
            if (chosenDir == null) {
                val otherCandidates = if (wantHorizontal) listOf(0 to 1, 0 to -1) else listOf(1 to 0, -1 to 0)
                for (dir in otherCandidates.shuffled()) {
                    val len = runLength(dir.first, dir.second)
                    if (len > 0) {
                        chosenDir = dir
                        chosenLen = len
                        // keep horizontalTurnNext consistent with the axis we actually used
                        horizontalTurnNext = (dir.second != 0)
                        break
                    }
                }
            }

            if (chosenDir == null) {
                // Fully boxed in (shouldn't normally happen) — teleport to a free cell.
                val cell = findRandomFreeCell()
                curCol = cell.first
                curRow = cell.second
                posX = cellCenterX(curCol)
                posY = cellCenterY(curRow)
                targetX = posX
                targetY = posY
                return
            }

            val legLen = 1 + Random.nextInt(chosenLen) // 1..chosenLen
            curCol += chosenDir.first * legLen
            curRow += chosenDir.second * legLen
            targetX = cellCenterX(curCol)
            targetY = cellCenterY(curRow)
            headingDeg = Math.toDegrees(
                Math.atan2((targetY - posY).toDouble(), (targetX - posX).toDouble())
            ).toFloat()
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
            if (visible) handler.postDelayed(drawRunner, 16L) // ~60fps
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
                pickNextLeg()
            } else {
                posX += dx / dist * step
                posY += dy / dist * step
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            val scale = (cellH.coerceAtMost(cellW) * 1.6f) / rocketBitmap.height.toFloat()
            val matrix = Matrix()
            matrix.postTranslate(-rocketBitmap.width / 2f, -rocketBitmap.height / 2f)
            matrix.postScale(scale, scale)
            matrix.postRotate(headingDeg - spriteBaseHeadingDeg)
            matrix.postTranslate(posX, posY)

            canvas.drawBitmap(rocketBitmap, matrix, null)
        }
    }
}
