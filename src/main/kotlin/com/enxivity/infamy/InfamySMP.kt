// Main plugin class. Connected to plugin.yml.
package com.enxivity.infamy

import org.bukkit.plugin.java.JavaPlugin

class InfamySMP : JavaPlugin() {
    lateinit var infamyManager: InfamyManager
    lateinit var itemManager: ItemManager

    override fun onEnable() {
        itemManager = ItemManager()
        infamyManager = InfamyManager(this)
    }

    override fun onDisable() {
    }
}