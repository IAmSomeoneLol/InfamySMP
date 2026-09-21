package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class BottleInteractListener(private val plugin: InfamySMP) : Listener {

    private fun spawnConsumptionBeacon(location: org.bukkit.Location, bottleType: String) {
        if (!plugin.config.getBoolean("settings.show-consumption-beacon", true)) return

        val particleStr = plugin.config.getString("settings.bottle-particles.$bottleType.particle", "END_ROD")?.uppercase() ?: "END_ROD"
        var primaryParticle = org.bukkit.Particle.END_ROD
        try {
            primaryParticle = org.bukkit.Particle.valueOf(particleStr)
        } catch (e: Exception) { }

        val dustColor = if (bottleType == "honor") org.bukkit.Color.AQUA else org.bukkit.Color.RED
        val dustOptions = org.bukkit.Particle.DustOptions(dustColor, 1.2f)

        val loc = location.clone().add(0.0, 0.5, 0.0)
        val world = loc.world

        world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 2.0f)
        world.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)

        fun spawnPrimary(l: org.bukkit.Location, count: Int, offset: Double, speed: Double) {
            if (primaryParticle == org.bukkit.Particle.DUST) {
                world.spawnParticle(primaryParticle, l, count, offset, offset, offset, 0.0, dustOptions)
            } else {
                world.spawnParticle(primaryParticle, l, count, offset, offset, offset, speed)
            }
        }

        for (i in 0..30) {
            val angle = i * (2 * Math.PI / 30)
            val x = Math.cos(angle) * 1.5
            val z = Math.sin(angle) * 1.5
            val burstLoc = loc.clone().add(x, 0.0, z)

            spawnPrimary(burstLoc, 2, 0.1, 0.02)
            world.spawnParticle(org.bukkit.Particle.FIREWORK, burstLoc, 2, 0.1, 0.1, 0.1, 0.02)
        }

        object : org.bukkit.scheduler.BukkitRunnable() {
            var tick = 0
            override fun run() {
                if (tick > 60) {
                    cancel()
                    return
                }

                val yOffset = tick * 0.4
                val radius = 0.8 + (tick * 0.01)
                val center = loc.clone().add(0.0, yOffset, 0.0)

                spawnPrimary(center, 5, 0.1, 0.0)

                if (primaryParticle != org.bukkit.Particle.GLOW) {
                    world.spawnParticle(org.bukkit.Particle.GLOW, center, 2, 0.2, 0.3, 0.2, 0.0)
                }

                for (i in 0..3) {
                    val angle = (tick * 0.3) + (i * (Math.PI / 2))
                    val x = Math.cos(angle) * radius
                    val z = Math.sin(angle) * radius
                    val spiralLoc = loc.clone().add(x, yOffset, z)

                    world.spawnParticle(org.bukkit.Particle.FIREWORK, spiralLoc, 2, 0.05, 0.05, 0.05, 0.0)
                    spawnPrimary(spiralLoc, 1, 0.0, 0.0)
                }
                tick++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

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

        // =======================
        // CONSUMING INFAMY BOTTLE
        // =======================
        if (pdc.has(plugin.itemManager.infamyKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            val points = pdc.get(plugin.itemManager.infamyKey, PersistentDataType.INTEGER) ?: 1

            if (pdc.has(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)) {
                val uuidStr = pdc.get(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)
                if (uuidStr != null) {
                    try {
                        val ownerUUID = UUID.fromString(uuidStr)
                        val currentWithdrawn = plugin.infamyManager.withdrawnPoints[ownerUUID] ?: 0
                        if (currentWithdrawn >= points) plugin.infamyManager.withdrawnPoints[ownerUUID] = currentWithdrawn - points
                    } catch (e: IllegalArgumentException) { }
                }
            }

            val isLocked = plugin.infamyManager.currentBoss != null && !plugin.infamyManager.forceUnlock21
            val maxAllowed = if (isLocked) 20 else 21

            if (currentRep >= maxAllowed) {
                player.sendMessage(plugin.messagesManager.getComponent("bottles.max-level-reached"))
                return
            }

            val targetRep = currentRep + points

            if (currentRep < 0 && targetRep > currentRep) {
                val refundAmount = Math.min(points, -currentRep)
                val refundBottle = plugin.itemManager.createHonorBottle(refundAmount)
                val leftovers = player.inventory.addItem(refundBottle)
                if (leftovers.isNotEmpty()) {
                    leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
                    player.sendMessage(plugin.messagesManager.getComponent("bottles.inventory-full-refund"))
                }
                player.sendMessage(plugin.messagesManager.getComponent("bottles.infamy-neutralized", "amount" to refundAmount))
            }

            if (targetRep > maxAllowed) {
                val refundAmount = targetRep - maxAllowed
                plugin.infamyManager.setReputation(player, maxAllowed)
                player.sendMessage(plugin.messagesManager.getComponent("bottles.level-cap-refund", "amount" to refundAmount))

                val origName = pdc.get(plugin.itemManager.ownerNameKey, PersistentDataType.STRING)
                val origUUID = pdc.get(plugin.itemManager.ownerUuidKey, PersistentDataType.STRING)
                val origKillId = pdc.get(plugin.itemManager.killIdKey, PersistentDataType.STRING)

                val refundBottle = plugin.itemManager.createInfamyBottle(refundAmount, origName, origUUID, origKillId)
                val leftovers = player.inventory.addItem(refundBottle)
                if (leftovers.isNotEmpty()) {
                    leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
                    player.sendMessage(plugin.messagesManager.getComponent("bottles.inventory-full-refund"))
                }
            } else {
                plugin.infamyManager.setReputation(player, targetRep)
                player.sendMessage(plugin.messagesManager.getComponent("bottles.infamy-consumed"))
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
            spawnConsumptionBeacon(player.location, "infamy")
            return
        }

        // =======================
        // CONSUMING HONOR BOTTLE
        // =======================
        if (pdc.has(plugin.itemManager.honorKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true

            if (currentRep >= 21) {
                player.sendMessage(plugin.messagesManager.getComponent("bottles.honor-blocked-by-boss"))
                return
            }

            val points = pdc.get(plugin.itemManager.honorKey, PersistentDataType.INTEGER) ?: 1

            if (currentRep <= -21) {
                player.sendMessage(plugin.messagesManager.getComponent("bottles.max-level-reached"))
                return
            }

            // Honor neutralizes active Infamy points
            if (currentRep > 0) {
                val neutralized = Math.min(points, currentRep)
                val refundBottle = plugin.itemManager.createInfamyBottle(neutralized, "Neutralized", player.uniqueId.toString())
                val leftovers = player.inventory.addItem(refundBottle)
                if (leftovers.isNotEmpty()) {
                    leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
                    player.sendMessage(plugin.messagesManager.getComponent("bottles.inventory-full-refund"))
                }
                player.sendMessage(plugin.messagesManager.getComponent("bottles.honor-neutralized", "amount" to neutralized))
            }

            val targetRep = currentRep - points
            if (targetRep < -21) {
                val applied = (-21 - currentRep).let { if (it < 0) -it else 0 }
                val refundAmount = points - applied
                plugin.infamyManager.setReputation(player, -21)
                player.sendMessage(plugin.messagesManager.getComponent("bottles.level-cap-refund", "amount" to refundAmount))

                val refundBottle = plugin.itemManager.createHonorBottle(refundAmount)
                val leftovers = player.inventory.addItem(refundBottle)
                if (leftovers.isNotEmpty()) {
                    leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
                    player.sendMessage(plugin.messagesManager.getComponent("bottles.inventory-full-refund"))
                }
            } else {
                plugin.infamyManager.setReputation(player, targetRep)
                // Reads your custom message ending with '.' from messagesconfig.yml
                player.sendMessage(plugin.messagesManager.getComponent("bottles.honor-consumed"))
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
            spawnConsumptionBeacon(player.location, "honor")
            return
        }

        // =======================
        // CONSUMING PURE INFAMY BOTTLE
        // =======================
        if (pdc.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            if (currentRep != 20) {
                player.sendMessage(plugin.messagesManager.getComponent("bottles.pure-not-lvl20"))
                return
            }

            val isLocked = plugin.infamyManager.currentBoss != null && !plugin.infamyManager.forceUnlock21
            if (isLocked) {
                player.sendMessage(plugin.messagesManager.getComponent("bottles.pure-locked"))
                return
            }

            plugin.infamyManager.setReputation(player, 21)

            if (plugin.infamyManager.getSettings(player.uniqueId).globalSounds) {
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })
            spawnConsumptionBeacon(player.location, "pure")
        }
    }
}