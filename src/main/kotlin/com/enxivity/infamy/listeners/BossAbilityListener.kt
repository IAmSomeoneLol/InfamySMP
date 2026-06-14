// Event listener for Boss Abilities. Connected to InfamySMP.kt.
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class BossAbilityListener(private val plugin: InfamySMP) : Listener {

    private val maceCooldowns = mutableMapOf<UUID, Long>()
    val sacrificeCooldowns = mutableMapOf<UUID, Long>()
    val activeSacrifices = mutableSetOf<UUID>()

    @EventHandler
    fun onBossInteract(event: PlayerInteractEvent) {
        val player = event.player
        val rep = plugin.infamyManager.getRawReputation(player)
        val action = event.action

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        // LEVEL 20: Mace Golem Slam
        if (plugin.hasInfamyAbility("mace_slam", rep) && event.item?.type == Material.MACE) {
            val now = System.currentTimeMillis()
            val lastUsed = maceCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 10000) {
                maceCooldowns[player.uniqueId] = now
                val startHeight = player.location.y

                player.velocity = player.location.direction.multiply(1.5).setY(0.8)
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f)

                object : BukkitRunnable() {
                    override fun run() {
                        if (player.isDead || !player.isOnline) {
                            cancel()
                            return
                        }
                        if (player.velocity.y == 0.0 || player.isOnGround) {
                            createMaceShockwave(player, startHeight)
                            cancel()
                        }
                    }
                }.runTaskTimer(plugin, 10L, 1L)
            }
        }

        // LEVEL 21: Helmet Sacrifice
        if (plugin.hasInfamyAbility("boss_sacrifice", rep) && player.isSneaking && event.item == null) {
            val now = System.currentTimeMillis()
            val lastUsed = sacrificeCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 900000) {
                sacrificeCooldowns[player.uniqueId] = now
                activeSacrifices.add(player.uniqueId)

                player.sendMessage(Component.text("You sacrificed your armor for immense power! (Strength 3 for 5m)", NamedTextColor.DARK_RED))
                player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1f, 1f)

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    activeSacrifices.remove(player.uniqueId)
                    if (player.isOnline) {
                        player.sendMessage(Component.text("Your bloodlust fades. You may wear a helmet again.", NamedTextColor.GRAY))
                    }
                }, 6000L)
            } else {
                val remaining = (900000 - (now - lastUsed)) / 1000 / 60
                player.sendMessage(Component.text("Sacrifice is on cooldown! ($remaining minutes left)", NamedTextColor.RED))
            }
        }
    }

    private fun createMaceShockwave(player: Player, startY: Double) {
        val fallDistance = startY - player.location.y
        if (fallDistance <= 0) return

        player.world.spawnParticle(Particle.EXPLOSION, player.location, 3)
        player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)

        for (entity in player.getNearbyEntities(5.0, 3.0, 5.0)) {
            if (entity is LivingEntity && entity.uniqueId != player.uniqueId) {
                val damage = (fallDistance * 1.5).coerceAtMost(12.0)
                entity.damage(damage, player)
                entity.velocity = entity.velocity.setY(0.8)
            }
        }
    }
}