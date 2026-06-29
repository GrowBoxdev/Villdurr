package com.growboxit.villdurr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

enum class BuildingType(val label: String, val w: Int, val h: Int, val color: Int) {
    TOWNHALL("Rathaus", 4, 4, Color.parseColor("#caa23a")),
    GOLDMINE("Goldmine", 2, 2, Color.parseColor("#8a6a2f")),
    ELIXIR("Elixir", 2, 2, Color.parseColor("#6a3f96")),
    WALL("Mauer", 2, 2, Color.parseColor("#5a5f63"))
}

enum class Mode {
    NONE, PLACE_GOLDMINE, PLACE_ELIXIR, PLACE_WALL, DEMOLISH
}

data class Building(
    val type: BuildingType,
    val gx: Int,
    val gy: Int,
    var level: Int = 1,
    var spentGold: Int = 0,
    var spentElixir: Int = 0
)

// @JvmOverloads + der (Context, AttributeSet?) Konstruktor sind nötig,
// damit Android diese Klasse aus der XML-Layout-Datei instanziieren kann.
class VillageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val GRID_SIZE = 24
    }

    var gold = 300
    var elixir = 300
    private val buildings = mutableListOf<Building>()
    private var mode = Mode.NONE
    private var cellSize = 0f

    // Callback nach außen, damit MainActivity die TextView updaten kann
    var resourceListener: ((gold: Int, elixir: Int) -> Unit)? = null

    private val bgPaint = Paint().apply { color = Color.parseColor("#11181a") }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#1f2e26")
        strokeWidth = 2f
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#0d1117")
        textSize = 28f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val buildingPaint = Paint()

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        // Rathaus startet fest in der Mitte
        val startX = (GRID_SIZE - BuildingType.TOWNHALL.w) / 2
        val startY = (GRID_SIZE - BuildingType.TOWNHALL.h) / 2
        buildings.add(Building(BuildingType.TOWNHALL, startX, startY, level = 1))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tickRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tickRunnable)
    }

    private fun tick() {
        var goldGain = 0
        var elixirGain = 0
        for (b in buildings) {
            when (b.type) {
                BuildingType.GOLDMINE -> goldGain += 2 + b.level * 2
                BuildingType.ELIXIR -> elixirGain += 2 + b.level * 2
                else -> {}
            }
        }
        gold += goldGain
        elixir += elixirGain
        resourceListener?.invoke(gold, elixir)
        invalidate()
    }

    fun setMode(newMode: Mode) {
        // Nochmal der gleiche Button = Modus wieder aus
        mode = if (mode == newMode) Mode.NONE else newMode
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = minOf(w, h).toFloat() / GRID_SIZE
        textPaint.textSize = cellSize * 0.4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        for (i in 0..GRID_SIZE) {
            val pos = i * cellSize
            canvas.drawLine(pos, 0f, pos, GRID_SIZE * cellSize, gridPaint)
            canvas.drawLine(0f, pos, GRID_SIZE * cellSize, pos, gridPaint)
        }

        for (b in buildings) {
            buildingPaint.color = b.type.color
            val left = b.gx * cellSize
            val top = b.gy * cellSize
            val right = left + b.type.w * cellSize
            val bottom = top + b.type.h * cellSize
            canvas.drawRect(left, top, right, bottom, buildingPaint)
            canvas.drawText(
                "Lv${b.level}",
                (left + right) / 2f,
                (top + bottom) / 2f + textPaint.textSize / 3f,
                textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (cellSize <= 0f) return true
        val gx = (event.x / cellSize).toInt()
        val gy = (event.y / cellSize).toInt()

        when (mode) {
            Mode.PLACE_GOLDMINE -> tryPlace(BuildingType.GOLDMINE, gx, gy)
            Mode.PLACE_ELIXIR -> tryPlace(BuildingType.ELIXIR, gx, gy)
            Mode.PLACE_WALL -> tryPlace(BuildingType.WALL, gx, gy)
            Mode.DEMOLISH -> tryDemolish(gx, gy)
            Mode.NONE -> tryUpgrade(gx, gy)
        }
        return true
    }

    private fun buildCost(type: BuildingType): Pair<Int, Int> = when (type) {
        BuildingType.GOLDMINE -> Pair(0, 60)
        BuildingType.ELIXIR -> Pair(60, 0)
        BuildingType.WALL -> Pair(15, 0)
        BuildingType.TOWNHALL -> Pair(0, 0)
    }

    private fun upgradeCost(b: Building): Pair<Int, Int> = when (b.type) {
        BuildingType.TOWNHALL -> Pair(150 * b.level, 150 * b.level)
        BuildingType.GOLDMINE -> Pair(0, 40 * b.level)
        BuildingType.ELIXIR -> Pair(40 * b.level, 0)
        BuildingType.WALL -> Pair(10 * b.level, 0)
    }

    private fun overlaps(gx: Int, gy: Int, w: Int, h: Int): Boolean {
        if (gx < 0 || gy < 0 || gx + w > GRID_SIZE || gy + h > GRID_SIZE) return true
        for (b in buildings) {
            val noOverlap = gx + w <= b.gx || b.gx + b.type.w <= gx ||
                    gy + h <= b.gy || b.gy + b.type.h <= gy
            if (!noOverlap) return true
        }
        return false
    }

    private fun tryPlace(type: BuildingType, gx: Int, gy: Int) {
        if (overlaps(gx, gy, type.w, type.h)) return
        val (g, e) = buildCost(type)
        if (gold < g || elixir < e) return
        gold -= g
        elixir -= e
        buildings.add(Building(type, gx, gy, level = 1, spentGold = g, spentElixir = e))
        resourceListener?.invoke(gold, elixir)
        invalidate()
    }

    private fun findBuildingAt(gx: Int, gy: Int): Building? {
        return buildings.find {
            gx >= it.gx && gx < it.gx + it.type.w && gy >= it.gy && gy < it.gy + it.type.h
        }
    }

    private fun tryUpgrade(gx: Int, gy: Int) {
        val b = findBuildingAt(gx, gy) ?: return
        if (b.level >= 20) return
        val (g, e) = upgradeCost(b)
        if (gold < g || elixir < e) return
        gold -= g
        elixir -= e
        b.level += 1
        b.spentGold += g
        b.spentElixir += e
        resourceListener?.invoke(gold, elixir)
        invalidate()
    }

    private fun tryDemolish(gx: Int, gy: Int) {
        val b = findBuildingAt(gx, gy) ?: return
        if (b.type == BuildingType.TOWNHALL) return
        gold += b.spentGold / 2
        elixir += b.spentElixir / 2
        buildings.remove(b)
        resourceListener?.invoke(gold, elixir)
        invalidate()
    }

    // --- Speichern/Laden, simples eigenes Format statt JSON-Lib ---

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append(gold).append(';').append(elixir).append(';')
        for (b in buildings) {
            sb.append(b.type.name).append(',')
                .append(b.gx).append(',')
                .append(b.gy).append(',')
                .append(b.level).append(',')
                .append(b.spentGold).append(',')
                .append(b.spentElixir).append('|')
        }
        return sb.toString()
    }

    fun restore(data: String) {
        if (data.isBlank()) return
        try {
            val parts = data.split(';')
            gold = parts[0].toInt()
            elixir = parts[1].toInt()
            buildings.clear()
            if (parts.size > 2 && parts[2].isNotBlank()) {
                for (entry in parts[2].split('|')) {
                    if (entry.isBlank()) continue
                    val f = entry.split(',')
                    buildings.add(
                        Building(
                            BuildingType.valueOf(f[0]),
                            f[1].toInt(), f[2].toInt(), f[3].toInt(),
                            f[4].toInt(), f[5].toInt()
                        )
                    )
                }
            }
            resourceListener?.invoke(gold, elixir)
            invalidate()
        } catch (e: Exception) {
            // korrupter Spielstand -> einfach ignorieren, frisches Spiel
        }
    }
}
