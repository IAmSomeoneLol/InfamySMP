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

    private fun spawnConsumptionBeacon(location: org.bukkit.Location, bottleType: String) {
        if (!plugin.config.getBoolean("settings.show-consumption-beacon", true)) return

        // Fetch the exact particle type from the config to match the dropped item
        val particleStr = plugin.config.getString("settings.bottle-particles.$bottleType.particle", "END_ROD")?.uppercase() ?: "END_ROD"
        var primaryParticle = org.bukkit.Particle.END_ROD
        try {
            primaryParticle = org.bukkit.Particle.valueOf(particleStr)
        } catch (e: Exception) {
            // Failsafe if the config has a typo
        }

        // Setup Redstone Dust color based on bottle type if DUST is used
        val dustColor = if (bottleType == "honor") org.bukkit.Color.AQUA else org.bukkit.Color.RED
        val dustOptions = org.bukkit.Particle.DustOptions(dustColor, 1.2f)

        val loc = location.clone().add(0.0, 0.5, 0.0)
        val world = loc.world

        world.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 2.0f)
        world.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)

        // Helper function to safely spawn the particle (handling the complex Redstone DUST math)
        fun spawnPrimary(l: org.bukkit.Location, count: Int, offset: Double, speed: Double) {
            if (primaryParticle == org.bukkit.Particle.DUST) {
                world.spawnParticle(primaryParticle, l, count, offset, offset, offset, 0.0, dustOptions)
            } else {
                world.spawnParticle(primaryParticle, l, count, offset, offset, offset, speed)
            }
        }

        // Ground burst effect
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

                // Central thick beam mapped to custom particle
                spawnPrimary(center, 5, 0.1, 0.0)

                // Inject a little generic glow into the core for extra magic (unless the primary IS glow)
                if (primaryParticle != org.bukkit.Particle.GLOW) {
                    world.spawnParticle(org.bukkit.Particle.GLOW, center, 2, 0.2, 0.3, 0.2, 0.0)
                }

                // Outer rotating spirals mapped to custom particle
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
                    } catch (e: IllegalArgumentException) {
                        // Safely skip if the UUID string was bugged (like an admin's name)
                    }
                }
            }

            val isLocked = plugin.infamyManager.currentBoss != null && !plugin.infamyManager.forceUnlock21
            val maxAllowed = if (isLocked) 20 else 21

            if (currentRep >= maxAllowed) {
                player.sendMessage(Component.text("You have reached the maximum allowed level!", NamedTextColor.RED))
                return
            }

            val targetRep = currentRep + points
            if (targetRep > maxAllowed) {
                val refundAmount = targetRep - maxAllowed
                plugin.infamyManager.setReputation(player, maxAllowed)
                player.sendMessage(Component.text("You hit the level cap! Refunded $refundAmount point(s).", NamedTextColor.YELLOW))

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

            // SPAWN BEACON (Mapped to "infamy")
            spawnConsumptionBeacon(player.location, "infamy")
            return
        }

        if (pdc.has(plugin.itemManager.honorKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true

            if (currentRep >= 21) {
                player.sendMessage(Component.text("You cannot consume Honor Bottles while holding the Most Infamous title! Withdraw your pure bottle or die to lose it first.", NamedTextColor.RED))
                return
            }

            plugin.infamyManager.setReputation(player, currentRep - 1)
            player.sendMessage(Component.text("You consumed an Honor Bottle!", NamedTextColor.AQUA))
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })

            // SPAWN BEACON (Mapped to "honor")
            spawnConsumptionBeacon(player.location, "honor")
            return
        }

        if (pdc.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)) {
            event.isCancelled = true
            if (currentRep != 20) return player.sendMessage(Component.text("Only a Level 20 player can consume the Pure Infamy Bottle!", NamedTextColor.RED))

            val isLocked = plugin.infamyManager.currentBoss != null && !plugin.infamyManager.forceUnlock21
            if (isLocked) return player.sendMessage(Component.text("The title of Most Infamous is currently locked!", NamedTextColor.RED))

            plugin.infamyManager.setReputation(player, 21)

            if (plugin.infamyManager.getSettings(player.uniqueId).globalSounds) {
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
            }

            markConsumed(player)
            plugin.server.scheduler.runTask(plugin, Runnable { player.inventory.getItem(event.hand)?.subtract(1) })

            // SPAWN BEACON (Mapped to "pure")
            spawnConsumptionBeacon(player.location, "pure")
        }
    }
}