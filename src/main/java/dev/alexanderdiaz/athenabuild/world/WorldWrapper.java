package dev.alexanderdiaz.athenabuild.world;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.util.Vector;

public class WorldWrapper {
    public static final String WORLDS_DIRECTORY = "athena_worlds";

    private final AthenaBuild plugin;
    @Getter
    private final String worldName;
    @Getter
    private final File worldDirectory;
    @Getter
    private final WorldConfig config;
    @Getter
    private World world;

    public WorldWrapper(AthenaBuild plugin, String worldName) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.worldDirectory = new File(Bukkit.getWorldContainer().getParentFile(), WORLDS_DIRECTORY + File.separator + worldName);
        this.config = new WorldConfig(worldDirectory);
        this.world = Bukkit.getWorld(worldDirectory.toPath().toString());
    }

    public static boolean alreadyExists(String worldName) {
        return new File(Bukkit.getWorldContainer().getParentFile(), WORLDS_DIRECTORY + File.separator + worldName).exists();
    }

    /**
     * Creates a new void world with the given name
     *
     * @return true if world was created successfully
     */
    public boolean createVoidWorld() {
        try {
            // Ensure world directory exists
            if (!worldDirectory.exists() && !worldDirectory.mkdirs()) {
                throw new IOException("Failed to create world directory");
            }

            // Create world using void generator and direct path
            WorldCreator creator = new WorldCreator(worldDirectory.toPath().toString())
                    .generator(new NullChunkGenerator());

            this.world = creator.createWorld();
            if (world == null) {
                throw new IllegalStateException("Failed to create world");
            }

            // Initialize world
            initializeWorld();
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create void world: " + worldName, e);
            return false;
        }
    }

    /**
     * Imports a world from a source directory
     *
     * @param sourceDir The source directory containing the world files
     * @return true if world was imported successfully
     */
    public boolean importWorld(File sourceDir) {
        try {
            if (isLoaded()) {
                throw new IllegalStateException("World is already loaded");
            }

            if (!worldDirectory.exists() && !worldDirectory.mkdirs()) {
                throw new IOException("Failed to create world directory");
            }

            copyDirectory(sourceDir.toPath(), worldDirectory.toPath());

            boolean loadSuccess = loadWorld();

            config.setSpawnLocation(world.getSpawnLocation());

            return loadSuccess;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to import world: " + worldName, e);
            return false;
        }
    }

    /**
     * Loads the world if it exists
     *
     * @return true if world was loaded successfully
     */
    public boolean loadWorld() {
        try {
            // Check if world directory exists
            if (!worldDirectory.exists()) {
                throw new IllegalStateException("World directory does not exist");
            }

            // Create and load world using direct path
            WorldCreator creator = new WorldCreator(worldDirectory.toPath().toString())
                    .generator(new NullChunkGenerator());
            this.world = creator.createWorld();

            if (world == null) {
                throw new IllegalStateException("Failed to load world");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load world: " + worldName, e);
            return false;
        }
    }

    /**
     * Unloads the world if it is loaded
     *
     * @return true if world was unloaded successfully
     */
    public boolean unloadWorld() {
        try {
            if (!isLoaded()) {
                return true;
            }

            return Bukkit.unloadWorld(world, true);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unload world: " + worldName, e);
            return false;
        }
    }

    /**
     * Deletes the world completely
     *
     * @return true if world was deleted successfully
     */
    public boolean deleteWorld() {
        try {
            // Unload world first
            if (isLoaded() && !unloadWorld()) {
                throw new IllegalStateException("Failed to unload world");
            }

            // Delete world directory
            deleteDirectory(worldDirectory);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete world: " + worldName, e);
            return false;
        }
    }

    /**
     * Checks if the world is currently loaded
     *
     * @return true if world is loaded
     */
    public boolean isLoaded() {
        if (world != null && world.isChunkLoaded(0, 0)) {
            return true;
        }

        // Check if the world is loaded by full path
        World loadedWorld = Bukkit.getWorld(worldDirectory.toPath().toString());
        if (loadedWorld != null) {
            this.world = loadedWorld;
            return true;
        }

        return false;
    }

    /**
     * Checks if the world exists on disk
     *
     * @return true if world exists
     */
    public boolean exists() {
        return worldDirectory.exists();
    }

    public Location getSpawnLocation() {
        if (world == null) {
            return null;
        }

        if (config.getSpawnLocation(world) != null) {
            return config.getSpawnLocation(world);
        }

        return world.getSpawnLocation();
    }

    public boolean setSpawnLocation(Location location) {
        if (!isLoaded()) {
            loadWorld();
        }

        if (world == null) {
            plugin.getLogger().log(Level.WARNING, "World is not loaded!");
            return false;
        }

        config.setSpawnLocation(location);
        world.setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        world.save();
        return true;
    }

    private void initializeWorld() {
        if (!isLoaded()) {
            return;
        }

        // Set basic world settings
        world.setSpawnLocation(0, 64, 0);
        world.setGameRuleValue("doMobSpawning", "false");
        world.setTime(6000); // Set to midday
        world.setAutoSave(true);
        world.save();

        Vector min = new Vector(-1, 63, -1);
        Vector max = new Vector(1, 63, 1);

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    world.getBlockAt(x, y, z).setType(Material.GLASS);
                }
            }
        }

        // Save spawn location to config
        config.setSpawnLocation(world.getSpawnLocation());
    }

    public void prepareImportedWorld() {
        if (!isLoaded()) {
            loadWorld();
        }

        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setAutoSave(true);
        world.save();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source)
                .forEach(sourcePath -> {
                    try {
                        Path targetPath = target.resolve(source.relativize(sourcePath));
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}