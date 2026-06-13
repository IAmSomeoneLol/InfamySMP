// Event tracking class. Connected to InfamySMP.kt and InfamyManager.kt.
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class DeathListener(private val plugin: InfamySMP) : Listener {

    private val killHistory = mutableMapOf<UUID, MutableMap<UUID, MutableList<Long>>>()

    @EventHandler
    public fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.player
        val killer = victim.killer

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val isBetrayal = plugin.teamManager.areTeammates(killer.uniqueId, victim.uniqueId)

            // Dying pushes the victim backwards on the slider (into Honor if they cross 0)
            val currentVictimRep = plugin.infamyManager.getRawReputation(victim)
            val lossAmount = if (isBetrayal) 3 else 1
            plugin.infamyManager.setReputation(victim, currentVictimRep - lossAmount)

            // Anti-Farm Mechanics
            val now = System.currentTimeMillis()
            val victimMap = killHistory.computeIfAbsent(killer.uniqueId) { mutableMapOf() }
            val timestamps = victimMap.computeIfAbsent(victim.uniqueId) { mutableListOf() }

            timestamps.removeIf { now - it > 1800000 }
            timestamps.add(now)

            val pointsToGive = when {
                timestamps.size > 3 -> -1
                isBetrayal -> 5
                else -> 1
            }

            val bottle = plugin.itemManager.createInfamyBottle(pointsToGive)
            victim.location.world?.dropItemNaturally(victim.location, bottle)
        } else {
            // Environmental death penalty pushes you backwards too
            val currentRep = plugin.infamyManager.getRawReputation(victim)
            plugin.infamyManager.setReputation(victim, currentRep - 1)
        }

        // Apply Invisibility Obfuscation
        if (victim.isInvisible || victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            val originalMessageComponent = event.deathMessage()
            if (originalMessageComponent != null) {
                val legacySerializer = LegacyComponentSerializer.legacySection()
                val legacyMsg = legacySerializer.serialize(originalMessageComponent)
                val obfuscatedMsg = legacyMsg.replace(victim.name, "§k${victim.name}§r")
                event.deathMessage(legacySerializer.deserialize(obfuscatedMsg))
            }
        }
    }
}