package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class BottleInteractListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onBottleDrink(event: PlayerItemConsumeEvent) {
        val player = event.player
        val item = event.item
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val currentRep = plugin.infamyManager.getRawReputation(player)

        fun markConsumed(consumer: org.bukkit.entity.Player) {
            if (pdc.has(plugin.itemManager.killIdKey, PersistentDataType.STRING)) {
                val killIdStr = pdc.get(plugin.itemManager.killIdKey, PersistentDataType.STRING)
                val record = plugin.infamyManager.killHistory.find { it.id.toString() == killIdStr }
                if (record != null) {
                    record.status = if (record.killer == consumer.uniqueId) "WITHDRAWN" else "STOLEN"
                }
            }
        }

        if (pdc.has(plugin.itemManager.infamyKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            val points = pdc.get(plugin.itemManager.infamyKey, PersistentDataType.INTEGER) ?: 1

            if (pdc.has(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)) {
                val ownerUUID = UUID.fromString(pdc.get(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)!!)
                val currentWithdrawn = plugin.infamyManager.withdrawnPoints[ownerUUID] ?: 0
                if (currentWithdrawn >= points) plugin.infamyManager.withdrawnPoints[ownerUUID] = currentWithdrawn - points
            }

            if (currentRep >= 20) return player.sendMessage(Component.text("You cannot exceed Level 20 without consuming a Pure Infamy Bottle!", NamedTextColor.RED))

            val targetRep = currentRep + points
            if (targetRep > 20) {
                val refundAmount = targetRep - 20
                plugin.infamyManager.setReputation(player, 20)
                player.sendMessage(Component.text("You hit the Level 20 cap! Refunded $refundAmount point(s).", NamedTextColor.YELLOW))

                val origName = pdc.get(plugin.itemManager.ownerNameKey, PersistentDataType.STRING)
                val origUUID = pdc.get(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)
                val origKillId = pdc.get(plugin.itemManager.killIdKey, PersistentDataType.STRING)

                val refundBottle = plugin.itemManager.createInfamyBottle(refundAmount, origName, origUUID, origKillId)
                player.inventory.addItem(refundBottle).values.forEach { player.world.dropItemNaturally(player.location, it) }
            } else {
                plugin.infamyManager.setReputation(player, targetRep)
                player.sendMessage(Component.text("You consumed an Infamy Bottle!", NamedTextColor.GREEN))
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
            return
        }

        if (pdc.has(plugin.itemManager.honorKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            plugin.infamyManager.setReputation(player, currentRep - 1)
            player.sendMessage(Component.text("You consumed an Honor Bottle!", NamedTextColor.AQUA))
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
            return
        }

        if (pdc.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            if (currentRep != 20) return player.sendMessage(Component.text("Only a Level 20 player can consume the Pure Infamy Bottle!", NamedTextColor.RED))
            if (plugin.infamyManager.currentBoss != null) return player.sendMessage(Component.text("The title of Most Infamous is already held!", NamedTextColor.RED))

            plugin.infamyManager.setReputation(player, 21)

            if (plugin.infamyManager.getSettings(player.uniqueId).globalSounds) {
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
        }
    }
}