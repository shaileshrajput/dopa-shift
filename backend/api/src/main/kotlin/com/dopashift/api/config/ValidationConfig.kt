package com.dopashift.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

/**
 * Configuration class documenting the server-side input validation strategy.
 *
 * ## Validation Layers
 *
 * 1. **Jakarta Bean Validation (JSR 380)**
 *    All request DTOs use Jakarta validation annotations (@NotBlank, @Size, @Min, @Max, @Pattern)
 *    and controller method parameters are annotated with @Valid. Spring automatically triggers
 *    validation before the handler method is invoked. Violations are caught by
 *    GlobalExceptionHandler.handleMethodArgumentNotValid() and returned as structured 400 errors.
 *
 * 2. **Input Sanitization (InputSanitizationFilter)**
 *    A servlet filter strips null bytes (\u0000), ASCII control characters (0x01–0x1F excluding
 *    whitespace), and DEL (0x7F) from all JSON request bodies before they reach the controllers.
 *    This is defense-in-depth against injection attacks that embed invisible characters.
 *
 * 3. **Parameterized Queries (JPA/Hibernate)**
 *    All database access uses Spring Data JPA repositories with derived query methods or JPQL
 *    with named parameters. No raw SQL string concatenation is used anywhere in the codebase.
 *    This eliminates SQL injection risk by design. Hibernate binds all user-supplied values
 *    as prepared statement parameters.
 *
 * 4. **Domain-Level Validation**
 *    Business rules (name uniqueness, limits, keyword constraints) are enforced in the domain
 *    use cases, providing a second validation boundary independent of the API layer.
 *
 * ## Field Constraints Summary
 *
 * | Field                    | Max Length | Additional Rules                |
 * |--------------------------|------------|---------------------------------|
 * | Goal name                | 100        | Not blank, unique per user      |
 * | Goal category            | 50         | Not blank                       |
 * | Goal keyword             | 50         | 1-20 per goal                   |
 * | Todo text                | 500        | Not blank                       |
 * | Habit checkpoint desc    | 200        | 1-30 per track                  |
 * | Display name             | 100        | Not blank                       |
 * | API key                  | 256        | Not blank                       |
 * | Password                 | 8+ chars   | Not blank                       |
 * | Daily allowance          | 1-480 min  | Integer range                   |
 * | Escalation interval      | 1-1440 min | Long range                      |
 * | Max escalations          | 0-10       | Integer range                   |
 *
 * Requirements: 22.7
 */
@Configuration
@Validated
class ValidationConfig
