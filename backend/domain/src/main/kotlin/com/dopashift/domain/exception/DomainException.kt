package com.dopashift.domain.exception

/**
 * Base class for domain-level exceptions.
 */
open class DomainException(message: String) : RuntimeException(message)

/**
 * Thrown when a goal name already exists for the same user.
 */
class GoalNameAlreadyExistsException(name: String) :
    DomainException("A goal with the name '$name' already exists for this user")

/**
 * Thrown when a referenced goal cannot be found.
 */
class GoalNotFoundException(id: String) :
    DomainException("Goal not found: $id")

/**
 * Thrown when attempting to create a Habit_Track or InterceptionRule
 * while the user has zero active goals.
 */
class NoActiveGoalsException :
    DomainException("At least one active goal is required to create a habit track or interception rule")

/**
 * Thrown when keyword validation fails.
 */
class KeywordValidationException(message: String) :
    DomainException(message)

/**
 * Thrown when a delete action references an invalid target goal for reassignment.
 */
class InvalidReassignmentTargetException(message: String) :
    DomainException(message)

/**
 * Thrown when a domain entity cannot be found by its identifier.
 */
class EntityNotFoundException(message: String) : DomainException(message)

/**
 * Thrown when a domain limit has been exceeded (e.g., max items per day).
 */
class LimitExceededException(message: String) : DomainException(message)

/**
 * Thrown when a user's LLM configuration cannot be found.
 */
class LlmConfigNotFoundException(userId: String) :
    DomainException("LLM configuration not found for user: $userId")
