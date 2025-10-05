package dev.alexanderdiaz.athenabuild.command;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.Permissions;
import dev.alexanderdiaz.athenabuild.world.WorldWrapper;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DeleteCommand {
    private final AthenaBuild plugin;

    public DeleteCommand(AthenaBuild plugin) {
        this.plugin = plugin;
    }

    @Command("delete <world>")
    @CommandDescription("Deletes a world.")
    @Permission(Permissions.DELETE)
    public void delete(
            final CommandSender sender,
            final @Argument(value = "world", suggestions = "worlds") String worldName) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can delete worlds.");
            return;
        }

        Player player = (Player) sender;
        WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

        if (!worldWrapper.exists()) {
            player.sendMessage("\n§cWorld '§e" + worldName + "§c' does not exist!\n");
            return;
        }

        if (worldWrapper.isLoaded()) {
            player.sendMessage("\n§cWorld '§e" + worldName + "§c' is loaded, please close it first!\n");
            return;
        }

        ConversationFactory factory = new ConversationFactory(plugin)
                .withModality(true)
                .withFirstPrompt(new DeleteConfirmPrompt(worldName))
                .withEscapeSequence("cancel")
                .withTimeout(30)
                .withLocalEcho(false);

        Conversation conversation = factory.buildConversation(player);
        conversation.addConversationAbandonedListener(event -> {
            if (!event.gracefulExit()) {
                player.sendMessage("\n§cWorld deletion cancelled.\n");
            }
        });

        conversation.begin();
    }

    @Suggestions("worlds")
    public List<String> suggestWorlds(CommandContext<CommandSender> context) {
        String input = context.rawInput().lastRemainingToken().toLowerCase();

        return plugin.athenaWorlds(false)
                .stream()
                .filter(world -> world.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }

    private class DeleteConfirmPrompt extends StringPrompt {
        private final String worldName;

        public DeleteConfirmPrompt(String worldName) {
            this.worldName = worldName;
        }

        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.RED + "Are you sure you want to delete the world '" +
                    ChatColor.YELLOW + worldName + ChatColor.RED + "'?\n" +
                    ChatColor.RED + "This action cannot be undone!\n" +
                    ChatColor.GRAY + "Type " + ChatColor.GREEN + "yes" +
                    ChatColor.GRAY + " to confirm or " + ChatColor.RED + "cancel" +
                    ChatColor.GRAY + " to abort.";
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            Player player = (Player) context.getForWhom();

            if (input.equalsIgnoreCase("yes")) {
                WorldWrapper worldWrapper = new WorldWrapper(plugin, worldName);

                boolean success = worldWrapper.deleteWorld();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("\n" + ChatColor.GRAY + ChatColor.BOLD + String.join("", Collections.nCopies(40, "-")));
                        player.sendMessage(ChatColor.RED + "§lWorld Deleted!");
                        player.sendMessage(ChatColor.GRAY + "World: " + ChatColor.YELLOW + worldName);
                        player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + String.join("", Collections.nCopies(40, "-")) + "\n");
                    } else {
                        player.sendMessage("\n§cFailed to delete world! Check console for details.\n");
                    }
                });

                context.setSessionData("gracefulExit", true);
            } else {
                player.sendMessage("\n§cWorld deletion cancelled.\n");
            }

            return Prompt.END_OF_CONVERSATION;
        }
    }
}