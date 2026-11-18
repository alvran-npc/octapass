package net.octa.octapass.hook.octammo;

import net.octa.octapass.AdvancedOctaPass;
import org.bukkit.Bukkit;

/** Detects OctaMMO without linking to undocumented API signatures. */
public final class OctaMMOHook {
    private final AdvancedOctaPass plugin;
    private boolean enabled;

    public OctaMMOHook(AdvancedOctaPass plugin) { this.plugin = plugin; }

    public void setup() {
        enabled = Bukkit.getPluginManager().getPlugin("OctaMMO") != null;
        if (enabled) {
            plugin.getLogger().warning("OctaMMO detected, but no direct quest listener was registered: public event/method signatures were not verifiable from the accessible API documentation. No guessed API calls are made.");
        }
    }

    public boolean enabled() { return enabled; }
}
