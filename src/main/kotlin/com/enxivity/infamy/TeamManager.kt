package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID

data class TeamData(
    val name: String,
    var leader: UUID,
    val members: MutableSet<UUID> = mutableSetOf(),
    var colorFormat: String = "&f",
    var icon: ItemStack? = null
)

class TeamManager(private val plugin: InfamySMP) {
    val teams = mutableMapOf<String, TeamData>()
    val playerTeams = mutableMapOf<UUID, String>()
    private val pendingInvites = mutableMapOf<UUID, String>()
    val teamChatToggled = mutableSetOf<UUID>()
    val coordsScoreboardToggled = mutableSetOf<UUID>()

    fun saveData() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val file = File(plugin.dataFolder, "teams.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("teams", null)
        teams.forEach { (name, data) ->
            config.set("teams.$name.leader", data.leader.toString())
            config.set("teams.$name.members", data.members.map { it.toString() })
            config.set("teams.$name.colorFormat", data.colorFormat)
            config.set("teams.$name.icon", data.icon)
        }
        config.save(file)
    }

    fun loadData() {
        val file = File(plugin.dataFolder, "teams.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        config.getConfigurationSection("teams")?.getKeys(false)?.forEach { name ->
            val leader = UUID.fromString(config.getString("teams.$name.leader") ?: return@forEach)
            val membersList = config.getStringList("teams.$name.members").map { UUID.fromString(it) }.toMutableSet()

            var colorFormat = config.getString("teams.$name.colorFormat") ?: "&f"

            colorFormat = colorFormat.replace("&i", "", ignoreCase = true)
                .replace("&k", "", ignoreCase = true)
                .replace("\\[.*?]\\s?".toRegex(), "")

            if (colorFormat.isBlank()) colorFormat = "&f"

            val icon = config.getItemStack("teams.$name.icon")

            teams[name] = TeamData(name, leader, membersList, colorFormat, icon)
            membersList.forEach { member -> playerTeams[member] = name }
        }
    }

    fun broadcastToTeam(teamName: String, message: String, color: NamedTextColor) {
        val team = teams[teamName] ?: return
        team.members.forEach { memberId ->
            val p = Bukkit.getPlayer(memberId)
            if (p != null && plugin.infamyManager.getSettings(p.uniqueId).teamMessages) p.sendMessage(Component.text(message, color))
        }
    }

    fun syncAllScoreboards() {
        // GHOST TEAM PURGER: Erases corrupted tags from Minecraft's world/data/scoreboard.dat
        val mainBoard = Bukkit.getScoreboardManager().mainScoreboard
        mainBoard.teams.filter { it.name.startsWith("inf_") }.forEach { it.unregister() }

        if (!plugin.config.getBoolean("settings.nametag-team-color", true)) return

        for (player in Bukkit.getOnlinePlayers()) {
            // Isolates player onto a new scoreboard so sidebars don't conflict, and stops Main Scoreboard pollution
            if (player.scoreboard == mainBoard) {
                player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
            }
            val board = player.scoreboard
            board.teams.filter { it.name.startsWith("inf_") }.forEach { it.unregister() }

            teams.values.forEach { team ->
                val sbTeamName = "inf_${team.name}".take(16)
                val sbTeam = board.registerNewTeam(sbTeamName)

                val formatComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(team.colorFormat)
                sbTeam.prefix(formatComp)

                team.members.forEach { memberId ->
                    val memberName = Bukkit.getOfflinePlayer(memberId).name
                    if (memberName != null) sbTeam.addEntry(memberName)
                }
            }
        }
    }

    fun removePlayerHandleLeader(playerUUID: UUID) {
        val teamName = playerTeams[playerUUID] ?: return
        val team = teams[teamName] ?: return

        team.members.remove(playerUUID)
        playerTeams.remove(playerUUID)
        teamChatToggled.remove(playerUUID)
        coordsScoreboardToggled.remove(playerUUID)

        Bukkit.getPlayer(playerUUID)?.let {
            plugin.infamyManager.updateTabList(it)
            it.scoreboard.getObjective("teamCoords")?.unregister()
        }

        if (team.members.isEmpty()) {
            teams.remove(teamName)
        } else if (team.leader == playerUUID) {
            val nextLeader = team.members
                .map { Bukkit.getOfflinePlayer(it) }
                .sortedBy { it.name?.lowercase() ?: "" }
                .first().uniqueId

            team.leader = nextLeader
            broadcastToTeam(teamName, "${Bukkit.getOfflinePlayer(nextLeader).name} has inherited team leadership!", NamedTextColor.GOLD)
        }
        syncAllScoreboards()
    }

    fun createTeam(leader: Player, teamName: String): Boolean {
        if (playerTeams.containsKey(leader.uniqueId)) return false
        if (teams.containsKey(teamName.lowercase())) return false
        val newTeam = TeamData(teamName, leader.uniqueId)
        newTeam.members.add(leader.uniqueId)
        teams[teamName.lowercase()] = newTeam
        playerTeams[leader.uniqueId] = teamName.lowercase()
        plugin.infamyManager.updateTabList(leader)
        syncAllScoreboards()
        return true
    }

    fun disbandTeam(leader: Player): Boolean {
        val teamName = playerTeams[leader.uniqueId] ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader != leader.uniqueId) return false
        broadcastToTeam(teamName, "The team '$teamName' has been disbanded by the leader.", NamedTextColor.RED)

        val onlineMembers = team.members.mapNotNull { Bukkit.getPlayer(it) }
        team.members.forEach {
            playerTeams.remove(it)
            teamChatToggled.remove(it)
            coordsScoreboardToggled.remove(it)
        }
        teams.remove(teamName)
        onlineMembers.forEach {
            plugin.infamyManager.updateTabList(it)
            it.scoreboard.getObjective("teamCoords")?.unregister()
        }
        syncAllScoreboards()
        return true
    }

    fun sendInvite(teamName: String, target: UUID): Boolean {
        if (playerTeams.containsKey(target)) return false
        pendingInvites[target] = teamName.lowercase()
        return true
    }

    fun acceptInvite(target: Player): Boolean {
        val teamName = pendingInvites.remove(target.uniqueId) ?: return false
        val team = teams[teamName] ?: return false
        team.members.add(target.uniqueId)
        playerTeams[target.uniqueId] = teamName
        broadcastToTeam(teamName, "${target.name} joined the team!", NamedTextColor.GREEN)
        plugin.infamyManager.updateTabList(target)
        syncAllScoreboards()
        return true
    }

    fun declineInvite(target: UUID): Boolean = pendingInvites.remove(target) != null

    fun leaveTeam(player: Player): Boolean {
        val teamName = playerTeams[player.uniqueId] ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader == player.uniqueId) {
            player.sendMessage(Component.text("You cannot leave as the leader. You must /infamy team disband.", NamedTextColor.RED))
            return false
        }
        removePlayerHandleLeader(player.uniqueId)
        broadcastToTeam(teamName, "${player.name} left the team.", NamedTextColor.YELLOW)
        return true
    }

    fun kickTeammate(leader: Player, target: UUID): Boolean {
        val teamName = playerTeams[leader.uniqueId] ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader != leader.uniqueId || target == leader.uniqueId || !team.members.contains(target)) return false
        removePlayerHandleLeader(target)
        broadcastToTeam(teamName, "${Bukkit.getOfflinePlayer(target).name} was kicked from the team.", NamedTextColor.YELLOW)
        return true
    }

    fun areTeammates(player1: UUID, player2: UUID): Boolean {
        val team1 = playerTeams[player1] ?: return false
        val team2 = playerTeams[player2] ?: return true
        return team1 == team2
    }

    fun getTeam(playerUUID: UUID): TeamData? = teams[playerTeams[playerUUID]]
}