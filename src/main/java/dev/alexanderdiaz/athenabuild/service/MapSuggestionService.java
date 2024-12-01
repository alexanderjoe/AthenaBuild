package dev.alexanderdiaz.athenabuild.service;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MapSuggestionService {
    private final AthenaBuild plugin;
    private final ConfigurationManager config;
    private final Map<String, List<String>> categoryMapCache;
    private final long cacheExpiry;
    private long lastCacheUpdate;

    public MapSuggestionService(AthenaBuild plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.categoryMapCache = new HashMap<>();
        this.cacheExpiry = 900000; // 15 minutes in milliseconds
        this.lastCacheUpdate = 0;
    }

    public List<String> suggestMaps(String category, String currentInput) {
        try {
            if (category == null || category.isEmpty()) {
                return Collections.emptyList();
            }

            // Check cache first
            if (System.currentTimeMillis() - lastCacheUpdate <= cacheExpiry
                    && categoryMapCache.containsKey(category)) {
                return filterSuggestions(categoryMapCache.get(category), currentInput);
            }

            // Get project info
            String projectPath = String.format("%s/%s",
                    config.getGitLabOrganization(),
                    config.getGitLabRepository());

            String encodedProjectPath = projectPath.replace("/", "%2F");
            String projectUrl = String.format("%s/projects/%s",
                    config.getGitlabApiUrl(),
                    encodedProjectPath);

            // Get project details
            HttpResponse<String> projectResponse = Unirest.get(projectUrl)
                    .header("PRIVATE-TOKEN", config.getGitlabToken())
                    .header("Accept", "application/json")
                    .asString();

            if (!projectResponse.isSuccess()) {
                return Collections.emptyList();
            }

            // Parse the project JSON
            JSONObject projectJson = new JSONObject(projectResponse.getBody());
            int projectId = projectJson.getInt("id");

            // Get repository tree
            String treeUrl = String.format("%s/projects/%d/repository/tree",
                    config.getGitlabApiUrl(),
                    projectId);

            // Get tree with query parameters
            HttpResponse<String> treeResponse = Unirest.get(treeUrl)
                    .header("PRIVATE-TOKEN", config.getGitlabToken())
                    .header("Accept", "application/json")
                    .queryString("path", category)
                    .queryString("ref", config.getDefaultBranch())
                    .queryString("per_page", 100)
                    .asString();

            if (!treeResponse.isSuccess()) {
                return Collections.emptyList();
            }

            String treeBody = treeResponse.getBody();
            if (treeBody == null || treeBody.trim().isEmpty()) {
                return Collections.emptyList();
            }

            // Parse the tree JSON and get directories
            List<String> maps = parseTreeResponse(treeBody);

            // Cache the results
            categoryMapCache.put(category, maps);
            lastCacheUpdate = System.currentTimeMillis();

            // Filter and return suggestions based on current input
            return filterSuggestions(maps, currentInput);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch map suggestions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseTreeResponse(String treeBody) {
        JSONArray items = new JSONArray(treeBody);
        List<String> maps = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String type = item.getString("type");
            String name = item.getString("name");

            if ("tree".equals(type)) {
                maps.add(name);
            }
        }

        return maps;
    }

    private List<String> filterSuggestions(List<String> maps, String currentInput) {
        String lowerInput = currentInput.toLowerCase();
        return maps.stream()
                .filter(map -> map.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}
