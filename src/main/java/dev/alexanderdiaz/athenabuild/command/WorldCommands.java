package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Command;

@Command("athenaworld|aw")
public class WorldCommands {
    private final AthenaBuild plugin;

    public WorldCommands(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command("setspawn|sp")
    public void setSpawn(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "You must be a player to use this command.");
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Could not find world.");
            return;
        }
        if (!world.getName().contains("athena_worlds")) {
            sender.sendMessage(ChatColor.RED + "Can only manage the spawn point of an Athena World.");
            return;
        }

        // Extract the simple world name from the full path
        // World name format is "athena_worlds/worldname" or "athena_worlds\worldname" depending on OS
        String fullWorldPath = world.getName();
        String worldName = fullWorldPath;

        // Remove the athena_worlds prefix and path separator
        if (fullWorldPath.contains(WorldWrapper.WORLDS_DIRECTORY)) {
            worldName = fullWorldPath.substring(fullWorldPath.lastIndexOf(java.io.File.separator) + 1);
        }

        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);
        boolean isSuccess = worldWrapper.setSpawnLocation(player.getLocation());

        if (isSuccess) {
            sender.sendMessage(ChatColor.GREEN + "Spawn location set.");
        } else {
            sender.sendMessage(ChatColor.RED + "Could not set spawn location.");
        }
    }
}
