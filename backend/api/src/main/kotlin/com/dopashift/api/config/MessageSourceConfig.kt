package com.dopashift.api.config

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

/**
 * Configures Spring MessageSource for server-generated text localization
 * (notifications, error messages) in en, hi, and mr.
 *
 * Locale resolution order:
 * 1. User's stored preferredLocale (set by application logic)
 * 2. Accept-Language header (via AcceptHeaderLocaleResolver)
 * 3. Fallback to English (Requirement 12.6)
 *
 * Requirements: 12.4, 12.6, 5.7
 */
@Configuration
class MessageSourceConfig {

    companion object {
        val SUPPORTED_LOCALES: List<Locale> = listOf(
            Locale.ENGLISH,
            Locale.forLanguageTag("hi"),
            Locale.forLanguageTag("mr")
        )
    }

    @Bean
    fun messageSource(): MessageSource {
        val messageSource = ReloadableResourceBundleMessageSource()
        messageSource.setBasename("classpath:messages")
        messageSource.setDefaultEncoding("UTF-8")
        // Fall back to English when a key is missing for the requested locale (Req 12.6)
        messageSource.setFallbackToSystemLocale(false)
        messageSource.setDefaultLocale(Locale.ENGLISH)
        return messageSource
    }

    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = AcceptHeaderLocaleResolver()
        resolver.setDefaultLocale(Locale.ENGLISH)
        resolver.supportedLocales = SUPPORTED_LOCALES
        return resolver
    }
}
