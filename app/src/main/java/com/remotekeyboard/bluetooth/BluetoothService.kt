package com.remotekeyboard.bluetooth

import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remotekeyboard.protocol.Command
import com.remotekeyboard.ui.RoleSelectActivity
import java.io.*
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class BluetoothService : Service() {

    companion object {
        const val TAG = "BluetoothService"
        val SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val CHANNEL_ID = "RemoteKeyboardChannel"
        const val NOTIF_ID = 1

        const val ACTION_START_SERVER = "START_SERVER"
        const val ACTION_START_CLIENT = "START_CLIENT"
        const val ACTION_STOP = "STOP"

        const val BROADCAST_CONNECTED    = "com.remotekeyboard.CONNECTED"
        const val BROADCAST_DISCONNECTED = "com.remotekeyboard.DISCONNECTED"
        const val BROADCAST_COMMAND      = "com.remotekeyboard.COMMAND"
        const val EXTRA_COMMAND_TYPE     = "cmd_type"
        const val EXTRA_COMMAND_TEXT     = "cmd_text"
        const val EXTRA_DEVICE_ADDRESS   = "device_address"

        var instance: BluetoothService? = null

        fun start(context: android.content.Context, action: String, deviceAddress: String? = null) {
            val intent = Intent(context, BluetoothService::class.java).apply {
                this.action = action
                deviceAddress?.let { putExtra(EXTRA_DEVICE_ADDRESS, it) }
            }
            // startForegroundService only exists on API 26+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val btAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val sendQueue = LinkedBlockingQueue<ByteArray>()
    private var running = false
    private var isServer = false
    private var targetDeviceAddress: String? = null

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var lastHeartbeat = 0L

    inner class LocalBinder : Binder() { fun getService() = this@BluetoothService }
    override fun onBind(intent: Intent?) = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Remote Keyboard running"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_START_CLIENT -> {
                targetDeviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                targetDeviceAddress?.let { startClient(it) }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startServer() {
        isServer = true
        Thread {
            try {
                serverSocket = btAdapter?.listenUsingRfcommWithServiceRecord("RemoteKeyboard", SERVICE_UUID)
                updateNotification("Waiting for keyboard phone...")
                val conn = serverSocket?.accept() ?: return@Thread
                handleConnection(conn)
            } catch (e: IOException) {
                Log.e(TAG, "Server error", e)
            }
        }.start()
    }

    private fun startClient(address: String) {
        isServer = false
        Thread {
            var attempts = 0
            while (true) {
                try {
                    val device = btAdapter?.getRemoteDevice(address) ?: break
                    val conn = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                    btAdapter?.cancelDiscovery()
                    conn.connect()
                    handleConnection(conn)
                    break
                } catch (e: IOException) {
                    attempts++
                    Log.w(TAG, "Connect attempt $attempts failed")
                    try { Thread.sleep(2000L * minOf(attempts, 5)) } catch (_: InterruptedException) {}
                }
            }
        }.start()
    }

    private fun handleConnection(conn: BluetoothSocket) {
        socket = conn
        outputStream = conn.outputStream
        inputStream = conn.inputStream
        running = true
        updateNotification("Connected to ${conn.remoteDevice.name ?: conn.remoteDevice.address}")
        sendBroadcast(Intent(BROADCAST_CONNECTED).putExtra(EXTRA_DEVICE_ADDRESS, conn.remoteDevice.address))
        startSender()
        startReceiver()
        startHeartbeat()
    }

    private fun startSender() {
        Thread {
            while (running) {
                try {
                    val cmd = sendQueue.take()
                    outputStream?.write(cmd)
                    outputStream?.flush()
                } catch (e: Exception) {
                    if (running) handleDisconnect()
                    break
                }
            }
        }.start()
    }

    private fun startReceiver() {
        Thread {
            while (running) {
                try {
                    val header = ByteArray(2)
                    inputStream?.read(header) ?: break
                    val len = header[1].toInt() and 0xFF
                    val payload = if (len > 0) ByteArray(len).also { inputStream?.read(it) } else ByteArray(0)
                    processIncoming(header + payload)
                } catch (e: Exception) {
                    if (running) handleDisconnect()
                    break
                }
            }
        }.start()
    }

    private fun processIncoming(bytes: ByteArray) {
        val (type, text) = Command.decode(bytes) ?: return
        when (type) {
            Command.TYPE_HEARTBEAT     -> send(Command.encode(Command.TYPE_HEARTBEAT_ACK))
            Command.TYPE_HEARTBEAT_ACK -> lastHeartbeat = System.currentTimeMillis()
            else -> sendBroadcast(Intent(BROADCAST_COMMAND)
                .putExtra(EXTRA_COMMAND_TYPE, type.toInt())
                .putExtra(EXTRA_COMMAND_TEXT, text))
        }
    }

    private fun startHeartbeat() {
        lastHeartbeat = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                if (!running) return
                send(Command.encode(Command.TYPE_HEARTBEAT))
                if (System.currentTimeMillis() - lastHeartbeat > 15_000) {
                    handleDisconnect(); return
                }
                heartbeatHandler.postDelayed(this, 5_000)
            }
        }
        heartbeatHandler.postDelayed(runnable, 5_000)
    }

    fun send(bytes: ByteArray) { sendQueue.offer(bytes) }

    private fun handleDisconnect() {
        running = false
        sendBroadcast(Intent(BROADCAST_DISCONNECTED))
        updateNotification("Disconnected — reconnecting...")
        cleanup()
        try { Thread.sleep(2000) } catch (_: InterruptedException) {}
        if (isServer) startServer() else targetDeviceAddress?.let { startClient(it) }
    }

    private fun cleanup() {
        try { socket?.close() }       catch (_: IOException) {}
        try { serverSocket?.close() } catch (_: IOException) {}
        socket = null; outputStream = null; inputStream = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Remote Keyboard", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, Intent(this, RoleSelectActivity::class.java), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Keyboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        running = false; cleanup(); instance = null
        super.onDestroy()
    }
}
