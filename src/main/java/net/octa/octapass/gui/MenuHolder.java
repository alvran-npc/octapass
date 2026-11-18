package net.octa.octapass.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private final String menu;
    private final int page;
    private Inventory inventory;
    public MenuHolder(String menu, int page) { this.menu = menu; this.page = page; }
    public String menu() { return menu; }
    public int page() { return page; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
