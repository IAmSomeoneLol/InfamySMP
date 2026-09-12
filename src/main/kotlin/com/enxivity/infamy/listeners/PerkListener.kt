package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import com.destroystokyo.paper.entity.villager.ReputationType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerItemMendEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect

class PerkListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onPotionApply(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val effect = event.newEffect ?: return
        if (event.cause == EntityPotionEffectEvent.Cause.PLUGIN) return

        var modifier = 1.0

        if (plugin.infamyManager.hasAbility(player, "double_potions", false)) modifier = 2.0
        if (plugin.infamyManager.hasAbility(player, "halved_potions", true)) modifier = 0.5

        if (modifier != 1.0) {
            val newDuration = (effect.duration * modifier).toInt()
            val newEffect = PotionEffect(effect.type, newDuration, effect.amplifier, effect.isAmbient, effect.hasParticles(), effect.hasIcon())
            event.isCancelled = true
            plugin.server.scheduler.runTask(plugin, Runnable { player.addPotionEffect(newEffect) })
        }
    }

    @EventHandler
    fun onVillagerInteract(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked

        if (entity is Villager) {
            val rep = plugin.infamyManager.getRawReputation(player)
            val reputation = entity.getReputation(player.uniqueId)

            if (plugin.infamyManager.hasAbility(player, "villager_wary", false)) {
                val penalty = when {
                    rep >= 20 -> 150
                    rep >= 15 -> 100
                    else -> 50
                }
                reputation.setReputation(ReputationType.MAJOR_NEGATIVE, penalty)
                entity.setReputation(player.uniqueId, reputation)
            } else {
                if (reputation.getReputation(ReputationType.MAJOR_NEGATIVE) > 0) {
                    reputation.setReputation(ReputationType.MAJOR_NEGATIVE, 0)
                    entity.setReputation(player.uniqueId, reputation)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerExpChange(event: PlayerExpChangeEvent) {
        if (plugin.infamyManager.hasAbility(event.player, "double_xp", true)) {
            event.amount *= 2
        }
    }

    @EventHandler
    fun onPlayerItemMend(event: PlayerItemMendEvent) {
        if (plugin.infamyManager.hasAbility(event.player, "double_xp", true)) {

            event.repairAmount *= 2
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val rep = plugin.infamyManager.getRawReputation(killer)
        val honor = plugin.infamyManager.getHonor(killer)

        var lootingBonus = 0

        // Checks Honor config
        if (plugin.infamyManager.hasAbility(killer, "good_fortune", true)) {
            if (honor >= 12) lootingBonus = 3
            else if (honor >= 9) lootingBonus = 2
            else if (honor >= 3) lootingBonus = 1
        }

        // Checks Infamy config (Overwrites if they are Infamous)
        if (plugin.infamyManager.hasAbility(killer, "bad_fortune", false)) {
            if (rep >= 20) lootingBonus = -100 // Erases all looting entirely
            else if (rep >= 18) lootingBonus = -1
            else if (rep >= 15) lootingBonus = -2
        }

        if (lootingBonus == 0) return

        val mob = event.entity as? org.bukkit.loot.Lootable ?: return
        val lootTable = mob.lootTable ?: return

        val weapon = killer.inventory.itemInMainHand
        val currentLooting = weapon.getEnchantmentLevel(Enchantment.LOOTING)

        var newLooting = currentLooting + lootingBonus
        if (newLooting < 0) newLooting = 0

        if (newLooting != currentLooting) {

            val equipmentDrops = event.drops.filter { drop ->
                val eq = event.entity.equipment
                eq != null && (drop.isSimilar(eq.helmet) || drop.isSimilar(eq.chestplate) || drop.isSimilar(eq.leggings) || drop.isSimilar(eq.boots) || drop.isSimilar(eq.itemInMainHand) || drop.isSimilar(eq.itemInOffHand))
            }


            val context = org.bukkit.loot.LootContext.Builder(event.entity.location)
                .lootedEntity(event.entity)
                .killer(killer)
                .lootingModifier(newLooting)
                .build()


            val newLoot = lootTable.populateLoot(java.util.Random(), context)


            event.drops.clear()
            event.drops.addAll(equipmentDrops)
            event.drops.addAll(newLoot)
        }
    }
}