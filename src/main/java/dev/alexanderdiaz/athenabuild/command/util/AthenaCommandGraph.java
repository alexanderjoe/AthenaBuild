package dev.alexanderdiaz.athenabuild.command.util;

import dev.alexanderdiaz.athenabuild.command.DownloadMap;
import dev.alexanderdiaz.athenabuild.command.UploadMap;
import org.bukkit.plugin.Plugin;

public class AthenaCommandGraph extends CommandGraph<Plugin> {

    public AthenaCommandGraph(Plugin plugin) throws Exception {
        super(plugin);
    }

    @Override
    protected void registerCommands() {
        register(new DownloadMap());
        register(new UploadMap());
    }

    @Override
    protected void setupInjectors() {

    }

    @Override
    protected void setupParsers() {

    }
}
