package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import dev.alexanderdiaz.athenabuild.service.MapSuggestionService;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

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
    @CommandDescription("Upload a map to the server from the configured GitHub repository.")
    @Permission(Permissions.UPLOAD)
    public void uploadMap(
            final CommandSender sender,
            final @Argument(value = "category", suggestions = "categories", description = "The category of the map in the GitHub repository.") String category,
            final @Argument(value = "mapName", suggestions = "mapNames", description = "The exact name of the map to import.") @Greedy String mapName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can upload maps.");
            return;
        }

        Player player = (Player) sender;

        if (!config.isGitHubConfigured()) {
            player.sendMessage("§cGitHub is not configured. Please contact an administrator.");
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

                // Download folder from GitHub
                player.sendMessage("§aDownloading from GitHub...");
                File tempDir = new File(plugin.getDataFolder(), "temp/" + worldName);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                downloadFolderFromGitHub(folderPath, tempDir);

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
        message.addExtra("\n");
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")));

        player.spigot().sendMessage(message);
    }

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

    private void downloadFolderFromGitHub(String folderPath, File targetDir) throws Exception {
        // Use GitHub Contents API to recursively download folder contents
        String contentsUrl = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
                config.getGithubApiUrl(),
                config.getGithubOrganization(),
                config.getGithubRepository(),
                folderPath,
                config.getDefaultBranch());

        downloadFolderRecursive(contentsUrl, targetDir, "");
    }

    private void downloadFolderRecursive(String contentsUrl, File targetDir, String relativePath) throws Exception {
        String response = fetchFromGitHub(contentsUrl);

        JSONParser parser = new JSONParser();
        JSONArray items = (JSONArray) parser.parse(response);

        for (Object item : items) {
            JSONObject entry = (JSONObject) item;
            String type = (String) entry.get("type");
            String name = (String) entry.get("name");

            // Skip ignored files
            if (shouldIgnoreFile(name)) {
                continue;
            }

            if ("dir".equals(type)) {
                // Recursively download subdirectory
                String subUrl = (String) entry.get("url");
                File subDir = new File(targetDir, relativePath.isEmpty() ? name : relativePath + "/" + name);
                if (!subDir.exists()) {
                    subDir.mkdirs();
                }
                downloadFolderRecursive(subUrl, targetDir, relativePath.isEmpty() ? name : relativePath + "/" + name);
            } else if ("file".equals(type)) {
                // Download file
                String downloadUrl = (String) entry.get("download_url");
                File targetFile = new File(targetDir, relativePath.isEmpty() ? name : relativePath + "/" + name);
                downloadFile(downloadUrl, targetFile);
            }
        }
    }

    private String fetchFromGitHub(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + config.getGithubToken());
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "AthenaBuild");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            String errorMessage = "";
            try (InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        errorMessage = response.toString();
                    }
                }
            }
            throw new IOException(String.format("Failed to fetch from GitHub: %d - %s. Response: %s",
                    connection.getResponseCode(),
                    connection.getResponseMessage(),
                    errorMessage));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private void downloadFile(String downloadUrl, File targetFile) throws IOException {
        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        // Raw content doesn't need authentication for public repos, but we'll add it for private repos
        connection.setRequestProperty("Authorization", "Bearer " + config.getGithubToken());
        connection.setRequestProperty("User-Agent", "AthenaBuild");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download file: " + downloadUrl + " - Status: " + connection.getResponseCode());
        }

        File parent = targetFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
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