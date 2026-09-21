package com.enxivity.infamy

import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class MessagesManager(private val plugin: InfamySMP) {

    private var config = YamlConfiguration()

    fun loadConfig() {
        val file = File(plugin.dataFolder, "messagesconfig.yml")
        if (!file.exists()) {
            plugin.saveResource("messagesconfig.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)

        val internalStream = plugin.getResource("messagesconfig.yml")
        if (internalStream != null) {
            val defConfig = YamlConfiguration.loadConfiguration(java.io.InputStreamReader(internalStream, java.nio.charset.StandardCharsets.UTF_8))
            config.setDefaults(defConfig)
        }
    }

    fun getRaw(key: String, default: String = ""): String {
        return config.getString(key) ?: default
    }

    fun getComponent(key: String, vararg placeholders: Pair<String, Any>, default: String = ""): Component {
        var raw = config.getString(key) ?: default
        if (raw.isEmpty()) raw = key

        for ((k, v) in placeholders) {
            raw = raw.replace("{$k}", v.toString())
        }

        return TeamColor.deserialize(raw)
    }
}