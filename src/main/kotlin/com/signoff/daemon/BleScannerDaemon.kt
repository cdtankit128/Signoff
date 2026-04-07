package com.signoff.daemon

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.Executors
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class BleScannerDaemon {

    private val logger = LoggerFactory.getLogger(BleScannerDaemon::class.java)
    private var process: Process? = null

    @PostConstruct
    fun startDaemon() {
        val pythonExecutable = File(".venv/Scripts/python.exe")
        val scriptFile = File("ble_scanner.py")

        if (!pythonExecutable.exists() || !scriptFile.exists()) {
            logger.warn("Python execution environment or BLE script not found! Run from the correct directory.")
            return
        }

        try {
            logger.info("Spawning Python BLE Scanner daemon...")
            
            val processBuilder = ProcessBuilder(pythonExecutable.absolutePath, scriptFile.name)
            processBuilder.directory(File(System.getProperty("user.dir")))
            processBuilder.redirectErrorStream(true) // Merge stderr and stdout

            process = processBuilder.start()

            // Consume process output in a background thread to prevent buffer overflow
            Executors.newSingleThreadExecutor().submit {
                try {
                    val reader = BufferedReader(InputStreamReader(process?.inputStream!!))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        logger.debug("[BLE-Scanner] {}", line)
                    }
                } catch (e: Exception) {
                    // Ignored on destroy
                }
            }

            logger.info("Python BLE Scanner daemon started successfully.")
            
        } catch (e: Exception) {
            logger.error("Failed to start Python BLE Scanner daemon", e)
        }
    }

    @PreDestroy
    fun stopDaemon() {
        process?.let {
            if (it.isAlive) {
                logger.info("Terminating Python BLE Scanner daemon...")
                it.destroy()
                
                // Fallback to forcible termination if it doesn't respond
                Thread.sleep(1000)
                if (it.isAlive) {
                    it.destroyForcibly()
                }
                logger.info("Daemon terminated.")
            }
        }
    }
}
