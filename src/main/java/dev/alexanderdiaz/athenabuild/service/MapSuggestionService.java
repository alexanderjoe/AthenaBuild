package dev.alexanderdiaz.athenabuild.service;

import dev.alexanderdiaz.athenabuild.AthenaBuild;
import dev.alexanderdiaz.athenabuild.config.ConfigurationManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

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

            // Build the path to the category folder
            String rootFolder = config.getMapsRootFolder();
            String path = rootFolder.isEmpty() ? category : rootFolder + "/" + category;

            // GitHub API URL format: /repos/{owner}/{repo}/contents/{path}
            String treeUrl = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
                    config.getGithubApiUrl(),
                    config.getGithubOrganization(),
                    config.getGithubRepository(),
                    path,
                    config.getDefaultBranch());

            String treeResponse = fetchFromGitHub(treeUrl);
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

    private String fetchFromGitHub(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + config.getGithubToken());
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "AthenaBuild");

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

                // GitHub uses "dir" for directories instead of "tree"
                if ("dir".equals(type)) {
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