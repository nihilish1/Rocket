package com.example.rocketwallpaper

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var gridEditor: GridEditorView
    private lateinit var columnsValueText: TextView
    private lateinit var rowsValueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 56, 56, 56)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(sectionTitle("Rocket Wallpaper Settings"))

        // --- Speed ---
        root.addView(sectionTitle("Speed"))
        val speedBar = SeekBar(this).apply {
            max = 550
            progress = (RocketSettings.getSpeed(this@SettingsActivity) - 50).coerceIn(0, 550)
        }
        speedBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            RocketSettings.setSpeed(this@SettingsActivity, progress + 50)
        })
        root.addView(speedBar)

        // --- Size ---
        root.addView(sectionTitle("Rocket size"))
        val sizeBar = SeekBar(this).apply {
            max = 250
            progress = (RocketSettings.getScalePct(this@SettingsActivity) - 50).coerceIn(0, 250)
        }
        sizeBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            RocketSettings.setScalePct(this@SettingsActivity, progress + 50)
        })
        root.addView(sizeBar)

        // --- Columns stepper ---
        root.addView(sectionTitle("Grid columns (match your launcher's home screen grid)"))
        val columnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        columnsValueText = TextView(this).apply {
            text = RocketSettings.getColumns(this@SettingsActivity).toString()
            textSize = 18f
            setPadding(32, 0, 32, 0)
        }
        columnsRow.addView(Button(this).apply {
            text = "-"
            setOnClickListener {
                val newVal = (RocketSettings.getColumns(this@SettingsActivity) - 1).coerceAtLeast(2)
                RocketSettings.setColumns(this@SettingsActivity, newVal)
                columnsValueText.text = newVal.toString()
                gridEditor.columns = newVal
                pruneBlockedCells()
                gridEditor.invalidate()
            }
        })
        columnsRow.addView(columnsValueText)
        columnsRow.addView(Button(this).apply {
            text = "+"
            setOnClickListener {
                val newVal = (RocketSettings.getColumns(this@SettingsActivity) + 1).coerceAtMost(12)
                RocketSettings.setColumns(this@SettingsActivity, newVal)
                columnsValueText.text = newVal.toString()
                gridEditor.columns = newVal
                gridEditor.invalidate()
            }
        })
        root.addView(columnsRow)

        // --- Rows stepper ---
        root.addView(sectionTitle("Grid rows"))
        val rowsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rowsValueText = TextView(this).apply {
            text = RocketSettings.getRows(this@SettingsActivity).toString()
            textSize = 18f
            setPadding(32, 0, 32, 0)
        }
        rowsRow.addView(Button(this).apply {
            text = "-"
            setOnClickListener {
                val newVal = (RocketSettings.getRows(this@SettingsActivity) - 1).coerceAtLeast(2)
                RocketSettings.setRows(this@SettingsActivity, newVal)
                rowsValueText.text = newVal.toString()
                gridEditor.rows = newVal
                pruneBlockedCells()
                gridEditor.invalidate()
            }
        })
        rowsRow.addView(rowsValueText)
        rowsRow.addView(Button(this).apply {
            text = "+"
            setOnClickListener {
                val newVal = (RocketSettings.getRows(this@SettingsActivity) + 1).coerceAtMost(14)
                RocketSettings.setRows(this@SettingsActivity, newVal)
                rowsValueText.text = newVal.toString()
                gridEditor.rows = newVal
                gridEditor.invalidate()
            }
        })
        root.addView(rowsRow)

        // --- Top margin ---
        root.addView(sectionTitle("Top margin (status bar / search bar)"))
        val topBar = SeekBar(this).apply {
            max = 30
            progress = RocketSettings.getTopMarginPct(this@SettingsActivity)
        }
        topBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            RocketSettings.setTopMarginPct(this@SettingsActivity, progress)
        })
        root.addView(topBar)

        // --- Bottom margin ---
        root.addView(sectionTitle("Bottom margin (dock)"))
        val bottomBar = SeekBar(this).apply {
            max = 30
            progress = RocketSettings.getBottomMarginPct(this@SettingsActivity)
        }
        bottomBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            RocketSettings.setBottomMarginPct(this@SettingsActivity, progress)
        })
        root.addView(bottomBar)

        // --- Side margin ---
        root.addView(sectionTitle("Side margin (left/right edge)"))
        val sideBar = SeekBar(this).apply {
            max = 15
            progress = RocketSettings.getSideMarginPct(this@SettingsActivity)
        }
        sideBar.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            RocketSettings.setSideMarginPct(this@SettingsActivity, progress)
        })
        root.addView(sideBar)

        // --- Grid editor ---
        root.addView(sectionTitle("Tap cells to mark them blocked (widgets, anything off-grid). Green = free, red = blocked."))
        gridEditor = GridEditorView(this).apply {
            columns = RocketSettings.getColumns(this@SettingsActivity)
            rows = RocketSettings.getRows(this@SettingsActivity)
            blockedCells.addAll(RocketSettings.getBlockedCells(this@SettingsActivity))
            onChanged = { cells -> RocketSettings.setBlockedCells(this@SettingsActivity, cells) }
        }
        root.addView(gridEditor)

        root.addView(Button(this).apply {
            text = "Clear all blocked cells"
            setOnClickListener {
                gridEditor.blockedCells.clear()
                gridEditor.invalidate()
                RocketSettings.setBlockedCells(this@SettingsActivity, emptySet())
            }
        })

        root.addView(Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        })
    }

    private fun pruneBlockedCells() {
        val clamped = RocketSettings.clampCellsToGrid(this, gridEditor.blockedCells)
        gridEditor.blockedCells.clear()
        gridEditor.blockedCells.addAll(clamped)
        RocketSettings.setBlockedCells(this, clamped)
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.BLACK)
        setPadding(0, 40, 0, 12)
    }
}
