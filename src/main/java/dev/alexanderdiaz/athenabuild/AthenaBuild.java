package dev.alexanderdiaz.athenabuild;

import dev.alexanderdiaz.athenabuild.command.util.AthenaCommandGraph;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import dev.alexanderdiaz.athenabuild.listener.PlayerListener;
import org.bukkit.plugin.PluginManager;
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
        getLogger().info("AthenaBuild has started loading...");

        // handle plugin startup
        loadConfiguration();
        registerCommands();
        registerListeners();

        getLogger().info("AthenaBuild has finished loading!");
    }

    @Override
    public void onDisable() {
        getLogger().log(Level.INFO, "AthenaBuild has been disabled!");
    }

    private void registerCommands() {
        try {
            new AthenaCommandGraph(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception while registering commands", e);
        }
    }

    private void registerListeners() {
        try {
            PluginManager pm = getServer().getPluginManager();
            pm.registerEvents(new PlayerListener(instance), this);
            getLogger().log(Level.INFO, "Listeners registered successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to register listeners", e);
        }
    }

    private void loadConfiguration() {
        try {
            this.configManager = new ConfigurationManager(this);
            getLogger().log(Level.INFO, "Configuration loaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", e);
        }
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}
