package com.hbrt.smartac

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }

        val statusText = TextView(this).apply {
            text = "Status: Disconnected"
            textSize = 20f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        layout.addView(statusText)

        val btnConnect = Button(this).apply { text = "Connect to AC" }
        layout.addView(btnConnect)

        val btnOpen = Button(this).apply { text = "OPEN MOUTH" }
        layout.addView(btnOpen)

        val btnClose = Button(this).apply { text = "CLOSE MOUTH" }
        layout.addView(btnClose)

        val btnFanOn = Button(this).apply { text = "FAN ON" }
        layout.addView(btnFanOn)

        val btnFanOff = Button(this).apply { text = "FAN OFF" }
        layout.addView(btnFanOff)

        val btnAutoOn = Button(this).apply { text = "AUTO ON" }
        layout.addView(btnAutoOn)

        val btnAutoOff = Button(this).apply { text = "AUTO OFF" }
        layout.addView(btnAutoOff)

        val btnSwingOn = Button(this).apply { text = "SWING ON" }
        layout.addView(btnSwingOn)

        val btnSwingOff = Button(this).apply { text = "SWING OFF" }
        layout.addView(btnSwingOff)

        setContentView(layout)

        btnConnect.setOnClickListener {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter == null || !adapter.isEnabled) {
                Toast.makeText(this, "Turn on Bluetooth first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Find the ESP32 device
            val device = adapter.bondedDevices.find { it.name == "Smart-AC-BT" }
            if (device == null) {
                Toast.makeText(this, "Pair with 'Smart-AC-BT' in Bluetooth settings first!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                statusText.text = "Status: Connected!"
                Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show()
                try { bluetoothSocket?.close() } catch (c: IOException) {}
            }
        }

        btnOpen.setOnClickListener { sendCommand("open\n") }
        btnClose.setOnClickListener { sendCommand("close\n") }
        btnFanOn.setOnClickListener { sendCommand("fanon\n") }
        btnFanOff.setOnClickListener { sendCommand("fanoff\n") }
        btnAutoOn.setOnClickListener { sendCommand("autoon\n") }
        btnAutoOff.setOnClickListener { sendCommand("autooff\n") }
        btnSwingOn.setOnClickListener { sendCommand("swingon\n") }
        btnSwingOff.setOnClickListener { sendCommand("swingoff\n") }
    }

    private fun sendCommand(command: String) {
        if (outputStream != null) {
            try {
                outputStream?.write(command.toByteArray())
            } catch (e: IOException) {
                Toast.makeText(this, "Error sending command", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Connect to AC first!", Toast.LENGTH_SHORT).show()
        }
    }
}
