// Item definition class. Connected to InfamySMP.kt and Player listeners.
package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemManager(private val plugin: InfamySMP) {

    // Unique keys to safely mark items without anvil exploit vulnerabilities
    val bottleTypeKey = NamespacedKey(plugin, "bottle_type")
    val bottleValueKey = NamespacedKey(plugin, "bottle_value")

    fun createInfamyBottle(points: Int): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta ?: return item

        // Modern Paper Component styling (optimized and safe)
        meta.displayName(Component.text("Infamy Bottle ($points)", NamedTextColor.RED))

        // Inject invisible data into the item
        meta.persistentDataContainer.set(bottleTypeKey, PersistentDataType.STRING, "infamy")
        meta.persistentDataContainer.set(bottleValueKey, PersistentDataType.INTEGER, points)

        item.itemMeta = meta
        return item
    }

    fun createHonorBottle(): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta ?: return item

        meta.displayName(Component.text("Honor Bottle (+1)", NamedTextColor.AQUA))

        meta.persistentDataContainer.set(bottleTypeKey, PersistentDataType.STRING, "honor")
        meta.persistentDataContainer.set(bottleValueKey, PersistentDataType.INTEGER, 1)

        item.itemMeta = meta
        return item
    }
}