package dev.alexanderdiaz.athenabuild.command.util;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.command.*;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

public class AthenaCommandGraph extends CommandGraph<AthenaBuild> {
    private BukkitAudiences audiences;

    public AthenaCommandGraph(AthenaBuild plugin) throws Exception {
        super(plugin);
        this.audiences = BukkitAudiences.create(plugin);
    }

    @Override
    protected void registerCommands() {
        manager.command(manager
                .commandBuilder("athena")
                .literal("help")
                .optional("query", StringParser.greedyStringParser())
                .handler(context -> minecraftHelp.queryCommands(
                                context.<String>optional("query").orElse(""), context.sender()
                        )
                )
        );

        register(new DownloadCommand(plugin));
        register(new UploadCommand(plugin));
        register(new CreateCommand(plugin));
        register(new OpenCommand(plugin));
        register(new CloseCommand(plugin));
    }

    @Override
    protected MinecraftHelp<CommandSender> createHelp() {
        if (audiences == null) {
            audiences = BukkitAudiences.create(plugin);
        }
        return MinecraftHelp.create(
                "/athena help",
                manager,
                audiences::sender
        );
    }

    @Override
    protected void setupInjectors() {

    }

    @Override
    protected void setupParsers() {

    }

    public void close() {
        if (audiences != null) {
            audiences.close();
        }
    }
}
