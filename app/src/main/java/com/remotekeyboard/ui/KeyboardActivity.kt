package com.remotekeyboard.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.remotekeyboard.bluetooth.BluetoothService
import com.remotekeyboard.databinding.ActivityKeyboardBinding

class KeyboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyboardBinding
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothService.BROADCAST_CONNECTED -> {
                    binding.statusText.text = "Connected!"
                    binding.statusText.setTextColor(0xFF00CC66.toInt())
                    binding.btnConnect.text = "Connected"
                }
                BluetoothService.BROADCAST_DISCONNECTED -> {
                    binding.statusText.text = "Disconnected — reconnecting..."
                    binding.statusText.setTextColor(0xFFFF4444.toInt())
                    binding.btnConnect.text = "Connect"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPairedDevices()

        binding.btnConnect.setOnClickListener {
            val idx = binding.deviceSpinner.selectedItemPosition
            if (idx < 0 || idx >= pairedDevices.size) {
                Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val device = pairedDevices[idx]
            BluetoothService.start(this, BluetoothService.ACTION_START_CLIENT, device.address)
            binding.statusText.text = "Connecting to ${device.name}..."
        }

        binding.btnImeHint.setOnClickListener {
            Toast.makeText(this,
                "On the target phone: select 'Remote Keyboard' as keyboard, then tap any text field",
                Toast.LENGTH_LONG).show()
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothService.BROADCAST_CONNECTED)
            addAction(BluetoothService.BROADCAST_DISCONNECTED)
        }
        registerReceiver(stateReceiver, filter)
    }

    private fun loadPairedDevices() {
        val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

        val bonded = try { adapter?.bondedDevices ?: emptySet() } catch (e: SecurityException) { emptySet() }
        pairedDevices.clear(); deviceNames.clear()
        pairedDevices.addAll(bonded)
        deviceNames.addAll(bonded.map { "${it.name ?: "Unknown"} (${it.address})" })

        binding.deviceSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, deviceNames)

        binding.noDevicesHint.visibility = if (pairedDevices.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }
}
