package dev.alexanderdiaz.athenabuild.service;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
            String projectResponse = fetchFromGitlab(projectUrl);
            if (projectResponse.isEmpty()) {
                return Collections.emptyList();
            }

            // Parse the project JSON
            JSONParser parser = new JSONParser();
            JSONObject projectJson = (JSONObject) parser.parse(projectResponse);
            long projectId = (Long) projectJson.get("id");

            // Get repository tree
            String treeUrl = String.format("%s/projects/%d/repository/tree",
                    config.getGitlabApiUrl(),
                    projectId);

            // Get tree with query parameters
            String encodedCategory = category.replace(" ", "%20");
            treeUrl += String.format("?path=%s&ref=%s&per_page=100",
                    encodedCategory,
                    config.getDefaultBranch());

            String treeResponse = fetchFromGitlab(treeUrl);
            if (treeResponse.isEmpty()) {
                return Collections.emptyList();
            }

            // Parse the tree JSON and get directories
            List<String> maps = parseTreeResponse(treeResponse);

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

    private String fetchFromGitlab(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("PRIVATE-TOKEN", config.getGitlabToken());
        connection.setRequestProperty("Accept", "application/json");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private List<String> parseTreeResponse(String treeBody) {
        try {
            JSONParser parser = new JSONParser();
            JSONArray items = (JSONArray) parser.parse(treeBody);
            List<String> maps = new ArrayList<>();

            for (Object item : items) {
                JSONObject entry = (JSONObject) item;
                String type = (String) entry.get("type");
                String name = (String) entry.get("name");

                if ("tree".equals(type)) {
                    maps.add(name);
                }
            }

            return maps;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to parse tree response: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> filterSuggestions(List<String> maps, String currentInput) {
        String lowerInput = currentInput.toLowerCase();
        return maps.stream()
                .filter(map -> map.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}