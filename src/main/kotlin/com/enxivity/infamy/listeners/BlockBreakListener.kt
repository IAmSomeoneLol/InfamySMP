package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector

class BlockBreakListener(private val plugin: InfamySMP) : Listener {

    private val placedOresKey = NamespacedKey(plugin, "placed_ores")

    // Packed coord calculation
    private fun getPacked(block: Block): Int {
        val lx = block.x and 15
        val lz = block.z and 15
        val ly = block.y + 64
        return lx or (lz shl 4) or (ly shl 8)
    }

    // Set placed ore
    private fun setPlacedOre(block: Block) {
        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val packed = getPacked(block)
        val array = pdc.get(placedOresKey, PersistentDataType.INTEGER_ARRAY) ?: IntArray(0)
        if (!array.contains(packed)) {
            pdc.set(placedOresKey, PersistentDataType.INTEGER_ARRAY, array.plus(packed))
        }
    }

    // Check placed ore
    private fun isPlacedOre(block: Block): Boolean {
        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val array = pdc.get(placedOresKey, PersistentDataType.INTEGER_ARRAY) ?: return false
        return array.contains(getPacked(block))
    }

    // Remove placed ore
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

    // Block place event
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val type = event.block.type
        if (type.name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            setPlacedOre(event.block)
        }
    }

    // Block break event
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE || !event.isDropItems) return

        val block = event.block
        val state = block.state

        // Container safety check
        if (state is Container) return

        // Bed safety check
        if (block.type.name.endsWith("_BED")) return

        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)
        val tool = player.inventory.itemInMainHand

        val isOre = block.type.name.endsWith("_ORE") || block.type == Material.ANCIENT_DEBRIS
        val isPlaced = if (isOre) isPlacedOre(block) else false

        if (isPlaced) removePlacedOre(block)

        val hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH)

        // Silk touch bonus
        if (hasSilkTouch) {
            if (isOre && !isPlaced && plugin.infamyManager.hasAbility(player, "good_fortune", true)) {
                if (Math.random() <= 0.5) {
                    val extra = if (Math.random() <= 0.15) 2 else 1
                    val centerLoc = block.location.add(0.5, 0.25, 0.5)
                    val dropItem = block.world.dropItem(centerLoc, ItemStack(block.type, extra))
                    dropItem.velocity = Vector(0.0, 0.1, 0.0)
                }
            }
            return
        }

        val currentFortune = tool.getEnchantmentLevel(Enchantment.FORTUNE)
        var newFortune = currentFortune

        // Fortune calculations
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

        val dummyTool = tool.clone()
        if (newFortune <= 0) dummyTool.removeEnchantment(Enchantment.FORTUNE)
        else dummyTool.addUnsafeEnchantment(Enchantment.FORTUNE, newFortune)

        val oldDrops = block.getDrops(tool, player).toList()
        val newDrops = if (newFortune != currentFortune) block.getDrops(dummyTool, player).toList() else oldDrops

        // Compare drop changes
        var dropsChanged = false
        if (oldDrops.size != newDrops.size) {
            dropsChanged = true
        } else {
            for (i in oldDrops.indices) {
                if (!oldDrops[i].isSimilar(newDrops[i]) || oldDrops[i].amount != newDrops[i].amount) {
                    dropsChanged = true
                    break
                }
            }
        }

        var finalDrops = if (dropsChanged) newDrops else oldDrops

        // Double Ore event
        if (isOre && !isPlaced && plugin.eventManager.isDoubleDropsEnabled() && plugin.eventManager.isDropsAffectOres()) {
            if (Math.random() <= plugin.eventManager.getDoubleDropsChance()) {
                val mult = plugin.eventManager.getDoubleDropsMultiplier()
                if (mult > 1) {
                    dropsChanged = true
                    finalDrops = finalDrops.map {
                        val cloned = it.clone()
                        cloned.amount = it.amount * mult
                        cloned
                    }
                }
            }
        }

        // Smooth drop spawn
        if (dropsChanged) {
            event.isDropItems = false
            val centerLoc = block.location.add(0.5, 0.25, 0.5)
            for (drop in finalDrops) {
                val itemEntity = block.world.dropItem(centerLoc, drop)
                itemEntity.velocity = Vector(0.0, 0.1, 0.0)
            }
        }
    }
}