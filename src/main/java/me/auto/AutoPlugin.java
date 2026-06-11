package me.auto;

import me.auto.listeners.*;
import me.auto.managers.*;
import me.auto.mine.MineCommand;
import me.auto.forest.ForestCommand;
import me.auto.farm.FarmCommand;
import me.auto.scoreboard.ScoreboardCommand;
import me.auto.scoreboard.ScoreboardManager;
import me.auto.selection.SelectionListener;
import me.auto.selection.SelectionManager;
import me.auto.npc.NpcCommand;
import me.auto.npc.NpcManager;
import me.auto.utils.VaultHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoPlugin extends JavaPlugin {

    private static AutoPlugin instance;

    private VaultHook vaultHook;
    private HUDManager hudManager;
    private SelectionManager selectionManager;
    private MineManager mineManager;
    private ForestManager forestManager;
    private FarmManager farmManager;
    private ScoreboardManager scoreboardManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().severe("Vault не знайдено! Вимикаємо плагін.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        hudManager        = new HUDManager(this);
        selectionManager  = new SelectionManager();
        // NpcManager потрібен раніше щоб менеджери могли викликати losePending
        npcManager        = new NpcManager(this);
        mineManager       = new MineManager(this);
        forestManager     = new ForestManager(this);
        farmManager       = new FarmManager(this);
        scoreboardManager = new ScoreboardManager(this);

        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MineListener(this),      this);
        getServer().getPluginManager().registerEvents(new ForestListener(this),    this);
        getServer().getPluginManager().registerEvents(new FarmListener(this),      this);
        getServer().getPluginManager().registerEvents(new NpcListener(this),       this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this),    this);

        reg("automine",       new MineCommand(this));
        reg("autoforest",     new ForestCommand(this));
        reg("autofarm",       new FarmCommand(this));
        reg("autoscoreboard", new ScoreboardCommand(this));
        reg("autonpc",        new NpcCommand(this));

        getLogger().info("AutoPlugin v3.0 увімкнено! ⛏🌾🪵");
    }

    @Override
    public void onDisable() {
        if (mineManager   != null) mineManager.saveAll();
        if (forestManager != null) forestManager.saveAll();
        if (farmManager   != null) farmManager.saveAll();
        if (npcManager    != null) npcManager.saveAll();
        getLogger().info("AutoPlugin вимкнено.");
    }

    private void reg(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        if (executor instanceof org.bukkit.command.CommandExecutor ce) cmd.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc)    cmd.setTabCompleter(tc);
    }

    public static AutoPlugin getInstance()           { return instance; }
    public Economy            getEconomy()           { return vaultHook.getEconomy(); }
    public HUDManager         getHudManager()        { return hudManager; }
    public SelectionManager   getSelectionManager()  { return selectionManager; }
    public MineManager        getMineManager()       { return mineManager; }
    public ForestManager      getForestManager()     { return forestManager; }
    public FarmManager        getFarmManager()       { return farmManager; }
    public ScoreboardManager  getScoreboardManager() { return scoreboardManager; }
    public NpcManager         getNpcManager()        { return npcManager; }
}
