package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemRestrictionsListener(private val plugin: InfamySMP) : Listener {

    @EventHandler
    fun onItemDamage(event: EntityDamageEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                checkBossBottleDestruction(item)
                return
            }
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemCombust(event: EntityCombustEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) event.isCancelled = true
    }

    @EventHandler
    fun onItemDespawn(event: ItemDespawnEvent) {
        checkBossBottleDestruction(event.entity)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (isBossBottle(item)) {
            event.itemDrop.isInvulnerable = true
            Bukkit.broadcast(Component.text("A Pure Infamy Bottle has been dropped into the world!", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f) }
        }
    }

    private fun checkBossBottleDestruction(item: Item) {
        if (isBossBottle(item.itemStack)) {
            Bukkit.broadcast(Component.text("The Pure Infamy Bottle has been lost to the abyss! The server is softlocked until an admin intervenes.", NamedTextColor.DARK_RED, TextDecoration.BOLD))
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.view.title())
        val player = event.whoClicked as Player

        if (title == "Registered Teams" || title == "Infamy Info Hub" || title == "Unlocked Abilities" || title == "Your Kill History" || title == "Server Kill History") {
            event.isCancelled = true

            if (title == "Infamy Info Hub") {
                when (event.currentItem?.type) {
                    Material.ENCHANTED_BOOK -> openAbilitiesGUI(player)
                    Material.SHIELD -> player.performCommand("infamy team")
                    Material.PLAYER_HEAD -> {
                        if (event.slot == 16) player.performCommand("infamy history")
                    }
                    else -> {}
                }
            }
            return
        }

        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory
        val current = event.currentItem
        val cursor = event.cursor

        if ((isBossBottle(current) && cursor?.type == Material.BUNDLE) || (isBossBottle(cursor) && current?.type == Material.BUNDLE)) {
            event.isCancelled = true
            player.sendMessage(Component.text("The Pure Infamy Bottle cannot be stuffed inside a bundle!", NamedTextColor.RED))
            return
        }

        if (clickedInv != null && clickedInv.type != InventoryType.PLAYER) {
            if (isBossBottle(cursor) || isBossBottle(current)) {
                event.isCancelled = true
                player.sendMessage(Component.text("The Pure Infamy Bottle cannot be put inside external containers!", NamedTextColor.RED))
                return
            }

            if (event.click == ClickType.NUMBER_KEY) {
                val hotbarItem = player.inventory.getItem(event.hotbarButton)
                if (isBossBottle(hotbarItem)) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("The Pure Infamy Bottle cannot be put inside external containers!", NamedTextColor.RED))
                    return
                }
            }

            if (event.click == ClickType.SWAP_OFFHAND) {
                if (isBossBottle(player.inventory.itemInOffHand)) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("The Pure Infamy Bottle cannot be put inside external containers!", NamedTextColor.RED))
                    return
                }
            }
        }

        if (event.click.isShiftClick && clickedInv?.type == InventoryType.PLAYER) {
            if (topInv.type != InventoryType.PLAYER && topInv.type != InventoryType.CRAFTING) {
                if (isBossBottle(current)) {
                    event.isCancelled = true
                    player.sendMessage(Component.text("The Pure Infamy Bottle cannot be stashed inside containers!", NamedTextColor.RED))
                    return
                }
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.type != InventoryType.PLAYER && event.view.topInventory.type != InventoryType.CRAFTING && isBossBottle(event.oldCursor)) {
            if (event.rawSlots.any { it < event.view.topInventory.size }) {
                event.isCancelled = true
                event.whoClicked.sendMessage(Component.text("The Pure Infamy Bottle cannot be spread across storage spaces!", NamedTextColor.RED))
            }
        }
    }

    private fun isBossBottle(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER)
    }

    private fun openAbilitiesGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Unlocked Abilities", NamedTextColor.DARK_PURPLE))
        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)

        fun setSlot(slot: Int, mat: Material, name: String, descLines: List<String>, reqLvl: Int, hasIt: Boolean, isHonor: Boolean) {
            val item = if (hasIt) ItemStack(mat) else ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = item.itemMeta ?: return
            val color = if (isHonor) NamedTextColor.AQUA else NamedTextColor.RED

            meta.displayName(Component.text(if (hasIt) name else "Locked: $name", color).decoration(TextDecoration.ITALIC, false))

            val lore = mutableListOf<Component>()
            descLines.forEach { line ->
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            }
            lore.add(Component.text(" "))
            lore.add(Component.text("Requires ${if (isHonor) "Honor" else "Infamy"} Level: $reqLvl", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))

            meta.lore(lore)
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        setSlot(11, Material.BOOK, "Good Fortune", listOf("Passively boosts your yields", "when mining ores with the", "Fortune enchantment."), 3, honor >= 3, true)
        setSlot(12, Material.BOOK, "Hero of the Village", listOf("Permanently grants you the", "Hero of the Village effect,", "giving you trade discounts."), 6, honor >= 6, true)
        setSlot(14, Material.BOOK, "Halved Potions", listOf("Automatically reduces the duration", "of all negative potion effects", "inflicted on you by 50%."), 9, honor >= 9, true)
        setSlot(15, Material.ENCHANTED_BOOK, "Weapon Fatigue", listOf("Hitting enemies will passively", "apply an attack speed penalty,", "slowing their weapon cooldowns."), 12, honor >= 12, true)

        setSlot(29, Material.BOOK, "Sword Block", listOf("Right-click a sword to gain", "50% damage reduction for 1.5s.", "(1.5s Cooldown)"), 3, rep >= 3, false)
        setSlot(30, Material.BOOK, "Shield Recovery", listOf("Instantly restores your shield", "if disabled by an enemy axe.", "(25s Cooldown)"), 6, rep >= 6, false)
        setSlot(31, Material.BOOK, "Axe Pierce", listOf("Your axe attacks will pierce", "through enemy shields, dealing", "2 hearts of true damage."), 9, rep >= 9, false)
        setSlot(32, Material.BOOK, "Double Potions", listOf("Doubles the duration of any", "positive potion effects", "applied to you."), 12, rep >= 12, false)
        setSlot(33, Material.BOOK, "Wary Villagers", listOf("Villagers sense your infamy", "and will severely increase", "their trade prices out of fear."), 12, rep >= 12, false)

        setSlot(38, Material.BOOK, "Passive Resistance", listOf("Grants you a permanent", "Resistance I potion effect."), 15, rep >= 15, false)
        setSlot(39, Material.BOOK, "Bad Fortune", listOf("Severely reduces or entirely", "neutralizes your Fortune", "enchantment ore drops."), 15, rep >= 15, false)
        setSlot(40, Material.BOOK, "Bleeding Edge", listOf("Right-click a sword to prepare.", "Your next strike inflicts severe", "bleeding and Nausea. (1m CD)"), 18, rep >= 18, false)
        setSlot(41, Material.BOOK, "Mace Slam", listOf("Right-click a Mace to launch", "forward, creating a massive", "shockwave upon landing. (10s CD)"), 20, rep >= 20, false)
        setSlot(42, Material.ENCHANTED_BOOK, "Boss Sacrifice", listOf("Sneak & right-click air to", "destroy your equipped helmet for", "5 minutes of Strength III. (15m CD)"), 21, rep >= 21, false)

        player.openInventory(inv)
    }
}