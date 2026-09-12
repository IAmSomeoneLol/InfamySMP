package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class EventManager(private val plugin: InfamySMP) {

    var isEventActive: Boolean = false
        private set

    var eventTimeRemaining: Long = 0L
        private set

    var eventTotalDuration: Long = 0L
        private set

    private var calendarEnabled: Boolean = false
    private var timezone: ZoneId = ZoneId.of("GMT+2")
    private var recurring: Boolean = true
    private var activeDays: Set<DayOfWeek> = emptySet()
    private var startHour: Int = 18
    private var autoDuration: Long = 7200L
    private var lastAutoTriggerDay: DayOfWeek? = null

    // Load config
    fun loadConfig() {
        val file = File(plugin.dataFolder, "event.yml")
        if (!file.exists()) {
            plugin.saveResource("event.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(file)

        calendarEnabled = config.getBoolean("calendar.enabled", false)
        val tzStr = config.getString("calendar.timezone", "GMT+2") ?: "GMT+2"
        timezone = try {
            ZoneId.of(tzStr)
        } catch (e: Exception) {
            ZoneId.of("GMT+2")
        }

        recurring = config.getBoolean("calendar.recurring", true)
        startHour = config.getInt("calendar.start-hour", 18)
        autoDuration = config.getLong("calendar.duration-seconds", 7200L)

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
        val file = File(plugin.dataFolder, "event.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("calendar.enabled", enabled)
        config.save(file)
    }

    // Start event
    fun startEvent(durationSeconds: Long) {
        isEventActive = true
        eventTimeRemaining = durationSeconds
        eventTotalDuration = durationSeconds

        val msg = Component.text("⚡ AN INFAMY EVENT HAS BEGUN! ⚡", NamedTextColor.GOLD, TextDecoration.BOLD)
        val sub = Component.text("Double Infamy, XP Boost, and Extra Drops are now active for ${durationSeconds / 60} minutes!", NamedTextColor.YELLOW)

        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(msg)
            player.sendMessage(sub)
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }
    }

    // Stop event
    fun stopEvent() {
        if (!isEventActive) return
        isEventActive = false
        eventTimeRemaining = 0L
        eventTotalDuration = 0L

        val msg = Component.text("The Infamy Event has concluded!", NamedTextColor.RED, TextDecoration.BOLD)
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(msg)
            player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f)
        }
    }

    // Tick scheduler
    fun tickSecond() {
        if (isEventActive) {
            if (eventTimeRemaining > 0) {
                eventTimeRemaining--
                if (eventTimeRemaining == 0L) {
                    stopEvent()
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
        val currentHour = now.hour

        if (activeDays.contains(currentDay) && currentHour == startHour) {
            if (lastAutoTriggerDay != currentDay) {
                lastAutoTriggerDay = currentDay
                startEvent(autoDuration)
            }
        } else if (lastAutoTriggerDay != null && lastAutoTriggerDay != currentDay) {
            if (recurring) {
                lastAutoTriggerDay = null
            }
        }
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