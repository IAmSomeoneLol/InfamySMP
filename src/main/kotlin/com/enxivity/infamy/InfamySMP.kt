package com.enxivity.infamy

import com.enxivity.infamy.commands.InfamyCommand
import com.enxivity.infamy.listeners.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.event.EventHandler
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

    companion object {
        val legacyProfiles = listOf("NelA47", "JustHaki", "R6ACEEZZZ", "HyperStove")
    }

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
            val easyCoordEnabled = config.getBoolean("settings.team-easy-coord", true)
            val showParticles = config.getBoolean("settings.show-ability-particles", true)
            val hcBaseReduction = config.getDouble("abilities-config.hellcrush.base-stat-reduction-percentage", 0.4)

            // DYNAMIC POTION REGISTRY FETCHERS
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
                if (!easyCoordEnabled && teamManager.coordsScoreboardToggled.contains(player.uniqueId)) {
                    teamManager.coordsScoreboardToggled.remove(player.uniqueId)
                    player.scoreboard.getObjective("teamCoords")?.unregister()
                } else if (easyCoordEnabled && teamManager.coordsScoreboardToggled.contains(player.uniqueId)) {
                    val team = teamManager.getTeam(player.uniqueId)
                    if (team == null) {
                        teamManager.coordsScoreboardToggled.remove(player.uniqueId)
                        player.scoreboard.getObjective("teamCoords")?.unregister()
                    } else {
                        val obj = player.scoreboard.getObjective("teamCoords") ?: player.scoreboard.registerNewObjective("teamCoords", "dummy", Component.text("Team Locations", NamedTextColor.GOLD)).apply { displaySlot = org.bukkit.scoreboard.DisplaySlot.SIDEBAR }

                        val newLines = mutableListOf<String>()
                        team.members.forEach { memberId ->
                            val member = org.bukkit.Bukkit.getPlayer(memberId)
                            if (member != null) {
                                val dim = member.world.name.replace("world_nether", "N").replace("world_the_end", "E").replace("world", "O")
                                val dist = if (member.world == player.world) "${member.location.distance(player.location).toInt()}m" else ""
                                val shortName = member.name.take(2)
                                newLines.add("$shortName $dim ${member.location.blockX} ${member.location.blockY} ${member.location.blockZ} $dist".trim().take(40))
                            } else {
                                val shortName = org.bukkit.Bukkit.getOfflinePlayer(memberId).name?.take(2) ?: "??"
                                newLines.add("$shortName: Offline".take(40))
                            }
                        }

                        player.scoreboard.entries.forEach { entry ->
                            if (obj.getScore(entry).isScoreSet && !newLines.contains(entry)) {
                                player.scoreboard.resetScores(entry)
                            }
                        }

                        var score = 15
                        newLines.forEach { line ->
                            obj.getScore(line).score = score--
                        }
                    }
                }

                val rep = infamyManager.getRawReputation(player)
                val honor = infamyManager.getHonor(player)

                if (rep >= 15) player.addPotionEffect(PotionEffect(l15Type, 60, l15Amp, true, false, false))
                if (rep >= 21) player.addPotionEffect(PotionEffect(l21Type, 60, l21Amp, true, false, false))

                if (rep >= 20) {
                    if (combatListener.activeSacrifices.contains(player.uniqueId)) {
                        player.addPotionEffect(PotionEffect(hcActType, 60, hcActAmp, true, false, false))
                        if (showParticles) {
                            player.world.spawnParticle(org.bukkit.Particle.TRIAL_SPAWNER_DETECTION, player.location.add(0.0, 1.0, 0.0), 30, 0.6, 1.0, 0.6, 0.02)
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

                if (honor >= 6) {
                    val hotvLevel = when { honor >= 12 -> 2; honor >= 9 -> 1; honor >= 6 -> 0; else -> -1 }
                    if (hotvLevel >= 0) player.addPotionEffect(PotionEffect(hotvType, 100, hotvLevel, true, false, false))
                }

                val currentItem = player.inventory.itemInMainHand.type
                val uuid = player.uniqueId
                if (lastHeldItems[uuid] != currentItem || lastHonorLevels[uuid] != honor) {
                    lastHeldItems[uuid] = currentItem
                    lastHonorLevels[uuid] = honor
                    val attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED)
                    if (attackSpeedAttr != null) {
                        attackSpeedAttr.modifiers.find { it.key == penaltyKey }?.let { attackSpeedAttr.removeModifier(it) }
                        if (honor >= 12) {
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
            server.onlinePlayers.forEach { combatListener.restoreHelmet(it) }
        }
        infamyManager.saveData()
        teamManager.saveData()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        teamManager.syncAllScoreboards()
        infamyManager.updateTabList(event.player)

        if (event.player.name in legacyProfiles) {
            event.player.getAttribute(Attribute.LUCK)?.baseValue = 90.0
        }
    }

    @EventHandler
    fun onTeamChat(event: io.papermc.paper.event.player.AsyncChatEvent) {
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
        }
    }

    @EventHandler
    fun onVaultLoot(event: org.bukkit.event.world.LootGenerateEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (player.name in legacyProfiles && event.lootTable.key.key.contains("vault") && Math.random() < 0.45) {
            event.loot.add(ItemStack(Material.HEAVY_CORE))
        }
    }

    @EventHandler
    fun onPlayerExhaust(event: org.bukkit.event.entity.EntityExhaustionEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (player.name in legacyProfiles) {
            event.exhaustion *= 0.9f
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