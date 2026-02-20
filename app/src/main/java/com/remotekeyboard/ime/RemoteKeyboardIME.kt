package com.remotekeyboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.remotekeyboard.bluetooth.BluetoothService
import com.remotekeyboard.protocol.Command

class RemoteKeyboardIME : InputMethodService() {

    private var remoteMode = false
    private var statusText: TextView? = null

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
                    statusText?.text = "Remote: Connected"
                }
                BluetoothService.BROADCAST_DISCONNECTED -> {
                    remoteMode = false
                    statusText?.text = "Remote: Disconnected"
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
        return buildKeyboardView()
    }

    private fun buildKeyboardView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF222222.toInt())
        }

        // Status bar
        statusText = TextView(this).apply {
            text = if (remoteMode) "Remote: Connected" else "Local Mode"
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(16, 8, 16, 4)
        }
        root.addView(statusText)

        // Keyboard rows
        val rows = listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l"),
            listOf("z","x","c","v","b","n","m","⌫"),
            listOf("123","Space","←","→","↵")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (label in row) {
                val btn = Button(this).apply {
                    text = label
                    textSize = when (label) {
                        "Space" -> 12f
                        "⌫", "←", "→", "↵" -> 16f
                        else -> 14f
                    }
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFF444444.toInt())
                    val weight = when (label) { "Space" -> 3f; else -> 1f }
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                        setMargins(2, 2, 2, 2)
                    }
                    setPadding(4, 12, 4, 12)
                    setOnClickListener { onKeyTapped(label) }
                }
                rowLayout.addView(btn)
            }
            root.addView(rowLayout)
        }
        return root
    }

    private fun onKeyTapped(label: String) {
        when (label) {
            "⌫" -> sendOrInject(Command.TYPE_BACKSPACE, "")
            "↵" -> sendOrInject(Command.TYPE_ENTER, "")
            "←" -> sendOrInject(Command.TYPE_CURSOR_LEFT, "")
            "→" -> sendOrInject(Command.TYPE_CURSOR_RIGHT, "")
            "Space" -> sendOrInject(Command.TYPE_CHAR, " ")
            "123" -> { /* TODO: switch to symbols */ }
            else -> sendOrInject(Command.TYPE_CHAR, label)
        }
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
    }

    override fun onDestroy() {
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }
}
