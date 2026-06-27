package com.enxivity.infamy.commands

import com.enxivity.infamy.InfamySMP
import com.enxivity.infamy.KillRecord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class InfamyCommand(private val plugin: InfamySMP) : CommandExecutor, TabCompleter {

    private fun isAdmin(sender: CommandSender): Boolean = sender.isOp || sender.hasPermission("infamysmp.admin")

    private val colorMap = mapOf(
        "black" to "&0", "dark_blue" to "&1", "dark_green" to "&2", "dark_aqua" to "&3",
        "dark_red" to "&4", "dark_purple" to "&5", "gold" to "&6", "gray" to "&7",
        "dark_gray" to "&8", "blue" to "&9", "green" to "&a", "aqua" to "&b",
        "red" to "&c", "light_purple" to "&d", "yellow" to "&e", "white" to "&f"
    )
    private val formatMap = mapOf(
        "bold" to "&l", "italic" to "&o", "underline" to "&n", "strikethrough" to "&m"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) handleInfoGui(sender)
            else sender.sendMessage(Component.text("Usage: /infamy <info|team|level|withdraw|bottle|history|cd|debugkill|add|top|settings|abilities> ...", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "info" -> handleInfoGui(sender)
            "settings" -> if (sender is Player) plugin.itemRestrictionsListener.openSettingsGUI(sender)
            "abilities" -> if (sender is Player) plugin.itemRestrictionsListener.openAbilitiesGUI(sender)
            "cd" -> if (sender is Player) handleCooldownsCommand(sender, args)
            "debugkill" -> handleDebugKillCommand(sender, args)
            "team" -> {
                if (args.size == 1 && sender is Player) handleTeamsGuiCommand(sender)
                else handleTeamCommand(sender, args)
            }
            "add" -> handleAddCommand(sender, args)
            "level" -> handleLevelCommand(sender, args)
            "withdraw" -> handleWithdrawCommand(sender, args)
            "bottle" -> handleBottleCommand(sender, args)
            "top" -> handleTopRefreshCommand(sender, args)
            "history" -> {
                if (args.size > 1 && args[1].lowercase() == "admin") handleAdminHistoryGui(sender)
                else handleHistoryGui(sender)
            }
            else -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            val subs = mutableListOf("team", "level", "withdraw", "history", "info", "cd", "settings", "abilities")
            if (isAdmin(sender)) subs.addAll(listOf("add", "bottle", "debugkill", "top"))
            completions.addAll(subs.filter { it.startsWith(args[0].lowercase()) })
        } else if (args.size == 2) {
            when (args[0].lowercase()) {
                "team" -> completions.addAll(listOf("create", "disband", "invite", "accept", "decline", "leave", "kick", "list", "chat", "color", "icon", "leadership", "coords").filter { it.startsWith(args[1].lowercase()) })
                "add", "level", "debugkill" -> if (isAdmin(sender)) completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) })
                "bottle" -> if (isAdmin(sender)) completions.addAll(listOf("honor", "infamy", "pure").filter { it.startsWith(args[1].lowercase()) })
                "top" -> if (isAdmin(sender)) completions.add("refresh")
                "history" -> if (isAdmin(sender) && "admin".startsWith(args[1].lowercase())) completions.add("admin")
                "cd" -> if (isAdmin(sender)) completions.add("refresh")
            }
        } else if (args.size == 3) {
            if ((args[0].lowercase() == "add" || args[0].lowercase() == "bottle" || args[0].lowercase() == "top") && isAdmin(sender)) {
                completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[2].lowercase()) })
            } else if (args[0].lowercase() == "debugkill" && isAdmin(sender)) {
                completions.addAll(listOf("DROPPED", "PICKED_UP", "STASHED", "WITHDRAWN", "STOLEN", "LOST").filter { it.startsWith(args[2].uppercase()) })
            } else if (args[0].lowercase() == "team") {
                if (args[1].lowercase() == "icon") completions.addAll(listOf("reset").filter { it.startsWith(args[2].lowercase()) })
                else if (args[1].lowercase() == "leadership" || args[1].lowercase() == "kick") completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[2].lowercase()) })
                else if (args[1].lowercase() == "coords") completions.addAll(listOf("on", "off", "toggle").filter { it.startsWith(args[2].lowercase()) })
                else if (args[1].lowercase() == "color") completions.addAll(colorMap.keys.filter { it.startsWith(args[2].lowercase()) })
            }
        } else if (args.size >= 4) {
            if (args[0].lowercase() == "team" && args[1].lowercase() == "color") {
                completions.addAll(formatMap.keys.filter { it.startsWith(args.last().lowercase()) && !args.contains(it) })
            }
        }
        return completions
    }

    private fun handleTopRefreshCommand(sender: CommandSender, args: Array<out String>) {
        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))

        if (args.size < 2 || args[1].lowercase() != "refresh") {
            return sender.sendMessage(Component.text("Usage: /infamy top refresh [player]", NamedTextColor.RED))
        }

        plugin.infamyManager.refreshBossLock()

        val msg = Component.text("The Most Infamous lock has been cleared by an admin! A Level 21 slot is now open.", NamedTextColor.DARK_RED, TextDecoration.BOLD)
        Bukkit.getOnlinePlayers().forEach { p ->
            if (plugin.infamyManager.getSettings(p.uniqueId).globalMessages) p.sendMessage(msg)
        }
        sender.sendMessage(Component.text("Successfully unlocked a Level 21 slot. Any existing Level 21 players will keep their rank.", NamedTextColor.GREEN))
    }

    private fun handleCooldownsCommand(player: Player, args: Array<out String>) {
        val cl = plugin.combatListener

        if (args.size > 1 && args[1].lowercase() == "refresh" && isAdmin(player)) {
            cl.swordBlockCooldowns.clear()
            cl.swordBlockActiveUntil.clear()
            cl.shieldAbilityCooldowns.clear()
            cl.activeBrokenShields.clear()
            cl.bleedCooldowns.clear()
            cl.activeBleedCharge.clear()
            cl.maceCooldowns.clear()
            cl.sacrificeCooldowns.clear()
            cl.activeSacrifices.clear()
            cl.maceActivePlayers.clear()
            player.sendMessage(Component.text("All ability cooldowns refreshed and active abilities terminated!", NamedTextColor.GREEN))
            return
        }

        val now = System.currentTimeMillis()
        val activeCDs = mutableListOf<String>()

        fun checkCD(lastUsed: Long, cdMs: Long, name: String) {
            if (now - lastUsed < cdMs) {
                val remaining = ((lastUsed + cdMs) - now) / 1000
                val min = remaining / 60
                val sec = remaining % 60
                val timeStr = if (min > 0) "${min}m ${sec}s" else "${sec}s"
                activeCDs.add("§c- $name: §e$timeStr remaining")
            }
        }

        checkCD(cl.swordBlockCooldowns[player.uniqueId] ?: 0, 60000, "Sword Block")
        checkCD(cl.shieldAbilityCooldowns[player.uniqueId] ?: 0, 25000, "Shield Recovery")
        checkCD(cl.bleedCooldowns[player.uniqueId] ?: 0, 60000, "Bleeding Edge")
        checkCD(cl.maceCooldowns[player.uniqueId] ?: 0, 60000, "Mace Slam")
        checkCD(cl.sacrificeCooldowns[player.uniqueId] ?: 0, 900000, "Hellcrush")

        if (activeCDs.isEmpty()) {
            player.sendMessage(Component.text("You have no abilities currently on cooldown!", NamedTextColor.GREEN))
        } else {
            player.sendMessage(Component.text("=== Your Active Cooldowns ===", NamedTextColor.DARK_RED))
            activeCDs.forEach { player.sendMessage(Component.text(it)) }
        }
    }

    private fun handleDebugKillCommand(sender: CommandSender, args: Array<out String>) {
        if (!isAdmin(sender) || sender !is Player) return sender.sendMessage(Component.text("Only admins can use this structural debugger.", NamedTextColor.RED))
        if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy debugkill <victimName> <DROPPED|PICKED_UP|STASHED|WITHDRAWN|STOLEN|LOST>", NamedTextColor.RED))

        val victimName = args[1]
        val inputStatus = args[2].uppercase()
        val validStatuses = listOf("DROPPED", "PICKED_UP", "STASHED", "WITHDRAWN", "STOLEN", "LOST")

        if (!validStatuses.contains(inputStatus)) return sender.sendMessage(Component.text("Invalid status state.", NamedTextColor.RED))

        val fakeId = UUID.randomUUID()
        val fakeLoc = "${sender.location.blockX}, ${sender.location.blockY}, ${sender.location.blockZ} (${sender.world.name})"
        val debugRecord = KillRecord(fakeId, sender.uniqueId, sender.name, UUID.randomUUID(), victimName, System.currentTimeMillis(), fakeLoc, inputStatus)

        plugin.infamyManager.killHistory.add(debugRecord)
        sender.sendMessage(Component.text("Injected debug profile into database with status: $inputStatus.", NamedTextColor.GREEN))
    }

    private fun handleInfoGui(sender: CommandSender) {
        if (sender !is Player) return
        val inv = Bukkit.createInventory(null, 27, Component.text("Infamy Info Hub", NamedTextColor.DARK_RED))

        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        meta.owningPlayer = sender
        meta.displayName(Component.text("Your Profile", NamedTextColor.GOLD))

        val ticks = sender.getStatistic(Statistic.PLAY_ONE_MINUTE)
        val hours = ticks / (20 * 60 * 60)
        val minutes = (ticks / (20 * 60)) % 60
        val rep = plugin.infamyManager.getRawReputation(sender)

        val levelComp = when {
            rep > 0 -> Component.text("Infamy Level: $rep", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            rep < 0 -> Component.text("Honor Level: ${-rep}", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
            else -> Component.text("Level: 0", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        }
        val tagRaw = plugin.infamyManager.getPrefixText(rep)
        val tagComp = LegacyComponentSerializer.legacyAmpersand().deserialize(tagRaw).decoration(TextDecoration.ITALIC, false)
        val team = plugin.teamManager.getTeam(sender.uniqueId)

        meta.lore(listOf(
            levelComp, tagComp,
            Component.text("Playtime: ${hours}h ${minutes}m", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Team: ${team?.name ?: "None"}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(" "),
            Component.text("Kills: ${plugin.infamyManager.playerKills[sender.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Deaths: ${plugin.infamyManager.playerDeaths[sender.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Withdrawn Points: ${plugin.infamyManager.withdrawnPoints[sender.uniqueId] ?: 0}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ))
        head.itemMeta = meta
        inv.setItem(10, head)

        val shield = ItemStack(Material.SHIELD)
        val shieldMeta = shield.itemMeta
        shieldMeta.displayName(Component.text("View Teams", NamedTextColor.GREEN))
        shieldMeta.lore(listOf(Component.text("Click to view Registered Server Teams.", NamedTextColor.GRAY)))
        if (team != null) { shieldMeta.addEnchant(Enchantment.UNBREAKING, 1, true); shieldMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS) }
        shield.itemMeta = shieldMeta
        inv.setItem(12, shield)

        val book = ItemStack(Material.ENCHANTED_BOOK)
        val bookMeta = book.itemMeta
        bookMeta.displayName(Component.text("View Abilities", NamedTextColor.LIGHT_PURPLE))
        bookMeta.lore(listOf(Component.text("Click to see your unlocked perks!", NamedTextColor.GRAY)))
        book.itemMeta = bookMeta
        inv.setItem(14, book)

        val skeleton = ItemStack(Material.PLAYER_HEAD)
        val skelMeta = skeleton.itemMeta as SkullMeta
        skelMeta.owningPlayer = Bukkit.getOfflinePlayer("MHF_Steve")
        skelMeta.displayName(Component.text("Kill History", NamedTextColor.RED))
        skelMeta.lore(listOf(Component.text("Click to view your assassination records.", NamedTextColor.GRAY)))
        skeleton.itemMeta = skelMeta
        inv.setItem(16, skeleton)

        val anvil = ItemStack(Material.ANVIL)
        val anvilMeta = anvil.itemMeta
        anvilMeta.displayName(Component.text("Personal Settings", NamedTextColor.GOLD))
        anvilMeta.lore(listOf(Component.text("Toggle Infamy sounds and messages.", NamedTextColor.GRAY)))
        anvil.itemMeta = anvilMeta
        inv.setItem(22, anvil)

        sender.openInventory(inv)
    }

    private fun handleHistoryGui(sender: CommandSender) {
        if (sender !is Player) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Your Kill History", NamedTextColor.DARK_RED))
        val history = plugin.infamyManager.killHistory.filter { it.killer == sender.uniqueId }.sortedByDescending { it.timestamp }.take(54)
        history.forEachIndexed { index, record -> populateHistorySlot(inv, index, record, false) }
        sender.openInventory(inv)
    }

    private fun handleAdminHistoryGui(sender: CommandSender) {
        if (sender !is Player || !isAdmin(sender)) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Server Kill History", NamedTextColor.DARK_RED))
        val history = plugin.infamyManager.killHistory.sortedByDescending { it.timestamp }.take(54)
        history.forEachIndexed { index, record -> populateHistorySlot(inv, index, record, true) }
        sender.openInventory(inv)
    }

    private fun populateHistorySlot(inv: org.bukkit.inventory.Inventory, index: Int, record: KillRecord, showCoords: Boolean) {
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
        if (showCoords) {
            lore.add(Component.text("Coords: ${record.location}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        }
        lore.add(Component.text(" "))
        lore.add(Component.text("Bottle Status: ${record.status}", color).decoration(TextDecoration.ITALIC, false))

        meta.lore(lore)
        head.itemMeta = meta
        inv.setItem(index, head)
    }

    private fun handleTeamsGuiCommand(sender: CommandSender) {
        if (sender !is Player) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Registered Teams", NamedTextColor.DARK_RED))
        plugin.teamManager.teams.values.forEachIndexed { index, team ->
            if (index >= 54) return@forEachIndexed

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
                val tagComp = LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.infamyManager.getPrefixText(rep)).decoration(TextDecoration.ITALIC, false)
                val memberComp = if (offline.isOnline) {
                    Component.text("+ ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).append(Component.text(offline.name ?: "Unknown", NamedTextColor.WHITE)).append(Component.text(" | ", NamedTextColor.DARK_GRAY)).append(tagComp)
                } else {
                    Component.text("- ${offline.name ?: "Unknown"} | ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true).append(tagComp)
                }
                lore.add(memberComp)
            }
            meta.lore(lore)
            head.itemMeta = meta
            inv.setItem(index, head)
        }
        sender.openInventory(inv)
    }

    private fun handleTeamCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy team <create|disband|invite|accept|decline|leave|kick|list|chat|color|icon|leadership|coords> [args]", NamedTextColor.RED))

        when (args[1].lowercase()) {
            "create" -> {
                // FIXED: Boss CAN create teams now
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team create <name>", NamedTextColor.RED))
                if (plugin.teamManager.createTeam(sender, args[2])) sender.sendMessage(Component.text("Team '${args[2]}' created!", NamedTextColor.GREEN)) else sender.sendMessage(Component.text("You are already in a team, or that name is taken.", NamedTextColor.RED))
            }
            "disband" -> if (plugin.teamManager.disbandTeam(sender)) sender.sendMessage(Component.text("Team disbanded successfully.", NamedTextColor.GREEN)) else sender.sendMessage(Component.text("Only the team leader can disband the team.", NamedTextColor.RED))
            "invite" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team invite <player>", NamedTextColor.RED))
                val target = Bukkit.getPlayer(args[2]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                val myTeam = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team!", NamedTextColor.RED))

                // FIXED: Checks if team has a Boss, blocks if team already has 2 members
                val hasBoss = myTeam.members.any { plugin.infamyManager.getRawReputationByUUID(it) >= 21 }
                if (hasBoss && myTeam.members.size >= 2) return sender.sendMessage(Component.text("A team with the Boss cannot have more than 2 members!", NamedTextColor.RED))

                if (plugin.teamManager.sendInvite(myTeam.name, target.uniqueId)) {
                    sender.sendMessage(Component.text("Invite sent to ${target.name}!", NamedTextColor.GREEN))
                    target.sendMessage(Component.text("${sender.name} invited you to join '${myTeam.name}'! Use /infamy team accept", NamedTextColor.AQUA))
                } else sender.sendMessage(Component.text("${target.name} is already in a team!", NamedTextColor.RED))
            }
            "accept" -> {
                val targetTeam = plugin.teamManager.getPendingInviteTeam(sender.uniqueId)
                if (targetTeam == null) {
                    return sender.sendMessage(Component.text("No pending invites.", NamedTextColor.RED))
                }

                // FIXED: Block joining if the team has/gets a Boss and exceeds 2 members
                val isSenderBoss = plugin.infamyManager.getRawReputation(sender) >= 21
                val teamHasBoss = targetTeam.members.any { plugin.infamyManager.getRawReputationByUUID(it) >= 21 }

                if ((isSenderBoss || teamHasBoss) && targetTeam.members.size >= 2) {
                    return sender.sendMessage(Component.text("A team with the Boss cannot have more than 2 members!", NamedTextColor.RED))
                }

                if (plugin.teamManager.acceptInvite(sender)) sender.sendMessage(Component.text("You joined the team!", NamedTextColor.GREEN)) else sender.sendMessage(Component.text("No pending invites.", NamedTextColor.RED))
            }
            "decline" -> { plugin.teamManager.declineInvite(sender.uniqueId); sender.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW)) }
            "leave" -> plugin.teamManager.leaveTeam(sender)
            "kick" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team kick <player>", NamedTextColor.RED))
                val target = Bukkit.getOfflinePlayer(args[2])
                if (plugin.teamManager.kickTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(Component.text("Kicked ${target.name} from the team.", NamedTextColor.YELLOW))
                } else sender.sendMessage(Component.text("Cannot kick this player. (Are you the leader?)", NamedTextColor.RED))
            }
            "list" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                sender.sendMessage(Component.text("Team: ${team.name} | Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD))
                team.members.forEach { sender.sendMessage(Component.text("- ${Bukkit.getOfflinePlayer(it).name}", NamedTextColor.GREEN)) }
            }
            "chat" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (args.size == 2) {
                    if (plugin.teamManager.teamChatToggled.contains(sender.uniqueId)) {
                        plugin.teamManager.teamChatToggled.remove(sender.uniqueId)
                        sender.sendMessage(Component.text("Team chat disabled. Messages are now global.", NamedTextColor.YELLOW))
                    } else {
                        plugin.teamManager.teamChatToggled.add(sender.uniqueId)
                        sender.sendMessage(Component.text("Team chat enabled! All chat messages will route to your team.", NamedTextColor.GREEN))
                    }
                } else {
                    val msgText = args.drop(2).joinToString(" ")
                    plugin.teamManager.broadcastToTeam(team.name, "[Team] (${sender.name}) | $msgText", NamedTextColor.AQUA)
                }
            }
            "color" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (team.leader != sender.uniqueId) return sender.sendMessage(Component.text("Only the team leader can change the color.", NamedTextColor.RED))
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /infamy team color <color> [format...]", NamedTextColor.RED))
                    return
                }

                val colorInput = args[2].lowercase()
                val colorCode = colorMap[colorInput]
                if (colorCode == null) {
                    sender.sendMessage(Component.text("Invalid Color! Available: ${colorMap.keys.joinToString(", ")}", NamedTextColor.RED))
                    return
                }

                var formatCode = ""
                if (args.size > 3) {
                    for (i in 3 until args.size) {
                        val formatInput = args[i].lowercase()
                        val fCode = formatMap[formatInput]
                        if (fCode != null && !formatCode.contains(fCode)) {
                            formatCode += fCode
                        } else if (fCode == null) {
                            sender.sendMessage(Component.text("Invalid Format '$formatInput'! Available: ${formatMap.keys.joinToString(", ")}", NamedTextColor.RED))
                            return
                        }
                    }
                }

                team.colorFormat = colorCode + formatCode
                sender.sendMessage(Component.text("Team color & formatting successfully updated!", NamedTextColor.GREEN))
                team.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { plugin.infamyManager.updateTabList(it) }
                plugin.teamManager.syncAllScoreboards()
            }
            "icon" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (team.leader != sender.uniqueId) return sender.sendMessage(Component.text("Only the team leader can change the icon.", NamedTextColor.RED))

                if (args.size == 3 && args[2].lowercase() == "reset") {
                    team.icon = null
                    sender.sendMessage(Component.text("Team icon reset to leader's head.", NamedTextColor.GREEN))
                    return
                }

                val item = sender.inventory.itemInMainHand
                if (item.type.name.endsWith("_BANNER")) {
                    val iconItem = item.clone()
                    iconItem.amount = 1
                    team.icon = iconItem
                    sender.sendMessage(Component.text("Team banner icon updated successfully! Check the /infamy team GUI.", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("You must hold a Banner to set the team icon! Use '/infamy team icon reset' to clear it.", NamedTextColor.RED))
                }
            }
            "leadership" -> {
                val teamName = plugin.teamManager.playerTeams[sender.uniqueId] ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                val team = plugin.teamManager.teams[teamName] ?: return sender.sendMessage(Component.text("Error finding team.", NamedTextColor.RED))
                if (team.leader != sender.uniqueId) return sender.sendMessage(Component.text("Only the team leader can transfer leadership.", NamedTextColor.RED))
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team leadership <player>", NamedTextColor.RED))

                val target = Bukkit.getOfflinePlayer(args[2])
                if (!team.members.contains(target.uniqueId)) return sender.sendMessage(Component.text("That player is not in your team.", NamedTextColor.RED))

                team.leader = target.uniqueId
                plugin.teamManager.broadcastToTeam(teamName, "${target.name} has been promoted to Team Leader!", NamedTextColor.GOLD)
            }
            "coords" -> {
                if (!plugin.config.getBoolean("settings.team-easy-coord", true)) {
                    return sender.sendMessage(Component.text("This feature is disabled on the server.", NamedTextColor.RED))
                }
                if (args.size == 3) {
                    when(args[2].lowercase()) {
                        "toggle", "on", "off" -> {
                            if (plugin.teamManager.coordsScoreboardToggled.contains(sender.uniqueId)) {
                                plugin.teamManager.coordsScoreboardToggled.remove(sender.uniqueId)
                                sender.scoreboard.getObjective("teamCoords")?.unregister()
                                sender.sendMessage(Component.text("Team coords scoreboard disabled.", NamedTextColor.YELLOW))
                            } else {
                                plugin.teamManager.coordsScoreboardToggled.add(sender.uniqueId)
                                sender.sendMessage(Component.text("Team coords scoreboard enabled.", NamedTextColor.GREEN))
                            }
                            return
                        }
                    }
                }

                val teamName = plugin.teamManager.playerTeams[sender.uniqueId] ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                val team = plugin.teamManager.teams[teamName] ?: return sender.sendMessage(Component.text("Error finding team.", NamedTextColor.RED))

                sender.sendMessage(Component.text("=== ${team.name} Member Locations ===", NamedTextColor.GOLD))
                team.members.forEach { memberId ->
                    val member = Bukkit.getPlayer(memberId)
                    if (member != null) {
                        val loc = member.location
                        val dim = loc.world.name.replace("world_nether", "Nether").replace("world_the_end", "The End").replace("world", "Overworld")
                        val coordsStr = "${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"

                        var distStr = ""
                        if (member.world == sender.world) {
                            val dist = member.location.distance(sender.location).toInt()
                            distStr = " (${dist}m away)"
                        }

                        sender.sendMessage(Component.text("- ${member.name}: ", NamedTextColor.AQUA)
                            .append(Component.text("$dim | $coordsStr", NamedTextColor.WHITE))
                            .append(Component.text(distStr, NamedTextColor.GRAY)))
                    } else {
                        val offPlayer = Bukkit.getOfflinePlayer(memberId)
                        sender.sendMessage(Component.text("- ${offPlayer.name}: Offline", NamedTextColor.DARK_GRAY))
                    }
                }
            }
        }
    }

    private fun handleAddCommand(sender: CommandSender, args: Array<out String>) {
        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))
        if (args.size < 4) return sender.sendMessage(Component.text("Usage: /infamy add <player> <honor|infamy> <amount>", NamedTextColor.RED))

        val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
        val type = args[2].lowercase()
        val amount = args[3].toIntOrNull() ?: return sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED))
        if (type != "infamy" && type != "honor") return sender.sendMessage(Component.text("You must specify either 'honor' or 'infamy'.", NamedTextColor.RED))

        val currentRep = plugin.infamyManager.getRawReputation(target)
        val maxAllowed = if (type == "infamy") { if (plugin.infamyManager.currentBoss != null && plugin.infamyManager.currentBoss != target.uniqueId) 20 else 21 } else -12
        val desiredRep = if (type == "honor") currentRep - amount else currentRep + amount
        val actualRep = if (type == "honor") desiredRep.coerceAtLeast(maxAllowed) else desiredRep.coerceAtMost(maxAllowed)

        val applied = kotlin.math.abs(actualRep - currentRep)
        val disregarded = amount - applied

        plugin.infamyManager.setReputation(target, actualRep)

        if (disregarded > 0) sender.sendMessage(Component.text("Applied $applied point(s). Disregarded $disregarded due to max limit.", NamedTextColor.YELLOW))
        else sender.sendMessage(Component.text("Successfully updated ${target.name}'s points by $applied.", NamedTextColor.GREEN))
    }

    private fun handleLevelCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size == 1 && sender is Player) {
            val rep = plugin.infamyManager.getRawReputation(sender)
            val msg = if (rep > 0) "$rep Infamy Level." else if (rep < 0) "${-rep} Honor Level." else "Level 0 (Neutral)."
            return sender.sendMessage(Component.text("You are $msg", NamedTextColor.YELLOW))
        }
        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))

        val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
        if (args.size == 3 && args[2].lowercase() == "reset") {
            plugin.infamyManager.resetReputation(target)
            return sender.sendMessage(Component.text("Reset ${target.name}'s level.", NamedTextColor.GREEN))
        }
        sender.sendMessage(Component.text("${target.name} is Level ${plugin.infamyManager.getRawReputation(target)}.", NamedTextColor.YELLOW))
    }

    private fun handleWithdrawCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        val isAdmin = isAdmin(sender)

        if (!isAdmin && !plugin.config.getBoolean("settings.allow-withdraw", true)) {
            return sender.sendMessage(Component.text("Withdrawing points is disabled.", NamedTextColor.RED))
        }
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy withdraw <amount>", NamedTextColor.RED))

        val amount = args[1].toIntOrNull() ?: return sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
        val currentRep = plugin.infamyManager.getRawReputation(sender)

        if (currentRep == 0) return sender.sendMessage(Component.text("You have 0 points to withdraw!", NamedTextColor.RED))

        if (currentRep > 0) {
            if (!isAdmin && !plugin.config.getBoolean("settings.allow-withdraw-infamy", true)) {
                return sender.sendMessage(Component.text("Infamy withdrawals are currently disabled by the server.", NamedTextColor.RED))
            }
            if (amount > currentRep) return sender.sendMessage(Component.text("You don't have enough Infamy points!", NamedTextColor.RED))

            plugin.infamyManager.withdrawnPoints[sender.uniqueId] = (plugin.infamyManager.withdrawnPoints[sender.uniqueId] ?: 0) + amount

            if (currentRep == 21) {
                plugin.infamyManager.setReputation(sender, currentRep - amount)
                sender.inventory.addItem(plugin.itemManager.createPureInfamyBottle(sender.name, "Withdrawn")).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }
                if (amount > 1) {
                    sender.inventory.addItem(plugin.itemManager.createInfamyBottle(amount - 1, sender.name, sender.uniqueId.toString())).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }
                }
                sender.sendMessage(Component.text("You withdrew points and extracted the Pure Infamy Bottle!", NamedTextColor.GREEN))
            } else {
                plugin.infamyManager.setReputation(sender, currentRep - amount)
                sender.inventory.addItem(plugin.itemManager.createInfamyBottle(amount, sender.name, sender.uniqueId.toString())).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }
                sender.sendMessage(Component.text("Successfully withdrew $amount Infamy point(s)!", NamedTextColor.GREEN))
            }
        } else {
            if (!isAdmin && !plugin.config.getBoolean("settings.allow-withdraw-honor", true)) {
                return sender.sendMessage(Component.text("Honor withdrawals are currently disabled by the server.", NamedTextColor.RED))
            }
            val absoluteHonor = -currentRep
            if (amount > absoluteHonor) return sender.sendMessage(Component.text("You don't have enough Honor points!", NamedTextColor.RED))

            plugin.infamyManager.setReputation(sender, currentRep + amount)
            for(i in 1..amount) {
                sender.inventory.addItem(plugin.itemManager.createHonorBottle()).values.forEach { sender.world.dropItemNaturally(sender.location, it) }
            }
            sender.sendMessage(Component.text("Successfully withdrew $amount Honor point(s)!", NamedTextColor.AQUA))
        }
    }

    private fun handleBottleCommand(sender: CommandSender, args: Array<out String>) {
        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy bottle <honor|infamy|pure> [player] [level] [count]", NamedTextColor.RED))

        val type = args[1].lowercase()
        if (type !in listOf("honor", "infamy", "pure")) return sender.sendMessage(Component.text("Type must be honor, infamy, or pure.", NamedTextColor.RED))

        val targetName = if (args.size > 2) args[2] else sender.name
        val target = Bukkit.getPlayer(targetName) ?: return sender.sendMessage(Component.text("Player '$targetName' not found.", NamedTextColor.RED))

        val level = if (args.size > 3) args[3].toIntOrNull() ?: 1 else 1
        val count = if (args.size > 4) args[4].toIntOrNull() ?: 1 else 1

        var given = 0
        for (i in 1..count) {
            val bottle = when (type) {
                "pure" -> plugin.itemManager.createPureInfamyBottle("Admin Spawned", sender.name)
                "honor" -> plugin.itemManager.createHonorBottle()
                else -> plugin.itemManager.createInfamyBottle(level, "Admin Spawned", null)
            }
            target.inventory.addItem(bottle).values.forEach { target.world.dropItemNaturally(target.location, it) }
            given++
        }
        sender.sendMessage(Component.text("Given $given $type bottle(s) to ${target.name}.", NamedTextColor.GREEN))
    }
}