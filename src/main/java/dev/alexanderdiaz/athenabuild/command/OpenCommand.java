package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OpenCommand {
    private final AthenaBuild plugin;

    public OpenCommand(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command("open <world>")
    @CommandDescription("Opens and teleports you to a world.")
    @Permission(Permissions.OPEN)
    public void execute(
            final CommandSender sender,
            final @Argument(value = "world", suggestions = "availableWorlds") String worldName) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can open worlds.");
            return;
        }

        Player player = (Player) sender;
        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

        if (!worldWrapper.exists()) {
            player.sendMessage("§cWorld '§e§l" + worldName + "§r§c' does not exist!");
            return;
        }

        player.sendMessage("§aOpening world...");

        if (!worldWrapper.isLoaded() && !worldWrapper.loadWorld()) {
            player.sendMessage("§cFailed to load world! Check console for details.");
            return;
        }

        player.teleport(worldWrapper.getSpawnLocation());
        player.setFlying(true);
        player.setAllowFlight(true);
        player.setGameMode(GameMode.CREATIVE);
        player.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(100, 10));
        player.sendMessage("§aTeleported to §e" + worldName + "§a!");

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
        }, 5L);
    }

    @Suggestions("availableWorlds")
    public List<String> suggestWorlds() {
        return plugin.athenaWorlds(false);
    }
}
