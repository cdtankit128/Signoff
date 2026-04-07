package com.signoff.ui

import com.signoff.service.PresenceService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.net.URI
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.system.exitProcess
import org.springframework.boot.SpringApplication

@Component
class TrayManager(
    private val presenceService: PresenceService
) : ApplicationContextAware {

    private val logger = LoggerFactory.getLogger(TrayManager::class.java)
    private var trayIcon: TrayIcon? = null
    private lateinit var applicationContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    @PostConstruct
    fun initTray() {
        if (!SystemTray.isSupported()) {
            logger.error("SystemTray is not supported on this platform.")
            return
        }

        try {
            val tray = SystemTray.getSystemTray()
            val image = createTrayIconImage()

            val popup = PopupMenu()

            val openItem = MenuItem("Open Dashboard")
            openItem.addActionListener {
                openDashboard()
            }

            val toggleLockItem = MenuItem(if (presenceService.lockEnabled) "Disable Auto-Lock" else "Enable Auto-Lock")
            toggleLockItem.addActionListener {
                presenceService.lockEnabled = !presenceService.lockEnabled
                toggleLockItem.label = if (presenceService.lockEnabled) "Disable Auto-Lock" else "Enable Auto-Lock"
            }

            val exitItem = MenuItem("Exit SignOff")
            exitItem.addActionListener {
                logger.info("Exiting application via System Tray...")
                // Destroy tray icon
                trayIcon?.let { tray.remove(it) }
                // Stop Spring Context gracefully
                SpringApplication.exit(applicationContext, { 0 })
                exitProcess(0)
            }

            popup.add(openItem)
            popup.addSeparator()
            popup.add(toggleLockItem)
            popup.addSeparator()
            popup.add(exitItem)

            trayIcon = TrayIcon(image, "SignOff Proximity Lock", popup)
            trayIcon?.isImageAutoSize = true
            
            // Double click opens Dashboard
            trayIcon?.addActionListener { openDashboard() }

            tray.add(trayIcon)
            logger.info("System Tray Icon initialized successfully.")

        } catch (e: Exception) {
            logger.error("Failed to initialize System Tray", e)
        }
    }

    private fun openDashboard() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("http://localhost:8080"))
            } else {
                val runtime = Runtime.getRuntime()
                runtime.exec(arrayOf("cmd", "/c", "start", "http://localhost:8080"))
            }
        } catch (e: Exception) {
            logger.error("Failed to open browser", e)
        }
    }

    private fun createTrayIconImage(): Image {
        // Draw a simple Lock Icon dynamically so we don't depend on external image files
        val size = 64
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Background circle
        g.color = Color(30, 41, 59) // Slate-800
        g.fillOval(0, 0, size, size)

        // Lock shackle
        g.color = Color.WHITE
        g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawArc(20, 12, 24, 24, 0, 180)

        // Lock body
        g.color = Color(6, 182, 212) // Cyan-500
        g.fillRoundRect(14, 24, 36, 28, 6, 6)

        // Keyhole
        g.color = Color.WHITE
        g.fillOval(28, 32, 8, 8)
        g.fillRect(30, 36, 4, 10)

        g.dispose()
        return image
    }

    @PreDestroy
    fun destroyTray() {
        if (SystemTray.isSupported() && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon)
        }
    }
}
