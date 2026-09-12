package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class BlockBreakListener(private val plugin: InfamySMP) : Listener {

    private val placedOresKey = NamespacedKey(plugin, "placed_ores")

    private fun getPacked(block: Block): Int {
        val lx = block.x and 15
        val lz = block.z and 15
        val ly = block.y + 64
        return lx or (lz shl 4) or (ly shl 8)
    }

    private fun setPlacedOre(block: Block) {
        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val packed = getPacked(block)
        val array = pdc.get(placedOresKey, PersistentDataType.INTEGER_ARRAY) ?: IntArray(0)
        if (!array.contains(packed)) {
            pdc.set(placedOresKey, PersistentDataType.INTEGER_ARRAY, array.plus(packed))
        }
    }

    private fun isPlacedOre(block: Block): Boolean {
        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val array = pdc.get(placedOresKey, PersistentDataType.INTEGER_ARRAY) ?: return false
        return array.contains(getPacked(block))
    }

    private fun removePlacedOre(block: Block) {
        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val packed = getPacked(block)
        val array = pdc.get(placedOresKey, PersistentDataType.INTEGER_ARRAY) ?: return
        if (array.contains(packed)) {
            val newArray = array.filter { it != packed }.toIntArray()
            if (newArray.isEmpty()) {
                pdc.remove(placedOresKey)
            } else {
                pdc.set(placedOresKey, PersistentDataType.INTEGER_ARRAY, newArray)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val type = event.block.type
        if (type.name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            setPlacedOre(event.block)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE || !event.isDropItems) return

        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)
        val tool = player.inventory.itemInMainHand

        val isOre = event.block.type.name.endsWith("_ORE") || event.block.type == Material.ANCIENT_DEBRIS
        val isPlaced = if (isOre) isPlacedOre(event.block) else false

        if (isPlaced) removePlacedOre(event.block)

        val hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH)

        if (hasSilkTouch) {
            if (isOre && !isPlaced && plugin.infamyManager.hasAbility(player, "good_fortune", true)) {

                if (Math.random() <= 0.5) {

                    val extra = if (Math.random() <= 0.15) 2 else 1

                    val centerLoc = event.block.location.clone().add(0.5, 0.2, 0.5)
                    event.block.world.dropItemNaturally(centerLoc, ItemStack(event.block.type, extra))
                }
            }
            return
        }

        val currentFortune = tool.getEnchantmentLevel(Enchantment.FORTUNE)
        var newFortune = currentFortune

        if (plugin.infamyManager.hasAbility(player, "good_fortune", true)) {
            if (honor >= 12) newFortune += 3
            else if (honor >= 9) newFortune += 2
            else if (honor >= 3) newFortune += 1
        }

        if (plugin.infamyManager.hasAbility(player, "bad_fortune", false)) {
            if (rep >= 20) newFortune = 0
            else if (rep >= 18) newFortune = newFortune.coerceAtMost(1)
            else if (rep >= 15) newFortune = newFortune.coerceAtMost(2)
        }

        if (newFortune != currentFortune) {
            val dummyTool = tool.clone()
            if (newFortune <= 0) dummyTool.removeEnchantment(Enchantment.FORTUNE)
            else dummyTool.addUnsafeEnchantment(Enchantment.FORTUNE, newFortune)

            val oldDrops = event.block.getDrops(tool, player).toList()
            val newDrops = event.block.getDrops(dummyTool, player).toList()

            var dropsChanged = oldDrops.size != newDrops.size
            if (!dropsChanged) {
                for (i in oldDrops.indices) {
                    if (!oldDrops[i].isSimilar(newDrops[i]) || oldDrops[i].amount != newDrops[i].amount) {
                        dropsChanged = true
                        break
                    }
                }
            }

            if (dropsChanged) {
                event.isDropItems = false
                val centerLoc = event.block.location.clone().add(0.5, 0.2, 0.5)
                for (drop in newDrops) {
                    event.block.world.dropItemNaturally(centerLoc, drop)
                }
            }
        }
    }
}