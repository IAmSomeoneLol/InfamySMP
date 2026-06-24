package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import com.destroystokyo.paper.entity.villager.ReputationType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.potion.PotionEffect

class PerkListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onPotionApply(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val effect = event.newEffect ?: return
        if (event.cause == EntityPotionEffectEvent.Cause.PLUGIN) return

        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)
        var modifier = 1.0

        if (rep >= 12) modifier = 2.0
        if (honor >= 9) modifier = 0.5

        if (modifier != 1.0) {
            val newDuration = (effect.duration * modifier).toInt()
            val newEffect = PotionEffect(effect.type, newDuration, effect.amplifier, effect.isAmbient, effect.hasParticles(), effect.hasIcon())
            event.isCancelled = true
            plugin.server.scheduler.runTask(plugin, Runnable { player.addPotionEffect(newEffect) })
        }
    }

    @EventHandler
    fun onVillagerInteract(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked

        if (entity is Villager) {
            val rep = plugin.infamyManager.getRawReputation(player)

            // Reaches directly into Paper's native API to read the villager's memory of this specific player
            val reputation = entity.getReputation(player.uniqueId)

            if (rep >= 12) {
                // Injects native MAJOR_NEGATIVE gossip (simulates the player hitting/killing them)
                // The higher this number, the worse the native red price hikes become.
                val penalty = when {
                    rep >= 20 -> 150 // Extreme price hikes
                    rep >= 15 -> 100 // Severe price hikes
                    else -> 50       // Wary price hikes
                }

                reputation.setReputation(ReputationType.MAJOR_NEGATIVE, penalty)
                entity.setReputation(player.uniqueId, reputation)

            } else {
                // If the player drops below 12 Infamy, we forgive the bad gossip so prices return to normal
                if (reputation.getReputation(ReputationType.MAJOR_NEGATIVE) > 0) {
                    reputation.setReputation(ReputationType.MAJOR_NEGATIVE, 0)
                    entity.setReputation(player.uniqueId, reputation)
                }
            }
        }
    }
}