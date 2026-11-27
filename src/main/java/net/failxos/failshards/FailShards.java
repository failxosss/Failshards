package net.failxos.failshards;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FailShards extends JavaPlugin {

    private ShardManager manager;
    private ShardAbilities abilities;
    private SecretTotemRecipe secretRecipe;
    private ElementRecipes elementRecipes;

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
        PluginCommand change = getCommand("changeshard");
        if (change != null) change.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("This command is for players only."); return true;
            }
            if (args.length != 0) return false;
            return manager.changeShard(player);
        });
        secretRecipe = new SecretTotemRecipe(this, manager);
        secretRecipe.register();
        elementRecipes = new ElementRecipes(this, manager);
        elementRecipes.register();
        manager.startTask();
    }

    @Override
    public void onDisable() {
        if (elementRecipes != null) elementRecipes.unregister();
        if (secretRecipe != null) secretRecipe.unregister();
        if (abilities != null) abilities.shutdown();
        if (manager != null) {
            manager.shutdown();
        }
    }
}
