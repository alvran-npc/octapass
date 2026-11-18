package net.octa.octapass.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoosterService {
    private final Map<UUID, Booster> personal = new ConcurrentHashMap<>();
    private volatile Booster global;

    public void give(UUID playerId, double multiplier, Duration duration) {
        if (multiplier <= 1 || duration.isNegative() || duration.isZero()) return;
        personal.put(playerId, new Booster(multiplier, Instant.now().plus(duration)));
    }
    public void giveGlobal(double multiplier, Duration duration) {
        if (multiplier <= 1 || duration.isNegative() || duration.isZero()) return;
        global = new Booster(multiplier, Instant.now().plus(duration));
    }
    public double multiplier(UUID playerId) {
        Instant now = Instant.now();
        double value = 1.0;
        Booster own = personal.get(playerId);
        if (own != null) {
            if (own.expiresAt().isAfter(now)) value *= own.multiplier();
            else personal.remove(playerId, own);
        }
        Booster all = global;
        if (all != null) {
            if (all.expiresAt().isAfter(now)) value *= all.multiplier();
            else global = null;
        }
        return value;
    }
    public boolean active(UUID playerId) { return multiplier(playerId) > 1.0; }
    public Duration remaining(UUID playerId) {
        Instant now = Instant.now();
        Booster own = personal.get(playerId);
        Booster all = global;
        Instant expiry = own != null && own.expiresAt().isAfter(now) ? own.expiresAt() : Instant.EPOCH;
        if (all != null && all.expiresAt().isAfter(now) && all.expiresAt().isAfter(expiry)) expiry = all.expiresAt();
        return expiry.equals(Instant.EPOCH) ? Duration.ZERO : Duration.between(now, expiry);
    }
    public double activeMultiplier(UUID playerId) { return multiplier(playerId); }
    private record Booster(double multiplier, Instant expiresAt) {}
}
