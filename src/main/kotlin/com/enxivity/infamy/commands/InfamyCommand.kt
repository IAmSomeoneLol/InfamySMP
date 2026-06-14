package com.enxivity.infamy.commands

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class InfamyCommand(private val plugin: InfamySMP) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /infamy <team|add|points|withdraw|givebossbottle> ...", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "team" -> handleTeamCommand(sender, args)
            "add" -> handleAddCommand(sender, args)
            "points" -> handlePointsCommand(sender, args)
            "withdraw" -> handleWithdrawCommand(sender, args)
            "givebossbottle" -> handleGiveBossBottleCommand(sender, args)
            else -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED))
        }
        return true
    }

    private fun handleWithdrawCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can withdraw points.", NamedTextColor.RED))
            return
        }

        if (!plugin.config.getBoolean("settings.allow-withdraw", true)) {
            sender.sendMessage(Component.text("Withdrawing points is disabled on this server.", NamedTextColor.RED))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /infamy withdraw <amount>", NamedTextColor.RED))
            return
        }

        val amount = args[1].toIntOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage(Component.text("Please specify a valid point value.", NamedTextColor.RED))
            return
        }

        val currentRep = plugin.infamyManager.getRawReputation(sender)
        if (currentRep <= 0) {
            sender.sendMessage(Component.text("You do not have any Infamy points to extract! (Negative Honor points cannot be withdrawn)", NamedTextColor.RED))
            return
        }

        if (amount > currentRep) {
            sender.sendMessage(Component.text("You don't have enough points! You only have $currentRep Infamy points.", NamedTextColor.RED))
            return
        }

        // Deduct points and generate custom model bottle
        val newRep = currentRep - amount
        plugin.infamyManager.setReputation(sender, newRep)

        val bottle = plugin.itemManager.createInfamyBottle(amount)
        val leftover = sender.inventory.addItem(bottle)

        // Safety drop if layout is entirely full
        leftover.values.forEach {
            val dropped = sender.world.dropItemNaturally(sender.location, it)
            dropped.isInvulnerable = true
        }

        sender.sendMessage(Component.text("Successfully withdrew $amount Infamy point(s) into a bottle!", NamedTextColor.GREEN))
    }

    private fun handleGiveBossBottleCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("infamysmp.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do this.", NamedTextColor.RED))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /infamy givebossbottle <player>", NamedTextColor.RED))
            return
        }

        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
            return
        }

        val bottle = plugin.itemManager.createBossBottle()
        val leftover = target.inventory.addItem(bottle)
        leftover.values.forEach {
            val dropped = target.world.dropItemNaturally(target.location, it)
            dropped.isInvulnerable = true
        }

        sender.sendMessage(Component.text("Gave 1 Core of Infamy to ${target.name}.", NamedTextColor.GREEN))
        target.sendMessage(Component.text("You have been granted a Core of Infamy by an administrator!", NamedTextColor.GOLD))
    }

    private fun handleTeamCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /infamy team <invite|kick|leave|list|accept|decline> [player]", NamedTextColor.RED))
            return
        }

        val action = args[1].lowercase()
        val targetName = if (args.size > 2) args[2] else null

        when (action) {
            "invite" -> {
                if (targetName == null) return
                val target = Bukkit.getPlayer(targetName) ?: return
                if (target.uniqueId == sender.uniqueId) return

                val senderRep = plugin.infamyManager.getRawReputation(sender)
                if (senderRep >= 21 && plugin.teamManager.getTeammates(sender.uniqueId).isNotEmpty()) {
                    sender.sendMessage(Component.text("As the Boss, you cannot have more than 1 teammate!", NamedTextColor.RED))
                    return
                }

                if (plugin.teamManager.sendInvite(sender.uniqueId, target.uniqueId)) {
                    sender.sendMessage(Component.text("Invite sent to ${target.name}!", NamedTextColor.GREEN))
                    target.sendMessage(Component.text("${sender.name} invited you to their team! Use /infamy team accept ${sender.name}", NamedTextColor.AQUA))
                } else {
                    sender.sendMessage(Component.text("${target.name} is already in a team!", NamedTextColor.RED))
                }
            }
            "accept" -> {
                if (plugin.teamManager.acceptInvite(sender.uniqueId)) {
                    sender.sendMessage(Component.text("You joined the team!", NamedTextColor.GREEN))
                } else {
                    sender.sendMessage(Component.text("You have no pending invites.", NamedTextColor.RED))
                }
            }
            "decline" -> {
                if (plugin.teamManager.declineInvite(sender.uniqueId)) {
                    sender.sendMessage(Component.text("Invite declined.", NamedTextColor.YELLOW))
                }
            }
            "leave" -> {
                plugin.teamManager.leaveTeam(sender.uniqueId)
                sender.sendMessage(Component.text("You left your team.", NamedTextColor.YELLOW))
            }
            "kick" -> {
                if (targetName == null) return
                val target = Bukkit.getOfflinePlayer(targetName)
                plugin.teamManager.removeTeammate(sender.uniqueId, target.uniqueId)
                sender.sendMessage(Component.text("You kicked ${target.name} from the team.", NamedTextColor.YELLOW))
                val onlineTarget = Bukkit.getPlayer(target.uniqueId)
                onlineTarget?.sendMessage(Component.text("You were kicked from the team by ${sender.name}.", NamedTextColor.RED))
            }
            "list" -> {
                val teammates = plugin.teamManager.getTeammates(sender.uniqueId)
                sender.sendMessage(Component.text("Your Teammates: ", NamedTextColor.GOLD))
                teammates.forEach { uuid ->
                    sender.sendMessage(Component.text("- ${Bukkit.getOfflinePlayer(uuid).name}", NamedTextColor.GREEN))
                }
            }
        }
    }

    private fun handleAddCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("infamysmp.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do this.", NamedTextColor.RED))
            return
        }
        if (args.size < 4) {
            sender.sendMessage(Component.text("Usage: /infamy add <player> <honor|infamy> <amount>", NamedTextColor.RED))
            return
        }

        val target = Bukkit.getPlayer(args[1]) ?: return
        val type = args[2].lowercase()
        val amount = args[3].toIntOrNull() ?: return

        val currentRep = plugin.infamyManager.getRawReputation(target)
        val newRep = if (type == "honor") {
            currentRep - amount
        } else {
            currentRep + amount
        }

        plugin.infamyManager.setReputation(target, newRep)
        sender.sendMessage(Component.text("Successfully updated ${target.name}'s points.", NamedTextColor.GREEN))
    }

    private fun handlePointsCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size == 1 && sender is Player) {
            val rep = plugin.infamyManager.getRawReputation(sender)
            if (rep > 0) {
                sender.sendMessage(Component.text("You have $rep Infamy points.", NamedTextColor.RED))
            } else if (rep < 0) {
                sender.sendMessage(Component.text("You have ${-rep} Honor points.", NamedTextColor.AQUA))
            } else {
                sender.sendMessage(Component.text("You have 0 points (Neutral).", NamedTextColor.GREEN))
            }
            return
        }

        if (!sender.hasPermission("infamysmp.admin")) {
            sender.sendMessage(Component.text("You don't have permission to check or reset others' points.", NamedTextColor.RED))
            return
        }

        val target = Bukkit.getPlayer(args[1]) ?: return

        if (args.size == 3 && args[2].lowercase() == "reset") {
            plugin.infamyManager.resetReputation(target)
            sender.sendMessage(Component.text("Reset ${target.name}'s points to 0.", NamedTextColor.GREEN))
            return
        }

        val rep = plugin.infamyManager.getRawReputation(target)
        val label = if (rep > 0) "Infamy" else if (rep < 0) "Honor" else "Neutral"
        sender.sendMessage(Component.text("${target.name} has ${kotlin.math.abs(rep)} points - $label.", NamedTextColor.YELLOW))
    }
}