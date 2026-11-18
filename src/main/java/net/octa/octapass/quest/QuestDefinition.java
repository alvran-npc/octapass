package net.octa.octapass.quest;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public record QuestDefinition(
        String id,
        String displayName,
        String description,
        String icon,
        String type,
        String variable,
        int amount,
        long xp,
        QuestCategory category,
        double weight,
        String permission,
        List<String> worlds,
        LocalDate startDate,
        LocalDate endDate,
        boolean premiumOnly
) {
    public QuestDefinition {
        id = id.toLowerCase(Locale.ROOT);
        type = type.toLowerCase(Locale.ROOT);
        amount = Math.max(1, amount);
        xp = Math.max(0, xp);
        weight = Math.max(0.01, weight);
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        permission = permission == null ? "" : permission;
        variable = variable == null ? "*" : variable;
    }

    public boolean dateActive(LocalDate today) {
        if (startDate != null && today.isBefore(startDate)) return false;
        return endDate == null || !today.isAfter(endDate);
    }
}
