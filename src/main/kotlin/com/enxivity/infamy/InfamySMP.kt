package com.enxivity.infamy

import com.enxivity.infamy.commands.InfamyCommand
import com.enxivity.infamy.listeners.*
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

class InfamySMP : JavaPlugin(), Listener {
    lateinit var infamyManager: InfamyManager
    lateinit var itemManager: ItemManager
    lateinit var teamManager: TeamManager
    lateinit var combatListener: CombatListener
    lateinit var itemRestrictionsListener: ItemRestrictionsListener

    override fun onEnable() {
        saveDefaultConfig()
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
            for (player in server.onlinePlayers) {
                val rep = infamyManager.getRawReputation(player)
                val honor = infamyManager.getHonor(player)

                if (rep >= 15) player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, false))
                if (rep >= 21) player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, false))

                if (rep >= 20) {
                    if (combatListener.activeSacrifices.contains(player.uniqueId)) {
                        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 2, true, false, false))
                    } else {
                        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, false))
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
                    if (aVal > 0) armorAttr?.addModifier(AttributeModifier(hellcrushArmorKey, -aVal, AttributeModifier.Operation.ADD_NUMBER))
                    if (tVal > 0) toughAttr?.addModifier(AttributeModifier(hellcrushToughKey, -tVal, AttributeModifier.Operation.ADD_NUMBER))
                } else {
                    armorAttr?.modifiers?.find { it.key == hellcrushArmorKey }?.let { armorAttr.removeModifier(it) }
                    toughAttr?.modifiers?.find { it.key == hellcrushToughKey }?.let { toughAttr.removeModifier(it) }
                }

                if (honor >= 6) {
                    val hotvLevel = when { honor >= 12 -> 2; honor >= 9 -> 1; honor >= 6 -> 0; else -> -1 }
                    if (hotvLevel >= 0) player.addPotionEffect(PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, hotvLevel, true, false, false))
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
        infamyManager.updateTabList(event.player)
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