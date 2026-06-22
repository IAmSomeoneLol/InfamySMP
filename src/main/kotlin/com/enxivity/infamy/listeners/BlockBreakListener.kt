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
        var newFortune = currentFortune

        // Honor Buffs
        if (honor >= 12) newFortune += 3
        else if (honor >= 9) newFortune += 2
        else if (honor >= 3) newFortune += 1

        // Infamy Nerfs (Overrides)
        if (rep >= 20) newFortune = 0
        else if (rep >= 18) newFortune = newFortune.coerceAtMost(1)
        else if (rep >= 15) newFortune = newFortune.coerceAtMost(2)

        if (newFortune != currentFortune) {
            event.isDropItems = false
            val dummyTool = tool.clone()

            if (newFortune <= 0) dummyTool.removeEnchantment(Enchantment.FORTUNE)
            else dummyTool.addUnsafeEnchantment(Enchantment.FORTUNE, newFortune)

            val drops = event.block.getDrops(dummyTool, player)
            for (drop in drops) {
                event.block.world.dropItemNaturally(event.block.location, drop)
            }
        }
    }
}