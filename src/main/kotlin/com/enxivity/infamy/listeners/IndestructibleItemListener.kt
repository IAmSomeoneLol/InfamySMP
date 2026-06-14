package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityCombustEvent

class IndestructibleItemListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onItemDamage(event: EntityDamageEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemCombust(event: EntityCombustEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            event.isCancelled = true
        }
    }
}