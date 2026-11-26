package net.failxos.failshards;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class ShardManager {

    public enum Mode { INVENTORY, HOTBAR, HAND }

    private final FailShards plugin;
    private final Map<String, Shard> shards = new LinkedHashMap<>();
    private final Map<String, Shard> byItemId = new HashMap<>();
    private final Map<UUID, Long> nextRandom = new HashMap<>();

    final NamespacedKey fallbackKey;
    final NamespacedKey receivedKey;
    final NamespacedKey pendingKey;

    private org.bukkit.configuration.file.YamlConfiguration language;
    private Mode mode = Mode.INVENTORY;
    private BukkitTask task;

    // ItemsAdder via reflection (no compile-time dependency)
    private Method iaByItemStack;
    private Method iaGetNamespacedId;
    private Method iaGetInstance;
    private Method iaGetItemStack;

    public ShardManager(FailShards plugin) {
        this.plugin = plugin;
        this.fallbackKey = new NamespacedKey(plugin, "shard_type");
        this.receivedKey = new NamespacedKey(plugin, "received_shard");
        this.pendingKey = new NamespacedKey(plugin, "pending_shard");
    }

    // ---------------------------------------------------------------- config

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        String code=cfg.getString("language","en").toLowerCase(Locale.ROOT);
        if(!code.matches("[a-z]{2}" ) || plugin.getResource("lang/"+code+".yml")==null) code="en";
        java.io.File langFile=new java.io.File(plugin.getDataFolder(),"lang/"+code+".yml");
        if(!langFile.exists()) plugin.saveResource("lang/"+code+".yml",false);
        language=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(langFile);

        for (Player p : Bukkit.getOnlinePlayers()) {
            clearModifiers(p);
        }
        shards.clear();
        byItemId.clear();

        try {
            mode = Mode.valueOf(cfg.getString("mode", "INVENTORY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.INVENTORY;
        }

        ConfigurationSection sec = cfg.getConfigurationSection("shards");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null || !s.getBoolean("enabled", true)) continue;

                String id = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                String itemId = s.getString("item", "failshards:" + id + "_shard");
                String name = s.getString("name", key);

                List<Shard.EffectDef> effects = parseEffects(s.getStringList("effects"));
                List<Shard.EffectDef> random = parseEffects(s.getStringList("random-effects"));

                List<Shard.AttrDef> attrs = new ArrayList<>();
                for (String raw : s.getStringList("attributes")) {
                    String[] parts = raw.split(":");
                    if (parts.length != 2) {
                        plugin.getLogger().warning("Invalid attribute: " + raw);
                        continue;
                    }
                    String attrName = parts[0].trim().toLowerCase(Locale.ROOT);
                    Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attrName));
                    if (attr == null) {
                        plugin.getLogger().warning("Unknown attribute: " + attrName);
                        continue;
                    }
                    try {
                        double amount = Double.parseDouble(parts[1].trim());
                        attrs.add(new Shard.AttrDef(attr, amount,
                                new NamespacedKey(plugin, "shard_" + id + "_" + attrName)));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid attribute value: " + raw);
                    }
                }

                Shard shard = new Shard(id, itemId, name, effects, attrs,
                        s.getInt("ignite-on-hit-ticks", 0), random,
                        Math.max(1, s.getInt("random-interval", 20)),
                        Math.max(1, s.getInt("random-duration", 15)));
                shards.put(id, shard);
                byItemId.put(itemId.toLowerCase(Locale.ROOT), shard);
            }
        }
        initItemsAdder();
        plugin.getLogger().info("Loaded shards: " + shards.size());
    }

    private List<Shard.EffectDef> parseEffects(List<String> list) {
        List<Shard.EffectDef> out = new ArrayList<>();
        for (String raw : list) {
            String[] parts = raw.split(":");
            PotionEffectType type = PotionEffectType.getByKey(
                    NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Unknown effect: " + raw);
                continue;
            }
            int amp = 0;
            if (parts.length > 1) {
                try {
                    amp = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid effect amplifier: " + raw);
                }
            }
            out.add(new Shard.EffectDef(type, amp));
        }
        return out;
    }

    private void initItemsAdder() {
        iaByItemStack = null;
        iaGetNamespacedId = null;
        iaGetInstance = null;
        iaGetItemStack = null;
        org.bukkit.plugin.Plugin ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (ia == null) {
            plugin.getLogger().warning("ItemsAdder not found - using fallback PAPER items.");
            return;
        }
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack", true, ia.getClass().getClassLoader());
            iaByItemStack = cs.getMethod("byItemStack", ItemStack.class);
            iaGetNamespacedId = cs.getMethod("getNamespacedID");
            iaGetInstance = cs.getMethod("getInstance", String.class);
            iaGetItemStack = cs.getMethod("getItemStack");
            plugin.getLogger().info("ItemsAdder connected.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load ItemsAdder API: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- getters

    public Map<String, Shard> shards() {
        return shards;
    }

    public Shard get(String key) {
        return key == null ? null : shards.get(key.toLowerCase(Locale.ROOT));
    }

    public Mode mode() {
        return mode;
    }

    public String text(String path) {
        String override=plugin.getConfig().getString("messages."+path, "");
        return override.isEmpty()?language.getString(path,path):override;
    }
    public void msg(org.bukkit.command.CommandSender to, String path, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        String text = text(path);
        if (!text.isEmpty()) {
            to.sendRichMessage(text, r);
        }
    }

    // ---------------------------------------------------------------- item identification

    private String iaId(ItemStack item) {
        if (iaByItemStack == null) return null;
        try {
            Object cs = iaByItemStack.invoke(null, item);
            if (cs == null) return null;
            Object id = iaGetNamespacedId.invoke(cs);
            return id == null ? null : id.toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    public Shard shardOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        String id = iaId(item);
        if (id != null) {
            Shard s = byItemId.get(id);
            if (s != null) return s;
        }
        if (item.hasItemMeta()) {
            String k = item.getItemMeta().getPersistentDataContainer().get(fallbackKey, PersistentDataType.STRING);
            if (k != null) return shards.get(k);
        }
        return null;
    }

    public ItemStack createItem(Shard s) {
        if (iaGetInstance != null) {
            try {
                Object cs = iaGetInstance.invoke(null, s.itemId());
                if (cs != null) {
                    return (ItemStack) iaGetItemStack.invoke(cs);
                }
            } catch (Exception ignored) {
                // ItemsAdder not loaded yet / item does not exist
            }
            return null;
        }
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> {
            m.displayName(MiniMessage.miniMessage().deserialize("<gold>" + s.name())
                    .decoration(TextDecoration.ITALIC, false));
            m.getPersistentDataContainer().set(fallbackKey, PersistentDataType.STRING, s.key());
        });
        return it;
    }

    public void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------------------------------------------------------------- first join

    public void giveRandomLater(Player p) {
        attempt(p, 0);
    }

    private void attempt(Player p, int n) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return; // remains pending until next join
            List<Shard> all = new ArrayList<>(shards.values());
            if (all.isEmpty()) return;
            Shard s = all.get(ThreadLocalRandom.current().nextInt(all.size()));
            ItemStack it = createItem(s);
            if (it == null) {
                if (n < 5) {
                    attempt(p, n + 1);
                } else {
                    plugin.getLogger().warning("Could not create item " + s.itemId()
                            + " - is the ItemsAdder pack installed?");
                }
                return;
            }
            give(p, it);
            p.getPersistentDataContainer().set(receivedKey, PersistentDataType.BYTE, (byte) 1);
            p.getPersistentDataContainer().remove(pendingKey);
            msg(p, "received", Placeholder.unparsed("shard", s.name()));
        }, n == 0 ? 40L : 100L);
    }

    // ---------------------------------------------------------------- abilities

    public Set<Shard> activeShards(Player p) {
        Set<Shard> out = new LinkedHashSet<>();
        PlayerInventory inv = p.getInventory();
        switch (mode) {
            case HAND -> {
                add(out, inv.getItemInMainHand());
                add(out, inv.getItemInOffHand());
            }
            case HOTBAR -> {
                for (int i = 0; i < 9; i++) add(out, inv.getItem(i));
                add(out, inv.getItemInOffHand());
            }
            case INVENTORY -> {
                for (ItemStack it : inv.getStorageContents()) add(out, it);
                add(out, inv.getItemInOffHand());
            }
        }
        return out;
    }

    private void add(Set<Shard> out, ItemStack it) {
        Shard s = shardOf(it);
        if (s != null) out.add(s);
    }

    public void startTask() {
        stopTask();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stopTask();
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearModifiers(p);
        }
    }

    public void forget(UUID id) {
        nextRandom.remove(id);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            Set<Shard> active = activeShards(p);

            for (Shard s : active) {
                for (Shard.EffectDef e : s.effects()) {
                    p.addPotionEffect(new PotionEffect(e.type(), 80, e.amplifier(), true, false, true));
                }
                if (!s.randomEffects().isEmpty()) {
                    long next = nextRandom.getOrDefault(p.getUniqueId(), 0L);
                    if (now >= next) {
                        Shard.EffectDef e = s.randomEffects()
                                .get(ThreadLocalRandom.current().nextInt(s.randomEffects().size()));
                        p.addPotionEffect(new PotionEffect(e.type(), s.randomDuration() * 20,
                                e.amplifier(), true, false, true));
                        nextRandom.put(p.getUniqueId(), now + s.randomInterval() * 1000L);
                    }
                }
            }
            syncAttributes(p, active);
        }
    }

    private void syncAttributes(Player p, Set<Shard> active) {
        for (Shard s : shards.values()) {
            boolean should = active.contains(s);
            for (Shard.AttrDef def : s.attributes()) {
                AttributeInstance inst = p.getAttribute(def.attribute());
                if (inst == null) continue;
                AttributeModifier existing = null;
                for (AttributeModifier m : inst.getModifiers()) {
                    if (def.key().equals(m.getKey())) {
                        existing = m;
                        break;
                    }
                }
                if (should && existing == null) {
                    inst.addTransientModifier(new AttributeModifier(def.key(), def.amount(),
                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                } else if (!should && existing != null) {
                    inst.removeModifier(existing);
                }
            }
        }
    }

    public void clearModifiers(Player p) {
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        for (Attribute a : Registry.ATTRIBUTE) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey().getNamespace().equals(ns)) {
                    inst.removeModifier(m);
                }
            }
        }
    }
}
