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

class InfamyCommand(private val plugin: InfamySMP) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) handleInfoGui(sender)
            else sender.sendMessage(Component.text("Usage: /infamy <info|team|level|withdraw|pureinfamybottle|history|add> ...", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "info" -> handleInfoGui(sender)
            "team" -> {
                if (args.size == 1 && sender is Player) handleTeamsGuiCommand(sender)
                else handleTeamCommand(sender, args)
            }
            "add" -> handleAddCommand(sender, args)
            "level" -> handleLevelCommand(sender, args)
            "withdraw" -> handleWithdrawCommand(sender, args)
            "pureinfamybottle" -> handlePureBottleCommand(sender, args)
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
            val subs = mutableListOf("team", "level", "withdraw", "history", "info")
            if (sender.hasPermission("infamysmp.admin")) subs.addAll(listOf("add", "pureinfamybottle"))
            completions.addAll(subs.filter { it.startsWith(args[0].lowercase()) })
        } else if (args.size == 2) {
            when (args[0].lowercase()) {
                "team" -> completions.addAll(listOf("create", "disband", "invite", "accept", "decline", "leave", "kick", "list").filter { it.startsWith(args[1].lowercase()) })
                "add", "pureinfamybottle", "level" -> if (sender.hasPermission("infamysmp.admin")) completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) })
                "history" -> if (sender.hasPermission("infamysmp.admin") && "admin".startsWith(args[1].lowercase())) completions.add("admin")
            }
        } else if (args.size == 3) {
            if (args[0].lowercase() == "add" && sender.hasPermission("infamysmp.admin")) {
                completions.addAll(listOf("infamy", "honor").filter { it.startsWith(args[2].lowercase()) })
            } else if (args[0].lowercase() == "level" && sender.hasPermission("infamysmp.admin")) {
                completions.addAll(listOf("reset").filter { it.startsWith(args[2].lowercase()) })
            }
        } else if (args.size == 4) {
            if (args[0].lowercase() == "add" && sender.hasPermission("infamysmp.admin")) {
                completions.addAll(listOf("1", "5", "10", "20").filter { it.startsWith(args[3]) })
            }
        }
        return completions
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
        val teamName = team?.name ?: "None"

        meta.lore(listOf(
            levelComp,
            tagComp,
            Component.text("Playtime: ${hours}h ${minutes}m", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Team: $teamName", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
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
        if (team != null) {
            shieldMeta.addEnchant(Enchantment.UNBREAKING, 1, true)
            shieldMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
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

        sender.openInventory(inv)
    }

    private fun handleHistoryGui(sender: CommandSender) {
        if (sender !is Player) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Your Kill History", NamedTextColor.DARK_RED))
        val history = plugin.infamyManager.killHistory.filter { it.killer == sender.uniqueId }.sortedByDescending { it.timestamp }.take(54)

        history.forEachIndexed { index, record -> populateHistorySlot(inv, index, record) }
        sender.openInventory(inv)
    }

    private fun handleAdminHistoryGui(sender: CommandSender) {
        if (sender !is Player || !sender.hasPermission("infamysmp.admin")) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Server Kill History", NamedTextColor.DARK_RED))
        val history = plugin.infamyManager.killHistory.sortedByDescending { it.timestamp }.take(54)

        history.forEachIndexed { index, record -> populateHistorySlot(inv, index, record) }
        sender.openInventory(inv)
    }

    private fun populateHistorySlot(inv: org.bukkit.inventory.Inventory, index: Int, record: KillRecord) {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta
        meta.owningPlayer = Bukkit.getOfflinePlayer(record.victim)
        meta.displayName(Component.text("Victim: ${record.victimName}", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))

        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm").format(Date(record.timestamp))

        val redeemStatus: String
        val redeemColor: NamedTextColor

        if (record.redeemed) {
            if (record.redeemedBy != null && record.redeemedBy != record.killer) {
                redeemStatus = "Stolen by ${record.redeemedByName}"
                redeemColor = NamedTextColor.DARK_PURPLE
            } else {
                redeemStatus = "Consumed"
                redeemColor = NamedTextColor.GRAY
            }
        } else {
            redeemStatus = "Unredeemed"
            redeemColor = NamedTextColor.GREEN
        }

        meta.lore(listOf(
            Component.text("Killed By: ${record.killerName}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
            Component.text("Date: $dateStr", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Coords: ${record.location}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(" "),
            Component.text("Bottle Status: $redeemStatus", redeemColor).decoration(TextDecoration.ITALIC, false)
        ))
        head.itemMeta = meta
        inv.setItem(index, head)
    }

    private fun handleTeamsGuiCommand(sender: CommandSender) {
        if (sender !is Player) return
        val inv = Bukkit.createInventory(null, 54, Component.text("Registered Teams", NamedTextColor.DARK_RED))

        plugin.teamManager.teams.values.forEachIndexed { index, team ->
            if (index >= 54) return@forEachIndexed
            val head = ItemStack(Material.PLAYER_HEAD)
            val meta = head.itemMeta as SkullMeta
            meta.owningPlayer = Bukkit.getOfflinePlayer(team.leader)
            meta.displayName(Component.text("Team: ${team.name}", NamedTextColor.RED))

            val lore = mutableListOf<Component>()
            lore.add(Component.text("Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("Members (${team.members.size}):", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))

            team.members.forEach { uuid ->
                val offline = Bukkit.getOfflinePlayer(uuid)
                val isOnline = offline.isOnline
                val rep = plugin.infamyManager.getRawReputationByUUID(uuid)
                val tagRaw = plugin.infamyManager.getPrefixText(rep)
                val tagComp = LegacyComponentSerializer.legacyAmpersand().deserialize(tagRaw).decoration(TextDecoration.ITALIC, false)

                val memberComp = if (isOnline) {
                    Component.text("+ ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(offline.name ?: "Unknown", NamedTextColor.WHITE))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(tagComp)
                } else {
                    Component.text("- ${offline.name ?: "Unknown"} | ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true)
                        .append(tagComp)
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
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy team <create|disband|invite|accept|decline|leave|kick|list> [args]", NamedTextColor.RED))

        val action = args[1].lowercase()
        when (action) {
            "create" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team create <name>", NamedTextColor.RED))
                if (plugin.teamManager.createTeam(sender, args[2])) {
                    sender.sendMessage(Component.text("Team '${args[2]}' created!", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("You are already in a team, or that name is taken.", NamedTextColor.RED))
                }
            }
            "disband" -> {
                if (plugin.teamManager.disbandTeam(sender)) {
                    sender.sendMessage(Component.text("Team disbanded successfully.", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("Only the team leader can disband the team.", NamedTextColor.RED))
                }
            }
            "invite" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team invite <player>", NamedTextColor.RED))
                val target = Bukkit.getPlayer(args[2]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                val myTeam = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team!", NamedTextColor.RED))

                if (plugin.infamyManager.getRawReputation(sender) >= 21 && myTeam.members.size >= 2) {
                    return sender.sendMessage(Component.text("As the Boss, you cannot have more than 1 teammate!", NamedTextColor.RED))
                }

                if (plugin.teamManager.sendInvite(myTeam.name, target.uniqueId)) {
                    sender.sendMessage(Component.text("Invite sent to ${target.name}!", NamedTextColor.GREEN))
                    target.sendMessage(Component.text("${sender.name} invited you to join '${myTeam.name}'! Use /infamy team accept", NamedTextColor.AQUA))
                } else {
                    sender.sendMessage(Component.text("${target.name} is already in a team!", NamedTextColor.RED))
                }
            }
            "accept" -> {
                if (plugin.teamManager.acceptInvite(sender)) sender.sendMessage(Component.text("You joined the team!", NamedTextColor.GREEN))
                else sender.sendMessage(Component.text("No pending invites.", NamedTextColor.RED))
            }
            "decline" -> {
                plugin.teamManager.declineInvite(sender.uniqueId)
                sender.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW))
            }
            "leave" -> {
                plugin.teamManager.leaveTeam(sender)
            }
            "kick" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team kick <player>", NamedTextColor.RED))
                val target = Bukkit.getOfflinePlayer(args[2])
                if (plugin.teamManager.kickTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(Component.text("Kicked ${target.name} from the team.", NamedTextColor.YELLOW))
                    Bukkit.getPlayer(target.uniqueId)?.sendMessage(Component.text("You were kicked from the team.", NamedTextColor.RED))
                } else {
                    sender.sendMessage(Component.text("Cannot kick this player. (Are you the leader?)", NamedTextColor.RED))
                }
            }
            "list" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                sender.sendMessage(Component.text("Team: ${team.name} | Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD))
                team.members.forEach { uuid ->
                    sender.sendMessage(Component.text("- ${Bukkit.getOfflinePlayer(uuid).name}", NamedTextColor.GREEN))
                }
            }
        }
    }

    private fun handleAddCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("infamysmp.admin")) return sender.sendMessage(Component.text("You don't have permission to do this.", NamedTextColor.RED))
        if (args.size < 4) return sender.sendMessage(Component.text("Usage: /infamy add <player> <honor|infamy> <amount>", NamedTextColor.RED))

        val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
        val type = args[2].lowercase()
        val amount = args[3].toIntOrNull() ?: return sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED))
        if (type != "infamy" && type != "honor") return sender.sendMessage(Component.text("You must specify either 'honor' or 'infamy'.", NamedTextColor.RED))

        val currentRep = plugin.infamyManager.getRawReputation(target)
        val maxAllowed = if (type == "infamy") {
            if (plugin.infamyManager.currentBoss != null && plugin.infamyManager.currentBoss != target.uniqueId) 20 else 21
        } else -12

        val desiredRep = if (type == "honor") currentRep - amount else currentRep + amount
        val actualRep = if (type == "honor") desiredRep.coerceAtLeast(maxAllowed) else desiredRep.coerceAtMost(maxAllowed)

        val applied = kotlin.math.abs(actualRep - currentRep)
        val disregarded = amount - applied

        plugin.infamyManager.setReputation(target, actualRep)

        if (disregarded > 0) {
            sender.sendMessage(Component.text("Applied $applied point(s). Disregarded $disregarded due to max limit.", NamedTextColor.YELLOW))
        } else {
            sender.sendMessage(Component.text("Successfully updated ${target.name}'s points by $applied.", NamedTextColor.GREEN))
        }
    }

    private fun handleLevelCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size == 1 && sender is Player) {
            val rep = plugin.infamyManager.getRawReputation(sender)
            val msg = if (rep > 0) "$rep Infamy Level." else if (rep < 0) "${-rep} Honor Level." else "Level 0 (Neutral)."
            return sender.sendMessage(Component.text("You are $msg", NamedTextColor.YELLOW))
        }
        if (!sender.hasPermission("infamysmp.admin")) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))

        val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
        if (args.size == 3 && args[2].lowercase() == "reset") {
            plugin.infamyManager.resetReputation(target)
            return sender.sendMessage(Component.text("Reset ${target.name}'s level.", NamedTextColor.GREEN))
        }
        sender.sendMessage(Component.text("${target.name} is Level ${plugin.infamyManager.getRawReputation(target)}.", NamedTextColor.YELLOW))
    }

    private fun handleWithdrawCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (!plugin.config.getBoolean("settings.allow-withdraw", true)) return sender.sendMessage(Component.text("Withdrawing points is disabled.", NamedTextColor.RED))
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy withdraw <amount>", NamedTextColor.RED))

        val amount = args[1].toIntOrNull() ?: return sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
        val currentRep = plugin.infamyManager.getRawReputation(sender)

        if (currentRep <= 0 || amount > currentRep) return sender.sendMessage(Component.text("You don't have enough Infamy points!", NamedTextColor.RED))

        plugin.infamyManager.withdrawnPoints[sender.uniqueId] = (plugin.infamyManager.withdrawnPoints[sender.uniqueId] ?: 0) + amount

        if (currentRep == 21) {
            plugin.infamyManager.setReputation(sender, currentRep - amount)
            val pureBottle = plugin.itemManager.createPureInfamyBottle(sender.name, "Withdrawn")
            sender.inventory.addItem(pureBottle).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }

            if (amount > 1) {
                val normalBottle = plugin.itemManager.createInfamyBottle(amount - 1, sender.name, sender.uniqueId.toString())
                sender.inventory.addItem(normalBottle).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }
            }
            sender.sendMessage(Component.text("You withdrew points and extracted the Pure Infamy Bottle!", NamedTextColor.GREEN))
        } else {
            plugin.infamyManager.setReputation(sender, currentRep - amount)
            val bottle = plugin.itemManager.createInfamyBottle(amount, sender.name, sender.uniqueId.toString())
            sender.inventory.addItem(bottle).values.forEach { sender.world.dropItemNaturally(sender.location, it).isInvulnerable = true }
            sender.sendMessage(Component.text("Successfully withdrew $amount Infamy point(s)!", NamedTextColor.GREEN))
        }
    }

    private fun handlePureBottleCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("infamysmp.admin")) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy pureinfamybottle <player>", NamedTextColor.RED))

        val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
        val bottle = plugin.itemManager.createPureInfamyBottle("Admin Spawned", sender.name)
        target.inventory.addItem(bottle).values.forEach { target.world.dropItemNaturally(target.location, it).isInvulnerable = true }
        sender.sendMessage(Component.text("Gave 1 Pure Infamy Bottle to ${target.name}.", NamedTextColor.GREEN))
    }
}