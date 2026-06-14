// Event listener for generating Honor Bottles. Connected to InfamySMP.kt.
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.raid.RaidFinishEvent

class HonorDeedListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onWardenKill(event: EntityDeathEvent) {
        // When a Warden dies, check if a player killed it
        if (event.entityType == EntityType.WARDEN) {
            val killer = event.entity.killer
            if (killer != null) {
                // Generate and drop 1 Honor Bottle at the Warden's body
                val bottle = plugin.itemManager.createHonorBottle()
                event.entity.world.dropItemNaturally(event.entity.location, bottle)
            }
        }
    }

    @EventHandler
    fun onRaidWin(event: RaidFinishEvent) {
        // When a village raid is successfully defended
        val winners = event.winners
        if (winners.isNotEmpty()) {
            // Generate and drop 1 Honor Bottle at the center of the village
            val bottle = plugin.itemManager.createHonorBottle()
            event.raid.location.world.dropItemNaturally(event.raid.location, bottle)
        }
    }
}