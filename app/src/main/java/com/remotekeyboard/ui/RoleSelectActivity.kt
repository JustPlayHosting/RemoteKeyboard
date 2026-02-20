package com.remotekeyboard.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.remotekeyboard.databinding.ActivityRoleSelectBinding

class RoleSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectBinding

    private val PERM_REQUEST = 100

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request permissions immediately on launch
        requestAllPermissions()

        binding.btnKeyboardMode.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, KeyboardActivity::class.java))
            } else {
                requestAllPermissions()
            }
        }

        binding.btnTargetMode.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, TargetActivity::class.java))
            } else {
                requestAllPermissions()
            }
        }

        binding.btnTeleprompter.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, TeleprompterActivity::class.java))
            } else {
                requestAllPermissions()
            }
        }

        binding.btnImeSetup.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        // If user previously denied, show explanation first
        val shouldExplain = missing.any {
            ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }

        if (shouldExplain) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth Permission Required")
                .setMessage("This app needs Bluetooth access to connect the two phones. Please grant the permission.")
                .setPositiveButton("Grant") { _, _ ->
                    ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) {
            val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            if (denied) {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required for this app to work.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
