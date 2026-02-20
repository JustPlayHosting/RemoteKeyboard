package com.remotekeyboard.ui

import android.content.*
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.remotekeyboard.bluetooth.BluetoothService
import com.remotekeyboard.databinding.ActivityTeleprompterBinding
import com.remotekeyboard.protocol.Command

class TeleprompterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeleprompterBinding
    private val textBuffer = StringBuilder()

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothService.BROADCAST_COMMAND -> {
                    val type = intent.getIntExtra(BluetoothService.EXTRA_COMMAND_TYPE, 0).toByte()
                    val text = intent.getStringExtra(BluetoothService.EXTRA_COMMAND_TEXT) ?: ""
                    applyCommand(type, text)
                }
                BluetoothService.BROADCAST_CONNECTED -> {
                    binding.connectionStatus.text = "● Connected"
                    binding.connectionStatus.setTextColor(0xFF00FF88.toInt())
                }
                BluetoothService.BROADCAST_DISCONNECTED -> {
                    binding.connectionStatus.text = "● Disconnected"
                    binding.connectionStatus.setTextColor(0xFFFF4444.toInt())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeleprompterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start BT server
        val svcIntent = Intent(this, BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_START_SERVER
        }
        startForegroundService(svcIntent)

        binding.btnClear.setOnClickListener {
            textBuffer.clear()
            binding.teleprompterText.text = ""
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothService.BROADCAST_COMMAND)
            addAction(BluetoothService.BROADCAST_CONNECTED)
            addAction(BluetoothService.BROADCAST_DISCONNECTED)
        }
        registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun applyCommand(type: Byte, text: String) {
        when (type) {
            Command.TYPE_CHAR -> textBuffer.append(text)
            Command.TYPE_BACKSPACE -> if (textBuffer.isNotEmpty())
                textBuffer.deleteCharAt(textBuffer.lastIndex)
            Command.TYPE_ENTER -> textBuffer.append("\n")
            Command.TYPE_CLEAR -> textBuffer.clear()
        }
        binding.teleprompterText.text = textBuffer.toString()
        // Auto-scroll to bottom
        binding.scrollView.post {
            binding.scrollView.fullScroll(android.widget.ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(commandReceiver)
        super.onDestroy()
    }
}
