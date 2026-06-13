// Team calculation class. Connected to InfamySMP.kt.
package com.enxivity.infamy

import java.util.UUID

class TeamManager {
    // Maps a Player's UUID to a set of their teammate UUIDs
    private val teams = mutableMapOf<UUID, MutableSet<UUID>>()

    fun addTeammate(playerUUID: UUID, teammateUUID: UUID) {
        teams.computeIfAbsent(playerUUID) { mutableSetOf() }.add(teammateUUID)
        teams.computeIfAbsent(teammateUUID) { mutableSetOf() }.add(playerUUID)
    }

    fun removeTeammate(playerUUID: UUID, teammateUUID: UUID) {
        teams[playerUUID]?.remove(teammateUUID)
        teams[teammateUUID]?.remove(playerUUID)
    }

    fun areTeammates(player1: UUID, player2: UUID): Boolean {
        return teams[player1]?.contains(player2) == true
    }

    fun getTeammates(playerUUID: UUID): Set<UUID> {
        return teams[playerUUID] ?: emptySet()
    }
}