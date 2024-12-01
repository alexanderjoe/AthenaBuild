package dev.alexanderdiaz.athenabuild;

import dev.alexanderdiaz.athenabuild.command.util.AthenaCommandGraph;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AthenaBuild extends JavaPlugin {
    public static final String SEPARATOR = "\n----------------------------------------\n";
    public static final String VERSION = Bukkit.getVersion();


    private static AthenaBuild instance;
    private ConfigurationManager configManager;

    public static AthenaBuild getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("§cAthenaBuild has started loading...");

        // Load configuration
        loadConfiguration();

        // Plugin startup logic
        registerCommands();

        getLogger().info("§aAthenaBuild version [" + VERSION + "] has finished loading!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("§cAthenaBuild has been disabled!");
    }

    private void registerCommands() {
        try {
            new AthenaCommandGraph(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Exception while registering commands", e);
        }
    }

    private void loadConfiguration() {
        try {
            this.configManager = new ConfigurationManager(this);
            getLogger().log(Level.INFO, "§aConfiguration loaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "§cFailed to load configuration", e);
        }
    }

    public ConfigurationManager getConfigManager() {
        return configManager;
    }
}
