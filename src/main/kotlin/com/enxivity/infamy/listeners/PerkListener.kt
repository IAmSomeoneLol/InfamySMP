package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.potion.PotionEffect

@Suppress("DEPRECATION")
class PerkListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onPotionApply(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val effect = event.newEffect ?: return
        if (event.cause == EntityPotionEffectEvent.Cause.PLUGIN) return

        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)
        var modifier = 1.0

        if (rep >= 12) modifier = 2.0
        if (honor >= 9) modifier = 0.5

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
            if (rep >= 12) {
                event.isCancelled = true
                val costMultiplier = when {
                    rep >= 20 -> 3.0
                    rep >= 15 -> 2.0
                    else -> 1.5
                }

                val customMerchant = Bukkit.createMerchant(Component.text("Wary Villager", NamedTextColor.RED))
                val badRecipes = entity.recipes.map { recipe ->
                    val newRecipe = MerchantRecipe(recipe.result, recipe.uses, recipe.maxUses, recipe.hasExperienceReward(), recipe.villagerExperience, recipe.priceMultiplier)
                    newRecipe.ingredients = recipe.ingredients.mapIndexed { index, itemStack ->
                        if (index == 0) {
                            val badItem = itemStack.clone()
                            badItem.amount = (badItem.amount * costMultiplier).toInt().coerceIn(1, 64)
                            badItem
                        } else itemStack
                    }
                    newRecipe
                }
                customMerchant.recipes = badRecipes
                player.openMerchant(customMerchant, true)
            }
        }
    }
}