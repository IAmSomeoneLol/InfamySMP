// Data tracking class. Connected to InfamySMP.kt.
package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class InfamyManager(private val plugin: InfamySMP) {
    private val reputationData = mutableMapOf<UUID, Int>()

    // Tracks the single player allowed to hold Level 21
    var currentBoss: UUID? = null

    fun getRawReputation(player: Player): Int {
        return reputationData.getOrDefault(player.uniqueId, 0)
    }

    fun getInfamy(player: Player): Int {
        val rep = getRawReputation(player)
        return if (rep > 0) rep else 0
    }

    fun getHonor(player: Player): Int {
        val rep = getRawReputation(player)
        return if (rep < 0) -rep else 0
    }

    fun setReputation(player: Player, amount: Int) {
        var finalAmount = amount.coerceAtLeast(-12)

        // BOSS LOCK LOGIC (Level 21 exclusivity)
        if (finalAmount >= 21) {
            if (currentBoss != null && currentBoss != player.uniqueId) {
                // Someone else is already the boss. Cap this player at 20.
                finalAmount = 20
                player.sendMessage(Component.text("You reached 21 Infamy, but the Most Infamous title is currently held by someone else! Kill them to take it.", NamedTextColor.RED))
            } else if (currentBoss != player.uniqueId) {
                // Claim the boss title!
                currentBoss = player.uniqueId
                Bukkit.broadcast(Component.text("${player.name} has become the Most Infamous Player!", NamedTextColor.DARK_RED))

                // Force Team size down to 2 members (Player + 1 Teammate)
                val teammates = plugin.teamManager.getTeammates(player.uniqueId).toList()
                if (teammates.size > 1) {
                    for (i in 1 until teammates.size) {
                        plugin.teamManager.removeTeammate(player.uniqueId, teammates[i])
                    }
                    player.sendMessage(Component.text("Your team was too large for a Boss! Extra members have been removed.", NamedTextColor.DARK_RED))
                }
            }
        } else if (finalAmount < 21 && currentBoss == player.uniqueId) {
            // The boss lost points (died) and lost the title!
            currentBoss = null
            Bukkit.broadcast(Component.text("${player.name} has lost the Most Infamous title! The throne is empty.", NamedTextColor.YELLOW))
        }

        reputationData[player.uniqueId] = finalAmount
        updateTabList(player, finalAmount)
    }

    private fun updateTabList(player: Player, points: Int) {
        val titlePrefix = when {
            points >= 21 -> Component.text("[Most Infamous Player]", NamedTextColor.DARK_RED)
            points >= 16 -> Component.text("[Extremely Dangerous Player]", NamedTextColor.RED)
            points >= 11 -> Component.text("[Dangerous Player]", NamedTextColor.GOLD)
            points >= 6 -> Component.text("[Risky Player]", NamedTextColor.YELLOW)
            points > 0 -> Component.text("[Normal Player]", NamedTextColor.GREEN)
            points < 0 -> Component.text("[Honorable]", NamedTextColor.AQUA)
            else -> Component.text("[Normal Player]", NamedTextColor.GREEN)
        }

        val tabName = titlePrefix
            .append(Component.text(" | ", NamedTextColor.GRAY))
            .append(Component.text(player.name, NamedTextColor.WHITE))

        player.playerListName(tabName)
    }
}