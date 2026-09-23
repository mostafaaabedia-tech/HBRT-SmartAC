package com.hbrt.smartac

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

// iOS Color Palette
val IosBackground = Color(0xFFF2F2F7)
val IosCardBackground = Color(0xFFFFFFFF)
val IosGreen = Color(0xFF34C759)
val IosBlue = Color(0xFF007AFF)
val IosOrange = Color(0xFFFF9500)
val IosRed = Color(0xFFFF3B30)
val IosTextColor = Color(0xFF1C1C1E)
val IosSubText = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    private var outputStream: OutputStream? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartACApp(
                onConnect = { device, callback -> connectToAC(device, callback) },
                getPairedDevices = { getPairedDevices() },
                sendCommand = { cmd -> sendCommand(cmd) }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPairedDevices(): Set<BluetoothDevice> {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        return if (adapter != null && adapter.isEnabled) {
            adapter.bondedDevices
        } else {
            emptySet()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToAC(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            // MUST run on background thread to prevent crash!
            Thread {
                try {
                    bluetoothSocket?.connect()
                    outputStream = bluetoothSocket?.outputStream
                    runOnUiThread { onResult(true) }
                } catch (e: IOException) {
                    try { bluetoothSocket?.close() } catch (c: IOException) {}
                    runOnUiThread { onResult(false) }
                }
            }.start()
        } catch (e: IOException) {
            onResult(false)
        }
    }

    private fun sendCommand(command: String) {
        if (outputStream != null) {
            try {
                outputStream?.write(command.toByteArray())
            } catch (e: IOException) {
                // Silent fail
            }
        }
    }
}

@Composable
fun SmartACApp(
    onConnect: (BluetoothDevice, (Boolean) -> Unit) -> Unit,
    getPairedDevices: () -> Set<BluetoothDevice>,
    sendCommand: (String) -> Unit
) {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var showDeviceList by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { isGranted ->
        if (isGranted.values.all { it }) {
            showDeviceList = true
        } else {
            Toast.makeText(context, "Bluetooth permissions are required!", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeviceList) {
        DeviceListScreen(
            devices = getPairedDevices(),
            onDeviceClick = { device ->
                showDeviceList = false
                isConnecting = true
                onConnect(device) { success ->
                    isConnecting = false
                    isConnected = success
                    Toast.makeText(context, if (success) "Connected!" else "Connection Failed", Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = { showDeviceList = false }
        )
    } else {
        Scaffold(
            containerColor = IosBackground,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(20.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()), // FIX: Now you can scroll!
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HBRT Smart AC",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, bottom = 5.dp)
                )
                Text(
                    text = "Herat Boys Robotic Team",
                    fontSize = 15.sp,
                    color = IosSubText,
                    modifier = Modifier.padding(bottom = 30.dp)
                )

                // 24-Hour Stats Chart
                StatsChartCard()
                Spacer(modifier = Modifier.height(20.dp))

                // Connect Button
                Button(
                    onClick = {
                        if (isConnecting) return@Button
                        val hasPermissions = permissionsToRequest.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (hasPermissions) {
                            showDeviceList = true
                        } else {
                            launcher.launch(permissionsToRequest)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) IosGreen else IosBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(55.dp)
                ) {
                    Text(
                        text = if (isConnecting) "Connecting..." else if (isConnected) "Connected" else "Connect to AC",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Fan Control Card
                IosCard(title = "Fan Power", subtitle = "Turn the AC fan on/off") {
                    IosToggle(onCmd = "fanon\n", offCmd = "fanoff\n", sendCommand = { sendCommand(it) }, color = IosGreen)
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Auto Mode Card
                IosCard(title = "Auto Mode", subtitle = "Adjusts based on temp") {
                    IosToggle(onCmd = "autoon\n", offCmd = "autooff\n", sendCommand = { sendCommand(it) }, color = IosOrange)
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Mouth Control Card
                IosCard(title = "Mouth Vent", subtitle = "Open or close the vent") {
                    IosToggle(onCmd = "open\n", offCmd = "close\n", sendCommand = { sendCommand(it) }, color = IosBlue)
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Swing Control Card
                IosCard(title = "Swing Mode", subtitle = "Oscillate the vent") {
                    IosToggle(onCmd = "swingon\n", offCmd = "swingoff\n", sendCommand = { sendCommand(it) }, color = IosBlue)
                }
            }
        }
    }
}

@Composable
fun DeviceListScreen(devices: Set<BluetoothDevice>, onDeviceClick: (BluetoothDevice) -> Unit, onCancel: () -> Unit) {
    Scaffold(
        containerColor = IosBackground,
        topBar = {
            Surface(color = IosCardBackground, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = IosBlue, fontSize = 18.sp) }
                    Spacer(Modifier.weight(1f))
                    Text("Select Device", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = IosTextColor)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(64.dp))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No paired devices found.\nPair your ESP32 in Bluetooth settings first.", textAlign = TextAlign.Center, color = IosSubText)
                }
            } else {
                devices.forEach { device ->
                    @SuppressLint("MissingPermission")
                    val name = device.name ?: "Unknown Device"
                    Surface(
                        color = IosCardBackground,
                        modifier = Modifier.fillMaxWidth().clickable { onDeviceClick(device) }
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = IosTextColor)
                            Text(device.address, fontSize = 14.sp, color = IosSubText)
                        }
                    }
                    Divider(color = Color(0xFFE9E9EA), thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
    }
}

@Composable
fun StatsChartCard() {
    val tempData = listOf(22f, 21f, 20f, 21f, 23f, 25f, 28f, 30f, 29f, 27f, 26f, 25f)
    val humData = listOf(60f, 65f, 68f, 60f, 55f, 50f, 45f, 40f, 42f, 48f, 50f, 52f)

    Surface(
        color = IosCardBackground,
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("24H Climate", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = IosTextColor)
                Text("Temp & Humidity", fontSize = 13.sp, color = IosSubText)
            }
            Spacer(modifier = Modifier.height(20.dp))
            
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val width = size.width
                val height = size.height
                val tempPath = Path()
                val humPath = Path()
                
                val maxVal = 80f
                val tempStep = width / (tempData.size - 1)
                val humStep = width / (humData.size - 1)

                tempData.forEachIndexed { index, value ->
                    val x = index * tempStep
                    val y = height - (value / maxVal) * height
                    if (index == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                }

                humData.forEachIndexed { index, value ->
                    val x = index * humStep
                    val y = height - (value / maxVal) * height
                    if (index == 0) humPath.moveTo(x, y) else humPath.lineTo(x, y)
                }

                drawPath(tempPath, color = IosRed, style = Stroke(width = 4f))
                drawPath(humPath, color = IosBlue, style = Stroke(width = 4f))
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(IosRed) }
                Text(" Temperature", fontSize = 12.sp, color = IosSubText)
                Spacer(Modifier.width(20.dp))
                Canvas(Modifier.size(10.dp)) { drawCircle(IosBlue) }
                Text(" Humidity", fontSize = 12.sp, color = IosSubText)
            }
        }
    }
}

@Composable
fun IosCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(
        color = IosCardBackground,
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = IosTextColor)
                Text(subtitle, fontSize = 13.sp, color = IosSubText)
            }
            content()
        }
    }
}

@Composable
fun IosToggle(onCmd: String, offCmd: String, sendCommand: (String) -> Unit, color: Color) {
    var checked by remember { mutableStateOf(false) }
    
    Switch(
        checked = checked,
        onCheckedChange = {
            checked = it
            sendCommand(if (it) onCmd else offCmd)
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = color,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE9E9EA),
            uncheckedThumbColor = Color.White
        )
    )
}
