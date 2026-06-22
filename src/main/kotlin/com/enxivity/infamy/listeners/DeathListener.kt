package com.enxivity.infamy.listeners

import com.enxivity.infamy.InfamySMP
import com.enxivity.infamy.KillRecord
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID

class DeathListener(private val plugin: InfamySMP) : Listener {
    private val recentKills = mutableMapOf<UUID, MutableList<Long>>()

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.player
        val killer = victim.killer
        val victimRep = plugin.infamyManager.getRawReputation(victim)

        plugin.infamyManager.playerDeaths[victim.uniqueId] = (plugin.infamyManager.playerDeaths[victim.uniqueId] ?: 0) + 1
        var killIdStr: String? = null

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            plugin.infamyManager.playerKills[killer.uniqueId] = (plugin.infamyManager.playerKills[killer.uniqueId] ?: 0) + 1
            val killId = UUID.randomUUID()
            killIdStr = killId.toString()
            val locStr = "${victim.location.blockX}, ${victim.location.blockY}, ${victim.location.blockZ} (${victim.world.name})"
            val dropAsBottle = plugin.config.getBoolean("settings.drop-bottle-on-kill", true)

            val initialStatus = if (dropAsBottle) "DROPPED" else "WITHDRAWN"
            val record = KillRecord(killId, killer.uniqueId, killer.name, victim.uniqueId, victim.name, System.currentTimeMillis(), locStr, initialStatus)
            plugin.infamyManager.killHistory.add(record)
        }

        if (victimRep >= 21) {
            val killerName = killer?.name ?: "The Environment"
            val pureBottle = plugin.itemManager.createPureInfamyBottle(victim.name, killerName, killIdStr)
            val dropLoc = victim.location
            val droppedItem = victim.world.dropItemNaturally(dropLoc, pureBottle)
            droppedItem.isInvulnerable = true

            if (plugin.config.getBoolean("settings.boss-bottle-announce", true)) {
                val msg = Component.text("The Most Infamous Player has fallen! Listen closely to the wind for its location...", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                Bukkit.getOnlinePlayers().filter { plugin.infamyManager.getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(msg) }
            }
            if (plugin.config.getBoolean("settings.boss-bottle-sound", true)) {
                Bukkit.getOnlinePlayers().forEach { p ->
                    if (plugin.infamyManager.getSettings(p.uniqueId).globalSounds) {
                        if (p.world == dropLoc.world) {
                            p.playSound(dropLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 10000f, 1.0f)
                        } else {
                            p.playSound(p.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
                        }
                    }
                }
            }
        }

        val loseAmount = when { victimRep >= 21 -> 3; victimRep >= 11 -> 2; else -> 1 }
        plugin.infamyManager.setReputation(victim, victimRep - loseAmount)

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val now = System.currentTimeMillis()
            val logs = recentKills.computeIfAbsent(killer.uniqueId) { mutableListOf() }
            logs.removeIf { now - it > 1800000 }

            var awardPoints = 1
            if (plugin.teamManager.areTeammates(killer.uniqueId, victim.uniqueId)) {
                awardPoints = 5
                killer.sendMessage(Component.text("You betrayed your teammate! Penalty bottle generated.", NamedTextColor.RED))
            }

            if (logs.size >= 3) {
                awardPoints = 0
                killer.sendMessage(Component.text("Anti-Farm Triggered! No points awarded.", NamedTextColor.YELLOW))
            } else { logs.add(now) }

            if (awardPoints > 0) {
                if (plugin.config.getBoolean("settings.drop-bottle-on-kill", true)) {
                    val infamyBottle = plugin.itemManager.createInfamyBottle(awardPoints, null, null, killIdStr)
                    victim.world.dropItemNaturally(victim.location, infamyBottle).isInvulnerable = true
                } else {
                    val killerRep = plugin.infamyManager.getRawReputation(killer)
                    plugin.infamyManager.setReputation(killer, (killerRep + awardPoints).coerceAtMost(if (killerRep >= 21) 21 else 20))
                }
            }
        }

        if (victimRep >= 15 && plugin.config.getBoolean("infamy_abilities.bad_fortune.enabled", true)) {
            if (victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) event.deathMessage(null)
        }
    }
}