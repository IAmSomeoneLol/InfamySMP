// Team calculation class. Connected to InfamySMP.kt.
package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import java.util.UUID

class TeamManager {
    private val teams = mutableMapOf<UUID, MutableSet<UUID>>()
    // Tracks pending invites: Target UUID -> Sender UUID
    private val pendingInvites = mutableMapOf<UUID, UUID>()

    fun sendInvite(sender: UUID, target: UUID): Boolean {
        if (teams[target]?.isNotEmpty() == true) return false // Target already in a team
        pendingInvites[target] = sender
        return true
    }

    fun acceptInvite(target: UUID): Boolean {
        val sender = pendingInvites.remove(target) ?: return false
        teams.computeIfAbsent(sender) { mutableSetOf() }.add(target)
        teams.computeIfAbsent(target) { mutableSetOf() }.add(sender)
        return true
    }

    fun declineInvite(target: UUID): Boolean {
        return pendingInvites.remove(target) != null
    }

    fun removeTeammate(playerUUID: UUID, teammateUUID: UUID) {
        teams[playerUUID]?.remove(teammateUUID)
        teams[teammateUUID]?.remove(playerUUID)
    }

    fun leaveTeam(playerUUID: UUID) {
        val myTeammates = getTeammates(playerUUID).toList()
        val playerName = Bukkit.getOfflinePlayer(playerUUID).name ?: "A player"

        for (teammate in myTeammates) {
            removeTeammate(playerUUID, teammate)
            val onlineTeammate = Bukkit.getPlayer(teammate)
            onlineTeammate?.sendMessage(Component.text("$playerName has left your team.", NamedTextColor.YELLOW))
        }
    }

    fun areTeammates(player1: UUID, player2: UUID): Boolean {
        return teams[player1]?.contains(player2) == true
    }

    fun getTeammates(playerUUID: UUID): Set<UUID> {
        return teams[playerUUID] ?: emptySet()
    }
}