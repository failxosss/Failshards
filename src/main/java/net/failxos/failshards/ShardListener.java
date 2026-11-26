package net.failxos.failshards;

import java.util.Iterator;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ShardListener implements Listener {

    private final FailShards plugin;
    private final ShardManager manager;

    public ShardListener(FailShards plugin, ShardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("first-join.enabled", true)) return;
        Player p = e.getPlayer();
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(manager.receivedKey, PersistentDataType.BYTE)) return;

        if (!p.hasPlayedBefore()) {
            pdc.set(manager.pendingKey, PersistentDataType.BYTE, (byte) 1);
        }
        boolean pending = pdc.has(manager.pendingKey, PersistentDataType.BYTE);
        boolean onlyNew = plugin.getConfig().getBoolean("first-join.only-new-players", true);
        if (onlyNew && !pending) return;

        manager.giveRandomLater(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.forget(e.getPlayer().getUniqueId());
        manager.clearModifiers(e.getPlayer());
    }

    /** Keep shards on death; other items drop. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("keep-on-death", true)) return;
        if (e.getKeepInventory()) return;

        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (manager.shardOf(item) != null) {
                it.remove();
                e.getItemsToKeep().add(item);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Set<Shard> active = manager.activeShards(p);
        int ticks = 0;
        for (Shard s : active) {
            ticks = Math.max(ticks, s.igniteTicks());
        }
        if (ticks > 0) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
        }
    }

    // ---- dropped shard protection

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Item item)) return;
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        if (manager.shardOf(item.getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent e) {
        if (!plugin.getConfig().getBoolean("protect-dropped-items", true)) return;
        if (manager.shardOf(e.getEntity().getItemStack()) != null) {
            e.setCancelled(true);
        }
    }
}
