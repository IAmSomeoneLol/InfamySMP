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
    val location: String, var status: String
)

data class PlayerSettings(
    var globalSounds: Boolean = true,
    var globalMessages: Boolean = true,
    var abilityMessages: Boolean = true,
    var cooldownMessages: Boolean = false,
    var teamMessages: Boolean = true,
    var abilityMessagesInChat: Boolean = false
)

class InfamyManager(private val plugin: InfamySMP) {
    private val reputationData = mutableMapOf<UUID, Int>()
    var currentBoss: UUID? = null
    var forceUnlock21: Boolean = false

    val playerKills = mutableMapOf<UUID, Int>()
    val playerDeaths = mutableMapOf<UUID, Int>()
    val withdrawnPoints = mutableMapOf<UUID, Int>()
    val killHistory = mutableListOf<KillRecord>()
    val playerSettings = mutableMapOf<UUID, PlayerSettings>()

    fun saveData() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val file = File(plugin.dataFolder, "data.yml")
        val config = YamlConfiguration.loadConfiguration(file)

        config.set("reputation", reputationData.mapKeys { it.key.toString() })
        config.set("boss", currentBoss?.toString())
        config.set("kills", playerKills.mapKeys { it.key.toString() })
        config.set("deaths", playerDeaths.mapKeys { it.key.toString() })
        config.set("withdrawn", withdrawnPoints.mapKeys { it.key.toString() })

        val settingsMap = playerSettings.mapValues {
            mapOf("sounds" to it.value.globalSounds, "msgs" to it.value.globalMessages, "ability" to it.value.abilityMessages, "cd" to it.value.cooldownMessages, "team" to it.value.teamMessages, "abilityChat" to it.value.abilityMessagesInChat)
        }
        config.set("settings", settingsMap.mapKeys { it.key.toString() })

        val historyMap = killHistory.map {
            mapOf("id" to it.id.toString(), "killer" to it.killer.toString(), "killerName" to it.killerName, "victim" to it.victim.toString(), "victimName" to it.victimName, "timestamp" to it.timestamp, "location" to it.location, "status" to it.status)
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
        config.getConfigurationSection("kills")?.getValues(false)?.forEach { (k, v) -> playerKills[UUID.fromString(k)] = (v as Number).toInt() }
        config.getConfigurationSection("deaths")?.getValues(false)?.forEach { (k, v) -> playerDeaths[UUID.fromString(k)] = (v as Number).toInt() }
        config.getConfigurationSection("withdrawn")?.getValues(false)?.forEach { (k, v) -> withdrawnPoints[UUID.fromString(k)] = (v as Number).toInt() }

        config.getConfigurationSection("settings")?.getKeys(false)?.forEach { k ->
            val sec = config.getConfigurationSection("settings.$k")
            if (sec != null) playerSettings[UUID.fromString(k)] = PlayerSettings(sec.getBoolean("sounds", true), sec.getBoolean("msgs", true), sec.getBoolean("ability", true), sec.getBoolean("cd", false), sec.getBoolean("team", true), sec.getBoolean("abilityChat", false))
        }

        config.getMapList("killHistory").forEach { map ->
            try {
                killHistory.add(KillRecord(UUID.fromString(map["id"] as String), UUID.fromString(map["killer"] as String), map["killerName"] as String, UUID.fromString(map["victim"] as String), map["victimName"] as String, (map["timestamp"] as Number).toLong(), map["location"] as String, map["status"] as String))
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
            points >= 21 -> "prefixes.infamy_21"
            points >= 16 -> "prefixes.infamy_16"
            points >= 11 -> "prefixes.infamy_11"
            points >= 6 -> "prefixes.infamy_6"
            points > 0 -> "prefixes.infamy_1"
            points < 0 -> "prefixes.honor"
            else -> "prefixes.neutral"
        }
        return plugin.config.getString(configPath, "&a[Normal]")!!
    }

    fun refreshBossLock() {
        forceUnlock21 = true
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

    fun setReputation(player: Player, amount: Int) {
        val oldPrefix = getPrefixText(getRawReputation(player))
        val finalAmount = amount.coerceIn(-12, 21)

        if (finalAmount >= 21) {
            if (currentBoss != null && currentBoss != player.uniqueId) {
                if (forceUnlock21) {
                    reputationData[player.uniqueId] = 21
                    forceUnlock21 = false
                    Bukkit.getOnlinePlayers().filter { getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(Component.text("${player.name} has ascended to Level 21!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED)) }

                    // FIXED: Now properly evaluates team size before kicking
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
                Bukkit.getOnlinePlayers().filter { getSettings(it.uniqueId).globalMessages }.forEach { it.sendMessage(Component.text("${player.name} has become the Most Infamous Player!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED)) }

                // FIXED: Evaluates team size
                val team = plugin.teamManager.getTeam(player.uniqueId)
                if (team != null && team.members.size > 2) {
                    plugin.teamManager.removePlayerHandleLeader(player.uniqueId)
                    player.sendMessage(Component.text("Your team was too large, so you have been cast out. The Boss can only have 1 teammate.", net.kyori.adventure.text.format.NamedTextColor.DARK_RED))
                }
                reputationData[player.uniqueId] = 21
            } else {
                reputationData[player.uniqueId] = 21
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

        plugin.teamManager.syncAllScoreboards()
    }
}