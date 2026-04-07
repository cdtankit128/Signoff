package com.signoff.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicBoolean

@Service
class DiscoveryService(
    @Value("\${server.port:8080}") private val serverPort: Int
) {
    private val logger = LoggerFactory.getLogger(DiscoveryService::class.java)
    private val isRunning = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    companion object {
        const val DISCOVERY_PORT = 8081
        const val PING_MSG = "SIGNOFF_DISCOVERY_PING"
        const val ACK_MSG_PREFIX = "SIGNOFF_SERVER_ACK:"
    }

    @PostConstruct
    fun start() {
        isRunning.set(true)
        thread = Thread {
            try {
                // Listen on all interfaces on port 8081
                socket = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                }
                logger.info("UDP Discovery Service listening on port $DISCOVERY_PORT")
                val buffer = ByteArray(256)
                
                while (isRunning.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    
                    val message = String(packet.data, 0, packet.length).trim()
                    if (message == PING_MSG) {
                        val replyMsg = "$ACK_MSG_PREFIX$serverPort"
                        val replyData = replyMsg.toByteArray()
                        val replyPacket = DatagramPacket(
                            replyData, 
                            replyData.size, 
                            packet.address, 
                            packet.port
                        )
                        socket?.send(replyPacket)
                        logger.debug("Answered discovery ping from ${packet.address.hostAddress}")
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    logger.error("UDP Discovery error: ${e.message}")
                }
            }
        }.apply {
            name = "UDP-Discovery"
            isDaemon = true
            start()
        }
    }

    @PreDestroy
    fun stop() {
        isRunning.set(false)
        socket?.close()
        thread?.interrupt()
    }
}
