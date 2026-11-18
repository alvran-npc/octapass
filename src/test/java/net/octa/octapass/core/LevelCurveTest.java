package net.octa.octapass.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LevelCurveTest {
    private YamlConfiguration loadSeason() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("season.yml")) {
            assertNotNull(stream, "season.yml should be present in test resources");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    void linearCurveCalculatesLevelsAndProgress() throws Exception {
        LevelCurve curve = new LevelCurve(loadSeason(), 50);
        assertEquals(100, curve.xpForNextLevel(1));
        assertEquals(125, curve.xpForNextLevel(2));
        assertEquals(1, curve.levelForXp(99));
        assertEquals(2, curve.levelForXp(100));
        assertEquals(0, curve.xpIntoLevel(225));
        assertEquals(225, curve.xpTotalToReach(3));
        assertEquals(25, curve.xpIntoLevel(250));
        assertEquals(150, curve.xpNeededForCurrentLevel(225));
    }

    @Test
    void maximumLevelStopsFurtherProgression() throws Exception {
        LevelCurve curve = new LevelCurve(loadSeason(), 50);
        long maxXp = curve.xpTotalToReach(curve.maxLevel());
        assertEquals(curve.maxLevel(), curve.levelForXp(maxXp));
        assertEquals(0, curve.xpNeededForCurrentLevel(maxXp));
        assertEquals(curve.maxLevel(), curve.levelForXp(Long.MAX_VALUE));
    }

    @Test
    void negativeXpIsTreatedAsZero() throws Exception {
        LevelCurve curve = new LevelCurve(loadSeason(), 50);
        assertEquals(1, curve.levelForXp(-500));
        assertEquals(0, curve.xpIntoLevel(-500));
    }
}
