package com.enxivity.infamy.commands

import com.enxivity.infamy.InfamySMP
import com.enxivity.infamy.KillRecord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.UUID

class InfamyCommand(private val plugin: InfamySMP) : CommandExecutor, TabCompleter {

    private val teamCommand = TeamCommand(plugin)

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
            if (sender is Player) plugin.itemRestrictionsListener.openInfoGui(sender)
            else sender.sendMessage(Component.text("Usage: /infamy <info|team|level|withdraw|bottle|history|cd|debugkill|add|top|settings|ability|eventhost> ...", NamedTextColor.RED))
            return true
        }

        when (args[0].lowercase()) {
            "info" -> if (sender is Player) plugin.itemRestrictionsListener.openInfoGui(sender)
            "settings" -> if (sender is Player) plugin.itemRestrictionsListener.openSettingsGUI(sender)
            "ability" -> {
                if (args.size >= 3 && args[1].lowercase() == "activate") {
                    if (sender is Player) handleActivateAbilityCommand(sender, args[2])
                    else sender.sendMessage(Component.text("Only players can activate abilities.", NamedTextColor.RED))
                } else if (sender is Player) {
                    plugin.itemRestrictionsListener.openAbilitiesGUI(sender)
                } else {
                    sender.sendMessage(Component.text("Usage: /infamy ability activate <name>", NamedTextColor.RED))
                }
            }
            "cd" -> if (sender is Player) handleCooldownsCommand(sender, args)
            "debugkill" -> handleDebugKillCommand(sender, args)
            "eventhost" -> handleEventHostCommand(sender, args)
            "team" -> teamCommand.onCommand(sender, command, "team", args.drop(1).toTypedArray())
            "add" -> handleAddCommand(sender, args)
            "level" -> handleLevelCommand(sender, args)
            "withdraw" -> handleWithdrawCommand(sender, args)
            "bottle" -> handleBottleCommand(sender, args)
            "top" -> handleTopRefreshCommand(sender, args)
            "viewec" -> {
                if (sender is Player && args.size >= 2) {
                    plugin.openEnderChestSnapshot(sender, args[1])
                }
            }
            "history" -> {
                if (sender is Player) {
                    if (args.size > 1 && args[1].lowercase() == "admin") plugin.itemRestrictionsListener.openHistoryGui(sender, true)
                    else plugin.itemRestrictionsListener.openHistoryGui(sender, false)
                } else {
                    sender.sendMessage(Component.text("Only players can view history GUIs.", NamedTextColor.RED))
                }
            }
            else -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val completions = mutableListOf<String>()

        if (args.isEmpty()) return completions

        if (args[0].lowercase() == "team") {
            return teamCommand.onTabComplete(sender, command, alias, args.drop(1).toTypedArray())
        }

        if (args.size == 1) {
            val subs = mutableListOf("team", "level", "withdraw", "history", "info", "cd", "settings", "ability", "eventhost")
            if (isAdmin(sender)) subs.addAll(listOf("add", "bottle", "debugkill", "top"))
            completions.addAll(subs.filter { it.startsWith(args[0].lowercase()) })
        } else if (args.size == 2) {
            when (args[0].lowercase()) {
                "ability" -> completions.addAll(listOf("activate").filter { it.startsWith(args[1].lowercase()) })
                "add", "level", "debugkill" -> if (isAdmin(sender)) completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) })
                "bottle" -> if (isAdmin(sender)) completions.addAll(listOf("honor", "infamy", "pure").filter { it.startsWith(args[1].lowercase()) })
                "withdraw" -> completions.addAll(listOf("all", "1", "5", "10").filter { it.startsWith(args[1].lowercase()) })
                "top" -> if (isAdmin(sender)) completions.add("refresh")
                "history" -> if (isAdmin(sender) && "admin".startsWith(args[1].lowercase())) completions.add("admin")
                "cd" -> if (isAdmin(sender)) completions.add("refresh")
                "eventhost" -> if (isAdmin(sender)) completions.addAll(listOf("true", "false", "configcalendar").filter { it.startsWith(args[1].lowercase()) })
            }
        } else if (args.size == 3) {
            when (args[0].lowercase()) {
                "ability" -> {
                    if (args[1].lowercase() == "activate" && sender is Player) {
                        val available = mutableListOf<String>()
                        val im = plugin.infamyManager
                        if (im.hasAbility(sender, "true_invisibility", true)) available.add("TrueInvisibility")
                        if (im.hasAbility(sender, "hunger_absorption", true)) available.add("SaturatingShield")
                        if (im.hasAbility(sender, "karma_delay", true)) available.add("KarmicJustice")
                        if (im.hasAbility(sender, "sword_block", false)) available.add("SwordBlock")
                        if (im.hasAbility(sender, "shield_sacrifice", false)) available.add("ShieldSacrifice")
                        if (im.hasAbility(sender, "shield_recovery", false)) available.add("ShieldRecovery")
                        if (im.hasAbility(sender, "bleeding_edge", false)) available.add("BleedingEdge")
                        if (im.hasAbility(sender, "mace_slam", false)) available.add("MaceSlam")
                        if (im.hasAbility(sender, "boss_sacrifice", false)) available.add("Hellcrush")
                        completions.addAll(available.filter { it.lowercase().startsWith(args[2].lowercase()) })
                    }
                }
                "add" -> if (isAdmin(sender)) {
                    completions.addAll(listOf("honor", "infamy").filter { it.startsWith(args[2].lowercase()) })
                }
                "bottle" -> if (isAdmin(sender)) {
                    completions.addAll(Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[2].lowercase()) })
                }
                "level" -> if (isAdmin(sender)) {
                    completions.addAll(listOf("reset").filter { it.startsWith(args[2].lowercase()) })
                }
                "debugkill" -> if (isAdmin(sender)) {
                    completions.addAll(listOf("DROPPED", "PICKED_UP", "STASHED", "WITHDRAWN", "STOLEN", "LOST").filter { it.startsWith(args[2].uppercase()) })
                }
                "eventhost" -> if (isAdmin(sender) && args[1].lowercase() == "configcalendar") {
                    completions.addAll(listOf("true", "false").filter { it.startsWith(args[2].lowercase()) })
                }
            }
        } else if (args.size == 4) {
            when (args[0].lowercase()) {
                "add" -> if (isAdmin(sender)) completions.addAll(listOf("1", "5", "10", "20").filter { it.startsWith(args[3]) })
                "bottle" -> if (isAdmin(sender)) completions.addAll(listOf("1", "5", "10").filter { it.startsWith(args[3]) })
            }
        } else if (args.size == 5) {
            if (args[0].lowercase() == "bottle" && isAdmin(sender)) {
                completions.addAll(listOf("1", "16", "64").filter { it.startsWith(args[4]) })
            }
        }
        return completions
    }

    private fun handleActivateAbilityCommand(player: Player, abilityName: String) {
        plugin.combatListener.activateAbility(player, abilityName)
    }

    private fun handleEventHostCommand(sender: CommandSender, args: Array<out String>) {
        if (args.size == 1) {
            val em = plugin.eventManager
            if (em.isEventActive) {
                val timeStr = em.getFormattedRemainingTime()
                val mods = em.getActiveModifiers().joinToString(", ").ifEmpty { "None" }
                sender.sendMessage(Component.text("Event Active: $timeStr", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("Modifiers: $mods", NamedTextColor.YELLOW))
            } else {
                sender.sendMessage(Component.text("Event Active: No", NamedTextColor.RED))
            }
            return
        }

        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))

        when (args[1].lowercase()) {
            "true" -> {
                if (plugin.eventManager.isCalendarEvent || plugin.eventManager.calendarEnabled) {
                    return sender.sendMessage(Component.text("Event is already started by calendar", NamedTextColor.RED))
                }

                if (plugin.eventManager.isEventActive) {
                    return sender.sendMessage(Component.text("An event is already active. End it first using /infamy eventhost false.", NamedTextColor.RED))
                }

                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy eventhost true <durationInSeconds>", NamedTextColor.RED))
                val duration = args[2].toLongOrNull() ?: return sender.sendMessage(Component.text("Duration must be a number in seconds.", NamedTextColor.RED))
                if (duration <= 0) return sender.sendMessage(Component.text("Duration must be greater than 0.", NamedTextColor.RED))
                plugin.eventManager.startEvent(duration)
                sender.sendMessage(Component.text("Started Infamy Event for $duration seconds.", NamedTextColor.GREEN))
            }
            "false" -> {
                plugin.eventManager.stopEvent()
                sender.sendMessage(Component.text("Infamy Event stopped.", NamedTextColor.YELLOW))
            }
            "configcalendar" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy eventhost configcalendar <true|false>", NamedTextColor.RED))
                val enabled = args[2].toBooleanStrictOrNull() ?: return sender.sendMessage(Component.text("Specify true or false.", NamedTextColor.RED))

                if (plugin.eventManager.calendarEnabled == enabled) {
                    return sender.sendMessage(Component.text("Calendar Schelduer already set to $enabled.", NamedTextColor.YELLOW))
                }

                plugin.eventManager.setCalendarEnabled(enabled)
                sender.sendMessage(Component.text("Calendar schedule set to: $enabled", NamedTextColor.GREEN))
            }
            else -> sender.sendMessage(Component.text("Unknown option. Use true, false, or configcalendar.", NamedTextColor.RED))
        }
    }

    private fun handleTopRefreshCommand(sender: CommandSender, args: Array<out String>) {
        if (!isAdmin(sender)) return sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED))
        if (args.size < 2 || args[1].lowercase() != "refresh") return sender.sendMessage(Component.text("Usage: /infamy top refresh [player]", NamedTextColor.RED))

        plugin.infamyManager.refreshBossLock()
        val msg = Component.text("The Most Infamous lock has been cleared by an admin! A Level 21 slot is now open.", NamedTextColor.DARK_RED, TextDecoration.BOLD)
        Bukkit.getOnlinePlayers().forEach { p ->
            if (plugin.infamyManager.getSettings(p.uniqueId).globalMessages) p.sendMessage(msg)
        }
        sender.sendMessage(Component.text("Successfully unlocked a Level 21 slot.", NamedTextColor.GREEN))
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
            cl.honorAbsorbCooldowns.clear()
            cl.honorInvisCooldowns.clear()
            cl.karmaCooldowns.clear()
            cl.activeKarma.clear()
            cl.primedKarma.clear()
            cl.shieldSacrificeCooldowns.clear()
            cl.axeStaggerCooldowns.clear()
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

        val c = plugin.config
        checkCD(cl.swordBlockCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.sword-block.cooldown-seconds", 60) * 1000, "Sword Block")
        checkCD(cl.shieldAbilityCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.shield-recovery.cooldown-seconds", 25) * 1000, "Shield Recovery")
        checkCD(cl.bleedCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.bleeding-edge.cooldown-seconds", 60) * 1000, "Bleeding Edge")
        checkCD(cl.maceCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.mace-slam.cooldown-seconds", 60) * 1000, "Mace Slam")
        checkCD(cl.sacrificeCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.hellcrush.cooldown-seconds", 900) * 1000, "Hellcrush")
        checkCD(cl.honorAbsorbCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.hunger-absorption.cooldown-seconds", 60) * 1000, "Saturating Shield")
        checkCD(cl.honorInvisCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.true-invisibility.cooldown-seconds", 300) * 1000, "True Invisibility")
        checkCD(cl.karmaCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.karma-delay.cooldown-seconds", 180) * 1000, "Karmic Justice")
        checkCD(cl.shieldSacrificeCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.shield-sacrifice.cooldown-seconds", 300) * 1000, "Shield Sacrifice")
        checkCD(cl.axeStaggerCooldowns[player.uniqueId] ?: 0, c.getLong("abilities-config.axe-stagger.cooldown-seconds", 45) * 1000, "Axe Stagger")

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

    private fun handleTeamCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy team <create|disband|invite|accept|decline|leave|kick|list|chat|color|icon|leadership|coords|promote|demote|rename> [args]", NamedTextColor.RED))

        when (args[1].lowercase()) {
            "create" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team create <name>", NamedTextColor.RED))
                val teamName = args[2]
                if (teamName.contains("&") || teamName.contains("§")) {
                    return sender.sendMessage(plugin.messagesManager.getComponent("teams.invalid-name"))
                }
                if (plugin.teamManager.createTeam(sender, teamName)) sender.sendMessage(plugin.messagesManager.getComponent("teams.created", "team" to teamName)) else sender.sendMessage(plugin.messagesManager.getComponent("teams.create-failed"))
            }
            "rename" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team rename <new_name>", NamedTextColor.RED))
                val newName = args[2]
                if (newName.contains("&") || newName.contains("§")) {
                    return sender.sendMessage(Component.text("Team name cannot contain formatting or color codes (& or §).", NamedTextColor.RED))
                }
                if (!plugin.teamManager.renameTeam(sender, newName)) sender.sendMessage(Component.text("Cannot rename team! (Are you the leader? Is the name taken?)", NamedTextColor.RED))
            }
            "disband" -> if (plugin.teamManager.disbandTeam(sender)) sender.sendMessage(plugin.messagesManager.getComponent("teams.disbanded")) else sender.sendMessage(plugin.messagesManager.getComponent("teams.disband-failed"))
            "invite" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team invite <player>", NamedTextColor.RED))
                val target = Bukkit.getPlayer(args[2]) ?: return sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                val myTeam = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team!", NamedTextColor.RED))

                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, myTeam.name)) return sender.sendMessage(Component.text("Only the team leader or officers can invite.", NamedTextColor.RED))

                if (plugin.teamManager.isBannedFromTeam(target.uniqueId, myTeam.name)) {
                    val remaining = plugin.teamManager.getRemainingBanTime(target.uniqueId, myTeam.name)
                    return sender.sendMessage(Component.text("This player cannot be added to this team for another $remaining.", NamedTextColor.RED))
                }

                val hasBoss = myTeam.members.any { plugin.infamyManager.getRawReputationByUUID(it) >= 21 }
                if (hasBoss && myTeam.members.size >= 2) return sender.sendMessage(Component.text("A team with the Boss cannot have more than 2 members!", NamedTextColor.RED))

                if (plugin.teamManager.sendInvite(myTeam.name, target.uniqueId)) {
                    sender.sendMessage(plugin.messagesManager.getComponent("teams.invite-sent", "player" to target.name))
                    target.sendMessage(plugin.messagesManager.getComponent("teams.invite-received", "player" to sender.name, "team" to myTeam.name))
                } else sender.sendMessage(plugin.messagesManager.getComponent("teams.already-in-team", "player" to target.name))
            }
            "accept" -> {
                val targetTeam = plugin.teamManager.getPendingInviteTeam(sender.uniqueId)
                if (targetTeam == null) {
                    return sender.sendMessage(Component.text("No pending invites.", NamedTextColor.RED))
                }

                if (plugin.teamManager.isBannedFromTeam(sender.uniqueId, targetTeam.name)) {
                    val remaining = plugin.teamManager.getRemainingBanTime(sender.uniqueId, targetTeam.name)
                    return sender.sendMessage(Component.text("You cannot join this team for another $remaining.", NamedTextColor.RED))
                }

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
                } else sender.sendMessage(Component.text("Cannot kick this player. (Insufficient perms or target is immune)", NamedTextColor.RED))
            }
            "promote" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team promote <player>", NamedTextColor.RED))
                val target = Bukkit.getOfflinePlayer(args[2])
                if (plugin.teamManager.promoteTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(Component.text("Successfully promoted ${target.name}.", NamedTextColor.GREEN))
                } else sender.sendMessage(Component.text("Cannot promote this player. (Only leader can promote)", NamedTextColor.RED))
            }
            "demote" -> {
                if (args.size < 3) return sender.sendMessage(Component.text("Usage: /infamy team demote <player>", NamedTextColor.RED))
                val target = Bukkit.getOfflinePlayer(args[2])
                if (plugin.teamManager.demoteTeammate(sender, target.uniqueId)) {
                    sender.sendMessage(Component.text("Successfully demoted ${target.name}.", NamedTextColor.YELLOW))
                } else sender.sendMessage(Component.text("Cannot demote this player. (Only leader can demote)", NamedTextColor.RED))
            }
            "list" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                sender.sendMessage(Component.text("Team: ${team.name} | Leader: ${Bukkit.getOfflinePlayer(team.leader).name}", NamedTextColor.GOLD))
                team.members.forEach {
                    val role = if (team.leader == it) " [Leader]" else if (team.officers.contains(it)) " [Officer]" else ""
                    sender.sendMessage(Component.text("- ${Bukkit.getOfflinePlayer(it).name}$role", NamedTextColor.GREEN))
                }
            }
            "chat" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (args.size == 2) {
                    if (plugin.teamManager.teamChatToggled.contains(sender.uniqueId)) {
                        plugin.teamManager.teamChatToggled.remove(sender.uniqueId)
                        sender.sendMessage(plugin.messagesManager.getComponent("teams.chat-disabled"))
                    } else {
                        plugin.teamManager.teamChatToggled.add(sender.uniqueId)
                        sender.sendMessage(plugin.messagesManager.getComponent("teams.chat-enabled"))
                    }
                } else {
                    val msgText = args.drop(2).joinToString(" ")
                    plugin.teamManager.sendTeamChat(team, sender, msgText)
                }
            }
            "color" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, team.name)) return sender.sendMessage(Component.text("Only the team leader or officers can change the color.", NamedTextColor.RED))
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /infamy team color <color> [format...]", NamedTextColor.RED))
                    return
                }

                val colorInput = args[2].lowercase()
                val colorCode = com.enxivity.infamy.TeamColor.getLegacyFormat(colorInput)
                if (colorCode == null) {
                    sender.sendMessage(Component.text("Invalid Color! Use Tab to view available colors (or #RRGGBB).", NamedTextColor.RED))
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
                plugin.teamManager.saveData()
                sender.sendMessage(plugin.messagesManager.getComponent("teams.color-updated"))
                team.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { plugin.infamyManager.updateTabList(it) }
                plugin.teamManager.syncAllScoreboards()
            }
            "icon" -> {
                val team = plugin.teamManager.getTeam(sender.uniqueId) ?: return sender.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED))
                if (!plugin.teamManager.isOfficerOrLeader(sender.uniqueId, team.name)) return sender.sendMessage(Component.text("Only the team leader or officers can change the icon.", NamedTextColor.RED))

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
                val set = plugin.infamyManager.getSettings(sender.uniqueId)
                if (args.size == 3) {
                    when(args[2].lowercase()) {
                        "toggle", "on", "off" -> {
                            if (set.scoreboardMode == "TEAM") {
                                set.scoreboardMode = "OFF"
                                sender.sendMessage(Component.text("Team coords scoreboard disabled.", NamedTextColor.YELLOW))
                            } else {
                                set.scoreboardMode = "TEAM"
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
        val maxAllowed = if (type == "infamy") { if (plugin.infamyManager.currentBoss != null && plugin.infamyManager.currentBoss != target.uniqueId) 20 else 21 } else -21
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
        if (args.size < 2) return sender.sendMessage(Component.text("Usage: /infamy withdraw <amount|all>", NamedTextColor.RED))

        val currentRep = plugin.infamyManager.getRawReputation(sender)
        if (currentRep == 0) return sender.sendMessage(Component.text("You have 0 points to withdraw!", NamedTextColor.RED))

        val amount = if (args[1].equals("all", ignoreCase = true)) {
            if (currentRep > 0) currentRep else -currentRep
        } else {
            val parsed = args[1].toIntOrNull() ?: return sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
            if (parsed <= 0) return sender.sendMessage(Component.text("Invalid amount.", NamedTextColor.RED))
            parsed
        }

        if (currentRep > 0) {
            if (!isAdmin && !plugin.config.getBoolean("settings.allow-withdraw-infamy", true)) {
                return sender.sendMessage(Component.text("Infamy withdrawals are currently disabled by the server.", NamedTextColor.RED))
            }
            if (amount > currentRep) return sender.sendMessage(Component.text("You don't have enough Infamy points!", NamedTextColor.RED))

            plugin.infamyManager.withdrawnPoints[sender.uniqueId] = (plugin.infamyManager.withdrawnPoints[sender.uniqueId] ?: 0) + amount

            if (currentRep == 21) {
                plugin.infamyManager.setReputation(sender, currentRep - amount)
                val leftovers1 = sender.inventory.addItem(plugin.itemManager.createPureInfamyBottle(sender.name, "Withdrawn"))
                if (leftovers1.isNotEmpty()) {
                    leftovers1.values.forEach {
                        val drop = sender.world.dropItem(sender.location, it)
                        drop.isInvulnerable = true
                        drop.velocity = Vector(0.0, 0.1, 0.0)
                    }
                    sender.sendMessage(Component.text("Inventory Full, Refund dropped on floor.", NamedTextColor.AQUA))
                }

                if (amount > 1) {
                    val leftovers2 = sender.inventory.addItem(plugin.itemManager.createInfamyBottle(amount - 1, sender.name, sender.uniqueId.toString()))
                    if (leftovers2.isNotEmpty()) {
                        leftovers2.values.forEach {
                            val drop = sender.world.dropItem(sender.location, it)
                            drop.isInvulnerable = true
                            drop.velocity = Vector(0.0, 0.1, 0.0)
                        }
                        sender.sendMessage(Component.text("Inventory Full, Refund dropped on floor.", NamedTextColor.AQUA))
                    }
                }
                sender.sendMessage(Component.text("You withdrew points and extracted the Pure Infamy Bottle!", NamedTextColor.GREEN))
            } else {
                plugin.infamyManager.setReputation(sender, currentRep - amount)
                val leftovers = sender.inventory.addItem(plugin.itemManager.createInfamyBottle(amount, sender.name, sender.uniqueId.toString()))
                if (leftovers.isNotEmpty()) {
                    leftovers.values.forEach {
                        val drop = sender.world.dropItem(sender.location, it)
                        drop.isInvulnerable = true
                        drop.velocity = Vector(0.0, 0.1, 0.0)
                    }
                    sender.sendMessage(Component.text("Inventory Full, Refund dropped on floor.", NamedTextColor.AQUA))
                }
                sender.sendMessage(plugin.messagesManager.getComponent("withdraw.infamy-success", "amount" to amount))
            }
        } else {
            if (!isAdmin && !plugin.config.getBoolean("settings.allow-withdraw-honor", true)) {
                return sender.sendMessage(Component.text("Honor withdrawals are currently disabled by the server.", NamedTextColor.RED))
            }
            val absoluteHonor = -currentRep
            if (amount > absoluteHonor) return sender.sendMessage(Component.text("You don't have enough Honor points!", NamedTextColor.RED))

            plugin.infamyManager.setReputation(sender, currentRep + amount)

            val bottle = plugin.itemManager.createHonorBottle(amount)
            val leftovers = sender.inventory.addItem(bottle)
            if (leftovers.isNotEmpty()) {
                leftovers.values.forEach {
                    val drop = sender.world.dropItem(sender.location, it)
                    drop.velocity = Vector(0.0, 0.1, 0.0)
                }
                sender.sendMessage(Component.text("Inventory Full, Refund dropped on floor.", NamedTextColor.AQUA))
            }
            sender.sendMessage(plugin.messagesManager.getComponent("withdraw.honor-success", "amount" to amount))
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
                "honor" -> plugin.itemManager.createHonorBottle(level)
                else -> plugin.itemManager.createInfamyBottle(level, "Admin Spawned", null)
            }
            target.inventory.addItem(bottle).values.forEach {
                val drop = target.world.dropItem(target.location, it)
                drop.velocity = Vector(0.0, 0.1, 0.0)
            }
            given++
        }
        sender.sendMessage(Component.text("Given $given $type bottle(s) to ${target.name}.", NamedTextColor.GREEN))
    }
}