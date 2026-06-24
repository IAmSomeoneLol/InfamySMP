package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.WanderingTrader
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.raid.RaidFinishEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.persistence.PersistentDataType

class HonorDeedListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onWardenKill(event: EntityDeathEvent) {
        if (event.entityType == EntityType.WARDEN) {
            val killer = event.entity.killer
            if (killer != null) {
                val bottle = plugin.itemManager.createHonorBottle()
                event.entity.world.dropItemNaturally(event.entity.location, bottle)
            }
        }
    }

    @EventHandler
    fun onRaidWin(event: RaidFinishEvent) {
        val winners = event.winners
        if (winners.isNotEmpty()) {
            val bottle = plugin.itemManager.createHonorBottle()
            event.raid.location.world.dropItemNaturally(event.raid.location, bottle)
        }
    }


    @EventHandler
    fun onTraderSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        if (entity is WanderingTrader) {
            injectHonorTrade(entity)
        }
    }


    @EventHandler
    fun onTraderInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (entity is WanderingTrader) {
            injectHonorTrade(entity)
        }
    }

    private fun injectHonorTrade(trader: WanderingTrader) {

        val hasHonorTrade = trader.recipes.any { recipe ->
            plugin.itemManager.isCustomBottle(recipe.result) &&
                    recipe.result.itemMeta?.persistentDataContainer?.has(plugin.itemManager.honorKey, PersistentDataType.INTEGER) == true
        }

        if (!hasHonorTrade) {

            val honorRecipe = MerchantRecipe(plugin.itemManager.createHonorBottle(), 1)
            honorRecipe.addIngredient(ItemStack(Material.EMERALD, 64))

            val newRecipes = trader.recipes.toMutableList()
            newRecipes.add(honorRecipe)
            trader.recipes = newRecipes
        }
    }
}