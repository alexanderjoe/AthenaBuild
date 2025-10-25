package dev.alexanderdiaz.athenabuild.world;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

public class WorldConfig {
    private final AthenaBuild plugin;
    private final File configFile;
    private final YamlConfiguration config;

    public WorldConfig(File worldDirectory) {
        this.plugin = AthenaBuild.getInstance();
        this.configFile = new File(worldDirectory, "athena.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void setSpawnLocation(Location location) {
        config.set("spawn.world", location.getWorld().getName());
        config.set("spawn.x", location.getX());
        config.set("spawn.y", location.getY());
        config.set("spawn.z", location.getZ());
        config.set("spawn.yaw", location.getYaw());
        config.set("spawn.pitch", location.getPitch());
        save();
    }

    public Location getSpawnLocation(World world) {
        if (!config.contains("spawn")) {
            return null;
        }

        return new Location(
                world,
                config.getDouble("spawn.x"),
                config.getDouble("spawn.y"),
                config.getDouble("spawn.z"),
                (float) config.getDouble("spawn.yaw"),
                (float) config.getDouble("spawn.pitch")
        );
    }

    private void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config", e);
        }
    }
}
