package com.signoff.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class LockService {

    private val logger = LoggerFactory.getLogger(LockService::class.java)

    /**
     * Locks the Windows workstation using the native API.
     * Executes: rundll32.exe user32.dll,LockWorkStation
     */
    fun lockWorkstation(reason: String): Boolean {
        return try {
            logger.warn("🔒 LOCKING WORKSTATION — Reason: $reason")
            
            val process = ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation")
                .redirectErrorStream(true)
                .start()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                logger.info("✅ Workstation locked successfully")
                true
            } else {
                logger.error("❌ Lock command failed with exit code: $exitCode")
                false
            }
        } catch (e: IOException) {
            logger.error("❌ Failed to execute lock command: ${e.message}", e)
            false
        } catch (e: InterruptedException) {
            logger.error("❌ Lock command interrupted: ${e.message}", e)
            Thread.currentThread().interrupt()
            false
        }
    }
}
