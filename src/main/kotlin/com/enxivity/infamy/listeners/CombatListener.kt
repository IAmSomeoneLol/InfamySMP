// Event listener for combat abilities. Connected to InfamySMP.kt.
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import io.papermc.paper.event.player.PlayerShieldDisableEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class CombatListener(private val plugin: InfamySMP) : Listener {

    private val swordBlockers = mutableMapOf<UUID, Long>()
    private val shieldAbilityCooldowns = mutableMapOf<UUID, Long>()
    private val activeBrokenShields = mutableSetOf<UUID>()

    // Level 18 Trackers
    private val bleedAbilityCooldowns = mutableMapOf<UUID, Long>()
    private val activeBleedStance = mutableSetOf<UUID>()

    @EventHandler
    fun onSwordInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        val infamy = plugin.infamyManager.getInfamy(player)

        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val isSword = item.type.name.endsWith("_SWORD")

            // LEVEL 3: Sword Blocking
            if (infamy >= 3 && isSword) {
                swordBlockers[player.uniqueId] = System.currentTimeMillis()
            }

            // LEVEL 18: Bleeding Stance Activation
            if (infamy >= 18 && (item.type.name == "DIAMOND_SWORD" || item.type.name == "NETHERITE_SWORD")) {
                val now = System.currentTimeMillis()
                val lastUsed = bleedAbilityCooldowns[player.uniqueId] ?: 0

                // Check 1 minute cooldown (60000 ms)
                if (now - lastUsed > 60000) {
                    bleedAbilityCooldowns[player.uniqueId] = now
                    activeBleedStance.add(player.uniqueId)

                    player.sendMessage(Component.text("Bleeding Edge active! Your next strike will cause severe bleeding.", NamedTextColor.DARK_RED))

                    // Automatically cancel the stance if they miss/don't hit anyone within 10 seconds
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        activeBleedStance.remove(player.uniqueId)
                    }, 200L)
                }
            }
        }
    }

    @EventHandler
    fun onShieldBreak(event: PlayerShieldDisableEvent) {
        val player = event.player

        // LEVEL 6: Shield Recovery
        if (plugin.infamyManager.getInfamy(player) >= 6) {
            val now = System.currentTimeMillis()
            val lastUsed = shieldAbilityCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 25000) {
                event.isCancelled = true
                shieldAbilityCooldowns[player.uniqueId] = now
                activeBrokenShields.add(player.uniqueId)

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    activeBrokenShields.remove(player.uniqueId)
                }, 100L)
            }
        }
    }

    @EventHandler
    fun onPlayerTakeDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        if (victim !is Player) return
        val now = System.currentTimeMillis()

        // Defending Mechanics Check
        val victimInfamy = plugin.infamyManager.getInfamy(victim)
        if (victimInfamy >= 3) {
            val lastBlocked = swordBlockers[victim.uniqueId] ?: 0
            if (now - lastBlocked <= 1500 && victim.inventory.itemInMainHand.type.name.endsWith("_SWORD")) {
                event.damage *= 0.5
            }
        }

        if (activeBrokenShields.contains(victim.uniqueId) && victim.isBlocking) {
            val currentHealth = victim.health
            victim.health = (currentHealth - 1.0).coerceAtLeast(0.0)
        }

        // Attacking Mechanics Check
        if (event.damager is Player) {
            val attacker = event.damager as Player
            val attackerInfamy = plugin.infamyManager.getInfamy(attacker)

            // LEVEL 9: Axe Pierce
            if (attackerInfamy >= 9 && victim.isBlocking && attacker.inventory.itemInMainHand.type.name.endsWith("_AXE")) {
                val currentHealth = victim.health
                victim.health = (currentHealth - 4.0).coerceAtLeast(0.0)
            }

            // LEVEL 18: Apply Bleed effect if stance is active
            if (activeBleedStance.contains(attacker.uniqueId)) {
                activeBleedStance.remove(attacker.uniqueId) // Consume the stance so it only works on one hit

                var ticks = 0
                // Create a repeating task to simulate the bleeding over time
                val bleedTask = object : BukkitRunnable() {
                    override fun run() {
                        if (ticks >= 5 || victim.isDead) {
                            // Apply 4 seconds (80 ticks) of Nausea at the end
                            victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 0))
                            cancel()
                            return
                        }

                        // Deal exactly half a heart of true damage per second
                        val hp = victim.health
                        victim.health = (hp - 1.0).coerceAtLeast(0.0)
                        victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.0, 0.0), 5)

                        ticks++
                    }
                }
                // Run the task every 20 ticks (1 second)
                bleedTask.runTaskTimer(plugin, 20L, 20L)
            }
        }
    }
}