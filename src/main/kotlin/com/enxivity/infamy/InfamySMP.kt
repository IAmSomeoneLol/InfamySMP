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
    lateinit var bossListener: BossAbilityListener

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()

        itemManager = ItemManager(this)
        infamyManager = InfamyManager(this)
        teamManager = TeamManager()
        bossListener = BossAbilityListener(this)

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(DeathListener(this), this)
        server.pluginManager.registerEvents(BottleInteractListener(this), this)
        server.pluginManager.registerEvents(CombatListener(this), this)
        server.pluginManager.registerEvents(PerkListener(this), this)
        server.pluginManager.registerEvents(BlockBreakListener(this), this)
        server.pluginManager.registerEvents(bossListener, this)
        server.pluginManager.registerEvents(HonorDeedListener(this), this)
        server.pluginManager.registerEvents(IndestructibleItemListener(this), this) // Registered Protection layer

        getCommand("infamy")?.setExecutor(InfamyCommand(this))
        registerElytraRecipe()

        val penaltyKey = NamespacedKey(this, "honor_weapon_penalty")
        val lastHeldItems = mutableMapOf<java.util.UUID, org.bukkit.Material>()
        val lastHonorLevels = mutableMapOf<java.util.UUID, Int>()

        server.scheduler.runTaskTimer(this, Runnable {
            for (player in server.onlinePlayers) {
                val rep = infamyManager.getRawReputation(player)
                val honor = infamyManager.getHonor(player)

                if (hasInfamyAbility("passive_resistance", rep)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, false))
                }

                if (hasInfamyAbility("mace_slam", rep) && !bossListener.activeSacrifices.contains(player.uniqueId)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, false))
                }

                if (hasInfamyAbility("boss_sacrifice", rep)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, false))
                }

                if (bossListener.activeSacrifices.contains(player.uniqueId)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 2, true, false, false))
                    val helmet = player.inventory.helmet
                    if (helmet != null && helmet.type != Material.AIR) {
                        player.inventory.helmet = null
                        player.world.dropItemNaturally(player.location, helmet)
                    }
                }

                if (hasHonorAbility("hero_of_the_village", honor)) {
                    val hotvLevel = when {
                        honor >= 12 -> 2
                        honor >= 9 -> 1
                        else -> 0
                    }
                    player.addPotionEffect(PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, hotvLevel, true, false, false))
                }

                val currentItem = player.inventory.itemInMainHand.type
                val uuid = player.uniqueId

                if (lastHeldItems[uuid] != currentItem || lastHonorLevels[uuid] != honor) {
                    lastHeldItems[uuid] = currentItem
                    lastHonorLevels[uuid] = honor

                    val attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED)
                    if (attackSpeedAttr != null) {
                        attackSpeedAttr.modifiers.find { it.key == penaltyKey }?.let { attackSpeedAttr.removeModifier(it) }

                        if (hasHonorAbility("weapon_cooldowns", honor)) {
                            val penalty = when {
                                currentItem.name.endsWith("_SWORD") -> -0.6
                                currentItem.name.endsWith("_AXE") -> -0.4
                                else -> 0.0
                            }
                            if (penalty < 0) {
                                val mod = AttributeModifier(penaltyKey, penalty, AttributeModifier.Operation.ADD_NUMBER)
                                attackSpeedAttr.addModifier(mod)
                            }
                        }
                    }
                }
            }
        }, 0L, 10L)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        infamyManager.updateTabList(event.player)
    }

    fun hasInfamyAbility(abilityName: String, rep: Int): Boolean {
        if (!config.getBoolean("infamy_abilities.$abilityName.enabled", true)) return false
        return rep >= config.getInt("infamy_abilities.$abilityName.level")
    }

    fun hasHonorAbility(abilityName: String, honor: Int): Boolean {
        if (!config.getBoolean("honor_abilities.$abilityName.enabled", true)) return false
        return honor >= config.getInt("honor_abilities.$abilityName.level")
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

    override fun onDisable() {}
}