package com.remotekeyboard.ime

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.remotekeyboard.bluetooth.BluetoothService
import com.remotekeyboard.protocol.Command

class RemoteKeyboardIME : InputMethodService() {

    private var remoteMode = false
    private var statusBar: TextView? = null
    private var keyboardView: KeyboardView? = null
    private var emojiPanel: EmojiPanel? = null
    private var rootContainer: FrameLayout? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothService.BROADCAST_COMMAND -> {
                    val type = intent.getIntExtra(BluetoothService.EXTRA_COMMAND_TYPE, 0).toByte()
                    val text = intent.getStringExtra(BluetoothService.EXTRA_COMMAND_TEXT) ?: ""
                    injectCommand(type, text)
                }
                BluetoothService.BROADCAST_CONNECTED -> {
                    remoteMode = true
                    statusBar?.text = "🔵 Connected"
                    statusBar?.setTextColor(Color.parseColor("#4CAF50"))
                }
                BluetoothService.BROADCAST_DISCONNECTED -> {
                    remoteMode = false
                    statusBar?.text = "⚫ Local"
                    statusBar?.setTextColor(Color.parseColor("#FF5252"))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(BluetoothService.BROADCAST_COMMAND)
            addAction(BluetoothService.BROADCAST_CONNECTED)
            addAction(BluetoothService.BROADCAST_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B2836"))
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#141F2B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        fun toolbarBtn(label: String, action: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.parseColor("#B0BEC5"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        }

        toolbar.addView(toolbarBtn("😊") { showEmoji() })

        toolbar.addView(toolbarBtn("📋") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this).toString()
                sendOrInject(Command.TYPE_CHAR, text)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        })

        toolbar.addView(toolbarBtn("🎨") {
            Toast.makeText(this, "Theme customisation coming soon", Toast.LENGTH_SHORT).show()
        })

        // Spacer
        toolbar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Status chip
        statusBar = TextView(this).apply {
            text = if (remoteMode) "🔵 Connected" else "⚫ Local"
            textSize = 10f
            setTextColor(
                if (remoteMode) Color.parseColor("#4CAF50")
                else Color.parseColor("#78909C")
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        toolbar.addView(statusBar)
        root.addView(toolbar)

        // Keyboard / Emoji frame
        rootContainer = FrameLayout(this)

        keyboardView = KeyboardView(this,
            onKey = { type, text -> sendOrInject(type, text) },
            onSwitchLayer = { layer ->
                when (layer) {
                    KeyboardLayer.EMOJI      -> showEmoji()
                    KeyboardLayer.SWITCH_IME -> switchIME()
                    else -> {}
                }
            }
        )

        rootContainer!!.addView(
            keyboardView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            rootContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return root
    }

    private fun switchIME() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // window token is required — this is the correct cross-version approach
        val token = window?.window?.attributes?.token
        if (token != null) {
            @Suppress("DEPRECATION")
            imm.switchToNextInputMethod(token, false)
        } else {
            // Fallback: show the system IME picker
            imm.showInputMethodPicker()
        }
    }

    private fun showEmoji() {
        val container = rootContainer ?: return
        emojiPanel?.let { container.removeView(it) }
        emojiPanel = EmojiPanel(this,
            onEmoji = { emoji -> sendOrInject(Command.TYPE_CHAR, emoji) },
            onBack  = { hideEmoji() }
        )
        container.addView(
            emojiPanel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(220))
        )
    }

    private fun hideEmoji() {
        emojiPanel?.let { rootContainer?.removeView(it) }
        emojiPanel = null
    }

    private fun sendOrInject(type: Byte, text: String) {
        if (remoteMode) {
            BluetoothService.instance?.send(Command.encode(type, text))
        } else {
            injectCommand(type, text)
        }
    }

    private fun injectCommand(type: Byte, text: String) {
        val ic = currentInputConnection ?: return
        when (type) {
            Command.TYPE_CHAR -> ic.commitText(text, 1)
            Command.TYPE_BACKSPACE -> ic.deleteSurroundingText(1, 0)
            Command.TYPE_ENTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            Command.TYPE_CURSOR_LEFT -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
            Command.TYPE_CURSOR_RIGHT -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            Command.TYPE_SELECT_ALL -> {
                val now = System.currentTimeMillis()
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON))
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        hideEmoji()
        keyboardView?.shiftState = ShiftState.OFF
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }
}
