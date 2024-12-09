package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;

import java.util.Collections;

public class CreateCommand {
    private final AthenaBuild plugin;

    public CreateCommand(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command(value = "create|new <name>")
    @CommandDescription(value = "Create a new world given a world name.")
    @Permission(Permissions.CREATE)
    public void create(CommandSender sender, @Argument(value = "name") String name) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can create worlds.");
            return;
        }

        Player player = (Player) sender;
        WorldWrapper worldWrapper = new WorldWrapper(plugin, name);

        if (worldWrapper.exists()) {
            player.sendMessage("§cA world with that name already exists!");
            return;
        }

        player.sendMessage("§aCreating new void world...");
        if (worldWrapper.createVoidWorld()) {
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1, 1f);
            sendCreatedMessage(player, worldWrapper.getWorldName());
        } else {
            player.sendMessage("§cFailed to create world! Check console for details.");
        }
    }

    private void sendCreatedMessage(Player player, String worldName) {
        String openCommand = "/open " + worldName;

        TextComponent message = new TextComponent("");
        message.addExtra("§8§l" + String.join("", Collections.nCopies(40, "-")) + "\n");
        message.addExtra("§a§lWorld Creation Complete!\n");
        message.addExtra("§7World Name: §f" + worldName + "\n");
        message.addExtra("\n§7The world can be accessed with this command:\n\n");

        TextComponent commandComponent = new TextComponent(
                "§e§l" + openCommand
        );
        commandComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                openCommand
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
}
