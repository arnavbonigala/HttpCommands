package com.arnavbonigala.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.arnavbonigala.Httpcommands;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpCommandsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "httpcommands.json";
    
    public boolean showStatusCode = false;
    public int maxResponseChars = 400;
    public String postContentType = "application/json";
    public int connectTimeoutMs = 5000;
    public int requestTimeoutMs = 10000;
    public int cooldownSeconds = 5;
    public int maxConcurrentRequests = 4;
    public boolean allowLocalTargets = false;
    
    private static HttpCommandsConfig instance;
    private static Path configPath;
    
    public static HttpCommandsConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
    
    public static void reload() {
        instance = load();
    }
    
    private static Path getConfigPath() {
        if (configPath == null) {
            // Use Fabric's config directory
            Path fabricConfigDir = FabricLoader.getInstance().getConfigDir();
            configPath = fabricConfigDir.resolve(CONFIG_FILE_NAME);
        }
        return configPath;
    }
    
    private static HttpCommandsConfig load() {
        Path path = getConfigPath();
        HttpCommandsConfig config;
        
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                config = GSON.fromJson(content, HttpCommandsConfig.class);
                if (config == null) {
                    config = new HttpCommandsConfig();
                }
            } catch (IOException e) {
                Httpcommands.LOGGER.error("Failed to load config file, using defaults", e);
                config = new HttpCommandsConfig();
            }
        } else {
            config = new HttpCommandsConfig();
        }
        
        // Save to ensure all fields are written
        config.save();
        return config;
    }
    
    public void save() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            Httpcommands.LOGGER.error("Failed to save config file", e);
        }
    }
}

