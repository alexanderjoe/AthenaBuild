package dev.alexanderdiaz.athenabuild.command.util;

import io.leangen.geantyref.TypeToken;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.injection.ParameterInjector;
import org.incendo.cloud.injection.ParameterInjectorRegistry;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserRegistry;
import org.incendo.cloud.setting.ManagerSetting;
import org.incendo.cloud.suggestion.FilteringSuggestionProcessor;

import java.lang.reflect.Type;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class CommandGraph<P extends Plugin> {
    protected P plugin;
    protected CommandManager<CommandSender> manager;
    protected MinecraftHelp<CommandSender> minecraftHelp;
    protected AnnotationParser<CommandSender> annotationParser;

    protected ParameterInjectorRegistry<CommandSender> injectors;
    protected ParserRegistry<CommandSender> parsers;

    public CommandGraph(P plugin) throws Exception {
        this.plugin = plugin;
        this.manager = createCommandManager();
        this.annotationParser = createAnnotationParser();

        // Utility
        this.injectors = manager.parameterInjectorRegistry();
        this.parsers = manager.parserRegistry();

        setupInjectors();
        setupParsers();

        this.minecraftHelp = createHelp();

        registerCommands();
    }

    protected CommandManager<CommandSender> createCommandManager() {
        LegacyPaperCommandManager<CommandSender> manager =
                LegacyPaperCommandManager.createNative(plugin, ExecutionCoordinator.simpleCoordinator());

        manager.settings().set(ManagerSetting.LIBERAL_FLAG_PARSING, true);

        // Basic suggestion filtering processor which avoids suggesting flags when not applicable
        manager.suggestionProcessor(new FilteringSuggestionProcessor<>(
                FilteringSuggestionProcessor.Filter.Simple.contextFree((s, i) -> i.isEmpty()
                        || !s.startsWith("-")
                        || s.toLowerCase(Locale.ROOT).startsWith(i.toLowerCase(Locale.ROOT)))));

        return manager;
    }

    protected AnnotationParser<CommandSender> createAnnotationParser() {
        return new AnnotationParser<>(manager, CommandSender.class);
    }

    protected abstract MinecraftHelp<CommandSender> createHelp();

    protected abstract void setupInjectors();

    protected abstract void setupParsers();

    protected abstract void registerCommands();

    // Commands
    protected void register(Object command) {
        annotationParser.parse(command);
    }

    // Injectors
    protected <T> void registerInjector(Class<T> type, ParameterInjector<CommandSender, T> provider) {
        injectors.registerInjector(type, provider);
    }

    protected <T> void registerInjector(Class<T> type, Supplier<T> supplier) {
        registerInjector(type, (a, b) -> supplier.get());
    }

    protected <T> void registerInjector(Class<T> type, Function<P, T> function) {
        registerInjector(type, (a, b) -> function.apply(plugin));
    }

    // Parsers
    protected <T> void registerParser(Class<T> type, ArgumentParser<CommandSender, T> parser) {
        parsers.registerParserSupplier(TypeToken.get(type), op -> parser);
    }

    protected <T> void registerParser(Class<T> type, ParserBuilder<T> parser) {
        parsers.registerParserSupplier(TypeToken.get(type), op -> parser.create(manager, op));
    }

    protected <T> void registerParser(Type type, ArgumentParser<CommandSender, T> parser) {
        parsers.registerParserSupplier(TypeToken.get(type), op -> parser);
    }

    protected <T> void registerParser(Type type, ParserBuilder<T> parser) {
        parsers.registerParserSupplier(TypeToken.get(type), op -> parser.create(manager, op));
    }
}
