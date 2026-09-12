package com.enxivity.infamy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.time.Duration
import java.util.UUID

data class KillRecord(
    val id: UUID, val killer: UUID, val killerName: String,
    val victim: UUID, val victimName: String, val timestamp: Long,
    val location: String, var status: String, var holderInfo: String? = null
)

data class PlayerSettings(
    var globalSounds: Boolean = true,
    var globalMessages: Boolean = true,
    var abilityMessages: Boolean = true,
    var cooldownMessages: Boolean = false,
    var teamMessages: Boolean = true,
    var abilityMessagesInChat: Boolean = false,
    var scoreboardMode: String = "OFF",
    var sbTeam: Boolean = true,
    var sbTeammates: Boolean = true,
    var sbKills: Boolean = true,
    var sbDeaths: Boolean = true,
    var sbKDR: Boolean = true,
    var sbPoints: Boolean = true,
    var sbOnline: Boolean = true
)

class InfamyManager(private val plugin: InfamySMP) {
    private val reputationData = mutableMapOf<UUID, Int>()
    var currentBoss: UUID? = null
    var forceUnlock21: Boolean = false
    var is21LockedByPure: Boolean = false
    var isPureBottleBypass: Boolean = false

    val playerKills = mutableMapOf<UUID, Int>()
    val playerDeaths = mutableMapOf<UUID, Int>()
    val withdrawnPoints = mutableMapOf<UUID, Int>()
    val killHistory = mutableListOf<KillRecord>()
    val playerSettings = mutableMapOf<UUID, PlayerSettings>()

    fun hasAbility(player: Player, ability: String, isHonor: Boolean): Boolean {
        val type = if (isHonor) "honor_abilities" else "infamy_abilities"
        if (!plugin.config.getBoolean("$type.$ability.enabled", true)) return false
        val reqLvl = plugin.config.getInt("$type.$ability.level", 99)
        val pLvl = if (isHonor) getHonor(player) else getRawReputation(player)
        return pLvl >= reqLvl
    }

    fun saveData() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val file = File(plugin.dataFolder, "data.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        config.set("reputation", reputationData.mapKeys { it.key.toString() })
        config.set("boss", currentBoss?.toString())
        config.set("is21LockedByPure", is21LockedByPure)
        config.set("kills", playerKills.mapKeys { it.key.toString() })
        config.set("deaths", playerDeaths.mapKeys { it.key.toString() })
        config.set("withdrawn", withdrawnPoints.mapKeys { it.key.toString() })

        val settingsMap = playerSettings.mapValues {
            mapOf(
                "sounds" to it.value.globalSounds, "msgs" to it.value.globalMessages, "ability" to it.value.abilityMessages,
                "cd" to it.value.cooldownMessages, "team" to it.value.teamMessages, "abilityChat" to it.value.abilityMessagesInChat,
                "sbMode" to it.value.scoreboardMode, "sbTeam" to it.value.sbTeam, "sbTeammates" to it.value.sbTeammates,
                "sbKills" to it.value.sbKills, "sbDeaths" to it.value.sbDeaths, "sbKDR" to it.value.sbKDR,
                "sbPoints" to it.value.sbPoints, "sbOnline" to it.value.sbOnline
            )
        }
        config.set("settings", settingsMap.mapKeys { it.key.toString() })

        val historyMap = killHistory.map {
            mapOf("id" to it.id.toString(), "killer" to it.killer.toString(), "killerName" to it.killerName, "victim" to it.victim.toString(), "victimName" to it.victimName, "timestamp" to it.timestamp, "location" to it.location, "status" to it.status, "holderInfo" to it.holderInfo)
        }
        config.set("killHistory", historyMap)
        config.save(file)
    }

    fun loadData() {
        val file = File(plugin.dataFolder, "data.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)

        config.getConfigurationSection("reputation")?.getValues(false)?.forEach { (k, v) -> reputationData[UUID.fromString(k)] = (v as Number).toInt() }
        config.getString("boss")?.let { currentBoss = UUID.fromString(it) }
        is21LockedByPure = config.getBoolean("is21LockedByPure", false)
        config.getConfigurationSection("kills")?.getValues(false)?.forEach { (k, v) -> playerKills[UUID.fromString(k)] = (v as Number).toInt() }
        config.getConfigurationSection("deaths")?.getValues(false)?.forEach { (k, v) -> playerDeaths[UUID.fromString(k)] = (v as Number).toInt() }
        config.getConfigurationSection("withdrawn")?.getValues(false)?.forEach { (k, v) -> withdrawnPoints[UUID.fromString(k)] = (v as Number).toInt() }

        config.getConfigurationSection("settings")?.getKeys(false)?.forEach { k ->
            val sec = config.getConfigurationSection("settings.$k")
            if (sec != null) {
                playerSettings[UUID.fromString(k)] = PlayerSettings(
                    sec.getBoolean("sounds", true), sec.getBoolean("msgs", true), sec.getBoolean("ability", true),
                    sec.getBoolean("cd", false), sec.getBoolean("team", true), sec.getBoolean("abilityChat", false),
                    sec.getString("sbMode", "OFF")!!, sec.getBoolean("sbTeam", true), sec.getBoolean("sbTeammates", true),
                    sec.getBoolean("sbKills", true), sec.getBoolean("sbDeaths", true), sec.getBoolean("sbKDR", true),
                    sec.getBoolean("sbPoints", true), sec.getBoolean("sbOnline", true)
                )
            }
        }

        config.getMapList("killHistory").forEach { map ->
            try {
                killHistory.add(KillRecord(UUID.fromString(map["id"] as String), UUID.fromString(map["killer"] as String), map["killerName"] as String, UUID.fromString(map["victim"] as String), map["victimName"] as String, (map["timestamp"] as Number).toLong(), map["location"] as String, map["status"] as String, map["holderInfo"] as? String))
            } catch (e: Exception) { }
        }
    }

    fun getSettings(uuid: UUID): PlayerSettings = playerSettings.getOrPut(uuid) { PlayerSettings() }
    fun getRawReputation(player: Player): Int = reputationData.getOrDefault(player.uniqueId, 0)
    fun getRawReputationByUUID(uuid: UUID): Int = reputationData.getOrDefault(uuid, 0)
    fun getInfamy(player: Player): Int = getRawReputation(player).let { if (it > 0) it else 0 }
    fun getHonor(player: Player): Int = getRawReputation(player).let { if (it < 0) -it else 0 }

    fun getPrefixText(points: Int): String {
        val configPath = when {
            points > 0 -> "prefixes.infamy_$points"
            points < 0 -> "prefixes.honor_${-points}"
            else -> "prefixes.neutral"
        }
        return plugin.config.getString(configPath) ?: plugin.config.getString(
            when {
                points >= 21 -> "prefixes.infamy_21"
                points >= 16 -> "prefixes.infamy_16"
                points >= 11 -> "prefixes.infamy_11"
                points >= 6 -> "prefixes.infamy_6"
                points > 0 -> "prefixes.infamy_1"
                points < 0 -> "prefixes.honor"
                else -> "prefixes.neutral"
            }, "&a[Normal]"
        )!!
    }

    fun refreshBossLock() {
        forceUnlock21 = true
        is21LockedByPure = false
    }

    fun forceRemoveBoss() {
        val bossId = currentBoss ?: return
        currentBoss = null
        reputationData[bossId] = 20
        val p = Bukkit.getPlayer(bossId)
        if (p != null) {
            p.sendMessage(Component.text("Your Most Infamous title has been forcefully removed by an admin! You are now Level 20.", net.kyori.adventure.text.format.NamedTextColor.RED))
            updateTabList(p)
        }
        saveData()
    }

    fun promoteToLevel21ViaPure(player: Player) {
        isPureBottleBypass = true
        setReputation(player, 21)
        isPureBottleBypass = false
    }

    fun setReputation(player: Player, amount: Int) {
        val oldPrefix = getPrefixText(getRawReputation(player))
        val finalAmount = amount.coerceIn(-21, 21)

        if (finalAmount >= 21) {
            val mainHand = player.inventory.itemInMainHand
            val offHand = player.inventory.itemInOffHand
            val holdingPure = (mainHand.hasItemMeta() && mainHand.itemMeta.hasDisplayName() &&
                    LegacyComponentSerializer.legacyAmpersand().serialize(mainHand.itemMeta.displayName()!!).contains("Pure Infamy", ignoreCase = true)) ||
                    (offHand.hasItemMeta() && offHand.itemMeta.hasDisplayName() &&
                            LegacyComponentSerializer.legacyAmpersand().serialize(offHand.itemMeta.displayName()!!).contains("Pure Infamy", ignoreCase = true))

            val hasBypassPermitted = isPureBottleBypass || holdingPure

            if (is21LockedByPure && !hasBypassPermitted) {
                player.sendMessage(Component.text("Level 21 is currently locked! You must consume a Pure Infamy Bottle to ascend.", net.kyori.adventure.text.format.NamedTextColor.RED))
                reputationData[player.uniqueId] = 20
                updateTabList(player)
                return
            }

            if (currentBoss != null && currentBoss != player.uniqueId) {
                if (forceUnlock21) {
                    reputationData[player.uniqueId] = 21
                    forceUnlock21 = false
                    is21LockedByPure = true
                    Bukkit.getOnlinePlayers().filter { getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(Component.text("${player.name} has ascended to Level 21!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED)) }

                    val team = plugin.teamManager.getTeam(player.uniqueId)
                    if (team != null && team.members.size > 2) {
                        plugin.teamManager.removePlayerHandleLeader(player.uniqueId)
                        player.sendMessage(Component.text("Your team was too large, so you have been cast out. The Boss can only have 1 teammate.", net.kyori.adventure.text.format.NamedTextColor.DARK_RED))
                    }
                } else {
                    player.sendMessage(Component.text("You reached 21 Infamy, but the title is locked by the current Boss!", net.kyori.adventure.text.format.NamedTextColor.RED))
                    reputationData[player.uniqueId] = 20
                }
            } else if (currentBoss != player.uniqueId) {
                currentBoss = player.uniqueId
                is21LockedByPure = true
                Bukkit.getOnlinePlayers().filter { getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(Component.text("${player.name} has become the Most Infamous Player!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED)) }

                val team = plugin.teamManager.getTeam(player.uniqueId)
                if (team != null && team.members.size > 2) {
                    plugin.teamManager.removePlayerHandleLeader(player.uniqueId)
                    player.sendMessage(Component.text("Your team was too large, so you have been cast out. The Boss can only have 1 teammate.", net.kyori.adventure.text.format.NamedTextColor.DARK_RED))
                }
                reputationData[player.uniqueId] = 21
            } else {
                reputationData[player.uniqueId] = 21
                is21LockedByPure = true
            }
        } else {
            if (finalAmount < 21 && currentBoss == player.uniqueId) {
                currentBoss = null
                Bukkit.getOnlinePlayers().filter { getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(Component.text("${player.name} has lost the Most Infamous title!", net.kyori.adventure.text.format.NamedTextColor.YELLOW)) }
            }
            reputationData[player.uniqueId] = finalAmount
        }

        val newPrefix = getPrefixText(reputationData[player.uniqueId] ?: 0)

        if (oldPrefix != newPrefix) {
            val titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(newPrefix)
            val subComp = Component.text("You've earned a new title with your levels.", net.kyori.adventure.text.format.NamedTextColor.GRAY)
            val times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(4000), Duration.ofMillis(1000))

            val fullTitle = Title.title(titleComp, subComp, times)
            player.showTitle(fullTitle)

            Bukkit.getOnlinePlayers().forEach { p ->
                if (getSettings(p.uniqueId).globalSounds) {
                    p.playSound(p.location, org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 1.0f)
                }
            }
        }

        updateTabList(player)
    }

    fun resetReputation(player: Player) = setReputation(player, 0)

    fun updateTabList(player: Player) {
        val rawPrefix = getPrefixText(getRawReputation(player))
        val prefixComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix)

        val team = plugin.teamManager.getTeam(player.uniqueId)
        val nameFormat = team?.colorFormat ?: "&f"

        val formattedName = LegacyComponentSerializer.legacyAmpersand().deserialize("$nameFormat${player.name}")

        val tabName = prefixComponent.append(Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.GRAY)).append(formattedName)
        player.playerListName(tabName)
    }
}