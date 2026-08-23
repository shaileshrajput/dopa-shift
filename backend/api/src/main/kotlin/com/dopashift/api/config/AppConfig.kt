package com.dopashift.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * General application configuration providing shared infrastructure beans.
 */
@Configuration
class AppConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
