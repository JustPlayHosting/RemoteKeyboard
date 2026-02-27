package com.remotekeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.remotekeyboard.bluetooth.BluetoothService
import com.remotekeyboard.databinding.ActivityTargetBinding

class TargetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTargetBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTargetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartListening.setOnClickListener {
            BluetoothService.start(this, BluetoothService.ACTION_START_SERVER)
            binding.statusText.text = "Listening for keyboard phone..."
            Toast.makeText(this,
                "Select 'Remote Keyboard' as your keyboard, then tap any text field!",
                Toast.LENGTH_LONG).show()
        }

        binding.btnImeSettings.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }
}
