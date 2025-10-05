package dev.alexanderdiaz.athenabuild.config;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.List;

public class ConfigurationManager {
    private final AthenaBuild plugin;
    private FileConfiguration config;

    // GITHUB CONFIG
    @Getter
    private String githubToken;
    @Getter
    private String githubApiUrl;
    @Getter
    private String githubOrganization;
    @Getter
    private String githubRepository;
    @Getter
    private String defaultBranch;
    // GITHUB -- MAPS CONFIG
    @Getter
    private String mapsRootFolder;
    @Getter
    private List<String> mapCategories;

    // UPLOAD CONFIG
    @Getter
    private int maxUploadSize;
    @Getter
    private List<String> ignoredFiles;

    public ConfigurationManager(AthenaBuild plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();

        plugin.reloadConfig();
        this.config = plugin.getConfig();

        loadGitHubConfig();
        loadUploadConfig();
    }

    private void loadGitHubConfig() {
        this.githubToken = config.getString("github.token", "");
        this.githubApiUrl = config.getString("github.api_url", "https://api.github.com");
        this.githubOrganization = config.getString("github.organization", "");
        this.githubRepository = config.getString("github.repository", "");
        this.defaultBranch = config.getString("github.default_branch", "main");
        this.mapsRootFolder = config.getString("github.maps.root_folder", "");
        this.mapCategories = config.getStringList("github.maps.categories");

        // Add default categories if none configured
        if (mapCategories.isEmpty()) {
            mapCategories = Arrays.asList("Arcade", "CTF", "CTW", "DTC", "DTM", "KOTH", "Mixed", "Nexus", "SkyWars", "TDM", "Walls");
        }
    }

    private void loadUploadConfig() {
        this.maxUploadSize = config.getInt("upload.max_size", 500);
        this.ignoredFiles = config.getStringList("upload.ignored_files");

        // Add default ignored files if none configured
        if (ignoredFiles.isEmpty()) {
            ignoredFiles = Arrays.asList(".git", ".gitignore", "README.md", "session.lock", "map.xml", "map.yml", "map.png", "map_banner.png");
        }
    }

    // VALIDATORS
    public boolean isValidCategory(String category) {
        return mapCategories.contains(category);
    }

    public boolean hasGitHubDefaults() {
        return !githubOrganization.isEmpty() && !githubRepository.isEmpty();
    }

    public boolean isGitHubConfigured() {
        return !githubToken.isEmpty();
    }
}
