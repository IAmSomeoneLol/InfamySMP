// Data tracking class. Connected to InfamySMP.kt.
package com.enxivity.infamy

import org.bukkit.entity.Player
import java.util.UUID

class InfamyManager(private val plugin: InfamySMP) {
    private val infamyData = mutableMapOf<UUID, Int>()
    private val honorData = mutableMapOf<UUID, Int>()

    fun getInfamy(player: Player): Int {
        return infamyData.getOrDefault(player.uniqueId, 0)
    }

    fun setInfamy(player: Player, amount: Int) {
        infamyData[player.uniqueId] = amount
        updateTabList(player, amount)
    }

    fun getHonor(player: Player): Int {
        return honorData.getOrDefault(player.uniqueId, 0)
    }

    fun setHonor(player: Player, amount: Int) {
        honorData[player.uniqueId] = amount
    }

    private fun updateTabList(player: Player, points: Int) {
        val title = when {
            points >= 21 -> "§4[Most Infamous Player]"
            points >= 16 -> "§c[Extremely Dangerous Player]"
            points >= 11 -> "§6[Dangerous Player]"
            points >= 6 -> "§e[Risky Player]"
            else -> "§a[Normal Player]"
        }
        player.playerListName = "$title | ${player.name}"
    }
}