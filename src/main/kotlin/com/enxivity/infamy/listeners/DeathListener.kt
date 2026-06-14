package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
            droppedItem.isInvulnerable = true // Native MC protection layer
        }

        // Deduct victim's standard points
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
            logs.removeIf { now - it > 1800000 } // Clear out records past 30 mins

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
                    // If config is false, enforce the Level 20 bottle cap manually
                    val newRep = (killerRep + awardPoints).coerceAtMost(if (killerRep >= 21) 21 else 20)
                    plugin.infamyManager.setReputation(killer, newRep)
                }
            }
        }

        // Invisibility hiding death records
        if (victimRep >= 15 && plugin.config.getBoolean("infamy_abilities.bad_fortune.enabled", true)) {
            if (victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                event.deathMessage(null)
            }
        }
    }
}