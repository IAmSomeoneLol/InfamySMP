@file:Suppress("DEPRECATION")
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
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

    val swordBlockCooldowns = mutableMapOf<UUID, Long>()
    val swordBlockActiveUntil = mutableMapOf<UUID, Long>()

    val shieldAbilityCooldowns = mutableMapOf<UUID, Long>()
    val activeBrokenShields = mutableSetOf<UUID>()

    val bleedCooldowns = mutableMapOf<UUID, Long>()
    val activeBleedCharge = mutableMapOf<UUID, Long>()

    val maceCooldowns = mutableMapOf<UUID, Long>()

    val sacrificeCooldowns = mutableMapOf<UUID, Long>()
    val activeSacrifices = mutableSetOf<UUID>()

    private fun msg(player: Player, text: String, color: NamedTextColor) {
        if (plugin.infamyManager.getSettings(player.uniqueId).abilityMessages) {
            player.sendMessage(Component.text(text, color))
        }
    }

    private fun msgCD(player: Player, text: String, color: NamedTextColor) {
        if (plugin.infamyManager.getSettings(player.uniqueId).cooldownMessages) {
            player.sendMessage(Component.text(text, color))
        }
    }

    fun restoreHelmet(player: Player) {
        val armorAttr = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR)
        val toughAttr = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS)
        val hellcrushArmorKey = org.bukkit.NamespacedKey(plugin, "hellcrush_armor_penalty")
        val hellcrushToughKey = org.bukkit.NamespacedKey(plugin, "hellcrush_toughness_penalty")

        armorAttr?.modifiers?.find { it.key == hellcrushArmorKey }?.let { armorAttr.removeModifier(it) }
        toughAttr?.modifiers?.find { it.key == hellcrushToughKey }?.let { toughAttr.removeModifier(it) }
    }

    @EventHandler
    fun onCombatInteract(event: PlayerInteractEvent) {
        // EXPLOIT FIX: Prevent double-firing from the off-hand
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return

        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val rep = plugin.infamyManager.getRawReputation(player)
        val mainItem = player.inventory.itemInMainHand
        val offItem = player.inventory.itemInOffHand

        // LEVEL 6: Shield Recovery (Manual Activation when Shield is disabled on vanilla cooldown)
        if (player.isSneaking && rep >= 6 && (mainItem.type == Material.SHIELD || offItem.type == Material.SHIELD)) {
            if (player.hasCooldown(Material.SHIELD)) {
                val now = System.currentTimeMillis()
                val lastUsed = shieldAbilityCooldowns[player.uniqueId] ?: 0

                if (now - lastUsed > 25000) {
                    shieldAbilityCooldowns[player.uniqueId] = now
                    player.setCooldown(Material.SHIELD, 0) // Strips off vanilla disable timer completely

                    // Deduct half a heart safely (1 HP)
                    player.health = (player.health - 1.0).coerceAtLeast(0.0)

                    msg(player, "Shield recovered instantly! (-0.5 Hearts)", NamedTextColor.GREEN)
                    player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f)
                } else {
                    val remaining = (25000 - (now - lastUsed)) / 1000
                    msgCD(player, "Shield Recovery on cooldown! (${remaining}s)", NamedTextColor.RED)
                }
            }
        }

        // LEVEL 3: Sword Block (1m CD, 5s Execution Protection)
        if (!mainItem.isEmpty && mainItem.type.name.endsWith("_SWORD") && rep >= 3) {
            val now = System.currentTimeMillis()
            val lastUsed = swordBlockCooldowns[player.uniqueId] ?: 0
            if (now - lastUsed > 60000) {
                swordBlockCooldowns[player.uniqueId] = now
                swordBlockActiveUntil[player.uniqueId] = now + 5000
                msg(player, "Sword Block armed! 50% damage protection for 5s.", NamedTextColor.GREEN)
                player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f)
            } else {
                msgCD(player, "Sword Block on cooldown! (${(60000 - (now - lastUsed))/1000}s)", NamedTextColor.RED)
            }
        }

        // LEVEL 18: Bleeding Edge Prime (Diamond and Netherite Swords Supported)
        if (!mainItem.isEmpty && (mainItem.type == Material.DIAMOND_SWORD || mainItem.type == Material.NETHERITE_SWORD) && rep >= 18) {
            val now = System.currentTimeMillis()
            val lastUsed = bleedCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 60000) {
                bleedCooldowns[player.uniqueId] = now
                activeBleedCharge[player.uniqueId] = now
                msg(player, "Bleeding Edge primed! Your next strike will inflict bleeding.", NamedTextColor.DARK_RED)
                player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.5f)

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (activeBleedCharge.containsKey(player.uniqueId)) {
                        activeBleedCharge.remove(player.uniqueId)
                        msg(player, "Bleeding Edge charge dissipated.", NamedTextColor.GRAY)
                    }
                }, 80L)
            } else {
                msgCD(player, "Bleeding Edge on cooldown! (${(60000 - (now - lastUsed))/1000}s)", NamedTextColor.RED)
            }
        }

        // LEVEL 20: Mace Slam
        if (!mainItem.isEmpty && mainItem.type == Material.MACE && rep >= 20) {
            val now = System.currentTimeMillis()
            val lastUsed = maceCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 10000) {
                maceCooldowns[player.uniqueId] = now
                val startHeight = player.location.y
                player.velocity = player.location.direction.multiply(2.2).setY(1.2)
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f)

                object : BukkitRunnable() {
                    override fun run() {
                        if (player.isDead || !player.isOnline) { cancel(); return }
                        if (player.velocity.y == 0.0 || player.isOnGround) {
                            createMaceShockwave(player, startHeight)
                            cancel()
                        }
                    }
                }.runTaskTimer(plugin, 10L, 1L)
            } else {
                msgCD(player, "Mace Slam on cooldown! (${(10000 - (now - lastUsed))/1000}s)", NamedTextColor.RED)
            }
        }

        // LEVEL 21: Hellcrush
        if (rep >= 21 && player.isSneaking && mainItem.isEmpty) {
            val now = System.currentTimeMillis()
            val lastUsed = sacrificeCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 900000) {
                sacrificeCooldowns[player.uniqueId] = now
                activeSacrifices.add(player.uniqueId)

                msg(player, "Hellcrush Activated! Helmet stats neutralized for 5 minutes of Strength III.", NamedTextColor.DARK_RED)
                player.world.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.7f)

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (activeSacrifices.contains(player.uniqueId)) {
                        activeSacrifices.remove(player.uniqueId)
                        if (player.isOnline) {
                            restoreHelmet(player)
                            msg(player, "Your Hellcrush fury has faded. Helmet stats restored.", NamedTextColor.GRAY)
                        }
                    }
                }, 6000L)
            } else {
                val remaining = (900000 - (now - lastUsed)) / 1000 / 60
                msgCD(player, "Hellcrush is on cooldown! ($remaining mins left)", NamedTextColor.RED)
            }
        }
    }

    private fun createMaceShockwave(player: Player, startY: Double) {
        val fallDistance = startY - player.location.y
        if (fallDistance <= 0) return

        player.world.spawnParticle(Particle.EXPLOSION, player.location, 5)
        player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f)

        for (entity in player.getNearbyEntities(6.0, 4.0, 6.0)) {
            if (entity is LivingEntity && entity.uniqueId != player.uniqueId) {
                val distance = entity.location.distance(player.location)
                val heightMultiplier = fallDistance * 1.5
                val distanceMultiplier = (6.0 - distance).coerceAtLeast(1.0)

                val damage = (heightMultiplier + distanceMultiplier).coerceAtMost(12.0)
                entity.damage(damage, player)
                entity.velocity = entity.velocity.setY(0.9)
            }
        }
    }

    @EventHandler
    fun onPlayerTakeDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        if (victim !is Player) return
        val now = System.currentTimeMillis()
        val victimRep = plugin.infamyManager.getRawReputation(victim)

        if (victimRep >= 3) {
            val expires = swordBlockActiveUntil[victim.uniqueId] ?: 0
            if (now <= expires && victim.inventory.itemInMainHand.type.name.endsWith("_SWORD")) {
                event.damage *= 0.5
                victim.world.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
            }
        }

        if (activeSacrifices.contains(victim.uniqueId)) {
            val helmet = victim.inventory.helmet
            if (helmet != null) {
                val protLevel = helmet.getEnchantmentLevel(Enchantment.PROTECTION)
                if (protLevel > 0) {
                    val reduction = (protLevel * 0.04).coerceAtMost(0.80)
                    event.damage *= (1.0 / (1.0 - reduction))
                }
            }
        }

        if (event.damager is Player) {
            val attacker = event.damager as Player
            val attackerRep = plugin.infamyManager.getRawReputation(attacker)

            if (attackerRep >= 9 && victim.isBlocking && attacker.inventory.itemInMainHand.type.name.endsWith("_AXE")) {
                victim.health = (victim.health - 4.0).coerceAtLeast(0.0)
            }

            if (activeBleedCharge.containsKey(attacker.uniqueId)) {
                activeBleedCharge.remove(attacker.uniqueId)
                msg(attacker, "Bleeding Edge strike landed!", NamedTextColor.DARK_RED)

                var ticks = 0
                val bleedTask = object : BukkitRunnable() {
                    override fun run() {
                        if (victim.isDead || !victim.isOnline) { cancel(); return }

                        if (ticks < 5) {
                            // Inflict exactly 1 HP (half heart) per second, ignoring armor & enchants
                            val targetHealth = (victim.health - 1.0).coerceAtLeast(0.0)
                            if (targetHealth <= 0.0) {
                                victim.damage(100.0, attacker) // Forces flawless death credit to attacker
                            } else {
                                victim.health = targetHealth
                                victim.playEffect(org.bukkit.EntityEffect.HURT)
                            }
                            victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.0, 0.0), 5)
                            ticks++
                        } else {
                            // Bleeding 5s ends -> Nausea starts immediately for 4 seconds (80 ticks)
                            victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 0))
                            cancel()
                        }
                    }
                }
                bleedTask.runTaskTimer(plugin, 20L, 20L)
            }
        }
    }
}