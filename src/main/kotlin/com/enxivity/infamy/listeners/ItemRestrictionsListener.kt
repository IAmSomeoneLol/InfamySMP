@file:Suppress("DEPRECATION")
package com.enxivity.infamy.listeners

import com.enxivity.infamy.ECSnapshotHolder
import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.Sound
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import java.text.SimpleDateFormat
import java.util.Date

class ItemRestrictionsListener(private val plugin: InfamySMP) : Listener {

    private fun updateBottleStatus(item: ItemStack?, status: String, holderInfo: String? = null) {
        if (item == null) return
        val pdc = item.itemMeta?.persistentDataContainer ?: return
        if (pdc.has(plugin.itemManager.killIdKey, PersistentDataType.STRING)) {
            val killIdStr = pdc.get(plugin.itemManager.killIdKey, PersistentDataType.STRING)
            val record = plugin.infamyManager.killHistory.find { it.id.toString() == killIdStr }
            if (record != null) {
                record.status = status
                record.holderInfo = holderInfo
            }
        }
    }

    @EventHandler
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val result = event.result ?: return
        val text = event.inventory.renameText
        if (!text.isNullOrEmpty() && text.contains("&")) {
            val meta = result.itemMeta
            meta?.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(text).decoration(TextDecoration.ITALIC, false))
            result.itemMeta = meta
            event.result = result
        }
    }

    @EventHandler
    fun onItemSpawn(event: ItemSpawnEvent) {
        val itemEntity = event.entity
        if (plugin.itemManager.isCustomBottle(itemEntity.itemStack)) {
            itemEntity.isInvulnerable = true
            itemEntity.setUnlimitedLifetime(true)
        }
    }

    @EventHandler
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        val item = event.item.itemStack
        if (isBossBottle(item)) {
            event.isCancelled = true
        } else if (isNormalKillBottle(item)) {
            val loc = event.inventory.location
            val stashInfo = if (loc != null) "${loc.blockX}, ${loc.blockY}, ${loc.blockZ} (${loc.world.name})" else "Unknown Block"
            updateBottleStatus(item, "STASHED", stashInfo)
        }
    }

    @EventHandler
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        if (event.entity is Player) {
            updateBottleStatus(event.item.itemStack, "PICKED_UP", event.entity.name)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        if (plugin.itemManager.isCustomBottle(item)) {
            updateBottleStatus(item, "DROPPED", null)

            if (isBossBottle(item)) {
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
    }

    @EventHandler
    fun onItemDamage(event: EntityDamageEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) {
            if (isBossBottle(item.itemStack)) {
                if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                    updateBottleStatus(item.itemStack, "LOST", null)
                    val msg = Component.text("The Pure Infamy Bottle has been lost to the abyss! The server is softlocked until an admin intervenes.", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    Bukkit.getOnlinePlayers().filter { plugin.infamyManager.getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(msg) }
                } else {
                    event.isCancelled = true
                }
            } else {
                if (event.cause == EntityDamageEvent.DamageCause.VOID) {
                    updateBottleStatus(item.itemStack, "LOST", null)
                }
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onItemCombust(event: EntityCombustEvent) {
        val item = event.entity as? Item ?: return
        if (plugin.itemManager.isCustomBottle(item.itemStack)) event.isCancelled = true
    }

    @EventHandler
    fun onItemDespawn(event: ItemDespawnEvent) {
        if (plugin.itemManager.isCustomBottle(event.entity.itemStack)) {
            event.isCancelled = true
        }
    }

    private fun setBorder(inv: org.bukkit.inventory.Inventory) {
        val pane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = pane.itemMeta
        meta.displayName(Component.empty())
        pane.itemMeta = meta

        for (i in 0..8) inv.setItem(i, pane)
        for (i in 45..53) inv.setItem(i, pane)
        inv.setItem(9, pane); inv.setItem(18, pane); inv.setItem(27, pane); inv.setItem(36, pane)
        inv.setItem(17, pane); inv.setItem(26, pane); inv.setItem(35, pane); inv.setItem(44, pane)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // ONLY cancels if top inventory is an ECSnapshotHolder. Vanilla Ender Chests are never affected!
        if (event.view.topInventory.holder is ECSnapshotHolder) {
            event.isCancelled = true
            return
        }

        val title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.view.title())
        val player = event.whoClicked as Player

        if (title in listOf("Infamy Info Hub", "Personal Settings", "Scoreboard Settings", "Unlocked Abilities", "Registered Teams", "Your Kill History", "Server Kill History", "Infamy Index")) {
            event.isCancelled = true

            if (title == "Infamy Info Hub") {
                when (event.slot) {
                    20 -> openAbilitiesGUI(player)
                    22 -> openIndexGUI(player)
                    24 -> player.performCommand("infamy team")
                    30 -> openSettingsGUI(player)
                    32 -> player.performCommand("infamy history")
                }
            } else if (title == "Personal Settings") {
                val settings = plugin.infamyManager.getSettings(player.uniqueId)
                when (event.slot) {
                    20 -> settings.globalSounds = !settings.globalSounds
                    21 -> settings.globalMessages = !settings.globalMessages
                    22 -> settings.abilityMessages = !settings.abilityMessages
                    23 -> settings.cooldownMessages = !settings.cooldownMessages
                    24 -> settings.teamMessages = !settings.teamMessages
                    30 -> settings.abilityMessagesInChat = !settings.abilityMessagesInChat
                    32 -> { openScoreboardSettingsGUI(player); return }
                    49 -> { openInfoGui(player); return }
                }
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                openSettingsGUI(player)
            } else if (title == "Scoreboard Settings") {
                val settings = plugin.infamyManager.getSettings(player.uniqueId)
                when (event.slot) {
                    13 -> {
                        settings.scoreboardMode = when (settings.scoreboardMode) {
                            "MAIN" -> "TEAM"
                            "TEAM" -> "COOLDOWN"
                            "COOLDOWN" -> "OFF"
                            else -> "MAIN"
                        }
                    }
                    29 -> settings.sbTeam = !settings.sbTeam
                    30 -> settings.sbTeammates = !settings.sbTeammates
                    31 -> settings.sbKills = !settings.sbKills
                    32 -> settings.sbDeaths = !settings.sbDeaths
                    33 -> settings.sbKDR = !settings.sbKDR
                    39 -> settings.sbPoints = !settings.sbPoints
                    41 -> settings.sbOnline = !settings.sbOnline
                    49 -> { openSettingsGUI(player); return }
                }
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                openScoreboardSettingsGUI(player)
            } else if (title == "Infamy Index") {
                if (event.slot == 49) openInfoGui(player)
            } else if (title == "Unlocked Abilities") {
                if (event.slot == 49) openInfoGui(player)
            } else if (title in listOf("Registered Teams", "Your Kill History", "Server Kill History")) {
                if (event.slot == 49) openInfoGui(player)
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
            val stashInfo = "${player.location.blockX}, ${player.location.blockY}, ${player.location.blockZ} (${player.world.name})"
            if (clickedInv == topInv && isNormalKillBottle(cursor)) updateBottleStatus(cursor, "STASHED", stashInfo)
            else if (clickedInv != topInv && event.click.isShiftClick && isNormalKillBottle(current)) updateBottleStatus(current, "STASHED", stashInfo)
            else if (clickedInv == topInv && isNormalKillBottle(current) && event.action.name.contains("PICKUP")) updateBottleStatus(current, "PICKED_UP", player.name)
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
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is ECSnapshotHolder) {
            event.isCancelled = true
            return
        }

        if (event.view.topInventory.type != InventoryType.PLAYER && event.view.topInventory.type != InventoryType.CRAFTING) {
            if (isBossBottle(event.oldCursor) && event.rawSlots.any { it < event.view.topInventory.size }) {
                event.isCancelled = true
                event.whoClicked.sendMessage(Component.text("The Pure Infamy Bottle cannot be spread across storage spaces!", NamedTextColor.RED))
            } else if (isNormalKillBottle(event.oldCursor) && event.rawSlots.any { it < event.view.topInventory.size }) {
                val stashInfo = "${event.whoClicked.location.blockX}, ${event.whoClicked.location.blockY}, ${event.whoClicked.location.blockZ} (${event.whoClicked.world.name})"
                updateBottleStatus(event.oldCursor, "STASHED", stashInfo)
            }
        }
    }

    private fun isBossBottle(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer?.has(plugin.itemManager.bossKey, PersistentDataType.INTEGER) == true
    private fun isNormalKillBottle(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer?.has(plugin.itemManager.killIdKey, PersistentDataType.STRING) == true

    private fun createBackItem(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(Component.text("Back", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        item.itemMeta = meta
        return item
    }

    fun openInfoGui(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Infamy Info Hub", NamedTextColor.DARK_RED))
        setBorder(inv)

        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        meta.owningPlayer = player
        meta.displayName(Component.text("Your Profile", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))

        val ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE)
        val hours = ticks / (20 * 60 * 60)
        val minutes = (ticks / (20 * 60)) % 60
        val rep = plugin.infamyManager.getRawReputation(player)

        val levelComp = when {
            rep > 0 -> Component.text("Infamy Level: $rep", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            rep < 0 -> Component.text("Honor Level: ${-rep}", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
            else -> Component.text("Level: 0", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        }
        val tagRaw = plugin.infamyManager.getPrefixText(rep)
        val tagComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(tagRaw).decoration(TextDecoration.ITALIC, false)
        val team = plugin.teamManager.getTeam(player.uniqueId)

        meta.lore(listOf(
            levelComp, tagComp,
            Component.text("Playtime: ${hours}h ${minutes}m", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Team: ${team?.name ?: "None"}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(" "),
            Component.text("Kills: ${plugin.infamyManager.playerKills[player.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Deaths: ${plugin.infamyManager.playerDeaths[player.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Withdrawn Points: ${plugin.infamyManager.withdrawnPoints[player.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ))
        head.itemMeta = meta
        inv.setItem(13, head)

        val abilities = ItemStack(Material.ENCHANTED_BOOK)
        val abMeta = abilities.itemMeta
        abMeta.displayName(Component.text("View Abilities", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
        abMeta.lore(listOf(Component.text("Click to see your unlocked perks!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        abilities.itemMeta = abMeta
        inv.setItem(20, abilities)

        val index = plugin.itemManager.createInfamyBottle(1)
        val idMeta = index.itemMeta
        idMeta.displayName(Component.text("Infamy Index", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        idMeta.lore(listOf(Component.text("Learn how the mechanics work.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        index.itemMeta = idMeta
        inv.setItem(22, index)

        val shield = ItemStack(Material.SHIELD)
        val shieldMeta = shield.itemMeta
        shieldMeta.displayName(Component.text("View Teams", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
        shieldMeta.lore(listOf(Component.text("Click to view Registered Server Teams.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        shield.itemMeta = shieldMeta
        inv.setItem(24, shield)

        val anvil = ItemStack(Material.ANVIL)
        val anvilMeta = anvil.itemMeta
        anvilMeta.displayName(Component.text("Personal Settings", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
        anvilMeta.lore(listOf(Component.text("Toggle Infamy sounds and messages.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        anvil.itemMeta = anvilMeta
        inv.setItem(30, anvil)

        val skeleton = ItemStack(Material.PLAYER_HEAD)
        val skelMeta = skeleton.itemMeta as SkullMeta
        skelMeta.owningPlayer = Bukkit.getOfflinePlayer("MHF_Steve")
        skelMeta.displayName(Component.text("Kill History", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        skelMeta.lore(listOf(Component.text("Click to view your assassination records.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        skeleton.itemMeta = skelMeta
        inv.setItem(32, skeleton)

        player.openInventory(inv)
    }

    fun openIndexGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Infamy Index", NamedTextColor.DARK_RED))
        setBorder(inv)

        val inf = plugin.itemManager.createInfamyBottle(1).apply {
            itemMeta = itemMeta.apply {
                lore(listOf(Component.text("How to get: Kill a player or be betrayed.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Drops when you die if you have > 0 points.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
        }
        val hon = plugin.itemManager.createHonorBottle(1).apply {
            itemMeta = itemMeta.apply {
                lore(listOf(Component.text("How to get: Buy from Wandering Trader,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Kill a Warden, or Win a Village Raid.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
        }
        val boss = plugin.itemManager.createPureInfamyBottle().apply {
            itemMeta = itemMeta.apply {
                lore(listOf(Component.text("Drops when the Level 21 Boss is killed.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Level 21 is LOCKED for everyone else until", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("someone consumes this specific bottle!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            }
        }

        inv.setItem(20, inf); inv.setItem(22, boss); inv.setItem(24, hon)
        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }

    fun openSettingsGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Personal Settings", NamedTextColor.DARK_GRAY))
        setBorder(inv)
        val settings = plugin.infamyManager.getSettings(player.uniqueId)

        fun createSettingItem(mat: Material, name: String, enabled: Boolean, extraLines: List<String> = emptyList()): ItemStack {
            val item = ItemStack(mat)
            val meta = item.itemMeta
            val color = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false))
            val lore = mutableListOf(Component.text(if (enabled) "Status: ON" else "Status: OFF", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            extraLines.forEach { lore.add(Component.text(it, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)) }
            meta.lore(lore)
            item.itemMeta = meta
            return item
        }

        inv.setItem(20, createSettingItem(Material.JUKEBOX, "Global Sounds", settings.globalSounds))
        inv.setItem(21, createSettingItem(Material.PAPER, "Global Messages", settings.globalMessages))
        inv.setItem(22, createSettingItem(Material.ENCHANTED_BOOK, "Ability Messages", settings.abilityMessages))
        inv.setItem(23, createSettingItem(Material.CLOCK, "Cooldown Warnings", settings.cooldownMessages))
        inv.setItem(24, createSettingItem(Material.NAME_TAG, "Team Messages", settings.teamMessages))

        val locStr = if (settings.abilityMessagesInChat) "Chat Box" else "Action Bar"
        val locItem = createSettingItem(Material.COMPASS, "Msg Location", true, listOf("Currently: $locStr"))
        inv.setItem(30, locItem)

        val sbItem = createSettingItem(Material.PAINTING, "Scoreboard Settings", true, listOf("Click to customize HUD"))
        inv.setItem(32, sbItem)

        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }

    fun openScoreboardSettingsGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Scoreboard Settings", NamedTextColor.DARK_GRAY))
        setBorder(inv)
        val settings = plugin.infamyManager.getSettings(player.uniqueId)

        val modeItem = ItemStack(Material.COMPARATOR)
        val modeMeta = modeItem.itemMeta
        modeMeta.displayName(Component.text("Scoreboard Mode: ${settings.scoreboardMode}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
        modeMeta.lore(listOf(Component.text("Click to Cycle (MAIN -> TEAM -> COOLDOWN -> OFF)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
        modeItem.itemMeta = modeMeta
        inv.setItem(13, modeItem)

        fun createToggle(mat: Material, name: String, enabled: Boolean): ItemStack {
            val item = ItemStack(mat)
            val meta = item.itemMeta
            val color = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text(if (enabled) "Shown on Main Board" else "Hidden from Main Board", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            item.itemMeta = meta
            return item
        }

        inv.setItem(29, createToggle(Material.WHITE_BANNER, "Team Name", settings.sbTeam))
        inv.setItem(30, createToggle(Material.PLAYER_HEAD, "Online Teammates", settings.sbTeammates))
        inv.setItem(31, createToggle(Material.IRON_SWORD, "Kills", settings.sbKills))
        inv.setItem(32, createToggle(Material.SKELETON_SKULL, "Deaths", settings.sbDeaths))
        inv.setItem(33, createToggle(Material.DIAMOND_SWORD, "K/D Ratio", settings.sbKDR))
        inv.setItem(39, createToggle(Material.EXPERIENCE_BOTTLE, "Honor/Infamy Points", settings.sbPoints))
        inv.setItem(41, createToggle(Material.OAK_DOOR, "Online Server Players", settings.sbOnline))

        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }

    data class AbilityDef(val id: String, val name: String, val actDesc: String, val descLines: List<String>)

    fun openAbilitiesGUI(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Unlocked Abilities", NamedTextColor.DARK_PURPLE))
        setBorder(inv)
        val rep = plugin.infamyManager.getRawReputation(player)
        val honor = plugin.infamyManager.getHonor(player)

        val c = plugin.config
        val haCd = c.getLong("abilities-config.hunger-absorption.cooldown-seconds", 60)
        val tiCd = c.getLong("abilities-config.true-invisibility.cooldown-seconds", 300)
        val tiDur = c.getLong("abilities-config.true-invisibility.duration-seconds", 20)
        val kdCd = c.getLong("abilities-config.karma-delay.cooldown-seconds", 180)
        val kdDur = c.getLong("abilities-config.karma-delay.delay-seconds", 6)
        val ssCd = c.getLong("abilities-config.shield-sacrifice.cooldown-seconds", 300)
        val ssDur = c.getLong("abilities-config.shield-sacrifice.duration-seconds", 120)
        val asCd = c.getLong("abilities-config.axe-stagger.cooldown-seconds", 45)
        val asTicks = c.getInt("abilities-config.axe-stagger.stagger-ticks", 20)

        fun setSlot(slot: Int, name: String, actDesc: String, descLines: List<String>, reqLvl: Int, hasIt: Boolean, isHonor: Boolean) {
            val item = ItemStack(if (hasIt) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE)
            val meta = item.itemMeta
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
            if (slot < 54) inv.setItem(slot, item)
        }

        val honorDefs = listOf(
            AbilityDef("good_fortune", "Good Fortune", "Activation: Passive (Mine with Fortune)", listOf("Raises your fortune effects based on level.")),
            AbilityDef("hero_of_the_village", "Hero of the Village", "Activation: Passive", listOf("Villagers appreciate you, giving better trades.")),
            AbilityDef("halved_potions", "Halved Potions", "Activation: Passive (All Potions)", listOf("Your potion durations are halved.")),
            AbilityDef("weapon_cooldowns", "Weapon Fatigue", "Activation: Passive (PvP Hit)", listOf("Attacking a player triggers a weapon cooldown penalty.", "Attacking mobs restores default vanilla attack speed.")),
            AbilityDef("double_xp", "Double XP", "Activation: Passive", listOf("You gain 2x Passive XP!")),
            AbilityDef("hunger_absorption", "Saturating Shield", "Activation: Sneak + Left-Click or Swap Hand (F)", listOf("Consume all active Hunger to convert it", "into temporary Absorption hearts. (${if (haCd >= 60) "${haCd / 60}m" else "${haCd}s"} CD)")),
            AbilityDef("true_invisibility", "True Invisibility", "Activation: Sneak + Right-Click (Empty Hand)", listOf("Safely hides your Armor, applying", "total Invisibility and Regeneration", "for $tiDur seconds. (${if (tiCd >= 60) "${tiCd / 60}m" else "${tiCd}s"} CD)")),
            AbilityDef("karma_delay", "Karmic Justice", "Activation: Sneak + Attack (Any Weapon)", listOf("Stop all incoming damage to opponent", "for ${kdDur}s. Afterwards, all blocked damage", "triggers instantly at once. (${if (kdCd >= 60) "${kdCd / 60}m" else "${kdCd}s"} CD)"))
        )

        val infamyDefs = listOf(
            AbilityDef("sword_block", "Sword Block", "Activation: Right-Click with Sword", listOf("Block incoming damage taking 50%.", "(60s CD)")),
            AbilityDef("shield_recovery", "Shield Recovery", "Activation: Sneak + Right-Click with Shield", listOf("Pull Shield back up after break.", "Takes 2 hearts of damage. (25s CD)")),
            AbilityDef("axe_pierce", "Axe Pierce", "Activation: Attack blocking enemy with Axe", listOf("Damage through Shields dealing 2 hearts.")),
            AbilityDef("shield_sacrifice", "Shield Sacrifice", "Activation: Sneak + Left-Click or Swap Hand (F)", listOf("Disable shield for ${if (ssDur >= 60) "${ssDur / 60}m" else "${ssDur}s"} to gain Resistance.", "(${if (ssCd >= 60) "${ssCd / 60}m" else "${ssCd}s"} CD)")),
            AbilityDef("double_potions", "Double Potions", "Activation: Passive", listOf("Receive DOUBLE TIME for EVERY Potion used.")),
            AbilityDef("villager_wary", "Wary Villagers", "Activation: Passive", listOf("Villagers are wary giving worse prices.")),
            AbilityDef("passive_resistance", "Passive Effect", "Activation: Passive", listOf("Receive Passive effect.")),
            AbilityDef("bad_fortune", "Bad Fortune", "Activation: Passive", listOf("Fortune is worsened or disabled.")),
            AbilityDef("axe_stagger", "Axe Stagger", "Activation: Attack with Axe", listOf("Stagger opponent for ${asTicks / 20.0}s. (${asCd}s CD)")),
            AbilityDef("bleeding_edge", "Bleeding Edge", "Activation: Right-Click Diamond/Netherite Sword", listOf("Next strike causes bleeding damage over time.", "(60s CD)")),
            AbilityDef("mace_slam", "Mace Slam & Passive", "Activation: Right-Click Mace", listOf("Dash and create shockwave. (60s CD)")),
            AbilityDef("boss_sacrifice", "Hellcrush (Boss)", "Activation: Sneak + Right Click (Empty Hand)", listOf("Max team size: 2. Constant Glowing.", "Activate buff but lose Helmet stats. (15m CD)"))
        )

        var hSlot = 37
        honorDefs.filter { plugin.config.getBoolean("honor_abilities.${it.id}.enabled", true) }
            .sortedBy { plugin.config.getInt("honor_abilities.${it.id}.level", 99) }
            .forEach { def ->
                val req = plugin.config.getInt("honor_abilities.${def.id}.level", 99)
                setSlot(hSlot++, def.name, def.actDesc, def.descLines, req, honor >= req, true)
            }

        var iSlot = 10
        infamyDefs.filter { plugin.config.getBoolean("infamy_abilities.${it.id}.enabled", true) }
            .sortedBy { plugin.config.getInt("infamy_abilities.${it.id}.level", 99) }
            .forEach { def ->
                if (iSlot == 17) iSlot = 19
                val req = plugin.config.getInt("infamy_abilities.${def.id}.level", 99)
                setSlot(iSlot++, def.name, def.actDesc, def.descLines, req, rep >= req, false)
            }

        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }

    private fun addHistoryLegend(inv: org.bukkit.inventory.Inventory) {
        val info = ItemStack(Material.BOOK)
        val meta = info.itemMeta
        meta.displayName(Component.text("Bottle Status Legend", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("DROPPED: The bottle is currently floating on the ground somewhere.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            Component.text("PICKED_UP: A player currently has the bottle in their inventory.", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
            Component.text("STASHED: The bottle was placed inside a Chest, Barrel, Hopper, etc.", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
            Component.text("WITHDRAWN: The original killer drank the bottle to claim their own points.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("STOLEN: Someone else drank the bottle to steal the points.", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
            Component.text("LOST: The bottle was thrown in Lava, the Void, a Cactus, or naturally despawned.", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)
        ))
        info.itemMeta = meta
        inv.setItem(45, info)
    }

    fun openHistoryGui(player: Player, adminMode: Boolean) {
        val title = if (adminMode) "Server Kill History" else "Your Kill History"
        val inv = Bukkit.createInventory(null, 54, Component.text(title, NamedTextColor.DARK_RED))
        setBorder(inv)

        val history = if (adminMode) plugin.infamyManager.killHistory.sortedByDescending { it.timestamp }.take(28)
        else plugin.infamyManager.killHistory.filter { it.killer == player.uniqueId }.sortedByDescending { it.timestamp }.take(28)

        var slot = 10
        history.forEach { record ->
            if (slot == 17) slot = 19
            if (slot == 26) slot = 28
            if (slot == 35) slot = 37

            val head = ItemStack(Material.PLAYER_HEAD)
            val meta = head.itemMeta as SkullMeta
            meta.owningPlayer = Bukkit.getOfflinePlayer(record.victim)
            meta.displayName(Component.text("Victim: ${record.victimName}", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))

            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm").format(Date(record.timestamp))
            val color = when (record.status) {
                "DROPPED" -> NamedTextColor.YELLOW
                "PICKED_UP" -> NamedTextColor.GOLD
                "STASHED" -> NamedTextColor.BLUE
                "WITHDRAWN" -> NamedTextColor.GRAY
                "STOLEN" -> NamedTextColor.DARK_PURPLE
                "LOST" -> NamedTextColor.DARK_RED
                else -> NamedTextColor.WHITE
            }

            val lore = mutableListOf(
                Component.text("Killed By: ${record.killerName}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Date: $dateStr", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            )
            if (adminMode) lore.add(Component.text("Coords: ${record.location}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text(" "))

            val infoText = record.holderInfo
            when (record.status) {
                "DROPPED" -> lore.add(Component.text("Bottle Status: DROPPED ON GROUND", color).decoration(TextDecoration.ITALIC, false))
                "PICKED_UP" -> lore.add(Component.text("Bottle Status: HELD BY ${infoText ?: "Unknown"}", color).decoration(TextDecoration.ITALIC, false))
                "STASHED" -> lore.add(Component.text("Bottle Status: STASHED AT ${infoText ?: "Unknown"}", color).decoration(TextDecoration.ITALIC, false))
                "WITHDRAWN" -> lore.add(Component.text("Bottle Status: WITHDRAWN BY ${infoText ?: record.killerName}", color).decoration(TextDecoration.ITALIC, false))
                "STOLEN" -> lore.add(Component.text("Bottle Status: STOLEN BY ${infoText ?: "Unknown"}", color).decoration(TextDecoration.ITALIC, false))
                "LOST" -> lore.add(Component.text("Bottle Status: LOST OR DESTROYED", color).decoration(TextDecoration.ITALIC, false))
                else -> lore.add(Component.text("Bottle Status: ${record.status}", color).decoration(TextDecoration.ITALIC, false))
            }
            meta.lore(lore)
            head.itemMeta = meta
            inv.setItem(slot, head)
            slot++
        }
        addHistoryLegend(inv)
        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }

    fun openTeamsGui(player: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Registered Teams", NamedTextColor.DARK_RED))
        setBorder(inv)

        var index = 10
        plugin.teamManager.teams.values.forEach { team ->
            if (index >= 44) return@forEach
            if (index == 17) index = 19
            if (index == 26) index = 28
            if (index == 35) index = 37

            val head = team.icon?.clone() ?: ItemStack(Material.PLAYER_HEAD).apply {
                val m = itemMeta as SkullMeta
                m.owningPlayer = Bukkit.getOfflinePlayer(team.leader)
                itemMeta = m
            }

            val meta = head.itemMeta
            meta.displayName(Component.text("Team: ${team.name}", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            val lore = mutableListOf(Component.text("Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false), Component.text("Members (${team.members.size}):", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            team.members.forEach { uuid ->
                val offline = Bukkit.getOfflinePlayer(uuid)
                val rep = plugin.infamyManager.getRawReputationByUUID(uuid)
                val tagComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.infamyManager.getPrefixText(rep)).decoration(TextDecoration.ITALIC, false)
                val role = if (team.leader == uuid) " [L]" else if (team.officers.contains(uuid)) " [O]" else ""
                val memberComp = if (offline.isOnline) {
                    Component.text("+ ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).append(Component.text((offline.name ?: "Unknown") + role, NamedTextColor.WHITE)).append(Component.text(" | ", NamedTextColor.DARK_GRAY)).append(tagComp)
                } else {
                    Component.text("- ${(offline.name ?: "Unknown")}$role | ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true).append(tagComp)
                }
                lore.add(memberComp)
            }
            meta.lore(lore)
            head.itemMeta = meta
            inv.setItem(index, head)
            index++
        }
        inv.setItem(49, createBackItem())
        player.openInventory(inv)
    }
}