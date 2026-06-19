package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

data class TeamData(val name: String, val leader: UUID, val members: MutableSet<UUID> = mutableSetOf())

// ADDED the plugin parameter here!
class TeamManager(private val plugin: InfamySMP) {
    val teams = mutableMapOf<String, TeamData>()
    val playerTeams = mutableMapOf<UUID, String>()
    private val pendingInvites = mutableMapOf<UUID, String>()

    // ==========================================
    // DATA PERSISTENCE (SAVE / LOAD)
    // ==========================================
    fun saveData() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val file = File(plugin.dataFolder, "teams.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        // Clear old data to prevent ghosts
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
            val leaderStr = config.getString("teams.$name.leader") ?: return@forEach
            val leader = UUID.fromString(leaderStr)
            val membersList = config.getStringList("teams.$name.members").map { UUID.fromString(it) }.toMutableSet()

            teams[name] = TeamData(name, leader, membersList)
            membersList.forEach { member -> playerTeams[member] = name }
        }
    }

    // ==========================================
    // TEAM LOGIC
    // ==========================================
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

        team.members.forEach { memberId ->
            playerTeams.remove(memberId)
            val member = Bukkit.getPlayer(memberId)
            member?.sendMessage(Component.text("The team '$teamName' has been disbanded by the leader.", NamedTextColor.RED))
        }
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

        team.members.forEach { memberId ->
            Bukkit.getPlayer(memberId)?.sendMessage(Component.text("${target.name} joined the team!", NamedTextColor.GREEN))
        }
        return true
    }

    fun declineInvite(target: UUID): Boolean {
        return pendingInvites.remove(target) != null
    }

    fun leaveTeam(player: Player): Boolean {
        val teamName = playerTeams.remove(player.uniqueId) ?: return false
        val team = teams[teamName] ?: return false

        if (team.leader == player.uniqueId) {
            player.sendMessage(Component.text("You cannot leave as the leader. You must /infamy team disband.", NamedTextColor.RED))
            playerTeams[player.uniqueId] = teamName
            return false
        }

        team.members.remove(player.uniqueId)
        team.members.forEach { memberId ->
            Bukkit.getPlayer(memberId)?.sendMessage(Component.text("${player.name} left the team.", NamedTextColor.YELLOW))
        }
        return true
    }

    fun kickTeammate(leader: Player, target: UUID): Boolean {
        val teamName = playerTeams[leader.uniqueId] ?: return false
        val team = teams[teamName] ?: return false

        if (team.leader != leader.uniqueId) return false
        if (target == leader.uniqueId) return false
        if (!team.members.contains(target)) return false

        team.members.remove(target)
        playerTeams.remove(target)
        return true
    }

    fun areTeammates(player1: UUID, player2: UUID): Boolean {
        val team1 = playerTeams[player1] ?: return false
        val team2 = playerTeams[player2] ?: return true
        return team1 == team2
    }

    fun getTeam(playerUUID: UUID): TeamData? {
        val teamName = playerTeams[playerUUID] ?: return null
        return teams[teamName]
    }
}