package dev.alexanderdiaz.athenabuild;

import dev.alexanderdiaz.athenabuild.command.util.AthenaCommandGraph;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AthenaBuild extends JavaPlugin {
    private static AthenaBuild instance;
    private ConfigurationManager configManager;

    public static AthenaBuild getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info(ChatColor.GREEN + "AthenaBuild has started loading...");

        // handle plugin startup
        loadConfiguration();
        registerCommands();

        getLogger().info(ChatColor.GREEN + "AthenaBuild has finished loading!");
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "AthenaBuild has been disabled!");
    }

    private void registerCommands() {
        try {
            new AthenaCommandGraph(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, ChatColor.RED + "Exception while registering commands", e);
        }
    }

    private void loadConfiguration() {
        try {
            this.configManager = new ConfigurationManager(this);
            getLogger().log(Level.INFO, ChatColor.RED + "Configuration loaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, ChatColor.RED + "Failed to load configuration", e);
        }
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}
