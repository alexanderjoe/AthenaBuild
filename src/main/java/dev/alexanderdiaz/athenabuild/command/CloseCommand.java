package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;

public class CloseCommand {
    private final AthenaBuild plugin;

    public CloseCommand(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command("close <world>")
    @CommandDescription("Closes a world and returns all players to spawn.")
    @Permission(Permissions.CLOSE)
    public void close(
            final CommandSender sender,
            final @Argument(value = "world", suggestions = "loadedWorlds") String worldName) {

        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

        if (!worldWrapper.exists()) {
            sender.sendMessage("§cWorld '§e" + worldName + "§c' does not exist!");
            return;
        }

        if (!worldWrapper.isLoaded()) {
            sender.sendMessage("§cWorld '§e" + worldName + "§c' is not loaded!");
            return;
        }

        World world = worldWrapper.getWorld();
        World spawnWorld = Bukkit.getWorlds().get(0);

        List<Player> worldPlayers = new ArrayList<>(world.getPlayers());
        for (Player player : worldPlayers) {
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1, 0.5f);

            player.sendMessage("");
            player.sendMessage("§c§lWorld Closing!");
            player.sendMessage("§7The world §f§l" + worldName + "§r§7 is being closed.");
            player.sendMessage("§7You have been returned to spawn.");
            player.sendMessage("");

            player.teleport(spawnWorld.getSpawnLocation());

            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
            }, 5L);
        }

        if (worldWrapper.unloadWorld()) {
            sender.sendMessage("§aWorld '§e" + worldName + "§a' has been closed.");
            sender.sendMessage("§7" + worldPlayers.size() + " player(s) were returned to spawn.");

            if (sender instanceof Player) {
                Bukkit.broadcastMessage("§7World §f§l" + worldName + "§r§7 has been closed by §f§l" + sender.getName() + "§r§7.");
            }
        } else {
            sender.sendMessage("§cFailed to close world! Check console for details.");
        }
    }

    @Suggestions("loadedWorlds")
    public List<String> suggestWorlds() {
        return plugin.athenaWorlds(true);
    }
}
