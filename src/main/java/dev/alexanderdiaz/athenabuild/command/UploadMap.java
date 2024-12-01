package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import kong.unirest.Unirest;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UploadMap {
    private final AthenaBuild plugin;
    private final ConfigurationManager config;

    public UploadMap() {
        this.plugin = AthenaBuild.getInstance();
        this.config = plugin.getConfigManager();
    }

    @Command("upload <category> <mapName>")
    @CommandDescription("Upload a map from GitLab to the server")
    @Permission("athenabuild.upload")
    public void uploadMap(
            final CommandSender sender,
            final @Argument("category") String category,
            final @Argument("mapName") String mapName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can upload maps.");
            return;
        }

        Player player = (Player) sender;

        if (!config.isGitLabConfigured()) {
            player.sendMessage("§cGitLab is not configured. Please contact an administrator.");
            return;
        }

        // Validate category
        if (!config.isValidCategory(category)) {
            player.sendMessage("§cInvalid category! Available categories: " + String.join(", ", config.getMapCategories()));
            return;
        }

        // Check if world already exists
        String worldName = mapName.toLowerCase().replace(" ", "_");
        if (Bukkit.getWorld(worldName) != null) {
            player.sendMessage("§cA world with that name already exists!");
            return;
        }


        // Build the folder path (e.g., "DTM/Quintus")
        String folderPath = buildFolderPath(category, mapName);

        // Run the heavy operations async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                player.sendMessage("§aStarting world upload process...");
                player.sendMessage("§7Category: " + category);
                player.sendMessage("§7Map: " + mapName);
                player.sendMessage("§7Path: " + folderPath);

                // Download archive from GitLab
                player.sendMessage("§aDownloading from GitLab...");
                byte[] archiveData = downloadFromGitLab(folderPath);

                // Create world directory
                File worldDirectory = new File(Bukkit.getWorldContainer(), worldName);
                if (!worldDirectory.exists()) {
                    worldDirectory.mkdirs();
                }

                // Extract archive
                player.sendMessage("§aExtracting world files...");
                extractArchive(archiveData, worldDirectory);

                // Load world on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        WorldCreator creator = new WorldCreator(worldName);
                        World world = creator.createWorld();

                        if (world != null) {
                            player.sendMessage("§aWorld successfully uploaded and loaded!");
                            player.sendMessage("§aMap '" + mapName + "' loaded as world: " + worldName);
                        } else {
                            player.sendMessage("§cFailed to load world after upload!");
                        }
                    } catch (Exception e) {
                        player.sendMessage("§cError loading world: " + e.getMessage());
                        plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error loading world", e);
                    }
                });

            } catch (Exception e) {
                player.sendMessage("§cError while processing world upload: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error while processing world upload", e);
            }
        });
    }

    private String buildFolderPath(String category, String mapName) {
        String rootFolder = config.getMapsRootFolder();
        String basePath = rootFolder.isEmpty() ? "" : rootFolder + "/";
        return basePath + category + "/" + mapName;
    }

    private byte[] downloadFromGitLab(String folderPath) throws Exception {
        // URL encode the folderPath and project path
        String encodedPath = folderPath.replace("/", "%2F");
        String projectPath = (config.getGitLabOrganization() + "/" + config.getGitLabRepository())
                .replace("/", "%2F");

        String downloadUrl = String.format("%s/projects/%s/repository/archive.zip?sha=%s&path=%s",
                config.getGitlabApiUrl(), projectPath, config.getDefaultBranch(), encodedPath);

        return Unirest.get(downloadUrl)
                .header("PRIVATE-TOKEN", config.getGitlabToken())
                .asBytes()
                .getBody();
    }

    private void extractArchive(byte[] archiveData, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveData))) {
            ZipEntry entry;
            String rootFolder = null;

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();

                // Find the root folder name from the first entry
                if (rootFolder == null && fileName.contains("/")) {
                    rootFolder = fileName.substring(0, fileName.indexOf("/"));
                }

                // Skip if it's a directory or ignored file
                if (entry.isDirectory() || shouldIgnoreFile(fileName)) {
                    continue;
                }

                // Remove the root folder and get relative path
                if (fileName.startsWith(rootFolder + "/")) {
                    fileName = fileName.substring(rootFolder.length() + 1);
                }

                // Skip files not in the map folder
                if (!fileName.contains("/")) {
                    continue;
                }

                // Get just the world files by removing category/mapname prefix
                // e.g., "DTM/AlphaComplex/region/r.0.0.mca" -> "region/r.0.0.mca"
                String[] parts = fileName.split("/");
                if (parts.length > 2) {
                    fileName = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
                } else {
                    continue;
                }

                // Create and extract the file
                File newFile = new File(targetDir, fileName);
                File parent = newFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private boolean shouldIgnoreFile(String fileName) {
        for (String ignoredFile : config.getIgnoredFiles()) {
            if (fileName.toLowerCase().endsWith(ignoredFile.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}