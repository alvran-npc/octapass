package net.octa.octapass.hook.octagens;

import net.octa.octapass.AdvancedOctaPass;
import org.bukkit.Bukkit;

/** Safe fallback integration using the OctaGens PlaceholderAPI values documented by its README. */
public final class OctaGensHook {
    private final AdvancedOctaPass plugin;
    private boolean enabled;
    public OctaGensHook(AdvancedOctaPass plugin) { this.plugin = plugin; }
    public void setup() {
        enabled = Bukkit.getPluginManager().getPlugin("OctaGens") != null;
        if (enabled) plugin.getLogger().info("OctaGens detected: generator objectives can use its documented PlaceholderAPI metrics.");
    }
    public boolean enabled() { return enabled; }
}
