// Event listener for passive perks and penalties. Connected to InfamySMP.kt.
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

class PerkListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onPotionApply(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val effect = event.newEffect ?: return

        if (event.cause == EntityPotionEffectEvent.Cause.PLUGIN) return

        val rep = plugin.infamyManager.getRawReputation(player)

        // Infamy 12 gets double, Honor 9 gets half
        if (rep >= 12 || rep <= -9) {
            val modifier = if (rep >= 12) 2.0 else 0.5
            val newDuration = (effect.duration * modifier).toInt()

            val newEffect = PotionEffect(
                effect.type, newDuration, effect.amplifier,
                effect.isAmbient, effect.hasParticles(), effect.hasIcon()
            )

            event.isCancelled = true
            plugin.server.scheduler.runTask(plugin, Runnable {
                player.addPotionEffect(newEffect)
            })
        }
    }

    @EventHandler
    fun onVillagerInteract(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked

        if (entity is Villager) {
            val infamy = plugin.infamyManager.getInfamy(player)
            // Only Infamy triggers bad trades. Honor uses vanilla trades + native HotV effect
            if (infamy >= 12) {
                event.isCancelled = true

                val costMultiplier = when {
                    infamy >= 20 -> 2.0
                    infamy >= 15 -> 1.75
                    else -> 1.5
                }

                val customMerchant = Bukkit.createMerchant(Component.text("Wary Villager", NamedTextColor.RED))

                val badRecipes = entity.recipes.map { recipe ->
                    val newRecipe = MerchantRecipe(
                        recipe.result, recipe.uses, recipe.maxUses,
                        recipe.hasExperienceReward(), recipe.villagerExperience, recipe.priceMultiplier
                    )

                    val newIngredients = recipe.ingredients.mapIndexed { index, itemStack ->
                        if (index == 0) {
                            val badItem = itemStack.clone()
                            badItem.amount = (badItem.amount * costMultiplier).toInt().coerceIn(1, 64)
                            badItem
                        } else {
                            itemStack
                        }
                    }
                    newRecipe.ingredients = newIngredients
                    newRecipe
                }

                customMerchant.recipes = badRecipes
                player.openMerchant(customMerchant, true)
            }
        }
    }
}