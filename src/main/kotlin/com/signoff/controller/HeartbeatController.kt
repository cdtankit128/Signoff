package com.signoff.controller

import com.signoff.model.ConfigUpdate
import com.signoff.model.HeartbeatMessage
import com.signoff.model.StatusResponse
import com.signoff.service.PresenceService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.http.ResponseEntity
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Controller
class HeartbeatController(
    private val presenceService: PresenceService
) {

    private val logger = LoggerFactory.getLogger(HeartbeatController::class.java)

    /**
     * WebSocket STOMP endpoint — phone sends heartbeats here.
     * Destination: /app/heartbeat
     */
    @MessageMapping("/heartbeat")
    fun handleHeartbeat(message: HeartbeatMessage) {
        presenceService.onHeartbeat(message)
    }

    /**
     * Handle WebSocket disconnect events.
     */
    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val headerAccessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = headerAccessor.sessionId ?: "unknown"
        presenceService.onDisconnect(sessionId)
    }

    // === REST Endpoints (for debugging & dashboard) ===

    /**
     * GET /api/status — Current device state and system info
     */
    @GetMapping("/api/status")
    @ResponseBody
    fun getStatus(): ResponseEntity<StatusResponse> {
        return ResponseEntity.ok(presenceService.getStatus())
    }

    /**
     * POST /api/config — Update thresholds at runtime
     */
    @PostMapping("/api/config")
    @ResponseBody
    fun updateConfig(@RequestBody update: ConfigUpdate): ResponseEntity<Map<String, String>> {
        presenceService.updateConfig(update)
        return ResponseEntity.ok(mapOf("status" to "updated"))
    }

    /**
     * POST /api/lock/toggle — Enable/disable auto-lock
     */
    @PostMapping("/api/lock/toggle")
    @ResponseBody
    fun toggleLock(): ResponseEntity<Map<String, Any>> {
        presenceService.lockEnabled = !presenceService.lockEnabled
        logger.info("🔒 Lock ${if (presenceService.lockEnabled) "ENABLED" else "DISABLED"}")
        return ResponseEntity.ok(mapOf(
            "lockEnabled" to presenceService.lockEnabled,
            "status" to "ok"
        ))
    }

    /**
     * POST /api/heartbeat — REST-based heartbeat (alternative to WebSocket, for testing)
     */
    @PostMapping("/api/heartbeat")
    @ResponseBody
    fun restHeartbeat(@RequestBody message: HeartbeatMessage): ResponseEntity<Map<String, String>> {
        presenceService.onHeartbeat(message)
        return ResponseEntity.ok(mapOf("status" to "received"))
    }

    /**
     * POST /api/ble — Python BLE Script sends RSSI here
     */
    @PostMapping("/api/ble")
    @ResponseBody
    fun bleHeartbeat(@RequestBody message: com.signoff.model.BleHeartbeatMessage): ResponseEntity<Map<String, String>> {
        presenceService.onBleHeartbeat(message)
        return ResponseEntity.ok(mapOf("status" to "ble_received"))
    }
}
