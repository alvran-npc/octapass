package net.octa.octapass.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceYamlTest {
    private static final List<String> RESOURCES = List.of(
            "config.yml", "messages.yml", "season.yml", "plugin.yml",
            "rewards/free.yml", "rewards/premium.yml",
            "quests/daily.yml", "quests/weekly.yml", "quests/season.yml",
            "quests/events/christmas.yml", "quests/events/ramadan.yml",
            "gui/main.yml", "gui/rewards.yml", "gui/quests.yml",
            "lang/id.yml", "lang/en.yml"
    );

    @Test
    void allBundledYamlResourcesParse() throws Exception {
        for (String resource : RESOURCES) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, "Missing bundled resource: " + resource);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertNotNull(yaml, "Failed to parse: " + resource);
            }
        }
    }

    @Test
    void sampleQuestPoolsContainExpectedQuestSections() throws Exception {
        for (String resource : List.of("quests/daily.yml", "quests/weekly.yml", "quests/season.yml")) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertFalse(yaml.getConfigurationSection("quests").getKeys(false).isEmpty(), resource);
            }
        }
    }
}
