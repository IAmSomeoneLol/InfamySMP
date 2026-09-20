package com.enxivity.infamy

import com.enxivity.infamy.commands.InfamyCommand
import com.enxivity.infamy.listeners.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Dedicated holder so vanilla Ender Chests are NEVER affected
class ECSnapshotHolder(val snapshotId: String, val ownerName: String) : InventoryHolder {
    private var inv: Inventory? = null
    fun setInventory(inventory: Inventory) { this.inv = inventory }
    override fun getInventory(): Inventory = inv ?: Bukkit.createInventory(this, 27)
}

data class ECSnapshot(val ownerName: String, val items: Array<ItemStack?>, val timestamp: Long)

class InfamySMP : JavaPlugin(), Listener {
    lateinit var infamyManager: InfamyManager
    lateinit var itemManager: ItemManager
    lateinit var teamManager: TeamManager
    lateinit var eventManager: EventManager
    lateinit var combatListener: CombatListener
    lateinit var itemRestrictionsListener: ItemRestrictionsListener

    val ecSnapshots = ConcurrentHashMap<String, ECSnapshot>()

    // Tracks whether player's last hit was on a player (PvP = true, PvE/Mob = false)
    val pvpDebuffActive = mutableMapOf<UUID, Boolean>()
    lateinit var penaltyKey: NamespacedKey

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
        reloadConfig()

        penaltyKey = NamespacedKey(this, "honor_weapon_penalty")

        itemManager = ItemManager(this)
        infamyManager = InfamyManager(this)
        teamManager = TeamManager(this)
        eventManager = EventManager(this)

        infamyManager.loadData()
        teamManager.loadData()
        eventManager.loadConfig()

        combatListener = CombatListener(this)
        itemRestrictionsListener = ItemRestrictionsListener(this)

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(DeathListener(this), this)
        server.pluginManager.registerEvents(BottleInteractListener(this), this)
        server.pluginManager.registerEvents(combatListener, this)
        server.pluginManager.registerEvents(PerkListener(this), this)
        server.pluginManager.registerEvents(BlockBreakListener(this), this)
        server.pluginManager.registerEvents(HonorDeedListener(this), this)
        server.pluginManager.registerEvents(itemRestrictionsListener, this)

        val infamyCmd = InfamyCommand(this)
        getCommand("infamy")?.setExecutor(infamyCmd)
        getCommand("infamy")?.setTabCompleter(infamyCmd)

        registerElytraRecipe()
        registerShulkerBoxRecipe()

        val hellcrushArmorKey = NamespacedKey(this, "hellcrush_armor_penalty")
        val hellcrushToughKey = NamespacedKey(this, "hellcrush_toughness_penalty")

        val lastHeldItems = mutableMapOf<UUID, Material>()
        val lastHonorLevels = mutableMapOf<UUID, Int>()

        // Scoreboard & Passives loop
        server.scheduler.runTaskTimer(this, Runnable {
            val showParticles = config.getBoolean("settings.show-ability-particles", true)
            val hcBaseReduction = config.getDouble("abilities-config.hellcrush.base-stat-reduction-percentage", 0.4)

            fun getEffect(path: String, default: String): PotionEffectType {
                val str = config.getString(path, default)!!.lowercase()
                return Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(str)) ?: PotionEffectType.GLOWING
            }

            val l15Type = getEffect("abilities-config.passives.level-15.effect", "weaving")
            val l15Amp = config.getInt("abilities-config.passives.level-15.amplifier", 0)

            val l20Type = getEffect("abilities-config.passives.level-20.effect", "strength")
            val l20Amp = config.getInt("abilities-config.passives.level-20.amplifier", 0)

            val l21Type = getEffect("abilities-config.passives.level-21-boss.effect", "glowing")
            val l21Amp = config.getInt("abilities-config.passives.level-21-boss.amplifier", 0)

            val hotvType = getEffect("abilities-config.passives.hero-of-the-village.effect", "hero_of_the_village")

            val hcActType = getEffect("abilities-config.hellcrush.active-effect.effect", "strength")
            val hcActAmp = config.getInt("abilities-config.hellcrush.active-effect.amplifier", 2)

            for (player in server.onlinePlayers) {
                val settings = infamyManager.getSettings(player.uniqueId)
                val mode = settings.scoreboardMode
                val board = player.scoreboard

                if (board == server.scoreboardManager.mainScoreboard) {
                    player.scoreboard = server.scoreboardManager.newScoreboard
                }

                if (mode == "OFF") {
                    player.scoreboard.getObjective("inf_sb")?.unregister()
                } else {
                    val obj = player.scoreboard.getObjective("inf_sb") ?: player.scoreboard.registerNewObjective("inf_sb", "dummy", Component.empty()).apply { displaySlot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR }
                    val lines = mutableListOf<String>()

                    when (mode) {
                        "TEAM" -> {
                            obj.displayName(Component.text(" Team Coords ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            val team = teamManager.getTeam(player.uniqueId)
                            if (team == null) {
                                lines.add("§cYou are not in a team.")
                            } else {
                                team.members.forEach { m ->
                                    val member = Bukkit.getPlayer(m)
                                    if (member != null) {
                                        val dim = member.world.name.replace("world_nether", "§cNether").replace("world_the_end", "§5End").replace("world", "§aOverworld")
                                        val dist = if (member.world == player.world) " §8[${member.location.distance(player.location).toInt()}m]" else ""
                                        lines.add("§f${member.name.take(6)}: §8${member.location.blockX}, ${member.location.blockY}, ${member.location.blockZ} §7($dim)§r$dist".take(40))
                                    } else {
                                        lines.add("§7${Bukkit.getOfflinePlayer(m).name?.take(6)}: Offline")
                                    }
                                }
                            }
                        }
                        "COOLDOWN" -> {
                            obj.displayName(Component.text(" Cooldowns ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                            val now = System.currentTimeMillis()

                            fun getRem(map: Map<UUID, Long>, cdSecs: Long): String {
                                val lastUsed = map[player.uniqueId] ?: 0
                                val rem = (cdSecs * 1000L) - (now - lastUsed)
                                return if (rem > 0) "§c${rem / 1000}s" else "§aReady"
                            }

                            if (infamyManager.hasAbility(player, "sword_block", false)) lines.add("§7Sword Block: ${getRem(combatListener.swordBlockCooldowns, config.getLong("abilities-config.sword-block.cooldown-seconds", 60))}")
                            if (infamyManager.hasAbility(player, "shield_recovery", false)) lines.add("§7Shield Recov: ${getRem(combatListener.shieldAbilityCooldowns, config.getLong("abilities-config.shield-recovery.cooldown-seconds", 25))}")
                            if (infamyManager.hasAbility(player, "shield_sacrifice", false)) lines.add("§7Shield Sacrf: ${getRem(combatListener.shieldSacrificeCooldowns, config.getLong("abilities-config.shield-sacrifice.cooldown-seconds", 300))}")
                            if (infamyManager.hasAbility(player, "axe_stagger", false)) lines.add("§7Axe Stagger: ${getRem(combatListener.axeStaggerCooldowns, config.getLong("abilities-config.axe-stagger.cooldown-seconds", 45))}")
                            if (infamyManager.hasAbility(player, "bleeding_edge", false)) lines.add("§7Bleeding Edge: ${getRem(combatListener.bleedCooldowns, config.getLong("abilities-config.bleeding-edge.cooldown-seconds", 60))}")
                            if (infamyManager.hasAbility(player, "mace_slam", false)) lines.add("§7Mace Slam: ${getRem(combatListener.maceCooldowns, config.getLong("abilities-config.mace-slam.cooldown-seconds", 60))}")
                            if (infamyManager.hasAbility(player, "boss_sacrifice", false)) lines.add("§7Hellcrush: ${getRem(combatListener.sacrificeCooldowns, config.getLong("abilities-config.hellcrush.cooldown-seconds", 900))}")

                            if (infamyManager.hasAbility(player, "hunger_absorption", true)) lines.add("§7Sat. Shield: ${getRem(combatListener.honorAbsorbCooldowns, config.getLong("abilities-config.hunger-absorption.cooldown-seconds", 60))}")
                            if (infamyManager.hasAbility(player, "true_invisibility", true)) lines.add("§7True Invis: ${getRem(combatListener.honorInvisCooldowns, config.getLong("abilities-config.true-invisibility.cooldown-seconds", 300))}")
                            if (infamyManager.hasAbility(player, "karma_delay", true)) lines.add("§7Karma: ${getRem(combatListener.karmaCooldowns, config.getLong("abilities-config.karma-delay.cooldown-seconds", 180))}")

                            if (lines.isEmpty()) lines.add("§7No active abilities unlocked.")
                        }
                        "MAIN" -> {
                            obj.displayName(Component.text("InfamySMP", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                            lines.add("§8§m-------------------")

                            val t = teamManager.getTeam(player.uniqueId)
                            if (settings.sbTeam) {
                                lines.add("▫ §c☈ Team: §f${t?.name ?: "None"}")
                            }
                            if (settings.sbTeammates) {
                                val online = t?.members?.count { Bukkit.getPlayer(it) != null } ?: 0
                                val total = t?.members?.size ?: 0
                                lines.add("  §a╰ $online/$total")
                            }

                            lines.add("§1") // Unique blank spacer 1

                            if (settings.sbKills) lines.add("▫ §c⚔ Kills: §a${infamyManager.playerKills[player.uniqueId] ?: 0}")
                            if (settings.sbDeaths) lines.add("▫ §c☠ Deaths: §c${infamyManager.playerDeaths[player.uniqueId] ?: 0}")
                            if (settings.sbKDR) {
                                val k = infamyManager.playerKills[player.uniqueId] ?: 0
                                val d = infamyManager.playerDeaths[player.uniqueId] ?: 0
                                val ratio = if (d == 0) k.toDouble() else String.format("%.2f", k.toDouble() / d).toDouble()
                                lines.add("▫ §c÷ KDR: §e$ratio")
                            }
                            if (settings.sbPoints) {
                                val rep = infamyManager.getRawReputation(player)
                                if (rep > 0) lines.add("▫ §c⚡ Path: §cInfamy §8(§f$rep§8)")
                                else if (rep < 0) lines.add("▫ §c⚡ Path: §bHonor §8(§f${-rep}§8)")
                                else lines.add("▫ §c⚡ Path: §aNo Points")
                            }

                            lines.add("§2") // Unique blank spacer 2

                            if (settings.sbOnline) lines.add("▫ §c👥 Online: §a${Bukkit.getOnlinePlayers().size}")
                            lines.add("§8§m------------------")
                        }
                    }

                    val currentEntries = player.scoreboard.entries.filter { obj.getScore(it).isScoreSet }
                    currentEntries.forEach { if (!lines.contains(it)) player.scoreboard.resetScores(it) }

                    var score = 15
                    lines.forEach { line ->
                        val safeLine = line.take(40)
                        if (obj.getScore(safeLine).score != score) {
                            obj.getScore(safeLine).score = score
                        }
                        score--
                    }
                }

                if (infamyManager.hasAbility(player, "passive_resistance", false)) {
                    player.addPotionEffect(PotionEffect(l15Type, 60, l15Amp, true, false, false))
                }
                if (infamyManager.hasAbility(player, "boss_sacrifice", false)) {
                    player.addPotionEffect(PotionEffect(l21Type, 60, l21Amp, true, false, false))
                }

                if (infamyManager.hasAbility(player, "mace_slam", false)) {
                    if (combatListener.activeSacrifices.contains(player.uniqueId)) {
                        player.addPotionEffect(PotionEffect(hcActType, 60, hcActAmp, true, false, false))

                        if (showParticles) {
                            try {
                                player.world.spawnParticle(org.bukkit.Particle.valueOf("TRIAL_SPAWNER_DETECTION"), player.location.add(0.0, 1.0, 0.0), 10, 0.4, 0.8, 0.4, 0.02)
                            } catch (e: Exception) {
                                player.world.spawnParticle(org.bukkit.Particle.FLAME, player.location.add(0.0, 1.0, 0.0), 10, 0.4, 0.8, 0.4, 0.02)
                            }
                        }
                    } else {
                        player.addPotionEffect(PotionEffect(l20Type, 60, l20Amp, true, false, false))
                    }
                }

                val armorAttr = player.getAttribute(Attribute.ARMOR)
                val toughAttr = player.getAttribute(Attribute.ARMOR_TOUGHNESS)

                if (combatListener.activeSacrifices.contains(player.uniqueId)) {
                    var aVal = 0.0
                    var tVal = 0.0
                    val helm = player.inventory.helmet
                    if (helm != null) {
                        when (helm.type) {
                            Material.LEATHER_HELMET -> aVal = 1.0
                            Material.GOLDEN_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET, Material.TURTLE_HELMET -> aVal = 2.0
                            Material.DIAMOND_HELMET -> { aVal = 3.0; tVal = 2.0 }
                            Material.NETHERITE_HELMET -> { aVal = 3.0; tVal = 3.0 }
                            else -> {}
                        }
                    }
                    armorAttr?.modifiers?.find { it.key == hellcrushArmorKey }?.let { armorAttr.removeModifier(it) }
                    toughAttr?.modifiers?.find { it.key == hellcrushToughKey }?.let { toughAttr.removeModifier(it) }

                    if (aVal > 0) armorAttr?.addModifier(AttributeModifier(hellcrushArmorKey, -(aVal * hcBaseReduction), AttributeModifier.Operation.ADD_NUMBER))
                    if (tVal > 0) toughAttr?.addModifier(AttributeModifier(hellcrushToughKey, -(tVal * hcBaseReduction), AttributeModifier.Operation.ADD_NUMBER))
                } else {
                    armorAttr?.modifiers?.find { it.key == hellcrushArmorKey }?.let { armorAttr.removeModifier(it) }
                    toughAttr?.modifiers?.find { it.key == hellcrushToughKey }?.let { toughAttr.removeModifier(it) }
                }

                if (infamyManager.hasAbility(player, "hero_of_the_village", true)) {
                    val honor = infamyManager.getHonor(player)
                    val hotvLevel = when { honor >= 12 -> 2; honor >= 9 -> 1; honor >= 6 -> 0; else -> -1 }
                    if (hotvLevel >= 0) player.addPotionEffect(PotionEffect(hotvType, 100, hotvLevel, true, false, false))
                }

                val currentItem = player.inventory.itemInMainHand.type
                val uuid = player.uniqueId
                val honor = infamyManager.getHonor(player)

                if (lastHeldItems[uuid] != currentItem || lastHonorLevels[uuid] != honor) {
                    lastHeldItems[uuid] = currentItem
                    lastHonorLevels[uuid] = honor
                    updateWeaponCooldownPenalty(player)
                }
            }

            val pureEnabled = config.getBoolean("settings.bottle-particles.pure.enabled", true)
            val pureParticleStr = config.getString("settings.bottle-particles.pure.particle", "DUST")?.uppercase() ?: "DUST"
            val infamyEnabled = config.getBoolean("settings.bottle-particles.infamy.enabled", true)
            val infamyParticleStr = config.getString("settings.bottle-particles.infamy.particle", "END_ROD")?.uppercase() ?: "END_ROD"
            val honorEnabled = config.getBoolean("settings.bottle-particles.honor.enabled", true)
            val honorParticleStr = config.getString("settings.bottle-particles.honor.particle", "GLOW")?.uppercase() ?: "GLOW"

            for (world in server.worlds) {
                for (item in world.getEntitiesByClass(org.bukkit.entity.Item::class.java)) {
                    val pdc = item.itemStack.itemMeta?.persistentDataContainer ?: continue
                    try {
                        if (pureEnabled && pdc.has(itemManager.bossKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            val p = org.bukkit.Particle.valueOf(pureParticleStr)
                            if (p == org.bukkit.Particle.DUST) world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 3, 0.15, 0.15, 0.15, 0.0, org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.2f))
                            else world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 3, 0.15, 0.15, 0.15, 0.02)
                        }
                        else if (infamyEnabled && pdc.has(itemManager.infamyKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            val p = org.bukkit.Particle.valueOf(infamyParticleStr)
                            if (p == org.bukkit.Particle.DUST) world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 2, 0.15, 0.15, 0.15, 0.0, org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.2f))
                            else world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 2, 0.15, 0.15, 0.15, 0.02)
                        }
                        else if (honorEnabled && pdc.has(itemManager.honorKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            val p = org.bukkit.Particle.valueOf(honorParticleStr)
                            if (p == org.bukkit.Particle.DUST) world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 3, 0.15, 0.15, 0.15, 0.0, org.bukkit.Particle.DustOptions(org.bukkit.Color.AQUA, 1.2f))
                            else world.spawnParticle(p, item.location.add(0.0, 0.4, 0.0), 3, 0.15, 0.15, 0.15, 0.02)
                        }
                    } catch (e: Exception) { }
                }
            }
        }, 0L, 20L)

        // Event scheduler
        var secondCounter = 0
        server.scheduler.runTaskTimer(this, Runnable {
            eventManager.tickSecond()
            if (++secondCounter >= 60) {
                secondCounter = 0
                combatListener.cleanExpiredCooldowns()
            }
        }, 0L, 20L)
    }

    // Dynamically updates weapon cooldowns (Default vanilla for mobs, Slow cooldown for PvP)
    fun updateWeaponCooldownPenalty(player: Player) {
        val attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED) ?: return
        attackSpeedAttr.modifiers.find { it.key == penaltyKey }?.let { attackSpeedAttr.removeModifier(it) }

        // Only apply penalty if the ability is unlocked AND the last hit was on a player
        if (infamyManager.hasAbility(player, "weapon_cooldowns", true) && pvpDebuffActive[player.uniqueId] == true) {
            val currentItem = player.inventory.itemInMainHand.type
            val swordPenalty = config.getDouble("abilities-config.weapon-fatigue.sword-penalty", -0.3)
            val axePenalty = config.getDouble("abilities-config.weapon-fatigue.axe-penalty", -0.2)

            val penalty = when {
                currentItem.name.endsWith("_SWORD") -> swordPenalty
                currentItem.name.endsWith("_AXE") -> axePenalty
                else -> 0.0
            }
            if (penalty < 0) {
                attackSpeedAttr.addModifier(AttributeModifier(penaltyKey, penalty, AttributeModifier.Operation.ADD_NUMBER))
            }
        }
    }

    override fun onDisable() {
        if (::combatListener.isInitialized) {
            server.onlinePlayers.forEach {
                combatListener.restoreHelmet(it)
                if (combatListener.activeTrueInvis.contains(it.uniqueId)) {
                    combatListener.resyncEquipment(it)
                }
            }
        }
        infamyManager.saveData()
        teamManager.saveData()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        teamManager.syncAllScoreboards()
        infamyManager.updateTabList(event.player)
    }

    fun openEnderChestSnapshot(viewer: Player, snapshotId: String) {
        val snapshot = ecSnapshots[snapshotId]
        if (snapshot == null) {
            viewer.sendMessage(Component.text("This Ender Chest snapshot has expired or does not exist.", NamedTextColor.RED))
            return
        }
        val holder = ECSnapshotHolder(snapshotId, snapshot.ownerName)
        val inv = Bukkit.createInventory(holder, 27, Component.text("${snapshot.ownerName}'s Ender Chest", NamedTextColor.DARK_GRAY))
        holder.setInventory(inv)
        for (i in 0 until 27.coerceAtMost(snapshot.items.size)) {
            inv.setItem(i, snapshot.items[i]?.clone())
        }
        viewer.openInventory(inv)
    }

    private fun getItemRarityColor(item: ItemStack): TextColor {
        if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
            val disp = item.itemMeta.displayName()
            val c = disp?.color()
            if (c != null) return c
        }
        return try {
            when (item.rarity.name) {
                "UNCOMMON" -> NamedTextColor.YELLOW
                "RARE" -> NamedTextColor.AQUA
                "EPIC" -> NamedTextColor.LIGHT_PURPLE
                else -> NamedTextColor.WHITE
            }
        } catch (e: Throwable) {
            NamedTextColor.WHITE
        }
    }

    fun buildChatComponent(sender: Player, rawMessage: String, defaultColor: TextColor? = null): Component {
        var root = Component.empty()
        if (defaultColor != null) root = root.color(defaultColor)

        val regex = Regex("(?i)\\[(item|ec)\\]")
        var lastIndex = 0

        for (match in regex.findAll(rawMessage)) {
            if (match.range.first > lastIndex) {
                val textSegment = rawMessage.substring(lastIndex, match.range.first)
                root = root.append(LegacyComponentSerializer.legacyAmpersand().deserialize(textSegment.replace('§', '&')))
            }

            val token = match.value.lowercase()
            if (token == "[item]") {
                val handItem = sender.inventory.itemInMainHand
                if (handItem.type.isAir) {
                    root = root.append(Component.text("[Empty Hand]", NamedTextColor.GRAY))
                } else {
                    val itemColor = getItemRarityColor(handItem)
                    val nameComponent = if (handItem.hasItemMeta() && handItem.itemMeta.hasDisplayName()) {
                        handItem.itemMeta.displayName()!!
                    } else {
                        Component.translatable(handItem.translationKey()).color(itemColor)
                    }

                    val itemComp = Component.text("[", itemColor)
                        .append(nameComponent)
                        .append(Component.text("]", itemColor))
                        .hoverEvent(handItem.asHoverEvent())

                    root = root.append(itemComp)
                }
            } else if (token == "[ec]") {
                val snapshotId = UUID.randomUUID().toString().take(8)
                val snapshotItems = sender.enderChest.contents.map { it?.clone() }.toTypedArray()
                ecSnapshots[snapshotId] = ECSnapshot(sender.name, snapshotItems, System.currentTimeMillis())

                val ecComp = Component.text("[${sender.name}'s Ender Chest]", NamedTextColor.LIGHT_PURPLE, TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to preview ${sender.name}'s Ender Chest snapshot", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand("/infamy viewec $snapshotId"))

                root = root.append(ecComp)
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < rawMessage.length) {
            val trailingText = rawMessage.substring(lastIndex)
            root = root.append(LegacyComponentSerializer.legacyAmpersand().deserialize(trailingText.replace('§', '&')))
        }

        return root
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onGlobalChat(event: io.papermc.paper.event.player.AsyncChatEvent) {
        val player = event.player
        val rawMsg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message())

        if (teamManager.teamChatToggled.contains(player.uniqueId)) {
            event.isCancelled = true
            val team = teamManager.getTeam(player.uniqueId)
            if (team == null) {
                teamManager.teamChatToggled.remove(player.uniqueId)
                player.sendMessage(Component.text("You are not in a team. Team chat disabled.", NamedTextColor.RED))
                return
            }
            teamManager.sendTeamChat(team, player, rawMsg)
        } else {
            val formattedMsg = buildChatComponent(player, rawMsg)

            if (config.getBoolean("settings.fancy-chat", true)) {
                val team = teamManager.getTeam(player.uniqueId)
                val teamPrefix = if (team != null) {
                    LegacyComponentSerializer.legacyAmpersand().deserialize(team.colorFormat + "[" + team.name + "]&r ")
                } else Component.empty()

                val arrow = Component.text(" » ", NamedTextColor.DARK_GRAY)

                event.renderer { _, sourceDisplayName, _, _ ->
                    teamPrefix.append(sourceDisplayName).append(arrow).append(formattedMsg)
                }
            } else {
                event.message(formattedMsg)
            }
        }
    }

    private fun registerElytraRecipe() {
        if (!config.getBoolean("elytra-recipe.enabled", true)) return
        val key = NamespacedKey(this, "craftable_elytra")
        val recipe = ShapedRecipe(key, ItemStack(Material.ELYTRA))
        val shape = config.getStringList("elytra-recipe.shape")

        if (shape.size != 3) return
        recipe.shape(shape[0], shape[1], shape[2])

        val ingredients = config.getConfigurationSection("elytra-recipe.ingredients") ?: return
        for (charKey in ingredients.getKeys(false)) {
            val matName = ingredients.getString(charKey) ?: continue
            val material = Material.matchMaterial(matName.uppercase()) ?: continue
            recipe.setIngredient(charKey[0], material)
        }
        server.addRecipe(recipe)
    }

    private fun registerShulkerBoxRecipe() {
        if (!config.getBoolean("shulker-box-recipe.enabled", true)) return
        val key = NamespacedKey(this, "craftable_shulker_box")
        val recipe = ShapedRecipe(key, ItemStack(Material.SHULKER_BOX))
        val shape = config.getStringList("shulker-box-recipe.shape")

        if (shape.size != 3) return
        recipe.shape(shape[0], shape[1], shape[2])

        val ingredients = config.getConfigurationSection("shulker-box-recipe.ingredients") ?: return
        for (charKey in ingredients.getKeys(false)) {
            val matName = ingredients.getString(charKey) ?: continue
            val material = Material.matchMaterial(matName.uppercase()) ?: continue
            recipe.setIngredient(charKey[0], material)
        }
        server.addRecipe(recipe)
    }
}