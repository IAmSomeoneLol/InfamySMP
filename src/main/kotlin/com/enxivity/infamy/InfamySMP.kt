// Main plugin class. Connected to plugin.yml.
package com.enxivity.infamy

import com.enxivity.infamy.commands.TeamCommand
import com.enxivity.infamy.listeners.BlockBreakListener
import com.enxivity.infamy.listeners.BossAbilityListener
import com.enxivity.infamy.listeners.BottleInteractListener
import com.enxivity.infamy.listeners.CombatListener
import com.enxivity.infamy.listeners.DeathListener
import com.enxivity.infamy.listeners.PerkListener
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class InfamySMP : JavaPlugin() {
    lateinit var infamyManager: InfamyManager
    lateinit var itemManager: ItemManager
    lateinit var teamManager: TeamManager
    lateinit var bossListener: BossAbilityListener

    override fun onEnable() {
        saveDefaultConfig()

        itemManager = ItemManager(this)
        infamyManager = InfamyManager(this)
        teamManager = TeamManager()
        bossListener = BossAbilityListener(this)

        server.pluginManager.registerEvents(DeathListener(this), this)
        server.pluginManager.registerEvents(BottleInteractListener(this), this)
        server.pluginManager.registerEvents(CombatListener(this), this)
        server.pluginManager.registerEvents(PerkListener(this), this)
        server.pluginManager.registerEvents(BlockBreakListener(this), this)
        server.pluginManager.registerEvents(bossListener, this)

        getCommand("team")?.setExecutor(TeamCommand(this))
        registerElytraRecipe()

        val penaltyKey = NamespacedKey(this, "honor_weapon_penalty")
        val lastHeldItems = mutableMapOf<java.util.UUID, org.bukkit.Material>()
        val lastHonorLevels = mutableMapOf<java.util.UUID, Int>()

        server.scheduler.runTaskTimer(this, Runnable {
            for (player in server.onlinePlayers) {
                val rep = infamyManager.getRawReputation(player)
                val honor = infamyManager.getHonor(player)

                // --- INFAMY 15: Resistance 1 ---
                if (rep >= 15) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, false))
                }

                // --- INFAMY 20: Constant Strength 1 ---
                if (rep >= 20 && !bossListener.activeSacrifices.contains(player.uniqueId)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, false))
                }

                // --- INFAMY 21: Constant Glowing ---
                if (rep >= 21) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, false))
                }

                // --- INFAMY 21: Helmet Sacrifice Active (Strength 3 & Stripper) ---
                if (bossListener.activeSacrifices.contains(player.uniqueId)) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 60, 2, true, false, false))

                    // Foolproof Helmet Remover: "As if it was never there"
                    val helmet = player.inventory.helmet
                    if (helmet != null && helmet.type != Material.AIR) {
                        player.inventory.helmet = null
                        // Safely drop it at their feet so they don't lose their god-armor
                        player.world.dropItemNaturally(player.location, helmet)
                    }
                }

                // --- HONOR 6+: Hero of the Village ---
                if (honor >= 6) {
                    val hotvLevel = when {
                        honor >= 12 -> 2
                        honor >= 9 -> 1
                        else -> 0
                    }
                    player.addPotionEffect(PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 100, hotvLevel, true, false, false))
                }

                // --- HONOR 12: Weapon Cooldown Penalty ---
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