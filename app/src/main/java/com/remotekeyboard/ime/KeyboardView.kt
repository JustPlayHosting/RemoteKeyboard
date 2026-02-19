package com.remotekeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.remotekeyboard.protocol.Command

/**
 * Programmatic keyboard layout — no XML needed.
 * Draws rows of keys; supports all defined Command types.
 */
class KeyboardView(
    context: Context,
    private val onKey: (type: Byte, text: String) -> Unit
) : LinearLayout(context) {

    private val rows: List<List<Key>> = buildRows()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A2E"))
        buildKeyViews()
    }

    private fun buildRows(): List<List<Key>> = listOf(
        // Row 1 – numbers
        listOf(
            Key("1", Command.TYPE_CHAR, "1"),
            Key("2", Command.TYPE_CHAR, "2"),
            Key("3", Command.TYPE_CHAR, "3"),
            Key("4", Command.TYPE_CHAR, "4"),
            Key("5", Command.TYPE_CHAR, "5"),
            Key("6", Command.TYPE_CHAR, "6"),
            Key("7", Command.TYPE_CHAR, "7"),
            Key("8", Command.TYPE_CHAR, "8"),
            Key("9", Command.TYPE_CHAR, "9"),
            Key("0", Command.TYPE_CHAR, "0")
        ),
        // Row 2 – qwerty top
        listOf(
            Key("q", Command.TYPE_CHAR, "q"),
            Key("w", Command.TYPE_CHAR, "w"),
            Key("e", Command.TYPE_CHAR, "e"),
            Key("r", Command.TYPE_CHAR, "r"),
            Key("t", Command.TYPE_CHAR, "t"),
            Key("y", Command.TYPE_CHAR, "y"),
            Key("u", Command.TYPE_CHAR, "u"),
            Key("i", Command.TYPE_CHAR, "i"),
            Key("o", Command.TYPE_CHAR, "o"),
            Key("p", Command.TYPE_CHAR, "p")
        ),
        // Row 3 – home row
        listOf(
            Key("a", Command.TYPE_CHAR, "a"),
            Key("s", Command.TYPE_CHAR, "s"),
            Key("d", Command.TYPE_CHAR, "d"),
            Key("f", Command.TYPE_CHAR, "f"),
            Key("g", Command.TYPE_CHAR, "g"),
            Key("h", Command.TYPE_CHAR, "h"),
            Key("j", Command.TYPE_CHAR, "j"),
            Key("k", Command.TYPE_CHAR, "k"),
            Key("l", Command.TYPE_CHAR, "l"),
            Key("⌫", Command.TYPE_BACKSPACE, "", weight = 1.5f)
        ),
        // Row 4 – bottom row
        listOf(
            Key("z", Command.TYPE_CHAR, "z"),
            Key("x", Command.TYPE_CHAR, "x"),
            Key("c", Command.TYPE_CHAR, "c"),
            Key("v", Command.TYPE_CHAR, "v"),
            Key("b", Command.TYPE_CHAR, "b"),
            Key("n", Command.TYPE_CHAR, "n"),
            Key("m", Command.TYPE_CHAR, "m"),
            Key(",", Command.TYPE_CHAR, ","),
            Key(".", Command.TYPE_CHAR, "."),
            Key("⏎", Command.TYPE_ENTER, "", accent = true)
        ),
        // Row 5 – special
        listOf(
            Key("←", Command.TYPE_CURSOR_LEFT, ""),
            Key("→", Command.TYPE_CURSOR_RIGHT, ""),
            Key("⇤", Command.TYPE_WORD_LEFT, ""),
            Key("⇥", Command.TYPE_WORD_RIGHT, ""),
            Key("SPACE", Command.TYPE_CHAR, " ", weight = 3f),
            Key("?", Command.TYPE_CHAR, "?"),
            Key("!", Command.TYPE_CHAR, "!"),
            Key("@", Command.TYPE_CHAR, "@"),
            Key("DEL", Command.TYPE_CLEAR, "")
        )
    )

    private fun buildKeyViews() {
        rows.forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
                ).apply { setMargins(2, 2, 2, 2) }
            }
            row.forEach { key ->
                rowLayout.addView(KeyButton(context, key, onKey))
            }
            addView(rowLayout)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics).toInt()
}

data class Key(
    val label: String,
    val type: Byte,
    val text: String,
    val weight: Float = 1f,
    val accent: Boolean = false
)

class KeyButton(
    context: Context,
    private val key: Key,
    private val onKey: (Byte, String) -> Unit
) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (key.accent) Color.parseColor("#E94560") else Color.parseColor("#16213E")
    }
    private val bgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (key.accent) Color.parseColor("#C73652") else Color.parseColor("#0F3460")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f,
            resources.displayMetrics)
    }
    private val rect = RectF()
    private var pressed = false

    init {
        layoutParams = LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, key.weight).apply {
            setMargins(3, 3, 3, 3)
        }
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 8f, 8f, if (pressed) bgPressedPaint else bgPaint)
        canvas.drawText(
            key.label,
            width / 2f,
            height / 2f - (textPaint.descent() + textPaint.ascent()) / 2,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressed = false
                invalidate()
                onKey(key.type, key.text)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
