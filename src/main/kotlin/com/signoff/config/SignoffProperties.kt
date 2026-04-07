package com.signoff.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "signoff")
class SignoffProperties {
    /** Time in ms before considering heartbeat lost */
    var heartbeatTimeoutMs: Long = 5000

    /** Grace period in ms before triggering lock after heartbeat loss */
    var gracePeriodMs: Long = 3000

    /** Cooldown in ms after locking before allowing another lock */
    var lockCooldownMs: Long = 30000

    /** Acceleration magnitude threshold to consider phone "moving" (m/s²) */
    var motionThreshold: Double = 2.0

    /** Expected heartbeat interval from phone (ms) */
    var heartbeatIntervalMs: Long = 2000

    /** The signal strength threshold below which distance is considered "away" (dBm) */
    var bleThresholdDbm: Int = -80
}
