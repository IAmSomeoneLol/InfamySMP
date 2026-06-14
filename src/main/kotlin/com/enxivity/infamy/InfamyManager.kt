// Data tracking class. Connected to InfamySMP.kt.
package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class InfamyManager(private val plugin: InfamySMP) {
    private val reputationData = mutableMapOf<UUID, Int>()
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

        if (finalAmount >= 21) {
            if (currentBoss != null && currentBoss != player.uniqueId) {
                finalAmount = 20
                player.sendMessage(Component.text("You reached 21 Infamy, but the Most Infamous title is held by someone else!", net.kyori.adventure.text.format.NamedTextColor.RED))
            } else if (currentBoss != player.uniqueId) {
                currentBoss = player.uniqueId
                Bukkit.broadcast(Component.text("${player.name} has become the Most Infamous Player!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED))

                val teammates = plugin.teamManager.getTeammates(player.uniqueId).toList()
                if (teammates.size > 1) {
                    for (i in 1 until teammates.size) {
                        plugin.teamManager.removeTeammate(player.uniqueId, teammates[i])
                    }
                    player.sendMessage(Component.text("Your team was too large for a Boss! Extra members have been removed.", net.kyori.adventure.text.format.NamedTextColor.DARK_RED))
                }
            }
        } else if (finalAmount < 21 && currentBoss == player.uniqueId) {
            currentBoss = null
            Bukkit.broadcast(Component.text("${player.name} has lost the Most Infamous title!", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
        }

        reputationData[player.uniqueId] = finalAmount
        updateTabList(player)
    }

    fun resetReputation(player: Player) {
        setReputation(player, 0)
    }

    fun updateTabList(player: Player) {
        val points = getRawReputation(player)
        val configPath = when {
            points >= 21 -> "prefixes.infamy_21"
            points >= 16 -> "prefixes.infamy_16"
            points >= 11 -> "prefixes.infamy_11"
            points >= 6 -> "prefixes.infamy_6"
            points > 0 -> "prefixes.infamy_1"
            points < 0 -> "prefixes.honor"
            else -> "prefixes.neutral"
        }

        val rawPrefix = plugin.config.getString(configPath, "&a[Normal]")!!
        val prefixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix)

        val tabName = prefixComponent
            .append(Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
            .append(Component.text(player.name, net.kyori.adventure.text.format.NamedTextColor.WHITE))

        player.playerListName(tabName)
    }
}