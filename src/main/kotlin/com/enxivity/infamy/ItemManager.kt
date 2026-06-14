package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemManager(private val plugin: InfamySMP) {
    val infamyKey = NamespacedKey(plugin, "infamy_bottle_value")
    val honorKey = NamespacedKey(plugin, "honor_bottle_value")
    val bossKey = NamespacedKey(plugin, "boss_bottle")

    fun createInfamyBottle(points: Int = 1): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Infamy Bottle (+$points)", NamedTextColor.RED))
        meta.lore(listOf(Component.text("Consume to gain $points Infamy point(s).", NamedTextColor.GRAY)))

        val cmd = plugin.config.getInt("custom-model-data.infamy_bottle", 10001)
        meta.setCustomModelData(cmd)
        meta.setEnchantmentGlintOverride(false) // Force removes the shiny potion glint

        meta.persistentDataContainer.set(infamyKey, PersistentDataType.INTEGER, points)
        item.itemMeta = meta
        return item
    }

    fun createHonorBottle(): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Honor Bottle (+1)", NamedTextColor.AQUA))
        meta.lore(listOf(Component.text("Consume to gain 1 Honor point.", NamedTextColor.GRAY)))

        val cmd = plugin.config.getInt("custom-model-data.honor_bottle", 10002)
        meta.setCustomModelData(cmd)
        meta.setEnchantmentGlintOverride(false) // Force removes the shiny potion glint

        meta.persistentDataContainer.set(honorKey, PersistentDataType.INTEGER, 1)
        item.itemMeta = meta
        return item
    }

    fun createBossBottle(): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Core of Infamy", NamedTextColor.DARK_RED))
        meta.lore(listOf(
            Component.text("The essence of a fallen Boss.", NamedTextColor.RED),
            Component.text("Required for a Level 20 player to reach Level 21.", NamedTextColor.GRAY)
        ))

        val cmd = plugin.config.getInt("custom-model-data.boss_bottle", 10003)
        meta.setCustomModelData(cmd)
        meta.setEnchantmentGlintOverride(true) // Forces the enchanted shiny glint permanently!

        meta.persistentDataContainer.set(bossKey, PersistentDataType.INTEGER, 1)
        item.itemMeta = meta
        return item
    }

    fun isCustomBottle(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        return pdc.has(infamyKey, PersistentDataType.INTEGER) ||
                pdc.has(honorKey, PersistentDataType.INTEGER) ||
                pdc.has(bossKey, PersistentDataType.INTEGER)
    }
}