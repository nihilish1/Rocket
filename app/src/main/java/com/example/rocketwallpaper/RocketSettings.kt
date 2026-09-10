package com.example.rocketwallpaper

import android.content.Context

/**
 * All user-configurable wallpaper settings, persisted with SharedPreferences
 * so they survive app restarts and are read live by the wallpaper engine.
 */
object RocketSettings {
    private const val PREFS_NAME = "rocket_settings"
    private const val KEY_COLUMNS = "columns"
    private const val KEY_ROWS = "rows"
    private const val KEY_TOP_MARGIN = "top_margin_pct"
    private const val KEY_BOTTOM_MARGIN = "bottom_margin_pct"
    private const val KEY_SIDE_MARGIN = "side_margin_pct"
    private const val KEY_SPEED = "speed"
    private const val KEY_SCALE = "scale_pct"
    private const val KEY_BLOCKED = "blocked_cells"

    const val DEFAULT_COLUMNS = 5
    const val DEFAULT_ROWS = 6
    const val DEFAULT_TOP_MARGIN_PCT = 16
    const val DEFAULT_BOTTOM_MARGIN_PCT = 13
    const val DEFAULT_SIDE_MARGIN_PCT = 4
    const val DEFAULT_SPEED = 260
    const val DEFAULT_SCALE_PCT = 100

    fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getColumns(context: Context) = prefs(context).getInt(KEY_COLUMNS, DEFAULT_COLUMNS)
    fun setColumns(context: Context, value: Int) { prefs(context).edit().putInt(KEY_COLUMNS, value).apply() }

    fun getRows(context: Context) = prefs(context).getInt(KEY_ROWS, DEFAULT_ROWS)
    fun setRows(context: Context, value: Int) { prefs(context).edit().putInt(KEY_ROWS, value).apply() }

    fun getTopMarginPct(context: Context) = prefs(context).getInt(KEY_TOP_MARGIN, DEFAULT_TOP_MARGIN_PCT)
    fun setTopMarginPct(context: Context, value: Int) { prefs(context).edit().putInt(KEY_TOP_MARGIN, value).apply() }

    fun getBottomMarginPct(context: Context) = prefs(context).getInt(KEY_BOTTOM_MARGIN, DEFAULT_BOTTOM_MARGIN_PCT)
    fun setBottomMarginPct(context: Context, value: Int) { prefs(context).edit().putInt(KEY_BOTTOM_MARGIN, value).apply() }

    fun getSideMarginPct(context: Context) = prefs(context).getInt(KEY_SIDE_MARGIN, DEFAULT_SIDE_MARGIN_PCT)
    fun setSideMarginPct(context: Context, value: Int) { prefs(context).edit().putInt(KEY_SIDE_MARGIN, value).apply() }

    fun getSpeed(context: Context) = prefs(context).getInt(KEY_SPEED, DEFAULT_SPEED)
    fun setSpeed(context: Context, value: Int) { prefs(context).edit().putInt(KEY_SPEED, value).apply() }

    fun getScalePct(context: Context) = prefs(context).getInt(KEY_SCALE, DEFAULT_SCALE_PCT)
    fun setScalePct(context: Context, value: Int) { prefs(context).edit().putInt(KEY_SCALE, value).apply() }

    fun getBlockedCells(context: Context): MutableSet<Pair<Int, Int>> {
        val raw = prefs(context).getString(KEY_BLOCKED, "") ?: ""
        val result = mutableSetOf<Pair<Int, Int>>()
        if (raw.isBlank()) return result
        raw.split(";").forEach { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val c = parts[0].toIntOrNull()
                val r = parts[1].toIntOrNull()
                if (c != null && r != null) result.add(c to r)
            }
        }
        return result
    }

    fun setBlockedCells(context: Context, cells: Set<Pair<Int, Int>>) {
        val raw = cells.joinToString(";") { "${it.first},${it.second}" }
        prefs(context).edit().putString(KEY_BLOCKED, raw).apply()
    }

    /** Drops any blocked cells that fall outside the current grid bounds. */
    fun clampCellsToGrid(context: Context, cells: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> {
        val cols = getColumns(context)
        val rows = getRows(context)
        return cells.filter { it.first in 0 until cols && it.second in 0 until rows }.toSet()
    }
}
