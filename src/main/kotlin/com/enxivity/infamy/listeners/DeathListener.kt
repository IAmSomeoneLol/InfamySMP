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
import org.bukkit.potion.PotionEffectType
import java.util.UUID

class DeathListener(private val plugin: InfamySMP) : Listener {
    private val recentKills = mutableMapOf<UUID, MutableList<Long>>()

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.player
        val killer = victim.killer
        val victimRep = plugin.infamyManager.getRawReputation(victim)

        if (plugin.combatListener.activeTrueInvis.contains(victim.uniqueId)) {
            plugin.combatListener.resyncEquipment(victim)
        }

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

        if (victimRep > 0) {
            val loseAmount = when { victimRep >= 21 -> 3; victimRep >= 11 -> 2; else -> 1 }
            plugin.infamyManager.setReputation(victim, Math.max(0, victimRep - loseAmount))
        }

        if (killer != null && killer.uniqueId != victim.uniqueId) {
            val now = System.currentTimeMillis()
            val logs = recentKills.computeIfAbsent(killer.uniqueId) { mutableListOf() }
            logs.removeIf { now - it > 1800000 }

            var awardPoints = 1
            if (plugin.teamManager.areTeammates(killer.uniqueId, victim.uniqueId)) {
                if (plugin.config.getBoolean("settings.betrayal.enabled", true)) {
                    awardPoints = plugin.config.getInt("settings.betrayal.points", 5)
                    killer.sendMessage(Component.text("You betrayed your teammate! Penalty bottle generated.", NamedTextColor.RED))
                    Bukkit.broadcast(Component.text("${killer.name} has killed their teammate ${victim.name} in a cold betrayal!", NamedTextColor.RED))
                    Bukkit.getOnlinePlayers().forEach { it.playSound(it.location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f) }

                    if (plugin.config.getBoolean("settings.betrayal.better-alternative", true)) {
                        val teamName = plugin.teamManager.playerTeams[killer.uniqueId]
                        if (teamName != null) {
                            plugin.teamManager.removePlayerHandleLeader(killer.uniqueId)
                            plugin.teamManager.banPlayerFromTeam(killer.uniqueId, teamName, 86400000L)
                            killer.sendMessage(Component.text("You have been kicked from the team and banned for 24 hours for betrayal!", NamedTextColor.DARK_RED))
                        }
                    }
                } else {
                    awardPoints = 0
                }
            }

            // Double Infamy event
            if (awardPoints == 1 && plugin.eventManager.isDoubleInfamyEnabled()) {
                if (Math.random() <= plugin.eventManager.getDoubleInfamyChance()) {
                    awardPoints += plugin.eventManager.getDoubleInfamyExtra()
                    killer.sendMessage(Component.text("Event Bonus: Extra Infamy awarded! (+$awardPoints Total)", NamedTextColor.GOLD))
                }
            }

            if (logs.size >= 3 && awardPoints > 0) {
                awardPoints = 0
                killer.sendMessage(Component.text("No points awarded.", NamedTextColor.YELLOW))
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

        if (plugin.infamyManager.hasAbility(victim, "bad_fortune", false)) {
            if (victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) event.deathMessage(null)
        }
    }
}