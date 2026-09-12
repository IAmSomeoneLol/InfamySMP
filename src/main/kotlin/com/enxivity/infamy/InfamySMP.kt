package com.enxivity.infamy

import com.enxivity.infamy.commands.InfamyCommand
import com.enxivity.infamy.listeners.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.Registry

class InfamySMP : JavaPlugin(), Listener {
    lateinit var infamyManager: InfamyManager
    lateinit var itemManager: ItemManager
    lateinit var teamManager: TeamManager
    lateinit var combatListener: CombatListener
    lateinit var itemRestrictionsListener: ItemRestrictionsListener

    override fun onEnable() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
        reloadConfig()

        itemManager = ItemManager(this)
        infamyManager = InfamyManager(this)
        teamManager = TeamManager(this)

        infamyManager.loadData()
        teamManager.loadData()

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

        val penaltyKey = NamespacedKey(this, "honor_weapon_penalty")
        val hellcrushArmorKey = NamespacedKey(this, "hellcrush_armor_penalty")
        val hellcrushToughKey = NamespacedKey(this, "hellcrush_toughness_penalty")

        val lastHeldItems = mutableMapOf<java.util.UUID, org.bukkit.Material>()
        val lastHonorLevels = mutableMapOf<java.util.UUID, Int>()

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
                // Scoreboard
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
                                    val member = org.bukkit.Bukkit.getPlayer(m)
                                    if (member != null) {
                                        val dim = member.world.name.replace("world_nether", "§cNether").replace("world_the_end", "§5End").replace("world", "§aOverworld")
                                        val dist = if (member.world == player.world) " §8[${member.location.distance(player.location).toInt()}m]" else ""
                                        lines.add("§f${member.name.take(6)}: §8${member.location.blockX}, ${member.location.blockY}, ${member.location.blockZ} §7($dim)§r$dist".take(40))
                                    } else {
                                        lines.add("§7${org.bukkit.Bukkit.getOfflinePlayer(m).name?.take(6)}: Offline")
                                    }
                                }
                            }
                        }
                        "COOLDOWN" -> {
                            obj.displayName(Component.text(" Cooldowns ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                            val now = System.currentTimeMillis()

                            fun getRem(map: Map<java.util.UUID, Long>, cdSecs: Long): String {
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
                            obj.displayName(Component.text(" Infamy SMP ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                            if (settings.sbTeam) {
                                val t = teamManager.getTeam(player.uniqueId)
                                lines.add("§7Team: §f${t?.name ?: "None"}")
                            }
                            if (settings.sbTeammates) {
                                val t = teamManager.getTeam(player.uniqueId)
                                val online = t?.members?.count { org.bukkit.Bukkit.getPlayer(it) != null } ?: 0
                                lines.add("§7Teammates Online: §f$online")
                            }
                            if (settings.sbKills) lines.add("§7Kills: §f${infamyManager.playerKills[player.uniqueId] ?: 0}")
                            if (settings.sbDeaths) lines.add("§7Deaths: §f${infamyManager.playerDeaths[player.uniqueId] ?: 0}")
                            if (settings.sbKDR) {
                                val k = infamyManager.playerKills[player.uniqueId] ?: 0
                                val d = infamyManager.playerDeaths[player.uniqueId] ?: 0
                                val ratio = if (d == 0) k.toDouble() else String.format("%.2f", k.toDouble() / d).toDouble()
                                lines.add("§7KDR: §f$ratio")
                            }
                            if (settings.sbPoints) {
                                val rep = infamyManager.getRawReputation(player)
                                if (rep > 0) lines.add("§7Path: §cInfamy §8(§f$rep§8)")
                                else if (rep < 0) lines.add("§7Path: §bHonor §8(§f${-rep}§8)")
                                else lines.add("§7Path: §aNo Points")
                            }
                            if (settings.sbOnline) lines.add("§7Online: §f${org.bukkit.Bukkit.getOnlinePlayers().size}")
                        }
                    }

                    // Scoreboard
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

                // Abilities
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

                // Attributes
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

                // Weapons
                val currentItem = player.inventory.itemInMainHand.type
                val uuid = player.uniqueId
                val honor = infamyManager.getHonor(player)

                if (lastHeldItems[uuid] != currentItem || lastHonorLevels[uuid] != honor) {
                    lastHeldItems[uuid] = currentItem
                    lastHonorLevels[uuid] = honor
                    val attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED)
                    if (attackSpeedAttr != null) {
                        attackSpeedAttr.modifiers.find { it.key == penaltyKey }?.let { attackSpeedAttr.removeModifier(it) }

                        if (infamyManager.hasAbility(player, "weapon_cooldowns", true)) {
                            val penalty = when {
                                currentItem.name.endsWith("_SWORD") -> -0.6
                                currentItem.name.endsWith("_AXE") -> -0.4
                                else -> 0.0
                            }
                            if (penalty < 0) attackSpeedAttr.addModifier(AttributeModifier(penaltyKey, penalty, AttributeModifier.Operation.ADD_NUMBER))
                        }
                    }
                }
            }

            // Particles
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
        }, 0L, 10L)
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onGlobalChat(event: io.papermc.paper.event.player.AsyncChatEvent) {
        val player = event.player
        if (teamManager.teamChatToggled.contains(player.uniqueId)) {
            event.isCancelled = true
            val team = teamManager.getTeam(player.uniqueId)
            if (team == null) {
                teamManager.teamChatToggled.remove(player.uniqueId)
                player.sendMessage(Component.text("You are not in a team. Team chat disabled.", NamedTextColor.RED))
                return
            }
            val msgText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message())
            teamManager.broadcastToTeam(team.name, "[Team] (${player.name}) | $msgText", NamedTextColor.AQUA)
        } else if (config.getBoolean("settings.fancy-chat", true)) {
            val team = teamManager.getTeam(player.uniqueId)
            val teamPrefix = if (team != null) {
                LegacyComponentSerializer.legacyAmpersand().deserialize(team.colorFormat + "[" + team.name + "]&r ")
            } else Component.empty()

            val arrow = Component.text(" » ", NamedTextColor.DARK_GRAY)

            event.renderer { source, sourceDisplayName, message, viewer ->
                teamPrefix.append(sourceDisplayName).append(arrow).append(message)
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
}