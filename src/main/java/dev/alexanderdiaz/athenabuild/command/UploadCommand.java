package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import dev.alexanderdiaz.athenabuild.service.MapSuggestionService;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import kong.unirest.Unirest;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UploadCommand {
    private final AthenaBuild plugin;
    private final ConfigurationManager config;
    private final MapSuggestionService mapSuggestionService;

    public UploadCommand(AthenaBuild plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.mapSuggestionService = new MapSuggestionService(plugin);
    }

    @Command("upload <category> <mapName>")
    @CommandDescription("Upload a map to the server from the configure GitLab repository.")
    @Permission(Permissions.UPLOAD)
    public void uploadMap(
            final CommandSender sender,
            final @Argument(value = "category", suggestions = "categories", description = "The category of the map in the GitLab repository.") String category,
            final @Argument(value = "mapName", suggestions = "mapNames", description = "The exact name of the map to import.") @Greedy String mapName) {
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

        // Sanitize the world name for Bukkit
        String worldName = sanitizeWorldName(mapName);
        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

        if (worldWrapper.exists()) {
            player.sendMessage("§cA world with the name §e" + worldName + "§c already exists.");
            return;
        }

        // Build the folder path (e.g., "DTM/Quintus")
        String folderPath = buildFolderPath(category, mapName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                player.sendMessage("§aStarting world upload process...");
                player.sendMessage("§7Category: " + category);
                player.sendMessage("§7Map: " + mapName);
                player.sendMessage("§7World Name: " + worldName);
                player.sendMessage("§7Path: " + folderPath);

                // Download archive from GitLab
                player.sendMessage("§aDownloading from GitLab...");
                byte[] archiveData = downloadFromGitLab(folderPath);

                // Create temporary directory for extraction
                File tempDir = new File(plugin.getDataFolder(), "temp/" + worldName);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                // Extract archive
                player.sendMessage("§aExtracting world files...");
                extractArchive(archiveData, tempDir);

                // Load world on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (worldWrapper.importWorld(tempDir)) {
                            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1f);
                            sendImportMessage(player, worldName);
                        } else {
                            player.sendMessage("§cFailed to load world after upload!");
                        }
                    } catch (Exception e) {
                        player.sendMessage("§cError loading world: " + e.getMessage());
                        plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error loading world", e);
                    } finally {
                        deleteDirectory(tempDir);
                    }
                });

            } catch (Exception e) {
                player.sendMessage("§cError while processing world upload: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error while processing world upload", e);
            }
        });
    }

    private void sendImportMessage(Player player, String worldName) {
        String importCommand = "/open " + worldName;

        TextComponent message = new TextComponent("");
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")) + "\n");
        message.addExtra("§a§lWorld Upload Complete!\n");
        message.addExtra("§7World Name: §f" + worldName + "\n");
        message.addExtra("\n§7The world can be accessed with this command:\n\n");

        TextComponent commandComponent = new TextComponent(
                "§e§l" + importCommand
        );
        commandComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                importCommand
        ));
        commandComponent.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§e§lCommand to open the world.").create()
        ));

        message.addExtra(commandComponent);
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")));

        player.spigot().sendMessage(message);
    }

    /**
     * Sanitizes a map name to create a valid world name.
     * Converts spaces and special characters to underscores and removes invalid characters.
     */
    private String sanitizeWorldName(String mapName) {
        String sanitized = mapName.toLowerCase();

        sanitized = sanitized.replaceAll("[\\s\\-']+", "_");
        sanitized = sanitized.replaceAll("[^a-z0-9_]", "");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        sanitized = sanitized.replaceAll("_+", "_");

        return sanitized;
    }

    @Suggestions("categories")
    public List<String> suggestCategories() {
        return config.getMapCategories();
    }

    @Suggestions("mapNames")
    public List<String> suggestMapNames(CommandContext<CommandSender> context) {
        String category = context.get("category");
        String currentInput = context.rawInput().lastRemainingToken().toLowerCase();
        return mapSuggestionService.suggestMaps(category, currentInput);
    }

    private String buildFolderPath(String category, String mapName) {
        String rootFolder = config.getMapsRootFolder();
        String basePath = rootFolder.isEmpty() ? "" : rootFolder + "/";
        return basePath + category + "/" + mapName;
    }

    private byte[] downloadFromGitLab(String folderPath) throws Exception {
        // URL encode the folderPath and project path
        String encodedPath = folderPath.replace(" ", "%20").replace("/", "%2F");
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