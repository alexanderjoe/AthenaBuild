package dev.alexanderdiaz.athenabuild.config;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.List;

public class ConfigurationManager {
    private final AthenaBuild plugin;
    private FileConfiguration config;

    private String gitlabToken;
    private String gitlabApiUrl;
    private String gitLabOrganization;
    private String gitLabRepository;
    private String defaultBranch;
    private String mapsRootFolder;
    private List<String> mapCategories;
    private int maxUploadSize;
    private String[] ignoredFiles;

    public ConfigurationManager(AthenaBuild plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();

        plugin.reloadConfig();
        this.config = plugin.getConfig();

        loadGitLabConfig();
        loadUploadConfig();
    }

    private void loadGitLabConfig() {
        this.gitlabToken = config.getString("gitlab.token", "");
        this.gitlabApiUrl = config.getString("gitlab.api_url", "https://gitlab.com/api/v4");
        this.gitLabOrganization = config.getString("gitlab.organization", "");
        this.gitLabRepository = config.getString("gitlab.repository", "");
        this.defaultBranch = config.getString("gitlab.default_branch", "master");
        this.mapsRootFolder = config.getString("gitlab.maps.root_folder", "");
        this.mapCategories = config.getStringList("gitlab.maps.categories");

        // Add default categories if none configured
        if (mapCategories.isEmpty()) {
            mapCategories = Arrays.asList("Arcade", "CTF", "CTW", "DTC", "DTM", "KOTH", "Mixed", "Nexus", "SkyWars", "TDM", "Walls");
        }
    }

    private void loadUploadConfig() {
        this.maxUploadSize = config.getInt("upload.max_size", 500);
        this.ignoredFiles = config.getStringList("upload.ignored_files").toArray(new String[0]);
    }

    public String getGitlabToken() {
        return gitlabToken;
    }

    public String getGitlabApiUrl() {
        return gitlabApiUrl;
    }

    public int getMaxUploadSize() {
        return maxUploadSize;
    }

    public String[] getIgnoredFiles() {
        return ignoredFiles;
    }

    public String getGitLabOrganization() {
        return gitLabOrganization;
    }

    public String getGitLabRepository() {
        return gitLabRepository;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getMapsRootFolder() {
        return mapsRootFolder;
    }

    public List<String> getMapCategories() {
        return mapCategories;
    }

    public boolean isValidCategory(String category) {
        return mapCategories.contains(category);
    }

    public boolean hasGitLabDefaults() {
        return !gitLabOrganization.isEmpty() && !gitLabRepository.isEmpty();
    }

    public boolean isGitLabConfigured() {
        return !gitlabToken.isEmpty();
    }
}
