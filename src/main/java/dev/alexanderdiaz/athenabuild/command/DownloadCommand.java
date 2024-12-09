package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DownloadCommand {
    private final AthenaBuild plugin;

    public DownloadCommand(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command("download [world]")
    @CommandDescription("Creates a download URL for a world by compressing and uploading the world.")
    @Permission(Permissions.DOWNLOAD)
    public void downloadWorld(
            final CommandSender sender,
            @Argument(value = "world", suggestions = "worldNames") String worldName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can download worlds.");
            return;
        }

        Player player = (Player) sender;
        if (worldName == null) {
            worldName = player.getWorld().getName();
        }
        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

        if (!worldWrapper.exists()) {
            player.sendMessage("§cWorld with name §e§l" + worldName + "§r§c not found.");
            return;
        }

        String fileWorldName = worldName + "-" + UUID.randomUUID().toString().substring(0, 6);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                player.sendMessage("§aStarting world download process...");

                // Create temporary zip file
                File tempZip = new File(plugin.getDataFolder(), fileWorldName + ".zip");
                tempZip.getParentFile().mkdirs();

                // Zip the world folder
                player.sendMessage("§aCompressing world folder...");
                zipWorld(worldWrapper.getWorldDirectory(), tempZip);

                // Upload to file.io
                player.sendMessage("§aUploading to file.io...");
                String downloadUrl = uploadToFileIo(tempZip);

                tempZip.delete();

                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1);
                sendDownloadMessage(player, downloadUrl);
            } catch (Exception e) {
                player.sendMessage("§cError while processing world download: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error while processing world download", e);
            }
        });
    }

    @Suggestions("worldNames")
    public List<String> suggestWorlds() {
        return plugin.athenaWorlds(true);
    }

    private void sendDownloadMessage(Player player, String downloadUrl) {
        TextComponent message = new TextComponent("");
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")) + "\n");
        message.addExtra("§a§lWorld Download Ready!\n");
        message.addExtra("\n§7Click the button below to download your world:\n\n");

        TextComponent downloadComponent = new TextComponent(
                "§8[§e§l⚡ Click to Download World§8]"
        );
        downloadComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL,
                downloadUrl
        ));
        downloadComponent.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§e§lClick to open download link").create()
        ));

        message.addExtra(downloadComponent);
        message.addExtra("\n\n§7§oThis link expires in 14 days or after one download\n");
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")));

        player.spigot().sendMessage(message);
    }

    private void zipWorld(File worldFolder, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            zipFolder(worldFolder, worldFolder.getName(), zos);
        }
    }

    private void zipFolder(File folder, String baseName, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            // Skip session.lock to avoid issues
            if (file.getName().equals("session.lock")) continue;

            String filePath = baseName + "/" + file.getName();
            if (file.isDirectory()) {
                zipFolder(file, filePath, zos);
            } else {
                zos.putNextEntry(new ZipEntry(filePath));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private String uploadToFileIo(File file) throws IOException {
        String boundary = "---------------------------" + System.currentTimeMillis();
        URL url = new URL("https://file.io");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream output = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: application/octet-stream\r\n\r\n");
            writer.flush();

            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
                output.flush();
            }

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
        }

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Upload failed with status: " + connection.getResponseCode());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Parse the JSON response to get the link
            // Simple parsing since we only need one field
            String jsonResponse = response.toString();
            int linkStart = jsonResponse.indexOf("\"link\":\"") + 8;
            int linkEnd = jsonResponse.indexOf("\"", linkStart);
            return jsonResponse.substring(linkStart, linkEnd);
        }
    }
}
