package net.octa.octapass.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultHook {
    private final JavaPlugin plugin;
    private Economy economy;
    public VaultHook(JavaPlugin plugin) { this.plugin = plugin; }
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) return false;
        economy = provider.getProvider();
        return economy != null;
    }
    public boolean available() { return economy != null; }
    public Economy economy() { return economy; }
}
