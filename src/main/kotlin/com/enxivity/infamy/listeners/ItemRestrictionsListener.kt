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
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemRestrictionsListener(private val plugin: InfamySMP) : Listener {

    private fun updateBottleStatus(item: ItemStack?, status: String) {
        if (item == null) return
        val pdc = item.itemMeta?.persistentDataContainer ?: return
        if (pdc.has(plugin.itemManager.killIdKey, PersistentDataType.STRING)) {
            val killIdStr = pdc.get(plugin.itemManager.killIdKey, PersistentDataType.STRING)
            val record = plugin.infamyManager.killHistory.find { it.id.toString() == killIdStr }
            if (record != null) record.status = status
        }
    }

    @EventHandler
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        val item = event.item.itemStack
        if (isBossBottle(item)) {
            event.isCancelled = true
        } else if (isNormalKillBottle(item)) {
            updateBottleStatus(item, "STASHED")
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        if (event.entity is Player) updateBottleStatus(event.item.itemStack, "PICKED_UP")
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        updateBottleStatus(item, "DROPPED")
        if (isBossBottle(item)) {
            event.itemDrop.isInvulnerable = true
            event.itemDrop.setUnlimitedLifetime(true)

            val dropLoc = event.itemDrop.location
            val msg = Component.text("A Pure Infamy Bottle has been dropped into the world! Listen closely...", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            Bukkit.getOnlinePlayers().filter { plugin.infamyManager.getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(msg) }

            Bukkit.getOnlinePlayers().forEach { p ->
                if (plugin.infamyManager.getSettings(p.uniqueId).globalSounds) {
                    if (p.world == dropLoc.world) {
                        p.playSound(dropLoc, Sound.BLOCK_BEACON_DEACTIVATE, 10000f, 0.5f)
                    } else {
                        p.playSound(p.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onItemDamage(event: EntityDamageEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                updateBottleStatus(item.itemStack, "LOST")
                if (isBossBottle(item.itemStack)) {
                    val msg = Component.text("The Pure Infamy Bottle has been lost to the abyss! The server is softlocked until an admin intervenes.", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    Bukkit.getOnlinePlayers().filter { plugin.infamyManager.getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(msg) }
                }
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
        if (isBossBottle(event.entity.itemStack)) {
            event.isCancelled = true
            return
        }
        updateBottleStatus(event.entity.itemStack, "LOST")
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.view.title())
        val player = event.whoClicked as Player

        if (title == "Registered Teams" || title == "Infamy Info Hub" || title == "Unlocked Abilities" || title == "Your Kill History" || title == "Server Kill History" || title == "Personal Settings") {
            event.isCancelled = true
            if (title == "Infamy Info Hub") {
                when (event.currentItem?.type) {
                    Material.ENCHANTED_BOOK -> openAbilitiesGUI(player)
                    Material.SHIELD -> player.performCommand("infamy team")
                    Material.PLAYER_HEAD -> if (event.slot == 16) player.performCommand("infamy history")
                    Material.ANVIL -> openSettingsGUI(player)
                    else -> {}
                }
            } else if (title == "Personal Settings") {
                val settings = plugin.infamyManager.getSettings(player.uniqueId)
                when (event.slot) {
                    11 -> settings.globalSounds = !settings.globalSounds
                    12 -> settings.globalMessages = !settings.globalMessages
                    13 -> settings.abilityMessages = !settings.abilityMessages
                    14 -> settings.cooldownMessages = !settings.cooldownMessages
                    15 -> settings.teamMessages = !settings.teamMessages
                }
                openSettingsGUI(player)
            }
            if (event.currentItem?.type == Material.RED_STAINED_GLASS_PANE && event.currentItem?.itemMeta?.displayName()?.contains(Component.text("Hellcrush Active")) == true) {
                event.isCancelled = true
            }
            return
        }

        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory
        val current = event.currentItem
        val cursor = event.cursor

        if (clickedInv != null && topInv.type != InventoryType.PLAYER && topInv.type != InventoryType.CRAFTING) {
            if (clickedInv == topInv && isNormalKillBottle(cursor)) updateBottleStatus(cursor, "STASHED")
            else if (clickedInv != topInv && event.click.isShiftClick && isNormalKillBottle(current)) updateBottleStatus(current, "STASHED")
            else if (clickedInv == topInv && isNormalKillBottle(current) && event.action.name.contains("PICKUP")) updateBottleStatus(current, "PICKED_UP")
        }

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
            if (event.click == ClickType.NUMBER_KEY && isBossBottle(player.inventory.getItem(event.hotbarButton))) {
                event.isCancelled = true
                player.sendMessage(Component.text("The Pure Infamy Bottle cannot be put inside external containers!", NamedTextColor.RED))
                return
            }
            if (event.click == ClickType.SWAP_OFFHAND && isBossBottle(player.inventory.itemInOffHand)) {
                event.isCancelled = true
                player.sendMessage(Component.text("The Pure Infamy Bottle cannot be put inside external containers!", NamedTextColor.RED))
                return
            }
        }

        if (event.click.isShiftClick && clickedInv?.type == InventoryType.PLAYER && topInv.type != InventoryType.PLAYER && topInv.type != InventoryType.CRAFTING) {
            if (isBossBottle(current)) {
                event.isCancelled = true
                player.sendMessage(Component.text("The Pure Infamy Bottle cannot be stashed inside containers!", NamedTextColor.RED))
            }
        }

        if (current?.type == Material.RED_STAINED_GLASS_PANE && current.itemMeta?.displayName()?.contains(Component.text("Hellcrush Active")) == true) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.type != InventoryType.PLAYER && event.view.topInventory.type != InventoryType.CRAFTING) {
            if (isBossBottle(event.oldCursor) && event.rawSlots.any { it < event.view.topInventory.size }) {
                event.isCancelled = true
                event.whoClicked.sendMessage(Component.text("The Pure Infamy Bottle cannot be spread across storage spaces!", NamedTextColor.RED))
            } else if (isNormalKillBottle(event.oldCursor) && event.rawSlots.any { it < event.view.topInventory.size }) {
                updateBottleStatus(event.oldCursor, "STASHED")
            }
        }
    }

    private fun isBossBottle(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer?.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER) == true
    private fun isNormalKillBottle(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer?.has(plugin.itemManager.killIdKey, PersistentDataType.STRING) == true

    fun openSettingsGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Personal Settings", NamedTextColor.DARK_GRAY))
        val settings = plugin.infamyManager.getSettings(player.uniqueId)

        fun getGlass(enabled: Boolean): ItemStack = ItemStack(if (enabled) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)

        val s1 = getGlass(settings.globalSounds).apply { itemMeta = itemMeta.apply { displayName(Component.text("Global Sounds", NamedTextColor.GOLD)); lore(listOf(Component.text(if (settings.globalSounds) "Enabled" else "Disabled", if (settings.globalSounds) NamedTextColor.GREEN else NamedTextColor.RED))) } }
        val s2 = getGlass(settings.globalMessages).apply { itemMeta = itemMeta.apply { displayName(Component.text("Global Messages", NamedTextColor.GOLD)); lore(listOf(Component.text(if (settings.globalMessages) "Enabled" else "Disabled", if (settings.globalMessages) NamedTextColor.GREEN else NamedTextColor.RED))) } }
        val s3 = getGlass(settings.abilityMessages).apply { itemMeta = itemMeta.apply { displayName(Component.text("Ability Messages", NamedTextColor.GOLD)); lore(listOf(Component.text(if (settings.abilityMessages) "Enabled" else "Disabled", if (settings.abilityMessages) NamedTextColor.GREEN else NamedTextColor.RED))) } }
        val s4 = getGlass(settings.cooldownMessages).apply { itemMeta = itemMeta.apply { displayName(Component.text("Cooldown Warnings", NamedTextColor.GOLD)); lore(listOf(Component.text(if (settings.cooldownMessages) "Enabled" else "Disabled", if (settings.cooldownMessages) NamedTextColor.GREEN else NamedTextColor.RED))) } }
        val s5 = getGlass(settings.teamMessages).apply { itemMeta = itemMeta.apply { displayName(Component.text("Team Messages", NamedTextColor.GOLD)); lore(listOf(Component.text(if (settings.teamMessages) "Enabled" else "Disabled", if (settings.teamMessages) NamedTextColor.GREEN else NamedTextColor.RED))) } }

        inv.setItem(11, s1); inv.setItem(12, s2); inv.setItem(13, s3); inv.setItem(14, s4); inv.setItem(15, s5)
        player.openInventory(inv)
    }

    fun openAbilitiesGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Unlocked Abilities", NamedTextColor.DARK_PURPLE))
        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)

        fun setSlot(slot: Int, mat: Material, name: String, actDesc: String, descLines: List<String>, reqLvl: Int, hasIt: Boolean, isHonor: Boolean) {
            val item = if (hasIt) ItemStack(mat) else ItemStack(Material.BLACK_STAINED_GLASS_PANE)
            val meta = item.itemMeta ?: return
            val color = if (isHonor) NamedTextColor.AQUA else NamedTextColor.RED
            meta.displayName(Component.text(if (hasIt) name else "Locked: $name", color).decoration(TextDecoration.ITALIC, false))
            val lore = mutableListOf<Component>()

            lore.add(Component.text(actDesc, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text(" "))
            descLines.forEach { line -> lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)) }
            lore.add(Component.text(" "))
            lore.add(Component.text("Requires ${if (isHonor) "Honor" else "Infamy"} Level: $reqLvl", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))

            meta.lore(lore)
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // HONOR LORE
        setSlot(11, Material.BOOK, "Good Fortune", "Activation: Passive (Mine with Fortune)", listOf("The Player due to their kind acts", "gains Good Fortune, raising their", "fortune effects based on level.", "", "Level 3: +1 Fortune Level", "Level 9: +2 Fortune Levels", "Level 12: +3 Fortune Levels"), 3, honor >= 3, true)
        setSlot(12, Material.BOOK, "Hero of the Village", "Activation: Passive", listOf("The Player due to their kind acts", "Villagers appreciate them,", "giving them better trades.", "", "Level 6: HotV 1", "Level 9: HotV 2", "Level 12: HotV 3"), 6, honor >= 6, true)
        setSlot(14, Material.BOOK, "Halved Potions", "Activation: Passive (All Potions)", listOf("Due to their good fortune they", "now struggle to take life,", "causing ALL their potion", "durations to be halved."), 9, honor >= 9, true)
        setSlot(15, Material.ENCHANTED_BOOK, "Weapon Fatigue", "Activation: Passive (On hit)", listOf("Due to their good fortune they", "now struggle to take life,", "causing their weapon cooldowns", "to be higher (Sword becomes Axe)."), 12, honor >= 12, true)

        // INFAMY LORE
        setSlot(28, Material.BOOK, "Sword Block", "Activation: Right-Click with Sword", listOf("The Player Receives the ability to", "block incoming damage with their sword,", "causing half damage to be received.", "(1m Cooldown)"), 3, rep >= 3, false)
        setSlot(29, Material.BOOK, "Shield Recovery", "Activation: Sneak + Right-Click with Shield", listOf("The Player Receives the ability to", "pull their Shield back up after", "being broken.", "HOWEVER they take half a heart of", "damage. (25s CD)"), 6, rep >= 6, false)
        setSlot(30, Material.BOOK, "Axe Pierce", "Activation: Attack blocking enemy with Axe", listOf("The Player Receives the ability to", "do Damage through Shields with an", "AXE while breaking it", "(Dealing 2 hearts)."), 9, rep >= 9, false)
        setSlot(31, Material.BOOK, "Double Potions", "Activation: Passive (All Potions)", listOf("The Player Receives DOUBLE TIME", "for EVERY Potion they use."), 12, rep >= 12, false)
        setSlot(32, Material.BOOK, "Wary Villagers", "Activation: Passive (When trading)", listOf("HOWEVER Villagers now are wary of you,", "giving worse trades.", "", "Level 12: Wary trades", "Level 15: Even worse trades", "Level 20: Max price hikes"), 12, rep >= 12, false)
        setSlot(33, Material.BOOK, "Passive Resistance", "Activation: Passive", listOf("The Player Receives a PASSIVE", "Resistance 1 Boost."), 15, rep >= 15, false)
        setSlot(34, Material.BOOK, "Bad Fortune", "Activation: Passive (Overrides Fortune)", listOf("HOWEVER With all the sins you have", "committed you now have BAD FORTUNE.", "", "Level 15: Max Fortune 2", "Level 18: Max Fortune 1", "Level 20: No Fortune benefits AT ALL"), 15, rep >= 15, false)
        setSlot(39, Material.BOOK, "Bleeding Edge", "Activation: Right-Click Diamond/Netherite Sword", listOf("Using any sword above Diamond Tier", "causes the affected player to take", "Half a Heart of damage every SECOND", "lasting 5s (goes through armor &", "gives nausea when ended for 4s).", "(1m CD)"), 18, rep >= 18, false)
        setSlot(40, Material.BOOK, "Constant Strength", "Activation: Passive", listOf("The Player Receives CONSTANT", "Strength 1 effect."), 20, rep >= 20, false)
        setSlot(41, Material.BOOK, "Mace Slam", "Activation: Right-Click Mace", listOf("Allows the user to dash directionally", "by right clicking. Landing creates a", "shockwave dealing damage & knockup", "(caps at 6 hearts damage).", "(1m CD)"), 20, rep >= 20, false)
        setSlot(42, Material.ENCHANTED_BOOK, "Hellcrush (Boss)", "Activation: Sneak + Right-Click (Empty Hand)", listOf("ONLY ONE PLAYER CAN RECEIVE", "Max team size: 2 Members.", "Constant Glowing.", "Activate Strength 3 for 5 Minutes,", "but lose the effects of your Helmet", "as if it was never there. (15m CD)"), 21, rep >= 21, false)

        player.openInventory(inv)
    }
}