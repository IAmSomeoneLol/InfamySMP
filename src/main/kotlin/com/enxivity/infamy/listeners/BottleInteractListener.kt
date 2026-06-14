package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.persistence.PersistentDataType

class BottleInteractListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onBottleDrink(event: PlayerItemConsumeEvent) {
        val player = event.player
        val item = event.item
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val currentRep = plugin.infamyManager.getRawReputation(player)

        // 1. Regular Infamy Bottle
        if (pdc.has(plugin.itemManager.infamyKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            val points = pdc.get(plugin.itemManager.infamyKey, PersistentDataType.INTEGER) ?: 1

            if (currentRep >= 20) {
                player.sendMessage(Component.text("Regular Infamy Bottles cannot grant Level 21. You must defeat the current Boss and consume the Core of Infamy!", NamedTextColor.RED))
                return
            }

            item.amount -= 1
            val targetRep = (currentRep + points).coerceAtMost(20)
            plugin.infamyManager.setReputation(player, targetRep)
            player.sendMessage(Component.text("You consumed an Infamy Bottle!", NamedTextColor.GREEN))
            return
        }

        // 2. Honor Bottle
        if (pdc.has(plugin.itemManager.honorKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            item.amount -= 1
            plugin.infamyManager.setReputation(player, currentRep - 1)
            player.sendMessage(Component.text("You consumed an Honor Bottle!", NamedTextColor.AQUA))
            return
        }

        // 3. Rare Boss Bottle (Core of Infamy)
        if (pdc.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true

            if (currentRep != 20) {
                player.sendMessage(Component.text("Only a Level 20 player can consume the Core of Infamy to break the ceiling!", NamedTextColor.RED))
                return
            }

            if (plugin.infamyManager.currentBoss != null) {
                player.sendMessage(Component.text("The title of Most Infamous is already held! You cannot break the ceiling yet.", NamedTextColor.RED))
                return
            }

            item.amount -= 1
            plugin.infamyManager.setReputation(player, 21)
            return
        }
    }
}