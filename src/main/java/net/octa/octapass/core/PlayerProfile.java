package net.octa.octapass.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProfile {
    private final UUID uniqueId;
    private volatile String seasonId;
    private volatile String lastName = "Unknown";
    private volatile long totalXp;
    private volatile boolean premium;
    private volatile long questsCompleted;
    private volatile String dailyPeriod = "";
    private volatile String weeklyPeriod = "";
    private volatile int dailyRerollsUsed;
    private final Set<String> claimedRewards = ConcurrentHashMap.newKeySet();
    private final Set<String> dailyAssigned = ConcurrentHashMap.newKeySet();
    private final Set<String> weeklyAssigned = ConcurrentHashMap.newKeySet();
    private final Set<String> completedQuestIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> questProgress = new ConcurrentHashMap<>();
    private final List<String> pendingRewards = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean actionBarEnabled = true;
    private volatile boolean bossBarEnabled = true;
    private volatile boolean notificationsEnabled = true;

    public PlayerProfile(UUID uniqueId, String seasonId) {
        this.uniqueId = uniqueId;
        this.seasonId = seasonId;
    }
    public UUID uniqueId() { return uniqueId; }
    public String seasonId() { return seasonId; }
    public String lastName() { return lastName; }
    public void lastName(String value) { if (value != null && !value.isBlank()) lastName = value; }
    public void seasonId(String value) { seasonId = value; }
    public long totalXp() { return totalXp; }
    public void totalXp(long value) { totalXp = Math.max(0, value); }
    public long addXp(long value) { if (value > 0) { try { totalXp = Math.addExact(totalXp, value); } catch (ArithmeticException ignored) { totalXp = Long.MAX_VALUE; } } return totalXp; }
    public boolean premium() { return premium; }
    public void premium(boolean value) { premium = value; }
    public long questsCompleted() { return questsCompleted; }
    public void questCompleted() { questsCompleted++; }
    public String dailyPeriod() { return dailyPeriod; }
    public void dailyPeriod(String value) { dailyPeriod = value; }
    public String weeklyPeriod() { return weeklyPeriod; }
    public void weeklyPeriod(String value) { weeklyPeriod = value; }
    public int dailyRerollsUsed() { return dailyRerollsUsed; }
    public void dailyRerollsUsed(int value) { dailyRerollsUsed = Math.max(0, value); }
    public Set<String> claimedRewards() { return claimedRewards; }
    public Set<String> dailyAssigned() { return dailyAssigned; }
    public Set<String> weeklyAssigned() { return weeklyAssigned; }
    public Set<String> completedQuestIds() { return completedQuestIds; }
    public Map<String, Integer> questProgress() { return questProgress; }
    public List<String> pendingRewards() { return pendingRewards; }
    public boolean actionBarEnabled() { return actionBarEnabled; }
    public void actionBarEnabled(boolean value) { actionBarEnabled = value; }
    public boolean bossBarEnabled() { return bossBarEnabled; }
    public void bossBarEnabled(boolean value) { bossBarEnabled = value; }
    public boolean notificationsEnabled() { return notificationsEnabled; }
    public void notificationsEnabled(boolean value) { notificationsEnabled = value; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uniqueId", uniqueId.toString());
        map.put("seasonId", seasonId);
        map.put("lastName", lastName);
        map.put("totalXp", totalXp);
        map.put("premium", premium);
        map.put("questsCompleted", questsCompleted);
        map.put("dailyPeriod", dailyPeriod);
        map.put("weeklyPeriod", weeklyPeriod);
        map.put("dailyRerollsUsed", dailyRerollsUsed);
        map.put("claimedRewards", new ArrayList<>(claimedRewards));
        map.put("dailyAssigned", new ArrayList<>(dailyAssigned));
        map.put("weeklyAssigned", new ArrayList<>(weeklyAssigned));
        map.put("completedQuestIds", new ArrayList<>(completedQuestIds));
        map.put("questProgress", new HashMap<>(questProgress));
        synchronized (pendingRewards) { map.put("pendingRewards", new ArrayList<>(pendingRewards)); }
        map.put("actionBarEnabled", actionBarEnabled);
        map.put("bossBarEnabled", bossBarEnabled);
        map.put("notificationsEnabled", notificationsEnabled);
        return map;
    }

    public static PlayerProfile fromMap(UUID id, String currentSeason, Map<String, Object> map) {
        PlayerProfile profile = new PlayerProfile(id, Objects.toString(map.getOrDefault("seasonId", currentSeason)));
        profile.lastName(Objects.toString(map.getOrDefault("lastName", "Unknown")));
        profile.totalXp(asLong(map.get("totalXp")));
        profile.premium(Boolean.parseBoolean(Objects.toString(map.getOrDefault("premium", false))));
        profile.questsCompleted(asLong(map.get("questsCompleted")));
        profile.dailyPeriod(Objects.toString(map.getOrDefault("dailyPeriod", "")));
        profile.weeklyPeriod(Objects.toString(map.getOrDefault("weeklyPeriod", "")));
        profile.dailyRerollsUsed((int) asLong(map.get("dailyRerollsUsed")));
        readSet(map.get("claimedRewards"), profile.claimedRewards);
        readSet(map.get("dailyAssigned"), profile.dailyAssigned);
        readSet(map.get("weeklyAssigned"), profile.weeklyAssigned);
        readSet(map.get("completedQuestIds"), profile.completedQuestIds);
        Object progress = map.get("questProgress");
        if (progress instanceof Map<?, ?> values) values.forEach((key, value) -> profile.questProgress.put(String.valueOf(key), (int) asLong(value)));
        Object pending = map.get("pendingRewards");
        if (pending instanceof Collection<?> values) values.forEach(value -> profile.pendingRewards.add(String.valueOf(value)));
        profile.actionBarEnabled = Boolean.parseBoolean(Objects.toString(map.getOrDefault("actionBarEnabled", true)));
        profile.bossBarEnabled = Boolean.parseBoolean(Objects.toString(map.getOrDefault("bossBarEnabled", true)));
        profile.notificationsEnabled = Boolean.parseBoolean(Objects.toString(map.getOrDefault("notificationsEnabled", true)));
        return profile;
    }

    private void questsCompleted(long value) { questsCompleted = Math.max(0, value); }
    private static void readSet(Object source, Set<String> target) {
        if (source instanceof Collection<?> values) values.forEach(value -> target.add(String.valueOf(value)));
    }
    private static long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (RuntimeException ignored) { return 0; }
    }
}
