@file:Suppress("DEPRECATION")
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID

data class KarmaSession(val attackerId: UUID, var accumulatedDamage: Double)

class CombatListener(private val plugin: InfamySMP) : Listener {

    init {
        instance = this
    }

    companion object {
        var instance: CombatListener? = null
            private set

        fun activateAbility(player: Player, abilityName: String): Boolean {
            return instance?.activateAbility(player, abilityName) ?: false
        }
    }

    val swordBlockCooldowns = mutableMapOf<UUID, Long>()
    val swordBlockActiveUntil = mutableMapOf<UUID, Long>()

    val shieldAbilityCooldowns = mutableMapOf<UUID, Long>()
    val activeBrokenShields = mutableSetOf<UUID>()

    val bleedCooldowns = mutableMapOf<UUID, Long>()
    val activeBleedCharge = mutableMapOf<UUID, Long>()

    val maceCooldowns = mutableMapOf<UUID, Long>()
    val maceActivePlayers = mutableSetOf<UUID>()

    val sacrificeCooldowns = mutableMapOf<UUID, Long>()
    val activeSacrifices = mutableSetOf<UUID>()

    val honorAbsorbCooldowns = mutableMapOf<UUID, Long>()
    val honorInvisCooldowns = mutableMapOf<UUID, Long>()

    val activeTrueInvis = mutableSetOf<UUID>()

    val karmaCooldowns = mutableMapOf<UUID, Long>()
    val activeKarma = mutableMapOf<UUID, KarmaSession>()
    val primedKarma = mutableMapOf<UUID, Long>()

    val shieldSacrificeCooldowns = mutableMapOf<UUID, Long>()
    val axeStaggerCooldowns = mutableMapOf<UUID, Long>()

    val staggeredPlayers = mutableMapOf<UUID, Long>()

    fun cleanExpiredCooldowns() {
        val now = System.currentTimeMillis()
        swordBlockCooldowns.entries.removeIf { now - it.value > 120000L }
        swordBlockActiveUntil.entries.removeIf { now >= it.value }
        shieldAbilityCooldowns.entries.removeIf { now - it.value > 60000L }
        bleedCooldowns.entries.removeIf { now - it.value > 120000L }
        activeBleedCharge.entries.removeIf { now - it.value > 30000L }
        maceCooldowns.entries.removeIf { now - it.value > 120000L }
        sacrificeCooldowns.entries.removeIf { now - it.value > 1800000L }
        honorAbsorbCooldowns.entries.removeIf { now - it.value > 120000L }
        honorInvisCooldowns.entries.removeIf { now - it.value > 600000L }
        karmaCooldowns.entries.removeIf { now - it.value > 3600000L }
        shieldSacrificeCooldowns.entries.removeIf { now - it.value > 600000L }
        axeStaggerCooldowns.entries.removeIf { now - it.value > 90000L }
        primedKarma.entries.removeIf { now - it.value > 30000L }
        staggeredPlayers.entries.removeIf { now >= it.value }
    }

    private fun msg(player: Player, component: Component) {
        val settings = plugin.infamyManager.getSettings(player.uniqueId)
        if (settings.abilityMessages) {
            if (settings.abilityMessagesInChat) player.sendMessage(component)
            else player.sendActionBar(component)
        }
    }

    private fun msgCD(player: Player, component: Component) {
        val settings = plugin.infamyManager.getSettings(player.uniqueId)
        if (settings.cooldownMessages) {
            if (settings.abilityMessagesInChat) player.sendMessage(component)
            else player.sendActionBar(component)
        }
    }

    fun restoreHelmet(player: Player) {
        val armorAttr = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR)
        val toughAttr = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS)
        val hellcrushArmorKey = NamespacedKey(plugin, "hellcrush_armor_penalty")
        val hellcrushToughKey = NamespacedKey(plugin, "hellcrush_toughness_penalty")

        armorAttr?.modifiers?.find { it.key == hellcrushArmorKey }?.let { armorAttr.removeModifier(it) }
        toughAttr?.modifiers?.find { it.key == hellcrushToughKey }?.let { toughAttr.removeModifier(it) }
    }

    fun forceHideEquipment(player: Player) {
        val emptyEquipment = mapOf(
            org.bukkit.inventory.EquipmentSlot.HEAD to ItemStack(Material.AIR),
            org.bukkit.inventory.EquipmentSlot.CHEST to ItemStack(Material.AIR),
            org.bukkit.inventory.EquipmentSlot.LEGS to ItemStack(Material.AIR),
            org.bukkit.inventory.EquipmentSlot.FEET to ItemStack(Material.AIR),
            org.bukkit.inventory.EquipmentSlot.HAND to ItemStack(Material.AIR),
            org.bukkit.inventory.EquipmentSlot.OFF_HAND to ItemStack(Material.AIR)
        )
        for (other in player.world.players) {
            if (other.uniqueId != player.uniqueId && !plugin.teamManager.areTeammates(player.uniqueId, other.uniqueId)) {
                other.sendEquipmentChange(player, emptyEquipment)
            }
        }
    }

    fun resyncEquipment(player: Player) {
        activeTrueInvis.remove(player.uniqueId)
        val realEquipment = mapOf(
            org.bukkit.inventory.EquipmentSlot.HEAD to (player.inventory.helmet ?: ItemStack(Material.AIR)),
            org.bukkit.inventory.EquipmentSlot.CHEST to (player.inventory.chestplate ?: ItemStack(Material.AIR)),
            org.bukkit.inventory.EquipmentSlot.LEGS to (player.inventory.leggings ?: ItemStack(Material.AIR)),
            org.bukkit.inventory.EquipmentSlot.FEET to (player.inventory.boots ?: ItemStack(Material.AIR)),
            org.bukkit.inventory.EquipmentSlot.HAND to player.inventory.itemInMainHand,
            org.bukkit.inventory.EquipmentSlot.OFF_HAND to player.inventory.itemInOffHand
        )
        for (other in player.world.players) {
            if (other.uniqueId != player.uniqueId && !plugin.teamManager.areTeammates(player.uniqueId, other.uniqueId)) {
                other.sendEquipmentChange(player, realEquipment)
            }
        }
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        activeTrueInvis.remove(uuid)
        activeBrokenShields.remove(uuid)
        maceActivePlayers.remove(uuid)
        activeSacrifices.remove(uuid)
        activeBleedCharge.remove(uuid)
        activeKarma.remove(uuid)
        primedKarma.remove(uuid)
        staggeredPlayers.remove(uuid)
        plugin.pvpDebuffActive.remove(uuid)
    }

    @EventHandler
    fun onArmorChange(event: com.destroystokyo.paper.event.player.PlayerArmorChangeEvent) {
        if (activeTrueInvis.contains(event.player.uniqueId)) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (event.player.isOnline) forceHideEquipment(event.player) })
        }
    }

    @EventHandler
    fun onHotbarSwitch(event: PlayerItemHeldEvent) {
        if (activeTrueInvis.contains(event.player.uniqueId)) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (event.player.isOnline) forceHideEquipment(event.player) })
        }
        if (plugin.pvpDebuffActive[event.player.uniqueId] == true) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (event.player.isOnline) plugin.updateWeaponCooldownPenalty(event.player)
            })
        }
    }

    @EventHandler
    fun onPickup(event: org.bukkit.event.entity.EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (activeTrueInvis.contains(player.uniqueId)) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) forceHideEquipment(player) })
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onStaggerMove(event: PlayerMoveEvent) {
        val player = event.player
        val expire = staggeredPlayers[player.uniqueId] ?: return
        val now = System.currentTimeMillis()

        if (now < expire) {
            val from = event.from
            val to = event.to
            if (from.x != to.x || from.y != to.y || from.z != to.z) {
                event.to = Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch)
            }
        } else {
            staggeredPlayers.remove(player.uniqueId)
        }
    }

    fun activateAbility(player: Player, rawName: String): Boolean {
        val name = rawName.lowercase().replace("_", "").replace(" ", "")
        return when (name) {
            "trueinvisibility", "invisibility", "trueinvis", "invis" -> activateTrueInvisibility(player)
            "maceslam", "mace", "slam" -> activateMaceSlam(player)
            "swordblock", "parry" -> activateSwordBlock(player)
            "bleedingedge", "bleed" -> activateBleedingEdge(player)
            "karmicjustice", "karma", "karmadelay" -> primeKarma(player)
            "shieldsacrifice", "sacrifice" -> activateShieldSacrifice(player)
            "shieldrecovery", "recovery" -> activateShieldRecovery(player)
            "saturatingshield", "hungerabsorption", "absorb", "satshield" -> activateSaturatingShield(player)
            "hellcrush", "bosssacrifice" -> activateHellcrush(player)
            else -> {
                player.sendMessage(Component.text("Unknown ability: $rawName. Available: TrueInvisibility, MaceSlam, SwordBlock, BleedingEdge, KarmicJustice, ShieldSacrifice, ShieldRecovery, SaturatingShield, Hellcrush", NamedTextColor.RED))
                false
            }
        }
    }

    fun activateSaturatingShield(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "hunger_absorption", true)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Saturating Shield"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = honorAbsorbCooldowns[player.uniqueId] ?: 0
        val cdMs = plugin.config.getLong("abilities-config.hunger-absorption.cooldown-seconds", 60) * 1000L

        if (now - lastUsed > cdMs) {
            val food = player.foodLevel
            if (food > 0) {
                honorAbsorbCooldowns[player.uniqueId] = now
                player.foodLevel = 0
                player.saturation = 0f

                val currentAbsorb = player.absorptionAmount
                val requiredAmp = Math.max(0, (food - 1) / 4)

                player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, requiredAmp, false, false, false))

                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (player.isOnline) {
                        player.absorptionAmount = currentAbsorb + food.toDouble()
                    }
                }, 3L)

                msg(player, plugin.messagesManager.getComponent("abilities.hunger-converted"))
                player.world.playSound(player.location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f)

                try {
                    player.world.spawnParticle(Particle.valueOf("TRIAL_SPAWNER_DETECTION_OMINOUS"), player.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.02)
                } catch (e: Exception) {
                    player.world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.location.add(0.0, 1.0, 0.0), 15, 0.3, 0.5, 0.3, 0.02)
                }
                return true
            } else {
                msg(player, plugin.messagesManager.getComponent("abilities.no-hunger"))
                return false
            }
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Absorption conversion", "time" to "${(cdMs - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateTrueInvisibility(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "true_invisibility", true)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "True Invisibility"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = honorInvisCooldowns[player.uniqueId] ?: 0
        val cdMs = plugin.config.getLong("abilities-config.true-invisibility.cooldown-seconds", 300) * 1000L
        val durSecs = plugin.config.getLong("abilities-config.true-invisibility.duration-seconds", 20)

        if (now - lastUsed > cdMs) {
            honorInvisCooldowns[player.uniqueId] = now
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, (durSecs * 20).toInt(), 0, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, (durSecs * 20).toInt(), 1, false, false, true))

            activeTrueInvis.add(player.uniqueId)
            forceHideEquipment(player)

            msg(player, plugin.messagesManager.getComponent("abilities.true-invis-activated", "duration" to durSecs))

            object : BukkitRunnable() {
                var ticks = 0
                override fun run() {
                    if (!player.isOnline || player.isDead || ticks >= (durSecs * 20)) {
                        if (player.isOnline) {
                            resyncEquipment(player)
                            msg(player, plugin.messagesManager.getComponent("abilities.true-invis-faded"))
                        } else {
                            activeTrueInvis.remove(player.uniqueId)
                        }
                        cancel()
                        return
                    }
                    forceHideEquipment(player)
                    ticks += 5
                }
            }.runTaskTimer(plugin, 0L, 5L)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "True Invisibility", "time" to "${(cdMs - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun primeKarma(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "karma_delay", true)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Karmic Justice"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = karmaCooldowns[player.uniqueId] ?: 0
        val cdMs = plugin.config.getLong("abilities-config.karma-delay.cooldown-seconds", 180) * 1000L

        if (now - lastUsed > cdMs) {
            primedKarma[player.uniqueId] = now
            msg(player, plugin.messagesManager.getComponent("abilities.karma-primed"))
            player.world.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Karmic Justice", "time" to "${(cdMs - (now - lastUsed)) / 1000}s"))
            return false
        }
    }

    fun activateShieldSacrifice(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "shield_sacrifice", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Shield Sacrifice"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = shieldSacrificeCooldowns[player.uniqueId] ?: 0
        val cdMs = plugin.config.getLong("abilities-config.shield-sacrifice.cooldown-seconds", 300) * 1000L
        val durSecs = plugin.config.getLong("abilities-config.shield-sacrifice.duration-seconds", 120)

        if (now - lastUsed > cdMs) {
            shieldSacrificeCooldowns[player.uniqueId] = now
            player.setCooldown(Material.SHIELD, (durSecs * 20).toInt())
            player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, (durSecs * 20).toInt(), 0, false, false, true))

            player.world.playSound(player.location, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f)
            player.world.spawnParticle(Particle.LAVA, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.0)

            val durStr = if (durSecs >= 60) "${durSecs / 60} minutes" else "${durSecs} seconds"
            msg(player, plugin.messagesManager.getComponent("abilities.shield-sacrificed", "duration" to durStr))
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Shield Sacrifice", "time" to "${(cdMs - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateShieldRecovery(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "shield_recovery", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Shield Recovery"))
            return false
        }
        if (!player.hasCooldown(Material.SHIELD)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.shield-not-on-cooldown"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = shieldAbilityCooldowns[player.uniqueId] ?: 0
        val cdSecs = plugin.config.getLong("abilities-config.shield-recovery.cooldown-seconds", 25)
        val hCost = plugin.config.getDouble("abilities-config.shield-recovery.health-cost", 4.0)

        if (now - lastUsed > cdSecs * 1000L) {
            shieldAbilityCooldowns[player.uniqueId] = now
            player.setCooldown(Material.SHIELD, 0)
            player.health = (player.health - hCost).coerceAtLeast(0.0)

            msg(player, plugin.messagesManager.getComponent("abilities.shield-recovered", "hearts" to (hCost / 2)))
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Shield Recovery", "time" to "${((cdSecs * 1000L) - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateSwordBlock(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "sword_block", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Sword Block"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = swordBlockCooldowns[player.uniqueId] ?: 0
        val cdSecs = plugin.config.getLong("abilities-config.sword-block.cooldown-seconds", 60)
        val durSecs = plugin.config.getLong("abilities-config.sword-block.duration-seconds", 5)

        if (now - lastUsed > cdSecs * 1000L) {
            swordBlockCooldowns[player.uniqueId] = now
            swordBlockActiveUntil[player.uniqueId] = now + (durSecs * 1000L)
            msg(player, plugin.messagesManager.getComponent("abilities.sword-block-armed", "duration" to durSecs))
            player.world.playSound(player.location, Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Sword Block", "time" to "${(cdSecs * 1000L - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateBleedingEdge(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "bleeding_edge", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Bleeding Edge"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = bleedCooldowns[player.uniqueId] ?: 0
        val cdSecs = plugin.config.getLong("abilities-config.bleeding-edge.cooldown-seconds", 60)
        val timeoutSecs = plugin.config.getLong("abilities-config.bleeding-edge.prime-timeout-seconds", 4)

        if (now - lastUsed > cdSecs * 1000L) {
            bleedCooldowns[player.uniqueId] = now
            activeBleedCharge[player.uniqueId] = now
            msg(player, plugin.messagesManager.getComponent("abilities.bleeding-edge-primed"))
            player.world.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.5f)

            val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)
            if (showParticles) {
                object : BukkitRunnable() {
                    var trailTicks = 0
                    override fun run() {
                        if (!activeBleedCharge.containsKey(player.uniqueId) || trailTicks >= (timeoutSecs * 20) || !player.isOnline || player.isDead) {
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
                    msg(player, plugin.messagesManager.getComponent("abilities.bleeding-edge-dissipated"))
                }
            }, timeoutSecs * 20L)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Bleeding Edge", "time" to "${(cdSecs * 1000L - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateMaceSlam(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "mace_slam", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Mace Slam"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = maceCooldowns[player.uniqueId] ?: 0
        val cdSecs = plugin.config.getLong("abilities-config.mace-slam.cooldown-seconds", 60)

        if (now - lastUsed > cdSecs * 1000L) {
            maceCooldowns[player.uniqueId] = now

            val fwdVel = plugin.config.getDouble("abilities-config.mace-slam.forward-velocity", 1.5)
            val upVel = plugin.config.getDouble("abilities-config.mace-slam.upward-velocity", 1.4)
            player.velocity = player.location.direction.multiply(fwdVel).setY(upVel)
            player.world.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f)

            msg(player, plugin.messagesManager.getComponent("abilities.mace-slam-activated"))
            maceActivePlayers.add(player.uniqueId)

            val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)

            object : BukkitRunnable() {
                var peakY = player.location.y
                var launched = false
                var ticksInAir = 0

                override fun run() {
                    if (player.isDead || !player.isOnline) {
                        maceActivePlayers.remove(player.uniqueId)
                        cancel()
                        return
                    }

                    ticksInAir++
                    if (player.location.y > peakY) {
                        peakY = player.location.y
                    }
                    if (player.velocity.y > 0.1) {
                        launched = true
                    }

                    if (showParticles) {
                        player.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.location.add(0.0, 1.0, 0.0), 5, 0.2, 0.2, 0.2, 0.02)
                    }

                    val hasDownwardMotion = player.velocity.y <= 0.0
                    val hitGround = player.isOnGround || player.location.subtract(0.0, 0.1, 0.0).block.type.isSolid

                    if (hasDownwardMotion && hitGround && (launched || ticksInAir >= 5)) {
                        createMaceShockwave(player, peakY, showParticles)

                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            maceActivePlayers.remove(player.uniqueId)
                        }, 10L)

                        cancel()
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Mace Slam", "time" to "${(cdSecs * 1000L - (now - lastUsed))/1000}s"))
            return false
        }
    }

    fun activateHellcrush(player: Player): Boolean {
        if (!plugin.infamyManager.hasAbility(player, "boss_sacrifice", false)) {
            player.sendMessage(plugin.messagesManager.getComponent("abilities.not-unlocked", "ability" to "Hellcrush"))
            return false
        }
        val now = System.currentTimeMillis()
        val lastUsed = sacrificeCooldowns[player.uniqueId] ?: 0
        val cdSecs = plugin.config.getLong("abilities-config.hellcrush.cooldown-seconds", 900)
        val durSecs = plugin.config.getLong("abilities-config.hellcrush.duration-seconds", 300)

        if (now - lastUsed > cdSecs * 1000L) {
            sacrificeCooldowns[player.uniqueId] = now
            activeSacrifices.add(player.uniqueId)

            msg(player, plugin.messagesManager.getComponent("abilities.hellcrush-activated"))
            player.world.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.2f, 0.7f)

            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (activeSacrifices.contains(player.uniqueId)) {
                    activeSacrifices.remove(player.uniqueId)
                    if (player.isOnline) {
                        restoreHelmet(player)
                        msg(player, plugin.messagesManager.getComponent("abilities.hellcrush-faded"))
                    }
                }
            }, durSecs * 20L)
            return true
        } else {
            msgCD(player, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Hellcrush", "time" to "${((cdSecs * 1000L) - (now - lastUsed)) / 1000 / 60} mins"))
            return false
        }
    }

    @EventHandler
    fun onSwapHand(event: org.bukkit.event.player.PlayerSwapHandItemsEvent) {
        val player = event.player

        if (activeTrueInvis.contains(player.uniqueId)) {
            plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) forceHideEquipment(player) })
        }

        if (!plugin.infamyManager.getSettings(player.uniqueId).actionActivation) return

        if (player.isSneaking && event.mainHandItem.type.isAir && plugin.infamyManager.hasAbility(player, "hunger_absorption", true)) {
            event.isCancelled = true
            activateSaturatingShield(player)
            return
        }

        if (player.isSneaking && (event.mainHandItem.type == Material.SHIELD || player.inventory.itemInOffHand.type == Material.SHIELD) && plugin.infamyManager.hasAbility(player, "shield_sacrifice", false)) {
            event.isCancelled = true
            activateShieldSacrifice(player)
            return
        }
    }

    @EventHandler
    fun onCombatInteract(event: PlayerInteractEvent) {
        if (event.hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return

        val player = event.player
        val action = event.action
        val mainItem = player.inventory.itemInMainHand
        val offItem = player.inventory.itemInOffHand

        val isLeftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
        val isRightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK

        if (!plugin.infamyManager.getSettings(player.uniqueId).actionActivation) return

        if (player.isSneaking && mainItem.type.isAir && isLeftClick && plugin.infamyManager.hasAbility(player, "hunger_absorption", true)) {
            activateSaturatingShield(player)
            return
        }

        if (player.isSneaking && mainItem.type.isAir && isRightClick && plugin.infamyManager.hasAbility(player, "true_invisibility", true)) {
            activateTrueInvisibility(player)
            return
        }

        if (player.isSneaking && mainItem.type.isAir && isRightClick && plugin.infamyManager.hasAbility(player, "boss_sacrifice", false)) {
            activateHellcrush(player)
            return
        }

        if (player.isSneaking && (mainItem.type == Material.SHIELD || offItem.type == Material.SHIELD) && isLeftClick && plugin.infamyManager.hasAbility(player, "shield_sacrifice", false)) {
            activateShieldSacrifice(player)
            return
        }

        if (player.isSneaking && (mainItem.type == Material.SHIELD || offItem.type == Material.SHIELD) && isRightClick && plugin.infamyManager.hasAbility(player, "shield_recovery", false)) {
            activateShieldRecovery(player)
            return
        }

        val item = event.item ?: return

        if (isRightClick && (item.type == Material.DIAMOND_SWORD || item.type == Material.NETHERITE_SWORD) && plugin.infamyManager.hasAbility(player, "bleeding_edge", false)) {
            activateBleedingEdge(player)
            return
        }

        if (isRightClick && item.type.name.endsWith("_SWORD") && plugin.infamyManager.hasAbility(player, "sword_block", false)) {
            activateSwordBlock(player)
            return
        }

        if (isRightClick && item.type == Material.MACE && plugin.infamyManager.hasAbility(player, "mace_slam", false)) {
            activateMaceSlam(player)
            return
        }
    }

    private fun createMaceShockwave(player: Player, peakY: Double, showParticles: Boolean) {
        val fallDistance = (peakY - player.location.y).coerceAtLeast(3.0)
        val maxDamage = plugin.config.getDouble("abilities-config.mace-slam.max-splash-damage", 12.0)

        if (showParticles) {
            player.world.spawnParticle(Particle.EXPLOSION, player.location, 3)
            player.world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.location, 30, 0.5, 0.1, 0.5, 0.2)

            try {
                player.world.spawnParticle(Particle.valueOf("SMASH_GROUND_PARTICLE"), player.location, 10, 0.5, 0.1, 0.5, 0.2)
            } catch (e: Exception) {
                try {
                    val blockData = player.location.subtract(0.0, 1.0, 0.0).block.blockData
                    player.world.spawnParticle(Particle.DUST_PILLAR, player.location, 10, 0.5, 0.1, 0.5, 0.2, blockData)
                } catch (_: Exception) { }
            }

            player.world.playSound(player.location, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.9f)
        } else {
            player.world.playSound(player.location, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f)
        }

        for (entity in player.getNearbyEntities(6.0, 4.0, 6.0)) {
            if (entity is LivingEntity && entity.uniqueId != player.uniqueId) {
                if (entity is Player && !plugin.config.getBoolean("settings.team-friendly-fire", true)) {
                    if (plugin.teamManager.areTeammates(player.uniqueId, entity.uniqueId)) continue
                }

                val dir = entity.location.toVector().subtract(player.location.toVector()).setY(0.0)
                if (dir.lengthSquared() > 0) dir.normalize() else dir.setX(1.0)
                entity.velocity = dir.multiply(1.0).setY(0.65)

                entity.noDamageTicks = 0
                entity.damage((fallDistance * 1.5).coerceAtMost(maxDamage), player)
            }
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return

        if (activeKarma.containsKey(player.uniqueId) && event.cause != EntityDamageEvent.DamageCause.VOID && event.cause != EntityDamageEvent.DamageCause.CUSTOM) {
            event.isCancelled = true
            activeKarma[player.uniqueId]?.accumulatedDamage = (activeKarma[player.uniqueId]?.accumulatedDamage ?: 0.0) + event.damage
            player.world.spawnParticle(Particle.ENCHANT, player.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.1)
        }

        if (event.cause == EntityDamageEvent.DamageCause.FALL && maceActivePlayers.contains(player.uniqueId)) {
            val reduction = plugin.config.getDouble("abilities-config.mace-slam.fall-damage-reduction", 0.25)
            event.damage *= (1.0 - reduction)
            maceActivePlayers.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onPlayerTakeDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return

        if (plugin.config.getBoolean("settings.instant-shield", true) && victim is Player && victim.isHandRaised) {
            val activeItem = victim.activeItem
            if (activeItem != null && activeItem.type == Material.SHIELD && !victim.isBlocking) {
                val damagerLoc = if (event.damager is org.bukkit.entity.Projectile) event.damager.location else event.damager.location
                val playerDir = victim.location.direction.setY(0).normalize()
                val damagerDir = damagerLoc.toVector().subtract(victim.location.toVector()).setY(0).normalize()

                if (playerDir.lengthSquared() > 0 && damagerDir.lengthSquared() > 0) {
                    val angle = Math.toDegrees(Math.acos(playerDir.dot(damagerDir).coerceIn(-1.0, 1.0)))
                    if (angle < 100.0) {
                        event.isCancelled = true
                        victim.world.playSound(victim.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f)

                        val meta = activeItem.itemMeta as? org.bukkit.inventory.meta.Damageable
                        if (meta != null) {
                            meta.damage += (event.damage / 2).toInt().coerceAtLeast(1)
                            if (meta.damage > activeItem.type.maxDurability) {
                                if (victim.inventory.itemInMainHand == activeItem) {
                                    victim.inventory.setItemInMainHand(null)
                                } else {
                                    victim.inventory.setItemInOffHand(null)
                                }
                                victim.world.playSound(victim.location, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f)
                            } else {
                                activeItem.itemMeta = meta
                            }
                        }
                        return
                    }
                }
            }
        }

        if (activeKarma.containsKey(victim.uniqueId)) {
            val session = activeKarma[victim.uniqueId]!!
            val damager = event.damager
            if (damager != null && damager.uniqueId == session.attackerId) {
                if (victim.noDamageTicks > victim.maximumNoDamageTicks / 2.0f) {
                    event.isCancelled = true
                    return
                }

                event.isCancelled = true
                victim.noDamageTicks = victim.maximumNoDamageTicks

                session.accumulatedDamage += event.finalDamage
                victim.world.spawnParticle(Particle.ENCHANT, victim.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.1)
                return
            }
        }

        if (victim is Player) {
            val attacker = event.damager as? Player
            if (attacker != null && !plugin.config.getBoolean("settings.team-friendly-fire", true)) {
                if (plugin.teamManager.areTeammates(victim.uniqueId, attacker.uniqueId)) {
                    event.isCancelled = true
                    return
                }
            }

            if (plugin.infamyManager.hasAbility(victim, "sword_block", false)) {
                val expires = swordBlockActiveUntil[victim.uniqueId] ?: 0
                if (System.currentTimeMillis() <= expires && victim.inventory.itemInMainHand.type.name.endsWith("_SWORD")) {
                    event.damage *= 0.5
                    victim.world.playSound(victim.location, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)

                    val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)
                    if (showParticles) {
                        victim.world.spawnParticle(Particle.LAVA, victim.location.add(0.0, 1.0, 0.0), 5, 0.3, 0.3, 0.3, 0.0)
                    }
                }
            }

            if (activeSacrifices.contains(victim.uniqueId)) {
                val helmet = victim.inventory.helmet
                if (helmet != null) {
                    val protLevel = helmet.getEnchantmentLevel(Enchantment.PROTECTION)
                    if (protLevel > 0) {
                        val reductionToRemove = (protLevel * 0.04 * 0.6).coerceAtMost(0.80)
                        event.damage *= (1.0 / (1.0 - reductionToRemove))
                    }
                }
            }
        }

        val attacker = event.damager as? Player ?: return

        if (plugin.infamyManager.hasAbility(attacker, "weapon_cooldowns", true)) {
            val isVictimPlayer = victim is Player
            if (plugin.pvpDebuffActive[attacker.uniqueId] != isVictimPlayer) {
                plugin.pvpDebuffActive[attacker.uniqueId] = isVictimPlayer
                plugin.updateWeaponCooldownPenalty(attacker)
            }
        }

        if (plugin.infamyManager.hasAbility(attacker, "axe_pierce", false) && victim is Player && victim.isBlocking && attacker.inventory.itemInMainHand.type.name.endsWith("_AXE")) {
            victim.health = (victim.health - 4.0).coerceAtLeast(0.0)
        }
        if (plugin.infamyManager.hasAbility(attacker, "axe_stagger", false) && attacker.inventory.itemInMainHand.type.name.endsWith("_AXE")) {
            val now = System.currentTimeMillis()
            val lastUsed = axeStaggerCooldowns[attacker.uniqueId] ?: 0
            val cdMs = plugin.config.getLong("abilities-config.axe-stagger.cooldown-seconds", 45) * 1000L
            val staggerTicks = plugin.config.getInt("abilities-config.axe-stagger.stagger-ticks", 20)

            if (now - lastUsed > cdMs) {
                axeStaggerCooldowns[attacker.uniqueId] = now

                if (victim is Player) {
                    staggeredPlayers[victim.uniqueId] = now + (staggerTicks * 50L)
                    victim.velocity = Vector(0.0, 0.0, 0.0)
                    victim.sendMessage(plugin.messagesManager.getComponent("abilities.stagger-victim"))
                } else {
                    victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, staggerTicks, 10, false, false, false))
                }

                msg(attacker, plugin.messagesManager.getComponent("abilities.stagger-attacker"))
            } else {
                msgCD(attacker, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Axe Stagger", "time" to "${(cdMs - (now - lastUsed)) / 1000}s"))
            }
        }
        val isKarmaPrimed = primedKarma.remove(attacker.uniqueId) != null
        val isCrouchAction = attacker.isSneaking && plugin.infamyManager.getSettings(attacker.uniqueId).actionActivation

        if (plugin.infamyManager.hasAbility(attacker, "karma_delay", true) && (isKarmaPrimed || isCrouchAction)) {
            if (activeKarma.containsKey(victim.uniqueId)) return

            val now = System.currentTimeMillis()
            val lastUsed = karmaCooldowns[attacker.uniqueId] ?: 0
            val cdMs = plugin.config.getLong("abilities-config.karma-delay.cooldown-seconds", 180) * 1000L
            val delaySecs = plugin.config.getLong("abilities-config.karma-delay.delay-seconds", 6)

            if (now - lastUsed > cdMs) {
                if (victim.noDamageTicks > victim.maximumNoDamageTicks / 2.0) return

                karmaCooldowns[attacker.uniqueId] = now
                activeKarma[victim.uniqueId] = KarmaSession(attacker.uniqueId, event.finalDamage)
                event.isCancelled = true
                victim.noDamageTicks = victim.maximumNoDamageTicks

                msg(attacker, plugin.messagesManager.getComponent("abilities.karma-applied", "duration" to delaySecs))

                if (victim is Player) {
                    victim.sendMessage(plugin.messagesManager.getComponent("abilities.karma-victim", "attacker" to attacker.name))
                }

                victim.world.playSound(victim.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)

                val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)
                if (showParticles) {
                    object : BukkitRunnable() {
                        var ticks = 0
                        override fun run() {
                            if (!activeKarma.containsKey(victim.uniqueId) || !victim.isValid || ticks >= (delaySecs * 20)) {
                                cancel()
                                return
                            }
                            victim.world.spawnParticle(Particle.GLOW, victim.location.add(0.0, 1.0, 0.0), 5, 0.4, 0.8, 0.4, 0.05)
                            victim.world.spawnParticle(Particle.END_ROD, victim.location.add(0.0, 1.0, 0.0), 3, 0.4, 0.8, 0.4, 0.02)
                            ticks += 5
                        }
                    }.runTaskTimer(plugin, 0L, 5L)
                }

                object : BukkitRunnable() {
                    override fun run() {
                        val session = activeKarma.remove(victim.uniqueId)
                        if (session != null && session.accumulatedDamage > 0 && victim.isValid) {
                            val nerf = plugin.config.getDouble("abilities-config.karma-delay.pool-nerf-percentage", 0.1)
                            val finalExplosionDamage = session.accumulatedDamage * (1.0 - nerf)

                            victim.world.playSound(victim.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f)
                            if (victim is Player) {
                                victim.sendMessage(plugin.messagesManager.getComponent("abilities.karma-unleashed", "damage" to String.format("%.1f", finalExplosionDamage)))
                            }

                            victim.damage(finalExplosionDamage, attacker)
                        }
                    }
                }.runTaskLater(plugin, delaySecs * 20L)
                return
            } else {
                msgCD(attacker, plugin.messagesManager.getComponent("abilities.cooldown-msg", "ability" to "Karmic Justice", "time" to "${(cdMs - (now - lastUsed)) / 1000}s"))
            }
        }
        if (activeBleedCharge.containsKey(attacker.uniqueId)) {
            activeBleedCharge.remove(attacker.uniqueId)
            msg(attacker, plugin.messagesManager.getComponent("abilities.bleeding-edge-landed"))

            var ticks = 0
            val showParticles = plugin.config.getBoolean("settings.show-ability-particles", true)
            val damagePerTick = plugin.config.getDouble("abilities-config.bleeding-edge.damage-per-tick", 1.0)
            val iframeTicks = plugin.config.getInt("abilities-config.bleeding-edge.iframe-ticks", 10)

            val bleedTask = object : BukkitRunnable() {
                override fun run() {
                    if (victim.isDead || !victim.isValid) { cancel(); return }
                    if (ticks < 5) {
                        victim.noDamageTicks = 0
                        victim.damage(damagePerTick, attacker)
                        victim.playHurtAnimation(0f)
                        victim.noDamageTicks = iframeTicks

                        victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.0, 0.0), 5)

                        if (showParticles) {
                            val redDust = org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.5f)
                            victim.world.spawnParticle(Particle.DUST, victim.location.add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.0, redDust)
                        }

                        ticks++
                    } else {
                        val nauseaDur = plugin.config.getInt("abilities-config.bleeding-edge.after-effect.duration-ticks", 80)
                        val nauseaAmp = plugin.config.getInt("abilities-config.bleeding-edge.after-effect.amplifier", 1)
                        victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, nauseaDur, nauseaAmp))
                        cancel()
                    }
                }
            }
            bleedTask.runTaskTimer(plugin, 20L, 20L)
        }
    }
}