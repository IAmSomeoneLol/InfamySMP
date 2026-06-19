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

    val ownerNameKey = NamespacedKey(plugin, "bottle_owner_name")
    val ownerUuidKey = NamespacedKey(plugin, "bottle_owner_uuid")
    val killIdKey = NamespacedKey(plugin, "kill_record_id")

    fun createInfamyBottle(points: Int = 1, ownerName: String? = null, ownerUuid: String? = null, killId: String? = null): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Infamy Bottle (+$points)", NamedTextColor.RED))

        val lore = mutableListOf(Component.text("Consume to gain $points Infamy point(s).", NamedTextColor.GRAY))
        if (ownerName != null) {
            lore.add(Component.text("Extracted by: $ownerName", NamedTextColor.DARK_GRAY))
            meta.persistentDataContainer.set(ownerNameKey, PersistentDataType.STRING, ownerName)
            if (ownerUuid != null) meta.persistentDataContainer.set(ownerUuidKey, PersistentDataType.STRING, ownerUuid)
        }
        if (killId != null) meta.persistentDataContainer.set(killIdKey, PersistentDataType.STRING, killId)

        meta.lore(lore)
        meta.setCustomModelData(plugin.config.getInt("custom-model-data.infamy_bottle", 10001))
        meta.setEnchantmentGlintOverride(false)
        meta.persistentDataContainer.set(infamyKey, PersistentDataType.INTEGER, points)

        item.itemMeta = meta
        return item
    }

    fun createHonorBottle(): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Honor Bottle (+1)", NamedTextColor.AQUA))
        meta.lore(listOf(Component.text("Consume to gain 1 Honor point.", NamedTextColor.GRAY)))

        meta.setCustomModelData(plugin.config.getInt("custom-model-data.honor_bottle", 10002))
        meta.setEnchantmentGlintOverride(false)
        meta.persistentDataContainer.set(honorKey, PersistentDataType.INTEGER, 1)
        item.itemMeta = meta
        return item
    }

    fun createPureInfamyBottle(ownerName: String? = null, droppedBy: String? = null, killId: String? = null): ItemStack {
        val item = ItemStack(Material.POTION)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Pure Infamy Bottle", NamedTextColor.DARK_RED))

        val lore = mutableListOf(
            Component.text("The essence of a fallen Boss.", NamedTextColor.RED),
            Component.text("Required for a Level 20 player to reach Level 21.", NamedTextColor.GRAY)
        )

        if (ownerName != null) lore.add(Component.text("Owned by: $ownerName", NamedTextColor.DARK_GRAY))
        if (droppedBy != null) lore.add(Component.text("Dropped by: $droppedBy", NamedTextColor.DARK_GRAY))
        if (killId != null) meta.persistentDataContainer.set(killIdKey, PersistentDataType.STRING, killId)

        meta.lore(lore)
        meta.setCustomModelData(plugin.config.getInt("custom-model-data.boss_bottle", 10003))
        meta.setEnchantmentGlintOverride(true)

        meta.persistentDataContainer.set(bossKey, PersistentDataType.INTEGER, 1)
        item.itemMeta = meta
        return item
    }

    fun isCustomBottle(item: ItemStack): Boolean {
        val pdc = item.itemMeta?.persistentDataContainer ?: return false
        return pdc.has(infamyKey, PersistentDataType.INTEGER) ||
                pdc.has(honorKey, PersistentDataType.INTEGER) ||
                pdc.has(bossKey, PersistentDataType.INTEGER)
    }
}