package com.signoff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SignoffApplication

fun main(args: Array<String>) {
    org.springframework.boot.builder.SpringApplicationBuilder(SignoffApplication::class.java).headless(false).run(*args)
    println("""
        
        ╔═══════════════════════════════════════════════╗
        ║        🔒 SignOff Server Started! 🔒          ║
        ║                                               ║
        ║  Dashboard: http://localhost:8080              ║
        ║  Status:    http://localhost:8080/api/status   ║
        ║                                               ║
        ║  Waiting for phone connection...               ║
        ╚═══════════════════════════════════════════════╝
        
    """.trimIndent())
}
