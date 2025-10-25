package dev.alexanderdiaz.athenabuild;

import dev.alexanderdiaz.athenabuild.command.util.AthenaCommandGraph;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import dev.alexanderdiaz.athenabuild.listener.PlayerListener;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AthenaBuild extends JavaPlugin {
    @Getter
    private static AthenaBuild instance;
    @Getter
    private ConfigurationManager configManager;
    private AthenaCommandGraph commandGraph;

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
    public void onLoad() {
        getLogger().info("AthenaBuild has been loaded!");
    }

    @Override
    public void onDisable() {
        if (commandGraph != null) {
            this.commandGraph.close();
        }

        getLogger().log(Level.INFO, "AthenaBuild has been disabled!");
    }

    private void registerCommands() {
        try {
            this.commandGraph = new AthenaCommandGraph(this);
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

    public List<String> athenaWorlds(boolean loadedOnly) {
        List<String> worlds = new ArrayList<>();
        File worldsDir = new File(Bukkit.getWorldContainer().getParentFile(), "athena_worlds");

        if (worldsDir.exists() && worldsDir.isDirectory()) {
            File[] files = worldsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (!loadedOnly) {
                            worlds.add(file.getName());
                        } else {
                            WorldWrapper wrapper = new WorldWrapper(instance, file.getName());
                            if (wrapper.isLoaded()) {
                                worlds.add(file.getName());
                            }
                        }
                    }
                }
            }
        }

        return worlds;
    }
}