package com.signoff.mobile.sensor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

class BleAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "BleAdvertiser"
        const val TARGET_UUID_STRING = "0000180F-0000-1000-8000-00805f9b34fb"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE Advertising started successfully!")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed: Error $errorCode")
            isAdvertising = false
        }
    }

    fun startAdvertising() {
        if (isAdvertising) return

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or not supported.")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "Bluetooth LE Advertising not supported on this device.")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val pUuid = ParcelUuid.fromString(TARGET_UUID_STRING)
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(pUuid)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Starting BLE Advertiser with UUID $TARGET_UUID_STRING...")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission!")
        } catch (e: Exception) {
            Log.e(TAG, "Advertising crash: ${e.message}")
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            Log.i(TAG, "BLE Advertising stopped.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission during stop.")
        } catch (e: Exception) {
            Log.e(TAG, "Stop crash: ${e.message}")
        }
        isAdvertising = false
    }
}
