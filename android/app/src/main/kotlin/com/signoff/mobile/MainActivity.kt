package com.signoff.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.signoff.mobile.databinding.ActivityMainBinding
import com.signoff.mobile.service.SignoffService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestPermissions()
    }

    private fun setupUI() {
        // Default server URL
        binding.etServerUrl.setText("")

        // Setup radio group
        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbBle.id) {
                binding.etServerUrl.isEnabled = false
                binding.etServerUrl.alpha = 0.5f
            } else {
                binding.etServerUrl.isEnabled = true
                binding.etServerUrl.alpha = 1.0f
            }
        }

        // Connect button
        binding.btnConnect.setOnClickListener {
            if (isServiceRunning) {
                stopSignoffService()
            } else {
                discoverAndConnect()
            }
        }

        updateUI()
    }

    private fun discoverAndConnect() {
        val isBleMode = binding.rbBle.isChecked
        if (isBleMode) {
            startSignoffService()
            return
        }

        binding.btnConnect.isEnabled = false
        binding.btnConnect.text = "🔍 Searching..."

        CoroutineScope(Dispatchers.IO).launch {
            var discoveredIp = performUdpDiscovery()
            
            // If UDP fails (e.g. Android Hotspot blocks broadcasts), fallback to TCP Subnet Sweep
            if (discoveredIp == null) {
                discoveredIp = performTcpSweep()
            }
            
            withContext(Dispatchers.Main) {
                binding.btnConnect.isEnabled = true
                if (discoveredIp != null) {
                    val url = "ws://$discoveredIp:8080/ws/websocket"
                    binding.etServerUrl.setText(url)
                    Toast.makeText(this@MainActivity, "Found laptop at $discoveredIp", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Discovery failed. Using manual IP.", Toast.LENGTH_SHORT).show()
                }
                startSignoffService()
            }
        }
    }

    private fun performUdpDiscovery(): String? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2000 // 2 seconds timeout

            val pingData = "SIGNOFF_DISCOVERY_PING".toByteArray()

            // Find all active broadcast addresses across all network interfaces (fixes Hotspot routing)
            val broadcastAddresses = mutableSetOf<InetAddress>()
            broadcastAddresses.add(InetAddress.getByName("255.255.255.255")) // Default fallback

            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (address in networkInterface.interfaceAddresses) {
                        address.broadcast?.let { broadcastAddresses.add(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fire packet to every valid broadcast address
            for (broadcastAddress in broadcastAddresses) {
                try {
                    val packet = DatagramPacket(pingData, pingData.size, broadcastAddress, 8081)
                    socket.send(packet)
                } catch (e: Exception) {
                    // Ignore individual routing failures
                }
            }

            val receiveBuffer = ByteArray(256)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            
            // Wait for response
            socket.receive(receivePacket)

            val response = String(receivePacket.data, 0, receivePacket.length).trim()
            if (response.startsWith("SIGNOFF_SERVER_ACK")) {
                return receivePacket.address.hostAddress
            }
        } catch (e: SocketTimeoutException) {
            // No laptop replied
        } catch (e: Exception) {
            // Network error
            e.printStackTrace()
        } finally {
            socket?.close()
        }
        return null
    }

    private suspend fun performTcpSweep(): String? = withContext(Dispatchers.IO) {
        val prefixes = mutableSetOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (address in networkInterface.interfaceAddresses) {
                    val ip = address.address
                    if (ip is Inet4Address) {
                        prefixes.add(ip.hostAddress.substringBeforeLast("."))
                    }
                }
            }
        } catch (e: Exception) { }

        // Common hotspot prefix fallback
        prefixes.add("192.168.43")
        
        for (prefix in prefixes) {
            val jobs = (1..254).map { i ->
                async {
                    val targetIp = "$prefix.$i"
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(targetIp, 8080), 800) // Fast timeout 
                        socket.close()
                        targetIp // Return IP if connection successful
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            val results = jobs.awaitAll()
            val validIp = results.firstOrNull { it != null }
            if (validIp != null) return@withContext validIp
        }
        
        return@withContext null
    }

    private fun startSignoffService() {
        val isBleMode = binding.rbBle.isChecked
        val serverUrl = binding.etServerUrl.text.toString().trim()

        if (!isBleMode && serverUrl.isEmpty()) {
            Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, SignoffService::class.java).apply {
            putExtra(SignoffService.EXTRA_SERVER_URL, serverUrl)
            putExtra(SignoffService.EXTRA_DEVICE_ID, Build.MODEL)
            putExtra(SignoffService.EXTRA_MODE, if (isBleMode) "ble" else "wifi")
        }

        ContextCompat.startForegroundService(this, intent)
        isServiceRunning = true
        updateUI()

        Toast.makeText(this, "SignOff service started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopSignoffService() {
        val intent = Intent(this, SignoffService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()

        Toast.makeText(this, "SignOff service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        if (isServiceRunning) {
            binding.btnConnect.text = "⏹ Disconnect"
            binding.btnConnect.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            binding.tvStatus.text = "🟢 Service Running"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            )
        } else {
            binding.btnConnect.text = "▶ Connect & Start"
            binding.btnConnect.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            binding.tvStatus.text = "⚪ Not Connected"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
        }
    }

    private fun requestPermissions() {
        val neededPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (denied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Some permissions denied. SignOff may not work correctly.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
