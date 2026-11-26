package net.failxos.failshards;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FailShards extends JavaPlugin {

    private ShardManager manager;
    private ShardAbilities abilities;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new ShardManager(this);
        manager.reload();
        getServer().getPluginManager().registerEvents(new ShardListener(this, manager), this);

        abilities = new ShardAbilities(this, manager);
        getServer().getPluginManager().registerEvents(abilities, this);

        PluginCommand cmd = getCommand("failshards");
        if (cmd != null) {
            ShardCommand sc = new ShardCommand(manager);
            cmd.setExecutor(sc);
            cmd.setTabCompleter(sc);
        }
        manager.startTask();
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
        if (manager != null) {
            manager.shutdown();
        }
    }
}
