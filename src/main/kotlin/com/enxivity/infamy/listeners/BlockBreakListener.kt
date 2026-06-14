// Event listener for mining and fortune penalties. Connected to InfamySMP.kt.
package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class BlockBreakListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)
        val tool = player.inventory.itemInMainHand

        val hasFortune = tool.containsEnchantment(Enchantment.FORTUNE)
        val currentFortune = if (hasFortune) tool.getEnchantmentLevel(Enchantment.FORTUNE) else 0

        var newFortuneLevel = currentFortune

        // Infamy: Bad Fortune Penalties
        if (plugin.hasInfamyAbility("bad_fortune", rep) && hasFortune) {
            val maxAllowed = when {
                rep >= 20 -> 0
                rep >= 18 -> 1
                else -> 2
            }
            if (currentFortune > maxAllowed) newFortuneLevel = maxAllowed
        }
        // Honor: Good Fortune Bonuses
        else if (plugin.hasHonorAbility("good_fortune", honor)) {
            val bonus = when {
                honor >= 12 -> 3
                honor >= 9 -> 2
                else -> 1
            }
            newFortuneLevel = currentFortune + bonus
        }

        // Apply drop replacements only if the fortune level changed
        if (newFortuneLevel != currentFortune) {
            event.isDropItems = false
            val dummyTool = tool.clone()

            if (newFortuneLevel <= 0) {
                dummyTool.removeEnchantment(Enchantment.FORTUNE)
            } else {
                dummyTool.addUnsafeEnchantment(Enchantment.FORTUNE, newFortuneLevel)
            }

            val drops = event.block.getDrops(dummyTool, player)
            for (drop in drops) {
                event.block.world.dropItemNaturally(event.block.location, drop)
            }
        }
    }
}