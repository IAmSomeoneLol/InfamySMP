// Command processing class. Connected to plugin.yml and TeamManager.kt.
package com.enxivity.infamy.commands

import com.enxivity.infamy.InfamySMP
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TeamCommand(private val plugin: InfamySMP) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /team <add|remove|list> [player]", NamedTextColor.RED))
            return true
        }

        val action = args[0].lowercase()
        val targetName = if (args.size > 1) args[1] else null

        when (action) {
            "add" -> {
                if (targetName == null) return true
                val target = Bukkit.getPlayer(targetName) ?: return true

                if (target.uniqueId == sender.uniqueId) return true

                // BOSS TEAM LIMIT CHECK: Prevent adding if they have 21 infamy and already have 1 teammate
                val senderRep = plugin.infamyManager.getRawReputation(sender)
                if (senderRep >= 21 && plugin.teamManager.getTeammates(sender.uniqueId).isNotEmpty()) {
                    sender.sendMessage(Component.text("As the Most Infamous Player, you cannot have more than 1 teammate!", NamedTextColor.RED))
                    return true
                }

                plugin.teamManager.addTeammate(sender.uniqueId, target.uniqueId)
                sender.sendMessage(Component.text("You are now teamed with ${target.name}!", NamedTextColor.GREEN))
                target.sendMessage(Component.text("You are now teamed with ${sender.name}!", NamedTextColor.GREEN))
            }
            "remove" -> {
                if (targetName == null) return true
                val target = Bukkit.getOfflinePlayer(targetName)
                plugin.teamManager.removeTeammate(sender.uniqueId, target.uniqueId)
                sender.sendMessage(Component.text("Removed ${target.name} from your team.", NamedTextColor.YELLOW))
            }
            "list" -> {
                val teammates = plugin.teamManager.getTeammates(sender.uniqueId)
                sender.sendMessage(Component.text("Your Teammates: ", NamedTextColor.GOLD))
                teammates.forEach { uuid ->
                    val name = Bukkit.getOfflinePlayer(uuid).name ?: "Unknown"
                    sender.sendMessage(Component.text("- $name", NamedTextColor.GREEN))
                }
            }
        }
        return true
    }
}