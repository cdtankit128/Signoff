package com.signoff.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // Enable a simple in-memory message broker for broadcasting to subscribers
        registry.enableSimpleBroker("/topic")
        // Prefix for messages FROM clients (phone → server)
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // WebSocket endpoint - phone connects here
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*") // Allow all origins (phone + dashboard)
            .withSockJS() // Fallback for browsers that don't support WebSocket
    }
}
