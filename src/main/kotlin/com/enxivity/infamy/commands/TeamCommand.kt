package com.enxivity.infamy.commands

import com.enxivity.infamy.InfamySMP
import com.enxivity.infamy.TeamColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class TeamCommand(private val plugin: InfamySMP) : CommandExecutor, TabCompleter {

    private val formatMap = mapOf(
        "bold" to "&l", "italic" to "&o", "underline" to "&n", "strikethrough" to "&m"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can execute team commands.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            plugin.itemRestrictionsListener.openTeamsGui(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label create <name>", NamedTextColor.RED)).let { true }
                val teamName = args[1]
                if (teamName.contains("&") || teamName.contains("§")) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.invalid-name")).let { true }
                }
                if (plugin.teamManager.createTeam(sender, teamName)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.created", "team" to teamName))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.create-failed"))
                }
            }
            "rename" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label rename <new_name>", NamedTextColor.RED)).let { true }
                val newName = args[1]
                if (newName.contains("&") || newName.contains("§")) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.invalid-name")).let { true }
                }
                if (!plugin.teamManager.renameTeam(sender, newName)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.rename-failed"))
                }
            }
            "disband" -> {
                if (plugin.teamManager.disbandTeam(sender)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.disbanded"))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.disband-failed"))
                }
            }
            "invite" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label invite <player>", NamedTextColor.RED)).let { true }
                val target = Bukkit.getPlayer(args[1]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)).let { true }
                val myTeam = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }

                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, myTeam.name)) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.invite-no-permission")).let { true }
                }

                if (plugin.teamManager.isBannedFromTeam(target.uniqueId, myTeam.name)) {
                    val remaining = plugin.teamManager.getRemainingBanTime(target.uniqueId, myTeam.name)
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.player-banned", "time" to remaining)).let { true }
                }

                val hasBoss = myTeam.members.any { plugin.infamyManager.getRawReputationByUUID(it) >= 21 }
                if (hasBoss && myTeam.members.size >= 2) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.boss-team-limit")).let { true }
                }

                if (plugin.teamManager.sendInvite(myTeam.name, target.uniqueId)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.invite-sent", "player" to target.name))
                    target.sendMessage(plugin.messagesManager.getComponent("teams.invite-received", "player" to sender.name, "team" to myTeam.name))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.already-in-team", "player" to target.name))
                }
            }
            "accept" -> {
                val targetTeam = plugin.teamManager.getPendingInviteTeam(sender.uniqueId)
                if (targetTeam == null) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.no-pending-invites")).let { true }
                }

                if (plugin.teamManager.isBannedFromTeam(sender.uniqueId, targetTeam.name)) {
                    val remaining = plugin.teamManager.getRemainingBanTime(sender.uniqueId, targetTeam.name)
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.you-are-banned", "time" to remaining)).let { true }
                }

                val isSenderBoss = plugin.infamyManager.getRawReputation(sender) >= 21
                val teamHasBoss = targetTeam.members.any { plugin.infamyManager.getRawReputationByUUID(it) >= 21 }

                if ((isSenderBoss || teamHasBoss) && targetTeam.members.size >= 2) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.boss-team-limit")).let { true }
                }

                if (plugin.teamManager.acceptInvite(sender)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.joined"))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.no-pending-invites"))
                }
            }
            "decline" -> {
                plugin.teamManager.declineInvite(sender.uniqueId)
                sender.sendMessage(plugin.messagesManager.getComponent("teams.invite-declined"))
            }
            "leave" -> plugin.teamManager.leaveTeam(sender)
            "kick" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label kick <player>", NamedTextColor.RED)).let { true }
                val target = Bukkit.getOfflinePlayer(args[1])
                if (plugin.teamManager.kickTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.kicked", "player" to (target.name ?: "Unknown")))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.kick-failed"))
                }
            }
            "promote" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label promote <player>", NamedTextColor.RED)).let { true }
                val target = Bukkit.getOfflinePlayer(args[1])
                if (plugin.teamManager.promoteTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.promoted", "player" to (target.name ?: "Unknown")))
                } else {
                    sender.sendMessage(Component.text("Cannot promote this player. (Only leader can promote)", NamedTextColor.RED))
                }
            }
            "demote" -> {
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label demote <player>", NamedTextColor.RED)).let { true }
                val target = Bukkit.getOfflinePlayer(args[1])
                if (plugin.teamManager.demoteTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.demoted", "player" to (target.name ?: "Unknown")))
                } else {
                    sender.sendMessage(Component.text("Cannot demote this player. (Only leader can demote)", NamedTextColor.RED))
                }
            }
            "list" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }
                sender.sendMessage(Component.text("Team: ${team.name} | Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD))
                team.members.forEach {
                    val role = if (team.leader == it) " [Leader]" else if (team.officers.contains(it)) " [Officer]" else ""
                    sender.sendMessage(Component.text("- ${Bukkit.getOfflinePlayer(it).name}$role", NamedTextColor.GREEN))
                }
            }
            "chat" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team-chat")).let { true }
                if (args.size == 1) {
                    if (plugin.teamManager.teamChatToggled.contains(sender.uniqueId)) {
                        plugin.teamManager.teamChatToggled.remove(sender.uniqueId)
                        sender.sendMessage(plugin.messagesManager.getComponent("teams.chat-disabled"))
                    } else {
                        plugin.teamManager.teamChatToggled.add(sender.uniqueId)
                        sender.sendMessage(plugin.messagesManager.getComponent("teams.chat-enabled"))
                    }
                } else {
                    val msgText = args.drop(1).joinToString(" ")
                    plugin.teamManager.sendTeamChat(team, sender, msgText)
                }
            }
            "color" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }
                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, team.name)) {
                    return sender.sendMessage(Component.text("Only the team leader or officers can change the color.", NamedTextColor.RED)).let { true }
                }
                if (args.size < 2) {
                    return sender.sendMessage(Component.text("Usage: /$label color <color> [format...]", NamedTextColor.RED)).let { true }
                }

                val colorInput = args[1].lowercase()
                val colorCode = TeamColor.getLegacyFormat(colorInput)
                if (colorCode == null) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.invalid-color")).let { true }
                }

                var formatCode = ""
                if (args.size > 2) {
                    for (i in 2 until args.size) {
                        val formatInput = args[i].lowercase()
                        val fCode = formatMap[formatInput]
                        if (fCode != null && !formatCode.contains(fCode)) {
                            formatCode += fCode
                        } else if (fCode == null) {
                            return sender.sendMessage(plugin.messagesManager.getComponent("teams.invalid-format", "format" to formatInput)).let { true }
                        }
                    }
                }

                team.colorFormat = colorCode + formatCode
                plugin.teamManager.saveData()
                sender.sendMessage(plugin.messagesManager.getComponent("teams.color-updated"))
                team.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { plugin.infamyManager.updateTabList(it) }
                plugin.teamManager.syncAllScoreboards()
            }
            "icon" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }
                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, team.name)) {
                    return sender.sendMessage(Component.text("Only the team leader or officers can change the icon.", NamedTextColor.RED)).let { true }
                }

                if (args.size >= 2 && args[1].lowercase() == "reset") {
                    team.icon = null
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.icon-reset")).let { true }
                }

                val item = sender.inventory.itemInMainHand
                if (item.type.name.endsWith("_BANNER")) {
                    val iconItem = item.clone()
                    iconItem.amount = 1
                    team.icon = iconItem
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.icon-updated"))
                } else {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.icon-must-hold-banner"))
                }
            }
            "leadership" -> {
                val teamName = plugin.teamManager.playerTeams[sender.uniqueId] ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }
                val team = plugin.teamManager.teams[teamName] ?: return sender.sendMessage(Component.text("Error finding team.", NamedTextColor.RED)).let { true }
                if (team.leader != sender.uniqueId) {
                    return sender.sendMessage(Component.text("Only the team leader can transfer leadership.", NamedTextColor.RED)).let { true }
                }
                if (args.size < 2) return sender.sendMessage(Component.text("Usage: /$label leadership <player>", NamedTextColor.RED)).let { true }

                val target = Bukkit.getOfflinePlayer(args[1])
                if (!team.members.contains(target.uniqueId)) {
                    return sender.sendMessage(Component.text("That player is not in your team.", NamedTextColor.RED)).let { true }
                }

                team.leader = target.uniqueId
                plugin.teamManager.broadcastComponentToTeam(teamName, plugin.messagesManager.getComponent("teams.leader-transferred", "player" to (target.name ?: "Unknown")))
            }
            "coords" -> {
                if (!plugin.config.getBoolean("settings.team-easy-coord", true)) {
                    return sender.sendMessage(Component.text("This feature is disabled on the server.", NamedTextColor.RED)).let { true }
                }
                val set = plugin.infamyManager.getSettings(sender.uniqueId)
                if (args.size >= 2) {
                    when (args[1].lowercase()) {
                        "toggle", "on", "off" -> {
                            if (set.scoreboardMode == "TEAM") {
                                set.scoreboardMode = "OFF"
                                sender.sendMessage(Component.text("Team coords scoreboard disabled.", NamedTextColor.YELLOW))
                            } else {
                                set.scoreboardMode = "TEAM"
                                sender.sendMessage(Component.text("Team coords scoreboard enabled.", NamedTextColor.GREEN))
                            }
                            return true
                        }
                    }
                }

                val teamName = plugin.teamManager.playerTeams[sender.uniqueId] ?: return sender.sendMessage(plugin.messagesManager.getComponent("teams.not-in-team")).let { true }
                val team = plugin.teamManager.teams[teamName] ?: return sender.sendMessage(Component.text("Error finding team.", NamedTextColor.RED)).let { true }

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
            else -> sender.sendMessage(Component.text("Unknown team subcommand. Type /$label for menu.", NamedTextColor.RED))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            val subs = listOf("create", "disband", "invite", "accept", "decline", "leave", "kick", "list", "chat", "color", "icon", "leadership", "coords", "promote", "demote", "rename")
            completions.addAll(subs.filter { it.startsWith(args[0].lowercase()) })
        } else if (args.size == 2) {
            when (args[0].lowercase()) {
                "invite", "kick", "promote", "demote", "leadership" -> {
                    completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) })
                }
                "color" -> completions.addAll(TeamColor.getColorNames().filter { it.startsWith(args[1].lowercase()) })
                "coords" -> completions.addAll(listOf("on", "off", "toggle").filter { it.startsWith(args[1].lowercase()) })
                "icon" -> completions.addAll(listOf("reset").filter { it.startsWith(args[1].lowercase()) })
            }
        } else if (args.size >= 3) {
            if (args[0].lowercase() == "color") {
                completions.addAll(formatMap.keys.filter { it.startsWith(args.last().lowercase()) && !args.contains(it) })
            }
        }
        return completions
    }
}