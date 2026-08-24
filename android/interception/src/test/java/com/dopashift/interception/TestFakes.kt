package com.dopashift.interception

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.local.entity.LocalTelemetryEvent

/**
 * Shared test fakes for interception module tests.
 * Internal visibility so they're accessible across test files in this package.
 */

internal class FakeInterceptionRuleDao : InterceptionRuleDao {
    private val rules = mutableListOf<LocalInterceptionRule>()

    fun addRule(rule: LocalInterceptionRule) {
        rules.add(rule)
    }

    override suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule? {
        return rules.find { it.appPackageName == packageName && it.isActive }
    }

    override suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule> {
        return rules.filter { it.userId == userId && it.isActive }
    }

    override suspend fun findById(id: String): LocalInterceptionRule? {
        return rules.find { it.id == id }
    }

    override suspend fun upsert(rule: LocalInterceptionRule) {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
    }

    override suspend fun deleteById(id: String) {
        rules.removeAll { it.id == id }
    }
}

internal class FakeTelemetryDao : TelemetryDao {
    private val events = mutableListOf<LocalTelemetryEvent>()

    override suspend fun upsert(event: LocalTelemetryEvent) {
        events.removeAll { it.id == event.id }
        events.add(event)
    }

    override suspend fun findByDate(date: String): List<LocalTelemetryEvent> {
        return events.filter { it.date == date }
    }

    override suspend fun getTotalForegroundSeconds(date: String, packageName: String): Long? {
        val total = events
            .filter { it.date == date && it.appPackageName == packageName }
            .sumOf { it.foregroundSeconds }
        return if (total == 0L) null else total
    }

    override suspend fun deleteOlderThan(cutoffDate: String) {
        events.removeAll { it.date < cutoffDate }
    }
}
