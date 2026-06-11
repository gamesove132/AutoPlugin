package me.auto.utils;

import me.auto.AutoPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final AutoPlugin plugin;
    private Economy economy;

    public VaultHook(AutoPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    public Economy getEconomy() { return economy; }

    public double getBalance(org.bukkit.entity.Player p) {
        return economy.getBalance(p);
    }

    public void deposit(org.bukkit.entity.Player p, double amount) {
        economy.depositPlayer(p, amount);
    }
}
