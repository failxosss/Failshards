package net.failxos.failshards;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.Set;

public final class ElementRecipes implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final Set<NamespacedKey> registered = new HashSet<>();
    private BukkitTask retry;
    public ElementRecipes(FailShards plugin, ShardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        retry = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            add("water", Material.TURTLE_SCUTE, Material.WATER_BUCKET, Material.HEART_OF_THE_SEA, PotionType.LONG_WATER_BREATHING);
            add("fire", Material.NETHERITE_INGOT, Material.MAGMA_CREAM, Material.LAVA_BUCKET, PotionType.LONG_FIRE_RESISTANCE);
            if (registered.size() == 2 && retry != null) retry.cancel();
        }, 20L, 40L);
    }
    private void add(String id, Material corner, Material edge, Material center, PotionType type) {
        NamespacedKey key = new NamespacedKey(plugin, id + "_shard_craft");
        if (registered.contains(key)) return;
        Shard shard = manager.get(id);
        if (shard == null) return;
        ItemStack result = manager.createItem(shard);
        if (result == null || result.getType().isAir()) return;
        result = result.clone();
        result.setAmount(1);
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(type);
        potion.setItemMeta(meta);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("CEC", "PMP", "CEC");
        recipe.setIngredient('C', corner);
        recipe.setIngredient('E', edge);
        recipe.setIngredient('M', center);
        recipe.setIngredient('P', new RecipeChoice.ExactChoice(potion));
        plugin.getServer().removeRecipe(key);
        if (plugin.getServer().addRecipe(recipe)) {
            registered.add(key);
            for (Player p : plugin.getServer().getOnlinePlayers()) p.discoverRecipe(key);
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        for (NamespacedKey key : registered) event.getPlayer().discoverRecipe(key);
    }
    public void unregister() {
        if (retry != null) retry.cancel();
        for (NamespacedKey key : registered) plugin.getServer().removeRecipe(key);
        registered.clear();
    }
}
