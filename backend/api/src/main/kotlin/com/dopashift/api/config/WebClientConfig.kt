package com.dopashift.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration for WebClient used to communicate with external services
 * (e.g., Keycloak token endpoint).
 */
@Configuration
class WebClientConfig {

    @Bean
    fun webClient(builder: WebClient.Builder): WebClient {
        return builder.build()
    }
}
