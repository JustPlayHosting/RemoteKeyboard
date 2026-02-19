package com.remotekeyboard.bluetooth

import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remotekeyboard.R
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

        // Actions
        const val ACTION_START_SERVER = "START_SERVER"
        const val ACTION_START_CLIENT = "START_CLIENT"
        const val ACTION_SEND = "SEND"
        const val ACTION_STOP = "STOP"

        // Broadcast
        const val BROADCAST_CONNECTED    = "com.remotekeyboard.CONNECTED"
        const val BROADCAST_DISCONNECTED = "com.remotekeyboard.DISCONNECTED"
        const val BROADCAST_COMMAND      = "com.remotekeyboard.COMMAND"
        const val EXTRA_COMMAND_TYPE     = "cmd_type"
        const val EXTRA_COMMAND_TEXT     = "cmd_text"
        const val EXTRA_DEVICE_ADDRESS   = "device_address"

        var instance: BluetoothService? = null
    }

    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val sendQueue = LinkedBlockingQueue<ByteArray>()
    private var senderThread: Thread? = null
    private var receiverThread: Thread? = null
    private var heartbeatHandler: Handler? = null
    private var heartbeatRunnable: Runnable? = null
    private var lastHeartbeat = 0L
    private var running = false
    private var isServer = false
    private var targetDeviceAddress: String? = null

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService() = this@BluetoothService
    }

    override fun onBind(intent: Intent?) = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Waiting for connection..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_START_CLIENT -> {
                targetDeviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                startClient(targetDeviceAddress ?: return START_STICKY)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startServer() {
        isServer = true
        Thread {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord("RemoteKeyboard", SERVICE_UUID)
                updateNotification("Waiting for keyboard to connect...")
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
            while (running || attempts == 0) {
                try {
                    val device = adapter?.getRemoteDevice(address) ?: break
                    val conn = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                    adapter?.cancelDiscovery()
                    conn.connect()
                    handleConnection(conn)
                    break
                } catch (e: IOException) {
                    attempts++
                    Log.w(TAG, "Connect attempt $attempts failed, retrying...")
                    Thread.sleep(2000L * minOf(attempts, 5))
                }
            }
        }.start()
    }

    private fun handleConnection(conn: BluetoothSocket) {
        socket = conn
        outputStream = conn.outputStream
        inputStream = conn.inputStream
        running = true

        sendBroadcast(Intent(BROADCAST_CONNECTED).apply {
            putExtra(EXTRA_DEVICE_ADDRESS, conn.remoteDevice.address)
        })
        updateNotification("Connected to ${conn.remoteDevice.name ?: conn.remoteDevice.address}")

        startSender()
        startReceiver()
        startHeartbeat()
    }

    private fun startSender() {
        senderThread = Thread {
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
        }.also { it.start() }
    }

    private fun startReceiver() {
        receiverThread = Thread {
            val buffer = ByteArray(256)
            while (running) {
                try {
                    val headerBytes = ByteArray(2)
                    inputStream?.read(headerBytes) ?: break
                    val type = headerBytes[0]
                    val len = headerBytes[1].toInt() and 0xFF
                    val payload = ByteArray(len)
                    if (len > 0) inputStream?.read(payload)
                    val fullCmd = headerBytes + payload
                    processIncoming(fullCmd)
                } catch (e: Exception) {
                    if (running) handleDisconnect()
                    break
                }
            }
        }.also { it.start() }
    }

    private fun processIncoming(bytes: ByteArray) {
        val (type, text) = Command.decode(bytes) ?: return
        when (type) {
            Command.TYPE_HEARTBEAT -> send(Command.encode(Command.TYPE_HEARTBEAT_ACK))
            Command.TYPE_HEARTBEAT_ACK -> lastHeartbeat = System.currentTimeMillis()
            else -> {
                sendBroadcast(Intent(BROADCAST_COMMAND).apply {
                    putExtra(EXTRA_COMMAND_TYPE, type)
                    putExtra(EXTRA_COMMAND_TEXT, text)
                })
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatHandler = Handler(Looper.getMainLooper())
        lastHeartbeat = System.currentTimeMillis()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                send(Command.encode(Command.TYPE_HEARTBEAT))
                val elapsed = System.currentTimeMillis() - lastHeartbeat
                if (elapsed > 15_000) {
                    Log.w(TAG, "Heartbeat timeout, reconnecting")
                    handleDisconnect()
                    return
                }
                heartbeatHandler?.postDelayed(this, 5_000)
            }
        }
        heartbeatHandler?.postDelayed(heartbeatRunnable!!, 5_000)
    }

    fun send(bytes: ByteArray) {
        sendQueue.offer(bytes)
    }

    private fun handleDisconnect() {
        running = false
        sendBroadcast(Intent(BROADCAST_DISCONNECTED))
        updateNotification("Disconnected — reconnecting...")
        cleanup()
        if (!isServer && targetDeviceAddress != null) {
            Thread.sleep(2000)
            startClient(targetDeviceAddress!!)
        } else if (isServer) {
            startServer()
        }
    }

    private fun cleanup() {
        heartbeatRunnable?.let { heartbeatHandler?.removeCallbacks(it) }
        try { socket?.close() } catch (_: IOException) {}
        try { serverSocket?.close() } catch (_: IOException) {}
        socket = null; outputStream = null; inputStream = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Remote Keyboard", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(this, 0,
            Intent(this, RoleSelectActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Keyboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        running = false
        cleanup()
        instance = null
        super.onDestroy()
    }
}

