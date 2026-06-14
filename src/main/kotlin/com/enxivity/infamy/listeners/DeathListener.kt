package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class DeathListener(private val plugin: InfamySMP) : Listener {
    private val recentKills = mutableMapOf<UUID, MutableList<Long>>()

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.player
        val killer = victim.killer
        val victimRep = plugin.infamyManager.getRawReputation(victim)

        // 1. Level 21 Boss Death Mechanics
        if (victimRep >= 21) {
            val bossBottle = plugin.itemManager.createBossBottle()
            val droppedItem = victim.world.dropItemNaturally(victim.location, bossBottle)
            droppedItem.isInvulnerable = true

            // Feature: Global Announcement
            if (plugin.config.getBoolean("settings.boss-bottle-announce", true)) {
                Bukkit.broadcast(Component.text("The Most Infamous Player has fallen! The Core of Infamy has been dropped!", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            }
            // Feature: Global Dragon Sound
            if (plugin.config.getBoolean("settings.boss-bottle-sound", true)) {
                for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(onlinePlayer.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
                }
            }
        }

        val loseAmount = when {
            victimRep >= 21 -> 3
            victimRep >= 11 -> 2
            else -> 1
        }
        plugin.infamyManager.setReputation(victim, victimRep - loseAmount)

        // 2. Killer Logic
        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val now = System.currentTimeMillis()
            val logs = recentKills.computeIfAbsent(killer.uniqueId) { mutableListOf() }
            logs.removeIf { now - it > 1800000 }

            var awardPoints = 1
            if (plugin.teamManager.areTeammates(killer.uniqueId, victim.uniqueId)) {
                awardPoints = 5
                killer.sendMessage(Component.text("You betrayed your teammate! Penalty bottle generated.", NamedTextColor.RED))
            }

            if (logs.size >= 3) {
                awardPoints = 0
                killer.sendMessage(Component.text("Anti-Farm Triggered! No points awarded.", NamedTextColor.YELLOW))
            } else {
                logs.add(now)
            }

            if (awardPoints > 0) {
                val dropAsBottle = plugin.config.getBoolean("settings.drop-bottle-on-kill", true)

                if (dropAsBottle) {
                    val infamyBottle = plugin.itemManager.createInfamyBottle(awardPoints)
                    val droppedItem = victim.world.dropItemNaturally(victim.location, infamyBottle)
                    droppedItem.isInvulnerable = true
                } else {
                    val killerRep = plugin.infamyManager.getRawReputation(killer)
                    val newRep = (killerRep + awardPoints).coerceAtMost(if (killerRep >= 21) 21 else 20)
                    plugin.infamyManager.setReputation(killer, newRep)
                }
            }
        }

        if (victimRep >= 15 && plugin.config.getBoolean("infamy_abilities.bad_fortune.enabled", true)) {
            if (victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                event.deathMessage(null)
            }
        }
    }
}