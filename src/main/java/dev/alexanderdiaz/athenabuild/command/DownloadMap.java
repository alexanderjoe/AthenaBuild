package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
import java.util.Collections;
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

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.playSound(player.getLocation(), Sound.NOTE_PIANO, 1, 1);
                    sendDownloadMessage(player, downloadUrl);
                });

            } catch (Exception e) {
                player.sendMessage("§cError while processing world download: " + e.getMessage());
                plugin.getLogger().log(Level.SEVERE, "Error while processing world download", e);
            }
        });
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
