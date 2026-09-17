package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class EventManager(private val plugin: InfamySMP) {

    var isEventActive: Boolean = false
        private set

    var isCalendarEvent: Boolean = false
        private set

    var eventTimeRemaining: Long = 0L
        private set

    var eventTotalDuration: Long = 0L
        private set

    var calendarEnabled: Boolean = true
        private set

    private var timezone: ZoneId = ZoneId.of("GMT+2")
    private var recurring: Boolean = true
    private var activeDays: Set<DayOfWeek> = emptySet()
    private val cancelledDays = mutableSetOf<DayOfWeek>()
    private var lastCheckedDay: DayOfWeek? = null

    // Load config
    fun loadConfig() {
        val file = File(plugin.dataFolder, "event.yml")
        if (!file.exists()) {
            plugin.saveResource("event.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(file)

        calendarEnabled = config.getBoolean("calendar.enabled", true)
        val tzStr = config.getString("calendar.timezone", "GMT+2") ?: "GMT+2"
        timezone = try {
            ZoneId.of(tzStr)
        } catch (e: Exception) {
            plugin.logger.warning("Invalid timezone '$tzStr' in event.yml. Defaulting to GMT+2.")
            ZoneId.of("GMT+2")
        }

        recurring = config.getBoolean("calendar.recurring", true)

        val days = mutableSetOf<DayOfWeek>()
        val daysSection = config.getConfigurationSection("calendar.days")
        daysSection?.getKeys(false)?.forEach { dayName ->
            if (daysSection.getBoolean(dayName, false)) {
                try {
                    days.add(DayOfWeek.valueOf(dayName.uppercase()))
                } catch (_: Exception) {}
            }
        }
        activeDays = days
    }

    // Save calendar
    fun setCalendarEnabled(enabled: Boolean) {
        calendarEnabled = enabled
        if (enabled) {
            cancelledDays.clear()
        }
        val file = File(plugin.dataFolder, "event.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("calendar.enabled", enabled)
        config.save(file)

        if (enabled) {
            checkCalendarTrigger()
        }
    }

    // Start event
    fun startEvent(durationSeconds: Long, isCalendar: Boolean = false) {
        isEventActive = true
        isCalendarEvent = isCalendar
        eventTimeRemaining = durationSeconds
        eventTotalDuration = if (isCalendar) 86400L else durationSeconds

        val msg = Component.text("Event Started.", NamedTextColor.GREEN)
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(msg)
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
        }
    }

    // Start calendar event
    private fun startCalendarEvent() {
        val now = ZonedDateTime.now(timezone)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(timezone)
        val secondsUntilMidnight = Duration.between(now, midnight).seconds.coerceAtLeast(1L)
        lastCheckedDay = now.dayOfWeek
        startEvent(secondsUntilMidnight, isCalendar = true)
    }

    // Stop event
    fun stopEvent() {
        if (!isEventActive) return
        val wasCalendar = isCalendarEvent
        isEventActive = false
        isCalendarEvent = false
        eventTimeRemaining = 0L
        eventTotalDuration = 0L

        if (wasCalendar) {
            val currentDay = ZonedDateTime.now(timezone).dayOfWeek
            cancelledDays.add(currentDay)
        }

        val msg = Component.text("Event Ended.", NamedTextColor.RED)
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(msg)
            player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f)
        }
    }

    // Tick scheduler
    fun tickSecond() {
        if (isEventActive) {
            if (isCalendarEvent) {
                val now = ZonedDateTime.now(timezone)
                val currentDay = now.dayOfWeek

                if (lastCheckedDay != null && lastCheckedDay != currentDay) {
                    stopEvent()
                    if (calendarEnabled && activeDays.contains(currentDay) && !cancelledDays.contains(currentDay)) {
                        startCalendarEvent()
                    }
                }
            } else {
                if (eventTimeRemaining > 0) {
                    eventTimeRemaining--
                    if (eventTimeRemaining == 0L) {
                        stopEvent()
                    }
                }
            }
        } else if (calendarEnabled) {
            checkCalendarTrigger()
        }
    }

    // Calendar check
    private fun checkCalendarTrigger() {
        val now = ZonedDateTime.now(timezone)
        val currentDay = now.dayOfWeek

        if (lastCheckedDay != currentDay) {
            if (recurring) {
                cancelledDays.remove(currentDay)
            }
            lastCheckedDay = currentDay
        }

        if (activeDays.contains(currentDay) && !cancelledDays.contains(currentDay)) {
            startCalendarEvent()
        }
    }

    // Format remaining time
    fun getFormattedRemainingTime(): String {
        if (!isEventActive) return "No"

        val totalSecs = if (isCalendarEvent) {
            val now = ZonedDateTime.now(timezone)
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(timezone)
            var secs = Duration.between(now, midnight).seconds

            // Consecutive days calculation
            var checkDate = now.plusDays(1)
            while (activeDays.contains(checkDate.dayOfWeek)) {
                secs += 86400L
                checkDate = checkDate.plusDays(1)
            }
            secs
        } else {
            eventTimeRemaining
        }

        val days = totalSecs / 86400
        val hours = (totalSecs % 86400) / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    // Active modifiers list
    fun getActiveModifiers(): List<String> {
        val list = mutableListOf<String>()
        val config = getEventConfig()
        if (config.getBoolean("double-infamy.enabled", true)) list.add("Double Infamy")
        if (config.getBoolean("double-exp.enabled", true)) list.add("Double Exp")
        if (config.getBoolean("double-drops.enabled", true)) list.add("Double Drops")
        return list
    }

    fun isDoubleInfamyEnabled(): Boolean = isEventActive && getEventConfig().getBoolean("double-infamy.enabled", true)
    fun getDoubleInfamyChance(): Double = getEventConfig().getDouble("double-infamy.chance", 0.5)
    fun getDoubleInfamyExtra(): Int = getEventConfig().getInt("double-infamy.extra-points", 1)

    fun isDoubleExpEnabled(): Boolean = isEventActive && getEventConfig().getBoolean("double-exp.enabled", true)
    fun getExpMultiplier(): Double = getEventConfig().getDouble("double-exp.multiplier", 2.0)
    fun isMendingCheaper(): Boolean = getEventConfig().getBoolean("double-exp.cheaper-mending", true)
    fun doesHonorExpStack(): Boolean = getEventConfig().getBoolean("double-exp.honor-stacks", true)

    fun isDoubleDropsEnabled(): Boolean = isEventActive && getEventConfig().getBoolean("double-drops.enabled", true)
    fun getDoubleDropsChance(): Double = getEventConfig().getDouble("double-drops.chance", 1.0)
    fun getDoubleDropsMultiplier(): Int = getEventConfig().getInt("double-drops.multiplier", 2)
    fun isDropsAffectOres(): Boolean = getEventConfig().getBoolean("double-drops.affects-ores", true)
    fun isDropsAffectMobs(): Boolean = getEventConfig().getBoolean("double-drops.affects-mobs", true)

    private fun getEventConfig(): YamlConfiguration {
        val file = File(plugin.dataFolder, "event.yml")
        return YamlConfiguration.loadConfiguration(file)
    }
}