// Item definition class. Connected to InfamySMP.kt and InfamyManager.kt.
package com.enxivity.infamy

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class ItemManager {

    fun createInfamyBottle(): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta
        meta?.setDisplayName("§cInfamy Bottle")
        item.itemMeta = meta
        return item
    }

    fun createHonorBottle(): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta
        meta?.setDisplayName("§bHonor Bottle")
        item.itemMeta = meta
        return item
    }
}