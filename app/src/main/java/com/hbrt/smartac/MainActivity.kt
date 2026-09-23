package com.hbrt.smartac

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val IosBackground = Color(0xFFF2F2F7)
val IosCardBackground = Color(0xFFFFFFFF)
val IosGreen = Color(0xFF34C759)
val IosBlue = Color(0xFF007AFF)
val IosOrange = Color(0xFFFF9500)
val IosRed = Color(0xFFFF3B30)
val IosTextColor = Color(0xFF1C1C1E)
val IosSubText = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    var outputStream: OutputStream? = null
    var inputStream: InputStream? = null
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
        return if (adapter != null && adapter.isEnabled) adapter.bondedDevices else emptySet()
    }

    @SuppressLint("MissingPermission")
    private fun connectToAC(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            Thread {
                try {
                    bluetoothSocket?.connect()
                    outputStream = bluetoothSocket?.outputStream
                    inputStream = bluetoothSocket?.inputStream
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
        outputStream?.let {
            try {
                it.write(command.toByteArray())
            } catch (e: IOException) {}
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

    var liveTemp by remember { mutableStateOf("--") }
    var liveHum by remember { mutableStateOf("--") }
    var espFanOn by remember { mutableStateOf(false) }
    var espSwingOn by remember { mutableStateOf(false) }
    var espAutoOn by remember { mutableStateOf(false) }
    
    val tempHistory = remember { mutableStateListOf(22f, 22f, 22f, 22f, 22f) }
    val humHistory = remember { mutableStateListOf(50f, 50f, 50f, 50f, 50f) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { isGranted ->
        if (isGranted.values.all { it }) showDeviceList = true
        else Toast.makeText(context, "Bluetooth permissions are required!", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(1024)
                while (true) {
                    try {
                        val bytes = (context as MainActivity).inputStream?.read(buffer) ?: break
                        if (bytes > 0) {
                            val rawMessage = String(buffer, 0, bytes)
                            val lines = rawMessage.split("\n")
                            for (line in lines) {
                                val parts = line.trim().split(",")
                                if (parts.size == 5) {
                                    val t = parts[0].toFloatOrNull()
                                    val h = parts[1].toFloatOrNull()
                                    if (t != null && h != null) {
                                        tempHistory.add(t)
                                        humHistory.add(h)
                                        if (tempHistory.size > 12) tempHistory.removeAt(0)
                                        if (humHistory.size > 12) humHistory.removeAt(0)
                                        
                                        withContext(Dispatchers.Main) {
                                            liveTemp = parts[0]
                                            liveHum = parts[1]
                                            espFanOn = parts[2] == "1"
                                            espSwingOn = parts[3] == "1"
                                            espAutoOn = parts[4] == "1"
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        break
                    }
                }
            }
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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("HBRT Smart AC", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = IosTextColor, modifier = Modifier.padding(top = 20.dp, bottom = 5.dp))
                Text("Herat Boys Robotic Team", fontSize = 15.sp, color = IosSubText, modifier = Modifier.padding(bottom = 30.dp))

                StatsChartCard(liveTemp, liveHum, tempHistory, humHistory)
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (isConnecting) return@Button
                        val hasPermissions = permissionsToRequest.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (hasPermissions) showDeviceList = true else launcher.launch(permissionsToRequest)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) IosGreen else IosBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(55.dp)
                ) {
                    Text(if (isConnecting) "Connecting..." else if (isConnected) "Connected" else "Connect to AC", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(30.dp))

                IosCard(title = "Fan Power", subtitle = "Live Status: ${if (espFanOn) "ON" else "OFF"}") {
                    IosToggle(isChecked = espFanOn, onCmd = "fanon\n", offCmd = "fanoff\n", sendCommand = { sendCommand(it) }, color = IosGreen)
                }
                Spacer(modifier = Modifier.height(20.dp))

                IosCard(title = "Auto Mode", subtitle = "Live Status: ${if (espAutoOn) "ON" else "OFF"}") {
                    IosToggle(isChecked = espAutoOn, onCmd = "autoon\n", offCmd = "autooff\n", sendCommand = { sendCommand(it) }, color = IosOrange)
                }
                Spacer(modifier = Modifier.height(20.dp))

                IosCard(title = "Swing Mode", subtitle = "Live Status: ${if (espSwingOn) "ON" else "OFF"}") {
                    IosToggle(isChecked = espSwingOn, onCmd = "swingon\n", offCmd = "swingoff\n", sendCommand = { sendCommand(it) }, color = IosBlue)
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
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Text("No paired devices found.\nPair your ESP32 in settings first.", textAlign = TextAlign.Center, color = IosSubText)
                }
            } else {
                devices.forEach { device ->
                    @SuppressLint("MissingPermission")
                    val name = device.name ?: "Unknown Device"
                    Surface(color = IosCardBackground, modifier = Modifier.fillMaxWidth().clickable { onDeviceClick(device) }) {
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
fun StatsChartCard(liveTemp: String, liveHum: String, tempData: List<Float>, humData: List<Float>) {
    Surface(
        color = IosCardBackground,
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Live Climate", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = IosTextColor)
                Text("Temp: $liveTemp°C  Hum: $liveHum%", fontSize = 13.sp, color = IosSubText, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val width = size.width
                val height = size.height
                val tempPath = Path()
                val humPath = Path()
                
                if (tempData.isNotEmpty()) {
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
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = IosTextColor)
                Text(subtitle, fontSize = 13.sp, color = IosSubText)
            }
            content()
        }
    }
}

@Composable
fun IosToggle(isChecked: Boolean, onCmd: String, offCmd: String, sendCommand: (String) -> Unit, color: Color) {
    var checked by remember(isChecked) { mutableStateOf(isChecked) }
    
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
