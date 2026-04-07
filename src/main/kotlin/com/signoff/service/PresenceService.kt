package com.signoff.service

import com.signoff.config.SignoffProperties
import com.signoff.model.*
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedDeque

@Service
class PresenceService(
    private val lockService: LockService,
    private val properties: SignoffProperties,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val logger = LoggerFactory.getLogger(PresenceService::class.java)

    // BLE RSSI smoothing queue
    private val rssiQueue = ConcurrentLinkedDeque<Int>()

    // Current device state
    @Volatile
    var deviceState: DeviceState? = null
        internal set

    // Lock control
    @Volatile
    var lockEnabled: Boolean = true

    private var lastLockTime: Long = 0
    private var gracePeriodStart: Long = 0
    private var inGracePeriod: Boolean = false

    // Lock event history (keep last 50)
    val lockEvents: ConcurrentLinkedDeque<LockEvent> = ConcurrentLinkedDeque()

    /**
     * Called when a heartbeat is received from the phone.
     */
    fun onHeartbeat(message: HeartbeatMessage) {
        val now = System.currentTimeMillis()
        val isMoving = message.acceleration > properties.motionThreshold

        val state = deviceState ?: DeviceState(
            deviceId = message.deviceId,
            firstConnectedAt = now
        )

        val wasDisconnected = !state.connected

        state.apply {
            deviceId = message.deviceId
            lastHeartbeat = now
            this.isMoving = isMoving
            lastAcceleration = message.acceleration
            connected = true
            heartbeatCount++
        }

        deviceState = state

        // Cancel grace period — phone is back
        if (inGracePeriod) {
            logger.info("✅ Phone reconnected during grace period — lock cancelled")
            inGracePeriod = false
            gracePeriodStart = 0
        }

        if (wasDisconnected) {
            logger.info("📱 Phone connected via Wi-Fi: ${message.deviceId}")
            broadcastUpdate("status_change", state)
        } else {
            broadcastUpdate("heartbeat", state)
        }
    }

    /**
     * Called when a BLE heartbeat (RSSI) is received from the Python script.
     */
    fun onBleHeartbeat(message: BleHeartbeatMessage) {
        val now = System.currentTimeMillis()

        // Push new RSSI to history queue (keep last 5 readings)
        rssiQueue.addLast(message.rssi)
        if (rssiQueue.size > 5) {
            rssiQueue.removeFirst()
        }

        // Calculate average
        val avgRssi = if (rssiQueue.isNotEmpty()) rssiQueue.sum() / rssiQueue.size else message.rssi

        val state = deviceState ?: DeviceState(
            deviceId = message.deviceId,
            firstConnectedAt = now
        )

        val wasDisconnected = !state.connected || state.mode != "BLUETOOTH"

        state.apply {
            deviceId = message.deviceId
            lastHeartbeat = now
            connected = true
            mode = "BLUETOOTH"
            lastRssi = message.rssi
            this.avgRssi = avgRssi
            heartbeatCount++
        }

        deviceState = state

        if (inGracePeriod) {
            // Cancel grace period if signal bounced back above threshold
            if (avgRssi >= properties.bleThresholdDbm) {
                logger.info("📡 Bluetooth signal recovered ($avgRssi dBm) during grace period — lock cancelled")
                inGracePeriod = false
                gracePeriodStart = 0
            }
        }

        if (wasDisconnected) {
            logger.info("📱 Phone connected via BLUETOOTH: ${message.deviceId} | Signal: $avgRssi dBm")
            broadcastUpdate("status_change", state)
        } else {
            broadcastUpdate("heartbeat", state)
        }
    }

    /**
     * Called when a WebSocket session disconnects.
     */
    fun onDisconnect(sessionId: String) {
        logger.warn("⚠️ WebSocket session disconnected: $sessionId")
        deviceState?.let { state ->
            state.connected = false
            broadcastUpdate("status_change", state)
        }
    }

    /**
     * Scheduled presence check — runs every 2 seconds.
     * This is the BRAIN of the system.
     */
    @Scheduled(fixedDelay = 2000)
    fun checkPresence() {
        val state = deviceState ?: return // No device registered yet

        val now = System.currentTimeMillis()
        val timeSinceHeartbeat = now - state.lastHeartbeat
        val isLost = timeSinceHeartbeat >= properties.heartbeatTimeoutMs

        if (state.mode == "WIFI") {
            if (!isLost) {
                // Heartbeat is fresh, cancel any pending lock
                if (inGracePeriod) {
                    inGracePeriod = false
                    gracePeriodStart = 0
                }
                return
            }

            // Heartbeat lost! Mark as disconnected
            if (state.connected) {
                state.connected = false
                logger.warn("💔 Heartbeat lost! Last seen ${timeSinceHeartbeat}ms ago")
                broadcastUpdate("status_change", state)
            }

            if (!lockEnabled) return

            if (!state.isMoving) {
                if (inGracePeriod) {
                    logger.debug("Phone was still — not locking despite heartbeat loss")
                    inGracePeriod = false
                    gracePeriodStart = 0
                }
                return
            }
            
            if (!inGracePeriod) {
                inGracePeriod = true
                gracePeriodStart = now
                logger.warn("⏳ Wi-Fi dropped while moving! Grace period started...")
                return
            }
        } else if (state.mode == "BLUETOOTH") {
            if (isLost && state.connected) {
                state.connected = false
                logger.warn("💔 Bluetooth Beacon lost! Last seen ${timeSinceHeartbeat}ms ago")
                broadcastUpdate("status_change", state)
            }

            if (!lockEnabled) return

            val signalWeak = state.avgRssi < properties.bleThresholdDbm

            // If signal is strong AND beacon is not lost, all is well
            if (!isLost && !signalWeak) {
                if (inGracePeriod) {
                    logger.debug("Bluetooth signal recovered — lock cancelled")
                    inGracePeriod = false
                    gracePeriodStart = 0
                }
                return
            }

            if (!inGracePeriod) {
                inGracePeriod = true
                gracePeriodStart = now
                val reasonText = if (isLost) "Signal lost entirely" else "Signal too weak (${state.avgRssi} dBm)"
                logger.warn("📡 $reasonText! Grace period started...")
                return
            }
        }

        // Check if grace period has elapsed
        val graceElapsed = now - gracePeriodStart
        if (graceElapsed < properties.gracePeriodMs) {
            return // Still in grace period
        }

        // Check cooldown
        val timeSinceLastLock = now - lastLockTime
        if (lastLockTime > 0 && timeSinceLastLock < properties.lockCooldownMs) {
            logger.debug("Lock on cooldown — ${properties.lockCooldownMs - timeSinceLastLock}ms remaining")
            return
        }

        // === LOCK THE WORKSTATION ===
        val reason = if (state.mode == "WIFI") {
            "Phone '${state.deviceId}' moved away (accel: ${state.lastAcceleration} m/s², heartbeat lost for ${timeSinceHeartbeat}ms)"
        } else {
            if (isLost) "Bluetooth signal lost entirely (timeout: ${timeSinceHeartbeat}ms)"
            else "Bluetooth signal fell below threshold: ${state.avgRssi} dBm"
        }

        val event = LockEvent(
            timestamp = now,
            reason = reason,
            deviceId = state.deviceId
        )

        val locked = lockService.lockWorkstation(reason)

        if (locked) {
            lastLockTime = now
            lockEvents.addFirst(event)
            if (lockEvents.size > 50) lockEvents.removeLast()

            broadcastUpdate("lock_event", state, event)
        }

        // Reset grace period
        inGracePeriod = false
        gracePeriodStart = 0
    }

    fun getStatus(): StatusResponse {
        return StatusResponse(
            deviceState = deviceState,
            lockEnabled = lockEnabled,
            recentLockEvents = lockEvents.toList().take(10),
            config = ConfigSnapshot(
                heartbeatTimeoutMs = properties.heartbeatTimeoutMs,
                gracePeriodMs = properties.gracePeriodMs,
                lockCooldownMs = properties.lockCooldownMs,
                motionThreshold = properties.motionThreshold,
                bleThresholdDbm = properties.bleThresholdDbm
            )
        )
    }

    fun updateConfig(update: ConfigUpdate) {
        update.heartbeatTimeoutMs?.let { properties.heartbeatTimeoutMs = it }
        update.gracePeriodMs?.let { properties.gracePeriodMs = it }
        update.lockCooldownMs?.let { properties.lockCooldownMs = it }
        update.motionThreshold?.let { properties.motionThreshold = it }
        update.bleThresholdDbm?.let { properties.bleThresholdDbm = it }
        logger.info("⚙️ Config updated: timeout=${properties.heartbeatTimeoutMs}ms, " +
                "grace=${properties.gracePeriodMs}ms, cooldown=${properties.lockCooldownMs}ms, " +
                "threshold=${properties.motionThreshold}, ble=${properties.bleThresholdDbm}")
    }

    private fun broadcastUpdate(type: String, state: DeviceState, lockEvent: LockEvent? = null) {
        val update = DashboardUpdate(
            type = type,
            deviceState = state.copy(), // Send a snapshot
            lockEvent = lockEvent
        )
        messagingTemplate.convertAndSend("/topic/dashboard", update)
    }
}
