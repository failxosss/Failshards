package net.failxos.failshards;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.Keyed;

/** Server-side shaped recipe, intentionally never unlocked in the recipe book. */
public final class SecretTotemRecipe implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final NamespacedKey key;

    public SecretTotemRecipe(FailShards plugin, ShardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.key = new NamespacedKey(plugin, "secret_totem_shard");
    }

    public void register() {
        plugin.getServer().removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.PAPER));
        recipe.shape("TAT", "AEA", "TAT");
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        recipe.setIngredient('E', Material.DRAGON_EGG);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (!plugin.getServer().addRecipe(recipe)) {
            plugin.getLogger().warning("Could not register secret Totem Shard recipe.");
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) player.undiscoverRecipe(key);
    }

    public void unregister() { plugin.getServer().removeRecipe(key); }

    private boolean matches(Recipe recipe) {
        return recipe instanceof Keyed keyed && key.equals(keyed.getKey());
    }

    private ItemStack result() {
        if (!plugin.getConfig().getBoolean("secret-totem-recipe.enabled", true)) return null;
        Shard shard = manager.get("totem");
        if (shard == null) return null;
        ItemStack item = manager.createItem(shard);
        if (item == null || item.getType().isAir()) return null;
        item = item.clone();
        item.setAmount(1);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if (matches(event.getRecipe())) event.getInventory().setResult(result());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!matches(event.getRecipe())) return;
        ItemStack expected = result();
        ItemStack current = event.getCurrentItem();
        if (expected == null || current == null || !current.isSimilar(expected) || current.getAmount() != 1) {
            event.setCancelled(true);
            event.getInventory().setResult(expected);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void discover(PlayerRecipeDiscoverEvent event) {
        if (key.equals(event.getRecipe())) event.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) { event.getPlayer().undiscoverRecipe(key); }
}
