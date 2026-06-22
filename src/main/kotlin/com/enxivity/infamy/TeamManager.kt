package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

data class TeamData(val name: String, val leader: UUID, val members: MutableSet<UUID> = mutableSetOf())

class TeamManager(private val plugin: InfamySMP) {
    val teams = mutableMapOf<String, TeamData>()
    val playerTeams = mutableMapOf<UUID, String>()
    private val pendingInvites = mutableMapOf<UUID, String>()

    fun saveData() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val file = File(plugin.dataFolder, "teams.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("teams", null)
        teams.forEach { (name, data) ->
            config.set("teams.$name.leader", data.leader.toString())
            config.set("teams.$name.members", data.members.map { it.toString() })
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
            teams[name] = TeamData(name, leader, membersList)
            membersList.forEach { member -> playerTeams[member] = name }
        }
    }

    private fun broadcastToTeam(teamName: String, message: String, color: NamedTextColor) {
        val team = teams[teamName] ?: return
        team.members.forEach { memberId ->
            val p = Bukkit.getPlayer(memberId)
            if (p != null && plugin.infamyManager.getSettings(p.uniqueId).teamMessages) p.sendMessage(Component.text(message, color))
        }
    }

    fun createTeam(leader: Player, teamName: String): Boolean {
        if (playerTeams.containsKey(leader.uniqueId)) return false
        if (teams.containsKey(teamName.lowercase())) return false
        val newTeam = TeamData(teamName, leader.uniqueId)
        newTeam.members.add(leader.uniqueId)
        teams[teamName.lowercase()] = newTeam
        playerTeams[leader.uniqueId] = teamName.lowercase()
        return true
    }

    fun disbandTeam(leader: Player): Boolean {
        val teamName = playerTeams[leader.uniqueId] ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader != leader.uniqueId) return false
        broadcastToTeam(teamName, "The team '$teamName' has been disbanded by the leader.", NamedTextColor.RED)
        team.members.forEach { playerTeams.remove(it) }
        teams.remove(teamName)
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
        return true
    }

    fun declineInvite(target: UUID): Boolean = pendingInvites.remove(target) != null

    fun leaveTeam(player: Player): Boolean {
        val teamName = playerTeams.remove(player.uniqueId) ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader == player.uniqueId) {
            player.sendMessage(Component.text("You cannot leave as the leader. You must /infamy team disband.", NamedTextColor.RED))
            playerTeams[player.uniqueId] = teamName
            return false
        }
        team.members.remove(player.uniqueId)
        broadcastToTeam(teamName, "${player.name} left the team.", NamedTextColor.YELLOW)
        return true
    }

    fun kickTeammate(leader: Player, target: UUID): Boolean {
        val teamName = playerTeams[leader.uniqueId] ?: return false
        val team = teams[teamName] ?: return false
        if (team.leader != leader.uniqueId || target == leader.uniqueId || !team.members.contains(target)) return false
        team.members.remove(target)
        playerTeams.remove(target)
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