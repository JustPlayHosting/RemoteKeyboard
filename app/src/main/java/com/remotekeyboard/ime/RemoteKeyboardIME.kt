package com.remotekeyboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        }
        listOf("😊", "GIF", "G▸", "📋", "🎨", "🎤").forEach { label ->
            toolbar.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.parseColor("#B0BEC5"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
            })
        }
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
                if (layer == KeyboardLayer.EMOJI) showEmoji()
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
