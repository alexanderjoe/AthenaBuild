package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import dev.alexanderdiaz.athenabuild.service.MapSuggestionService;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

public final class UploadCommand {
    private final AthenaBuild plugin;
    private final ConfigurationManager config;
    private final MapSuggestionService mapSuggestionService;

    public UploadCommand(AthenaBuild plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.mapSuggestionService = new MapSuggestionService(plugin);
    }

    @Command("upload git <category> <mapName>")
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

    @Command("upload url <world_name> <url>")
    @CommandDescription("Upload a map to the server from a URL.")
    @Permission(Permissions.UPLOAD)
    public void uploadMapFromUrl(
            final CommandSender sender,
            final @Argument(value = "world_name", description = "The name for the imported world to be created as.") String worldName,
            final @Argument(value = "url", description = "The URL of the map to import.") @Greedy String url
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can upload maps.");
            return;
        }

        if (WorldWrapper.alreadyExists(worldName)) {
            sender.sendMessage("§cA world with the name §e" + worldName + " §calready exists. Choose a different name.");
            return;
        }

        // Validate URL
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            player.sendMessage("§cInvalid URL: " + e.getMessage());
            return;
        }

        // Sanitize the world name for Bukkit
        String sanitizedWorldName = sanitizeWorldName(worldName);
        WorldWrapper worldWrapper = new WorldWrapper(plugin, sanitizedWorldName);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File tempZip = null;
            File tempDir = null;
            try {
                player.sendMessage("§aStarting world upload from URL...");
                player.sendMessage("§7World Name: " + sanitizedWorldName);
                player.sendMessage("§7URL: " + url);

                // Download ZIP file
                player.sendMessage("§aDownloading ZIP file...");
                tempZip = new File(plugin.getDataFolder(), "temp/" + sanitizedWorldName + ".zip");
                tempZip.getParentFile().mkdirs();
                downloadFile(url, tempZip);

                // Extract ZIP to temporary directory
                player.sendMessage("§aExtracting world files...");
                tempDir = new File(plugin.getDataFolder(), "temp/" + sanitizedWorldName);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                unzipFile(tempZip, tempDir);

                // Validate world files
                player.sendMessage("§aValidating world files...");
                File worldRoot = findWorldRoot(tempDir);
                if (worldRoot == null) {
                    player.sendMessage("§cInvalid world! The ZIP must contain a valid Minecraft world with level.dat");
                    return;
                }

                // Clean up world-specific files before importing
                player.sendMessage("§aCleaning up world files...");
                cleanUpWorldFiles(worldRoot);

                // Load world on main thread
                File finalTempDir = tempDir;
                File finalTempZip = tempZip;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (worldWrapper.importWorld(worldRoot)) {
                            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1f);
                            sendImportMessage(player, sanitizedWorldName);
                            worldWrapper.prepareImportedWorld();
                        } else {
                            player.sendMessage("§cFailed to load world after upload!");
                        }
                    } catch (Exception e) {
                        player.sendMessage("§cError loading world: " + e.getMessage());
                        plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error loading world", e);
                    } finally {
                        // Clean up temp files
                        if (finalTempZip != null && finalTempZip.exists()) {
                            finalTempZip.delete();
                        }
                        if (finalTempDir != null && finalTempDir.exists()) {
                            deleteDirectory(finalTempDir);
                        }
                    }
                });

            } catch (Exception e) {
                player.sendMessage("§cError while processing world upload: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, ChatColor.RED + "Error while processing world upload from URL", e);

                // Clean up temp files on error
                if (tempZip != null && tempZip.exists()) {
                    tempZip.delete();
                }
                if (tempDir != null && tempDir.exists()) {
                    deleteDirectory(tempDir);
                }
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

    /**
     * Unzips a file to the specified destination directory
     *
     * @param zipFile The ZIP file to extract
     * @param destDir The destination directory
     * @throws IOException If an I/O error occurs
     */
    private void unzipFile(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[4096];
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);

                // Create parent directories
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // Create parent directory if it doesn't exist
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // Write file
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    /**
     * Prevents Zip Slip vulnerability by validating the file path
     *
     * @param destinationDir The destination directory
     * @param zipEntry       The ZIP entry
     * @return The validated file
     * @throws IOException If the file path is invalid (Zip Slip attempt)
     */
    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * Finds the root directory containing level.dat in the extracted files.
     * The world might be nested in subdirectories within the ZIP.
     *
     * @param directory The directory to search
     * @return The directory containing level.dat, or null if not found
     */
    private File findWorldRoot(File directory) {
        // Check if current directory contains level.dat
        File levelDat = new File(directory, "level.dat");
        if (levelDat.exists() && levelDat.isFile()) {
            return directory;
        }

        // Search subdirectories (up to 3 levels deep to avoid infinite search)
        return findWorldRootRecursive(directory, 0, 3);
    }

    private File findWorldRootRecursive(File directory, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File levelDat = new File(file, "level.dat");
                if (levelDat.exists() && levelDat.isFile()) {
                    return file;
                }

                // Recursively search subdirectory
                File result = findWorldRootRecursive(file, currentDepth + 1, maxDepth);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Cleans up world-specific files that should be deleted before importing a world from an external source.
     * This includes:
     * - uid.dat: Unique world identifier that should be regenerated
     * - athena.yml: Plugin-specific configuration that should be reset
     * - data folder: Contains world-specific data (player stats, structures, etc.)
     *
     * @param worldRoot The root directory of the world
     */
    private void cleanUpWorldFiles(File worldRoot) {
        // Delete uid.dat
        File uidDat = new File(worldRoot, "uid.dat");
        if (uidDat.exists() && uidDat.isFile()) {
            uidDat.delete();
            plugin.getLogger().info("Deleted uid.dat from imported world");
        }

        // Delete athena.yml if present
        File athenaYml = new File(worldRoot, "athena.yml");
        if (athenaYml.exists() && athenaYml.isFile()) {
            athenaYml.delete();
            plugin.getLogger().info("Deleted athena.yml from imported world");
        }

        // Delete data folder
        File dataFolder = new File(worldRoot, "data");
        if (dataFolder.exists() && dataFolder.isDirectory()) {
            deleteDirectory(dataFolder);
            plugin.getLogger().info("Deleted data folder from imported world");
        }
    }
}