package com.remotekeyboard.ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.remotekeyboard.protocol.Command

enum class ShiftState { OFF, ON, CAPS_LOCK }
enum class KeyboardLayer { ALPHA, SYMBOL, SYMBOL2, EMOJI, SWITCH_IME }

data class Key(
    val label: String,
    val type: Byte,
    val text: String,
    val numHint: String = "",
    val weight: Float = 1f,
    val isSpecial: Boolean = false,
    val isAction: Boolean = false,
    val longPressText: String = ""
)

class KeyboardView(
    context: Context,
    private val onKey: (type: Byte, text: String) -> Unit,
    private val onSwitchLayer: (KeyboardLayer) -> Unit
) : LinearLayout(context) {

    var shiftState = ShiftState.OFF
        set(value) { field = value; refresh() }

    var currentLayer = KeyboardLayer.ALPHA
        set(value) { field = value; rebuild() }

    private val alphaRows = listOf(
        listOf(
            Key("q", Command.TYPE_CHAR, "q", "1"),
            Key("w", Command.TYPE_CHAR, "w", "2"),
            Key("e", Command.TYPE_CHAR, "e", "3"),
            Key("r", Command.TYPE_CHAR, "r", "4"),
            Key("t", Command.TYPE_CHAR, "t", "5"),
            Key("y", Command.TYPE_CHAR, "y", "6"),
            Key("u", Command.TYPE_CHAR, "u", "7"),
            Key("i", Command.TYPE_CHAR, "i", "8"),
            Key("o", Command.TYPE_CHAR, "o", "9"),
            Key("p", Command.TYPE_CHAR, "p", "0")
        ),
        listOf(
            Key("a", Command.TYPE_CHAR, "a"),
            Key("s", Command.TYPE_CHAR, "s"),
            Key("d", Command.TYPE_CHAR, "d"),
            Key("f", Command.TYPE_CHAR, "f"),
            Key("g", Command.TYPE_CHAR, "g"),
            Key("h", Command.TYPE_CHAR, "h"),
            Key("j", Command.TYPE_CHAR, "j"),
            Key("k", Command.TYPE_CHAR, "k"),
            Key("l", Command.TYPE_CHAR, "l")
        ),
        listOf(
            Key("⇧", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.5f),
            Key("z", Command.TYPE_CHAR, "z"),
            Key("x", Command.TYPE_CHAR, "x"),
            Key("c", Command.TYPE_CHAR, "c"),
            Key("v", Command.TYPE_CHAR, "v"),
            Key("b", Command.TYPE_CHAR, "b"),
            Key("n", Command.TYPE_CHAR, "n"),
            Key("m", Command.TYPE_CHAR, "m", longPressText = "!"),
            Key("⌫", Command.TYPE_BACKSPACE, "", isSpecial = true, weight = 1.5f)
        ),
        listOf(
            Key("?123", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.3f),
            Key(",", Command.TYPE_CHAR, ","),
            Key("🌐", Command.TYPE_CHAR, "", isSpecial = true),
            Key("English", Command.TYPE_CHAR, " ", weight = 3f),
            Key(".", Command.TYPE_CHAR, ".", longPressText = "…"),
            Key("↵", Command.TYPE_ENTER, "", isAction = true, weight = 1.3f)
        )
    )

    private val symbolRows = listOf(
        listOf(
            Key("1", Command.TYPE_CHAR, "1"), Key("2", Command.TYPE_CHAR, "2"),
            Key("3", Command.TYPE_CHAR, "3"), Key("4", Command.TYPE_CHAR, "4"),
            Key("5", Command.TYPE_CHAR, "5"), Key("6", Command.TYPE_CHAR, "6"),
            Key("7", Command.TYPE_CHAR, "7"), Key("8", Command.TYPE_CHAR, "8"),
            Key("9", Command.TYPE_CHAR, "9"), Key("0", Command.TYPE_CHAR, "0")
        ),
        listOf(
            Key("@", Command.TYPE_CHAR, "@"), Key("#", Command.TYPE_CHAR, "#"),
            Key("$", Command.TYPE_CHAR, "$"), Key("%", Command.TYPE_CHAR, "%"),
            Key("&", Command.TYPE_CHAR, "&"), Key("-", Command.TYPE_CHAR, "-"),
            Key("+", Command.TYPE_CHAR, "+"), Key("(", Command.TYPE_CHAR, "("),
            Key(")", Command.TYPE_CHAR, ")"), Key("/", Command.TYPE_CHAR, "/")
        ),
        listOf(
            Key("=\\<", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.5f),
            Key("*", Command.TYPE_CHAR, "*"), Key("\"", Command.TYPE_CHAR, "\""),
            Key("'", Command.TYPE_CHAR, "'"), Key(":", Command.TYPE_CHAR, ":"),
            Key(";", Command.TYPE_CHAR, ";"), Key("!", Command.TYPE_CHAR, "!"),
            Key("?", Command.TYPE_CHAR, "?"),
            Key("⌫", Command.TYPE_BACKSPACE, "", isSpecial = true, weight = 1.5f)
        ),
        listOf(
            Key("ABC", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.3f),
            Key(",", Command.TYPE_CHAR, ","),
            Key("🌐", Command.TYPE_CHAR, "", isSpecial = true),
            Key("English", Command.TYPE_CHAR, " ", weight = 3f),
            Key(".", Command.TYPE_CHAR, "."),
            Key("↵", Command.TYPE_ENTER, "", isAction = true, weight = 1.3f)
        )
    )

    private val symbol2Rows = listOf(
        listOf(
            Key("~", Command.TYPE_CHAR, "~"), Key("`", Command.TYPE_CHAR, "`"),
            Key("|", Command.TYPE_CHAR, "|"), Key("•", Command.TYPE_CHAR, "•"),
            Key("√", Command.TYPE_CHAR, "√"), Key("π", Command.TYPE_CHAR, "π"),
            Key("÷", Command.TYPE_CHAR, "÷"), Key("×", Command.TYPE_CHAR, "×"),
            Key("¶", Command.TYPE_CHAR, "¶"), Key("∆", Command.TYPE_CHAR, "∆")
        ),
        listOf(
            Key("£", Command.TYPE_CHAR, "£"), Key("¢", Command.TYPE_CHAR, "¢"),
            Key("€", Command.TYPE_CHAR, "€"), Key("¥", Command.TYPE_CHAR, "¥"),
            Key("^", Command.TYPE_CHAR, "^"), Key("°", Command.TYPE_CHAR, "°"),
            Key("=", Command.TYPE_CHAR, "="), Key("{", Command.TYPE_CHAR, "{"),
            Key("}", Command.TYPE_CHAR, "}"), Key("\\", Command.TYPE_CHAR, "\\")
        ),
        listOf(
            Key("?123", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.5f),
            Key("%", Command.TYPE_CHAR, "%"), Key("©", Command.TYPE_CHAR, "©"),
            Key("®", Command.TYPE_CHAR, "®"), Key("™", Command.TYPE_CHAR, "™"),
            Key("✓", Command.TYPE_CHAR, "✓"), Key("[", Command.TYPE_CHAR, "["),
            Key("]", Command.TYPE_CHAR, "]"),
            Key("⌫", Command.TYPE_BACKSPACE, "", isSpecial = true, weight = 1.5f)
        ),
        listOf(
            Key("ABC", Command.TYPE_CHAR, "", isSpecial = true, weight = 1.3f),
            Key(",", Command.TYPE_CHAR, ","),
            Key("🌐", Command.TYPE_CHAR, "", isSpecial = true),
            Key("English", Command.TYPE_CHAR, " ", weight = 3f),
            Key(".", Command.TYPE_CHAR, "."),
            Key("↵", Command.TYPE_ENTER, "", isAction = true, weight = 1.3f)
        )
    )

    init {
        orientation = VERTICAL
        rebuild()
    }

    private fun activeRows() = when (currentLayer) {
        KeyboardLayer.ALPHA       -> alphaRows
        KeyboardLayer.SYMBOL      -> symbolRows
        KeyboardLayer.SYMBOL2     -> symbol2Rows
        KeyboardLayer.EMOJI       -> alphaRows
        KeyboardLayer.SWITCH_IME  -> alphaRows
    }

    fun rebuild() {
        removeAllViews()
        activeRows().forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply {
                    setMargins(dp(4), dp(3), dp(4), dp(3))
                }
            }
            row.forEach { key -> rowLayout.addView(makeKeyButton(key)) }
            addView(rowLayout)
        }
    }

    private fun refresh() { rebuild() }

    private fun makeKeyButton(key: Key): View {
        return KeyButton(context, key, shiftState) { tappedKey ->
            handleKeyTap(tappedKey)
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
    }

    private fun handleKeyTap(key: Key) {
        when (key.label) {
            "⇧" -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF       -> ShiftState.ON
                    ShiftState.ON        -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                return
            }
            "?123"  -> { currentLayer = KeyboardLayer.SYMBOL;  return }
            "ABC"   -> { currentLayer = KeyboardLayer.ALPHA;   return }
            "=\\<"  -> { currentLayer = KeyboardLayer.SYMBOL2; return }
            "😊"    -> { onSwitchLayer(KeyboardLayer.EMOJI);   return }
            "🌐"    -> { onSwitchLayer(KeyboardLayer.SWITCH_IME); return }
            "⌫"    -> { onKey(Command.TYPE_BACKSPACE, "");     return }
            "↵"    -> { onKey(Command.TYPE_ENTER, "");          return }
            "English" -> { onKey(Command.TYPE_CHAR, " ");       return }
        }

        if (key.type == Command.TYPE_CHAR && key.text.length == 1) {
            val ch = if (shiftState != ShiftState.OFF) key.text.uppercase() else key.text
            onKey(Command.TYPE_CHAR, ch)
            if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
        } else if (key.text.isNotEmpty()) {
            onKey(key.type, key.text)
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}

class KeyButton(
    context: Context,
    private val key: Key,
    private val shiftState: ShiftState,
    private val onClick: (Key) -> Unit
) : View(context) {

    private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect      = RectF()
    private var pressed   = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    init {
        isClickable = true
        isFocusable = true
        val sp = { sp: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        }
        textPaint.apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sp(if (key.label.length > 3) 13f else 16f)
            isFakeBoldText = key.isAction
        }
        hintPaint.apply {
            color = Color.parseColor("#99FFFFFF")
            textSize = sp(9f)
            textAlign = Paint.Align.LEFT
        }
    }

    private fun bgColor(): Int = when {
        pressed        -> if (key.isAction) Color.parseColor("#1A6FE8") else Color.parseColor("#4A6070")
        key.isAction   -> Color.parseColor("#4285F4")
        key.isSpecial  -> Color.parseColor("#313B47")
        else           -> Color.parseColor("#3C4D5C")
    }

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = bgColor()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, dp(6f), dp(6f), bgPaint)

        if (key.numHint.isNotEmpty()) {
            canvas.drawText(key.numHint, dp(5f), hintPaint.textSize + dp(2f), hintPaint)
        }

        val displayLabel = when {
            key.label == "⇧" -> when (shiftState) {
                ShiftState.OFF       -> "⇧"
                ShiftState.ON        -> "⬆"
                ShiftState.CAPS_LOCK -> "⬆⬆"
            }
            key.label.length == 1 && key.label[0].isLetter() ->
                if (shiftState != ShiftState.OFF) key.label.uppercase() else key.label
            else -> key.label
        }

        canvas.drawText(
            displayLabel,
            width / 2f,
            height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true; invalidate()
                if (key.label == "⌫") startRepeat()
            }
            MotionEvent.ACTION_UP -> {
                pressed = false; invalidate()
                stopRepeat()
                onClick(key)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false; invalidate()
                stopRepeat()
            }
        }
        return true
    }

    private fun startRepeat() {
        repeatRunnable = object : Runnable {
            override fun run() { onClick(key); handler.postDelayed(this, 50) }
        }
        handler.postDelayed(repeatRunnable!!, 500)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}

class EmojiPanel(
    context: Context,
    private val onEmoji: (String) -> Unit,
    private val onBack: () -> Unit
) : LinearLayout(context) {

    private val emojis = listOf(
        "😀","😁","😂","🤣","😃","😄","😅","😆","😉","😊","😋","😎","😍","😘","🥰",
        "😗","😙","😚","🙂","🤗","🤩","🤔","🤨","😐","😑","😶","🙄","😏","😣","😥",
        "😮","🤐","😯","😪","😫","😴","😌","😛","😜","😝","🤤","😒","😓","😔","😕",
        "🙃","🤑","😲","😷","🤒","🤕","🤢","🤧","🥵","🥶","🥴","😵","🤯","🤠","🥳",
        "😈","👿","👹","👺","💀","👻","👽","🤖","💩","😺","😸","😹","😻","😼","😽",
        "👍","👎","👌","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👋","🤚",
        "🖐️","✋","🖖","💪","❤️","🧡","💛","💚","💙","💜","🖤","💔","❣️","💕","💞",
        "💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉️","✡️","🔯","🙏","💯","🎉"
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1B2836"))

        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(TextView(context).apply {
            text = "← ABC"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onBack() }
        })
        addView(bar)

        val grid = GridView(context).apply {
            numColumns = 8
            horizontalSpacing = dp(4)
            verticalSpacing = dp(4)
            setPadding(dp(8), dp(4), dp(8), dp(8))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            adapter = EmojiAdapter(context, emojis, onEmoji)
        }
        addView(grid)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}

class EmojiAdapter(
    context: Context,
    private val emojis: List<String>,
    private val onEmoji: (String) -> Unit
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    override fun getCount() = emojis.size
    override fun getItem(pos: Int) = emojis[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val tv = (convertView as? TextView) ?: TextView(parent.context).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
        tv.text = emojis[pos]
        tv.setOnClickListener { onEmoji(emojis[pos]) }
        return tv
    }
}
