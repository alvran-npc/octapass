package net.octa.octapass.quest;

import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class QuestService {
    private final JavaPlugin plugin;
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();
    private final Set<String> manuallyEnabledEvents = new HashSet<>();
    private final Set<String> manuallyDisabledEvents = new HashSet<>();
    private ZoneId zoneId;
    private LocalTime dailyReset;
    private DayOfWeek weeklyStart;
    private LocalTime weeklyReset;
    private int dailyCount;
    private int weeklyCount;

    public QuestService(JavaPlugin plugin) { this.plugin = plugin; }

    public void load() {
        definitions.clear();
        File root = new File(plugin.getDataFolder(), "quests");
        loadFile(new File(root, "daily.yml"), QuestCategory.DAILY);
        loadFile(new File(root, "weekly.yml"), QuestCategory.WEEKLY);
        loadFile(new File(root, "season.yml"), QuestCategory.SEASON);
        File events = new File(root, "events");
        File[] files = events.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) for (File file : files) loadFile(file, QuestCategory.EVENT);

        var config = plugin.getConfig();
        try { zoneId = ZoneId.of(config.getString("timezone", "Asia/Jakarta")); }
        catch (DateTimeException exception) {
            zoneId = ZoneId.of("UTC");
            plugin.getLogger().warning("Invalid timezone in config.yml; using UTC");
        }
        dailyReset = parseTime(config.getString("quest-reset.daily-time", "00:00"), LocalTime.MIDNIGHT);
        weeklyReset = parseTime(config.getString("quest-reset.weekly-time", "00:00"), LocalTime.MIDNIGHT);
        try { weeklyStart = DayOfWeek.valueOf(config.getString("quest-reset.weekly-start", "MONDAY").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { weeklyStart = DayOfWeek.MONDAY; }
        dailyCount = Math.max(0, config.getInt("quest-reset.daily-count", 3));
        weeklyCount = Math.max(0, config.getInt("quest-reset.weekly-count", 3));
    }

    private void loadFile(File file, QuestCategory category) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection quest = section.getConfigurationSection(id);
            if (quest == null) continue;
            try {
                LocalDate start = parseDate(quest.getString("start-date"));
                LocalDate end = parseDate(quest.getString("end-date"));
                String questType = quest.getString("type", "playtime").toLowerCase(Locale.ROOT);
                if (isRestrictedType(questType)) {
                    plugin.getLogger().warning("Quest " + id + " uses a disabled quest type and was skipped: " + questType);
                    continue;
                }
                String description = quest.isList("description")
                        ? String.join("\n", quest.getStringList("description"))
                        : quest.getString("description", "Complete this objective.");
                QuestDefinition definition = new QuestDefinition(
                        id,
                        quest.getString("display-name", id),
                        description,
                        quest.getString("icon", "PAPER"),
                        questType,
                        quest.getString("variable", "*"),
                        quest.getInt("amount", 1),
                        quest.getLong("xp", 0),
                        category,
                        quest.getDouble("weight", 1.0),
                        quest.getString("permission", ""),
                        quest.getStringList("worlds"),
                        start, end,
                        quest.getBoolean("premium-only", false)
                );
                definitions.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid quest " + id + " in " + file.getName(), exception);
            }
        }
    }

    public void refreshAssignments(PlayerProfile profile) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate dailyDate = now.toLocalDate();
        if (now.toLocalTime().isBefore(dailyReset)) dailyDate = dailyDate.minusDays(1);
        String dailyKey = dailyDate.toString();
        if (!dailyKey.equals(profile.dailyPeriod())) {
            clearCategoryState(profile, QuestCategory.DAILY);
            profile.dailyAssigned().clear();
            profile.dailyPeriod(dailyKey);
            profile.dailyRerollsUsed(0);
            profile.dailyAssigned().addAll(weightedPick(QuestCategory.DAILY, dailyCount));
        }

        LocalDate weeklyDate = now.toLocalDate();
        if (now.toLocalTime().isBefore(weeklyReset)) weeklyDate = weeklyDate.minusDays(1);
        weeklyDate = weeklyDate.with(TemporalAdjusters.previousOrSame(weeklyStart));
        String weeklyKey = weeklyDate.toString();
        if (!weeklyKey.equals(profile.weeklyPeriod())) {
            clearCategoryState(profile, QuestCategory.WEEKLY);
            profile.weeklyAssigned().clear();
            profile.weeklyPeriod(weeklyKey);
            profile.weeklyAssigned().addAll(weightedPick(QuestCategory.WEEKLY, weeklyCount));
        }
    }

    private void clearCategoryState(PlayerProfile profile, QuestCategory category) {
        for (QuestDefinition quest : definitions.values()) {
            if (quest.category() == category) {
                profile.questProgress().remove(quest.id());
                profile.completedQuestIds().remove(quest.id());
            }
        }
    }

    private List<String> weightedPick(QuestCategory category, int count) {
        List<QuestDefinition> pool = definitions.values().stream().filter(q -> q.category() == category).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String> chosen = new ArrayList<>();
        while (!pool.isEmpty() && chosen.size() < count) {
            double total = pool.stream().mapToDouble(QuestDefinition::weight).sum();
            double needle = ThreadLocalRandom.current().nextDouble(total);
            QuestDefinition selected = pool.get(pool.size() - 1);
            for (QuestDefinition candidate : pool) {
                needle -= candidate.weight();
                if (needle <= 0) { selected = candidate; break; }
            }
            chosen.add(selected.id());
            pool.remove(selected);
        }
        return chosen;
    }

    public List<QuestDefinition> activeFor(PlayerProfile profile) {
        refreshAssignments(profile);
        List<QuestDefinition> result = new ArrayList<>();
        for (String id : profile.dailyAssigned()) addIfPresent(result, id);
        for (String id : profile.weeklyAssigned()) addIfPresent(result, id);
        LocalDate today = LocalDate.now(zoneId);
        for (QuestDefinition quest : definitions.values()) {
            if (quest.category() == QuestCategory.EVENT &&
                    (manuallyEnabledEvents.contains(quest.id()) || (!manuallyDisabledEvents.contains(quest.id()) && quest.dateActive(today)))) result.add(quest);
            if (quest.category() == QuestCategory.SEASON) result.add(quest);
        }
        return result;
    }

    private void addIfPresent(List<QuestDefinition> result, String id) {
        QuestDefinition quest = definitions.get(id);
        if (quest != null) result.add(quest);
    }

    public List<QuestDefinition> byCategory(QuestCategory category, PlayerProfile profile) {
        return activeFor(profile).stream().filter(q -> q.category() == category).toList();
    }


    public boolean rerollDaily(PlayerProfile profile) {
        refreshAssignments(profile);
        if (profile.dailyAssigned().isEmpty()) return false;
        List<QuestDefinition> available = definitions.values().stream()
                .filter(q -> q.category() == QuestCategory.DAILY && !profile.dailyAssigned().contains(q.id()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (available.isEmpty()) return false;
        String removed = profile.dailyAssigned().stream().skip(ThreadLocalRandom.current().nextInt(profile.dailyAssigned().size())).findFirst().orElse(null);
        if (removed == null) return false;
        profile.dailyAssigned().remove(removed);
        profile.questProgress().remove(removed);
        profile.completedQuestIds().remove(removed);
        double total = available.stream().mapToDouble(QuestDefinition::weight).sum();
        double needle = ThreadLocalRandom.current().nextDouble(total);
        QuestDefinition selected = available.get(available.size() - 1);
        for (QuestDefinition candidate : available) {
            needle -= candidate.weight();
            if (needle <= 0) { selected = candidate; break; }
        }
        profile.dailyAssigned().add(selected.id());
        return true;
    }

    private boolean isRestrictedType(String type) {
        return net.octa.octapass.quest.action.QuestActionRegistry.isRestrictedType(type);
    }

    public QuestDefinition get(String id) { return definitions.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<QuestDefinition> all() { return Collections.unmodifiableCollection(definitions.values()); }
    public int size() { return definitions.size(); }
    public ZoneId zoneId() { return zoneId; }

    public boolean startEvent(String id) {
        QuestDefinition quest = get(id);
        if (quest == null || quest.category() != QuestCategory.EVENT) return false;
        manuallyDisabledEvents.remove(quest.id());
        manuallyEnabledEvents.add(quest.id());
        return true;
    }
    public boolean stopEvent(String id) {
        QuestDefinition quest = get(id);
        if (quest == null || quest.category() != QuestCategory.EVENT) return false;
        manuallyEnabledEvents.remove(quest.id());
        manuallyDisabledEvents.add(quest.id());
        return true;
    }
    public Set<String> manuallyEnabledEvents() { return Collections.unmodifiableSet(manuallyEnabledEvents); }

    public Duration untilDailyReset() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now.toLocalTime().isBefore(dailyReset) ? now.toLocalDate().atTime(dailyReset).atZone(zoneId) : now.toLocalDate().plusDays(1).atTime(dailyReset).atZone(zoneId);
        return Duration.between(now, next);
    }
    public Duration untilWeeklyReset() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate nextDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(weeklyStart));
        ZonedDateTime next = nextDate.atTime(weeklyReset).atZone(zoneId);
        if (!next.isAfter(now)) next = next.plusWeeks(1);
        return Duration.between(now, next);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }
    private static LocalTime parseTime(String value, LocalTime fallback) {
        try { return LocalTime.parse(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
