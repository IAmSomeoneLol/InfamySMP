@file:Suppress("DEPRECATION")
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import io.papermc.paper.event.player.PlayerShieldDisableEvent
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
import org.bukkit.inventory.ItemStack
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
        if (event.hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return

        val player = event.player
        val rep = plugin.infamyManager.getRawReputation(player)
        val action = event.action

        val mainItem = player.inventory.itemInMainHand
        val offItem = player.inventory.itemInOffHand

        // ==========================================
        // LEVEL 21: HELLCRUSH
        // ==========================================
        if (mainItem.type.isAir && player.isSneaking && rep >= 21) {
            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                val now = System.currentTimeMillis()
                val lastUsed = sacrificeCooldowns[player.uniqueId] ?: 0

                if (now - lastUsed > 900000) {
                    sacrificeCooldowns[player.uniqueId] = now
                    activeSacrifices.add(player.uniqueId)

                    msg(player, "Hellcrush Activated! Helmet stats neutralized for 5 minutes of Strength III.", NamedTextColor.DARK_RED)
                    player.world.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.2f, 0.7f)

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

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        // ==========================================
        // LEVEL 6: SHIELD RECOVERY
        // ==========================================
        if ((mainItem.type == Material.SHIELD || offItem.type == Material.SHIELD) && player.isSneaking && rep >= 6) {
            if (player.hasCooldown(Material.SHIELD)) {
                val now = System.currentTimeMillis()
                val lastUsed = shieldAbilityCooldowns[player.uniqueId] ?: 0

                if (now - lastUsed > 25000) {
                    shieldAbilityCooldowns[player.uniqueId] = now
                    player.setCooldown(Material.SHIELD, 0)

                    player.health = (player.health - 1.0).coerceAtLeast(0.0)

                    if (plugin.config.getBoolean("settings.show-ability-particles", true)) {
                        player.world.spawnParticle(Particle.ITEM, player.location.add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.05, ItemStack(Material.SHIELD))
                    }

                    msg(player, "Shield recovered instantly! (-0.5 Hearts)", NamedTextColor.GREEN)
                    player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f)
                } else {
                    val remaining = (25000 - (now - lastUsed)) / 1000
                    msgCD(player, "Shield Recovery on cooldown! (${remaining}s)", NamedTextColor.RED)
                }
            }
        }

        val item = event.item ?: return

        // ==========================================
        // LEVEL 3: SWORD BLOCK
        // ==========================================
        if (item.type.name.endsWith("_SWORD") && rep >= 3) {
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

        // ==========================================
        // LEVEL 18: BLEEDING EDGE PRIME
        // ==========================================
        if ((item.type == Material.DIAMOND_SWORD || item.type == Material.NETHERITE_SWORD) && rep >= 18) {
            val now = System.currentTimeMillis()
            val lastUsed = bleedCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 60000) {
                bleedCooldowns[player.uniqueId] = now
                activeBleedCharge[player.uniqueId] = now
                msg(player, "Bleeding Edge primed! Your next strike will inflict bleeding.", NamedTextColor.DARK_RED)
                player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.5f)

                if (plugin.config.getBoolean("settings.show-ability-particles", true)) {
                    var trailTicks = 0
                    object : BukkitRunnable() {
                        override fun run() {
                            if (!activeBleedCharge.containsKey(player.uniqueId) || trailTicks >= 80 || !player.isOnline || player.isDead) {
                                cancel()
                                return
                            }
                            val redDust = org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f)
                            player.world.spawnParticle(Particle.DUST, player.location.add(0.0, 1.0, 0.0), 3, 0.3, 0.3, 0.3, 0.0, redDust)
                            trailTicks += 2
                        }
                    }.runTaskTimer(plugin, 0L, 2L)
                }

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

        // ==========================================
        // LEVEL 20: MACE SLAM
        // ==========================================
        if (item.type == Material.MACE && rep >= 20) {
            val now = System.currentTimeMillis()
            val lastUsed = maceCooldowns[player.uniqueId] ?: 0

            if (now - lastUsed > 60000) {
                maceCooldowns[player.uniqueId] = now
                val startHeight = player.location.y
                player.velocity = player.location.direction.multiply(2.2).setY(1.2)
                player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f)

                val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)

                object : BukkitRunnable() {
                    override fun run() {
                        if (player.isDead || !player.isOnline) { cancel(); return }

                        if (showParticles) {
                            player.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.location, 2, 0.1, 0.1, 0.1, 0.01)
                        }

                        val blockBelow = player.location.clone().subtract(0.0, 0.1, 0.0).block
                        if (player.velocity.y <= 0.0 && blockBelow.type.isSolid) {
                            createMaceShockwave(player, startHeight, showParticles)
                            cancel()
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L)
            } else {
                msgCD(player, "Mace Slam on cooldown! (${(60000 - (now - lastUsed))/1000}s)", NamedTextColor.RED)
            }
        }
    }

    private fun createMaceShockwave(player: Player, startY: Double, showParticles: Boolean) {
        val fallDistance = startY - player.location.y
        if (fallDistance <= 0) return

        if (showParticles) player.world.spawnParticle(Particle.EXPLOSION, player.location, 5)
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
        val victim = event.entity as? LivingEntity ?: return
        val now = System.currentTimeMillis()

        if (victim is Player) {
            val victimRep = plugin.infamyManager.getRawReputation(victim)

            if (victimRep >= 3) {
                val expires = swordBlockActiveUntil[victim.uniqueId] ?: 0
                if (now <= expires && victim.inventory.itemInMainHand.type.name.endsWith("_SWORD")) {
                    event.damage *= 0.5
                    victim.world.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)

                    if (plugin.config.getBoolean("settings.show-ability-particles", true)) {
                        victim.world.spawnParticle(Particle.LAVA, victim.location.add(0.0, 1.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0)
                    }
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

            if (activeBrokenShields.contains(victim.uniqueId) && victim.isBlocking) {
                victim.health = (victim.health - 1.0).coerceAtLeast(0.0)
            }
        }

        val attacker = event.damager as? Player ?: return
        val attackerRep = plugin.infamyManager.getRawReputation(attacker)

        if (attackerRep >= 9 && victim is Player && victim.isBlocking && attacker.inventory.itemInMainHand.type.name.endsWith("_AXE")) {
            victim.health = (victim.health - 4.0).coerceAtLeast(0.0)
        }

        if (activeBleedCharge.containsKey(attacker.uniqueId)) {
            activeBleedCharge.remove(attacker.uniqueId)
            msg(attacker, "Bleeding Edge strike landed!", NamedTextColor.DARK_RED)

            var ticks = 0
            val bleedTask = object : BukkitRunnable() {
                override fun run() {
                    if (victim.isDead || !victim.isValid) { cancel(); return }

                    if (ticks < 5) {
                        val targetHealth = (victim.health - 1.0).coerceAtLeast(0.0)
                        if (targetHealth <= 0.0) {
                            victim.damage(100.0, attacker)
                        } else {
                            victim.health = targetHealth
                            victim.playHurtAnimation(0f)
                        }
                        victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.0, 0.0), 5)
                        ticks++
                    } else {
                        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 80, 0))
                        cancel()
                    }
                }
            }
            bleedTask.runTaskTimer(plugin, 20L, 20L)
        }
    }
}