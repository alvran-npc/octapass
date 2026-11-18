package net.octa.octapass.core;

import java.time.Duration;
import java.time.Instant;

public record Season(String id, String name, Instant startsAt, Instant endsAt, int maxLevel) {
    public boolean isActive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
    public Duration timeLeft(Instant now) {
        return now.isBefore(endsAt) ? Duration.between(now, endsAt) : Duration.ZERO;
    }
}
