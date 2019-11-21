package org.redcastlemedia.multitallented.civs.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.redcastlemedia.multitallented.civs.ConfigManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class FallbackConfigUtil {
    private FallbackConfigUtil() {

    }
    public static FileConfiguration getConfig(File originalFile, String filePath) {
        FileConfiguration config = new YamlConfiguration();

        try {
            String url = "/resources/" + ConfigManager.getInstance().getDefaultConfigSet() + "/" + filePath;
            InputStream inputStream = FallbackConfigUtil.class.getResourceAsStream(url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            config.load(reader);
            if (originalFile != null && originalFile.exists()) {
                FileConfiguration configOverride = new YamlConfiguration();
                configOverride.load(originalFile);
                for (String key : configOverride.getKeys(true)) {
                    config.set(key, configOverride.get(key));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;
    }
}