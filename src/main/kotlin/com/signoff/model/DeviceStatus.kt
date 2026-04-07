package com.signoff.model

data class HeartbeatMessage(
    val deviceId: String = "",
    val moving: Boolean = false,
    val acceleration: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class BleHeartbeatMessage(
    val deviceId: String = "",
    val rssi: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceState(
    var deviceId: String = "unknown",
    var lastHeartbeat: Long = 0,
    var isMoving: Boolean = false,
    var lastAcceleration: Float = 0f,
    var connected: Boolean = false,
    var firstConnectedAt: Long = 0,
    var heartbeatCount: Long = 0,
    var mode: String = "WIFI",
    var lastRssi: Int = 0,
    var avgRssi: Int = 0
) {
    val timeSinceLastHeartbeat: Long
        get() = if (lastHeartbeat == 0L) Long.MAX_VALUE
                else System.currentTimeMillis() - lastHeartbeat
    
    val isHeartbeatFresh: Boolean
        get() = timeSinceLastHeartbeat < 10_000 // less than 10 seconds old
}

data class LockEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val deviceId: String = ""
)

data class StatusResponse(
    val deviceState: DeviceState?,
    val serverTime: Long = System.currentTimeMillis(),
    val lockEnabled: Boolean = true,
    val recentLockEvents: List<LockEvent> = emptyList(),
    val config: ConfigSnapshot = ConfigSnapshot()
)

data class ConfigSnapshot(
    val heartbeatTimeoutMs: Long = 5000,
    val gracePeriodMs: Long = 3000,
    val lockCooldownMs: Long = 30000,
    val motionThreshold: Double = 2.0,
    val bleThresholdDbm: Int = -80
)

data class ConfigUpdate(
    val heartbeatTimeoutMs: Long? = null,
    val gracePeriodMs: Long? = null,
    val lockCooldownMs: Long? = null,
    val motionThreshold: Double? = null,
    val bleThresholdDbm: Int? = null
)

/** Broadcast to dashboard via WebSocket */
data class DashboardUpdate(
    val type: String, // "heartbeat", "status_change", "lock_event"
    val deviceState: DeviceState?,
    val lockEvent: LockEvent? = null,
    val timestamp: Long = System.currentTimeMillis()
)
