package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.persistence.PersistentDataType

class IndestructibleItemListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onItemDamage(event: EntityDamageEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            // Invulnerability does not protect against the Void.
            if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                checkBossBottleDestruction(item)
                return // Let the void destroy it naturally
            }
            event.isCancelled = true // Block explosions, cactus, lava, etc.
        }
    }

    @EventHandler
    fun onItemCombust(event: EntityCombustEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            event.isCancelled = true
        }
    }

    // Triggers if the item sits on the ground for 5 minutes
    @EventHandler
    fun onItemDespawn(event: ItemDespawnEvent) {
        checkBossBottleDestruction(event.entity)
    }

    // Helper function to broadcast the softlock warning
    private fun checkBossBottleDestruction(item: Item) {
        val meta = item.itemStack.itemMeta ?: return
        if (meta.persistentDataContainer.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)) {
            Bukkit.broadcast(Component.text("The Core of Infamy has been lost to the abyss! The server is softlocked until an admin intervenes.", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        }
    }
}