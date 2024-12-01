package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DownloadMap {
    private final AthenaBuild plugin;

    public DownloadMap() {
        this.plugin = AthenaBuild.getInstance();
    }

    @Command("download <mapName>")
    @CommandDescription("Download a map from the server")
    @Permission("athenabuild.download")
    public void downloadMap(
            CommandSender sender,
            @Argument("mapName") String mapName) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can download maps.");
            return;
        }

        Player player = (Player) sender;
        World world = Bukkit.getWorld(mapName);

        if (world == null) {
            player.sendMessage("§cWorld not found.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                player.sendMessage("§aStarting world download process...");

                // Create temporary zip file
                File tempZip = new File(plugin.getDataFolder(), mapName + ".zip");
                tempZip.getParentFile().mkdirs();

                // Zip the world folder
                player.sendMessage("§aCompressing world folder...");
                zipWorld(world.getWorldFolder(), tempZip);

                // Upload to file.io
                player.sendMessage("§aUploading to file.io...");
                String downloadUrl = uploadToFileIo(tempZip);

                // Clean up
                tempZip.delete();

                // Send success message
                player.sendMessage("§aWorld download ready!");
                player.sendMessage("§aDownload link (expires in 1 hour): §e" + downloadUrl);

            } catch (Exception e) {
                player.sendMessage("§cError while processing world download: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error while processing world download", e);
            }
        });
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
        HttpResponse<JsonNode> response = Unirest.post("https://file.io")
                .field("file", file)
                .asJson();

        if (!response.isSuccess()) {
            throw new IOException("Upload failed with status: " + response.getStatus());
        }

        return response.getBody()
                .getObject()
                .getString("link");
    }
}
