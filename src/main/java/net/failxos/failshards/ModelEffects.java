package net.failxos.failshards;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Professional multi-layer visual ability scenes.
 * Uses multiple display layers: circle + sigil + focus + main emitters + accents.
 * Cosmetic only; gameplay logic stays in ShardAbilities.
 */
public final class ModelEffects implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final BukkitTask task;
    private final YamlConfiguration profiles;

    private static final Set<String> STATIONARY = Set.of(
            "shock", "storm", "grid", "wall", "spikes", "geyser", "galaxy", "reverse",
            "zone", "fan", "wave", "focus", "projectile", "cone", "burst", "tornado"
    );
    private static final Set<String> DIRECTIONAL = Set.of(
            "fan", "trail", "projectile", "wave", "zigzag", "dash", "tornado", "focus",
            "aim", "cone", "wings", "gate"
    );

    private static final class Scene {
        final Player player;
        final World world;
        final String key;
        final int slot;
        final int duration;
        final List<ItemDisplay> models = new ArrayList<>();
        final List<ItemDisplay> accents = new ArrayList<>();
        int age = 0;
        int flash = 0;
        String motion;
        double radius, height, scale, meshSpin, meshStretch, meshPhase;
        Location origin;
        ItemDisplay circle;
        ItemDisplay sigil;
        ItemDisplay focus;
        Scene(Player p, String k, int s, int d) {
            player = p;
            world = p.getWorld();
            key = k;
            slot = s;
            duration = d;
        }
    }

    public ModelEffects(FailShards plugin, ShardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        File f = new File(plugin.getDataFolder(), "visual-profiles.yml");
        if (!f.exists()) plugin.saveResource("visual-profiles.yml", false);
        profiles = YamlConfiguration.loadConfiguration(f);
        try (var in = plugin.getResource("visual-profiles.yml")) {
            if (in != null) profiles.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            plugin.getLogger().warning(ex.toString());
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 2L);
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("visual-effects.enabled", true); }
    public boolean particles() { return !enabled() || plugin.getConfig().getBoolean("visual-effects.vanilla-particles", false); }

    private ItemStack asset(String id) {
        ItemStack item = manager.visualItem("failshards:" + id);
        if (item == null && warned.add(id)) plugin.getLogger().warning("Missing visual model: failshards:" + id + ". Regenerate ItemsAdder pack.");
        return item;
    }

    private ItemDisplay spawn(Location l, ItemStack item) {
        return l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setViewRange((float) Math.max(.1, Math.min(1.75, plugin.getConfig().getDouble("visual-effects.view-range", .75))));
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
        });
    }

    public void play(Player p, String key, int slot, int ticks) {
        if (!enabled() || !plugin.getConfig().getBoolean("visual-effects.shards." + key + ".enabled", true)) return;
        String path = "profiles." + key + ".slot-" + (slot + 1) + ".";
        if (!profiles.getBoolean(path + "enabled", true)) return;
        ItemStack symbol = asset("fx_mesh6_" + key + "_" + (slot + 1));
        if (symbol == null) return;
        remove(p.getUniqueId());
        if (scenes.size() >= Math.max(1, plugin.getConfig().getInt("visual-effects.max-active-players", 30))) return;

        int maxDisplays = Math.min(20, Math.max(4, plugin.getConfig().getInt("visual-effects.max-displays-per-player", 12)));
        int duration = Math.max(10, Math.min(600, plugin.getConfig().getInt("visual-effects.shards." + key + ".slot-" + (slot + 1) + "-ticks", ticks)));
        Scene s = new Scene(p, key, slot, duration);
        scenes.put(p.getUniqueId(), s);

        s.motion = profiles.getString(path + "motion", "rise");
        s.radius = Math.max(.2, Math.min(8, profiles.getDouble(path + "radius", 1.5)));
        s.height = Math.max(0, Math.min(8, profiles.getDouble(path + "height", 1)));
        s.scale = Math.max(.1, Math.min(2.2, profiles.getDouble(path + "scale", .7)));
        s.origin = p.getLocation().clone();
        s.meshSpin = profiles.getDouble(path + "mesh-spin", .8);
        s.meshStretch = profiles.getDouble(path + "mesh-stretch", .15);
        s.meshPhase = profiles.getDouble(path + "mesh-phase", 0);

        int n = Math.max(1, Math.min(maxDisplays, profiles.getInt(path + "count", 6)));
        try {
            Location l = p.getLocation();
            l.setYaw(0);
            l.setPitch(0);
            ItemStack circleAsset = asset("fx_" + key + "_circle");
            ItemStack sigilAsset = asset("fx_" + key + "_sigil");
            ItemStack orbAsset = asset("fx_" + key + "_orb");
            ItemStack mesh = symbol;

            for (int i = 0; i < n; i++) s.models.add(spawn(l, mesh));
            int left = Math.max(0, maxDisplays - n);
            if (profiles.getBoolean(path + "ground-circle", true) && circleAsset != null && left > 0) {
                s.circle = spawn(l, circleAsset);
                left--;
            }
            if (false && sigilAsset != null && left > 0) {
                s.sigil = spawn(l, sigilAsset);
                left--;
            }
            if (false && orbAsset != null && left > 0) {
                s.focus = spawn(l, orbAsset);
                left--;
            }
            int accentCount = 0;
            for (int i = 0; i < accentCount; i++) s.accents.add(spawn(l, symbol));
            render(s);
        } catch (RuntimeException ex) {
            remove(p.getUniqueId());
            plugin.getLogger().warning("Visual effect failed: " + ex.getMessage());
        }
    }

    private int accentCountFor(String motion) {
        return switch (motion) {
            case "shock", "burst", "spikes", "bloom" -> 4;
            case "tornado", "storm", "galaxy", "clock", "reverse" -> 3;
            case "cone", "projectile", "fan", "focus" -> 2;
            default -> 2;
        };
    }

    private boolean usesOrbMain(String motion) {
        return Set.of("lock", "spikes", "shield", "dome", "pulse", "bubble", "heart", "clock").contains(motion);
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double smooth(double x) { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private boolean stationary(String motion) { return STATIONARY.contains(motion); }
    private boolean directional(String motion) { return DIRECTIONAL.contains(motion); }

    private void place(ItemDisplay d, Location origin, double x, double y, double z, float size, Quaternionf rotation, Display.Billboard billboard) {
        Location l = origin.clone().add(x, y, z);
        l.setYaw(0);
        l.setPitch(0);
        d.teleport(l);
        d.setInterpolationDelay(0);
        d.setBillboard(billboard);
        d.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(size), new Quaternionf()));
    }

    private void render(Scene s) {
        double t = s.age / 20.0;
        double u = Math.min(1, s.age / (double) s.duration);
        double build = smooth(u / 0.18);
        double active = smooth((u - 0.15) / 0.55);
        double finish = smooth((u - 0.78) / 0.22);
        double fade = clamp(Math.min(build, 1.0 - finish * 0.88), 0.04, 1.0);
        double radius = s.radius;
        double height = s.height;
        Location follow = s.player.getLocation();
        Location center = stationary(s.motion) ? s.origin : follow;
        double heading = directional(s.motion) ? Math.toRadians(s.player.getLocation().getYaw()) : Math.toRadians(s.origin.getYaw());

        if (s.circle != null) {
            double baseSize = radius * 2.0 * (.35 + .65 * build);
            if (Set.of("shock", "burst", "wave", "projectile", "cone", "focus").contains(s.motion)) baseSize *= (.25 + active * 1.35);
            if (Set.of("storm", "zone", "shield", "galaxy").contains(s.motion)) baseSize *= (1 + 0.05 * Math.sin(t * 4));
            if (finish > 0) baseSize *= (1 - finish * 0.45);
            place(s.circle, center, 0, .06, 0, (float) (baseSize * fade),
                    new Quaternionf().rotateY((float) (t * 1.8)).rotateX((float) Math.PI / 2), Display.Billboard.FIXED);
        }

        if (s.sigil != null) {
            double sigilSize = radius * 1.25 * (.30 + .70 * build);
            if (stationary(s.motion)) sigilSize *= (.85 + .25 * Math.sin(t * 2.4));
            if (finish > 0) sigilSize *= (1 - finish * 0.6);
            place(s.sigil, center, 0, .12, 0, (float) (sigilSize * fade),
                    new Quaternionf().rotateY((float) (-t * 1.4)).rotateX((float) Math.PI / 2), Display.Billboard.FIXED);
        }

        if (s.focus != null) {
            double fy = .7 + height + build * 1.1 + Math.sin(t * 6) * .08;
            double fsize = s.scale * (.55 + .55 * build) * (1 - finish * .9);
            Location focusCenter = stationary(s.motion) ? center : follow;
            place(s.focus, focusCenter, 0, fy, 0, (float) Math.max(.01, fsize),
                    new Quaternionf().rotateY((float) (t * 2.2)).rotateZ((float) (Math.sin(t * 3) * .15)), Display.Billboard.CENTER);
        }

        for (int i = 0; i < s.accents.size(); i++) {
            double f = (i + .5) / s.accents.size();
            double a = 2 * Math.PI * f + t * (1.2 + i * .15);
            double rr = radius * (.35 + .65 * active);
            double x = rr * Math.cos(a), z = rr * Math.sin(a), y = .25 + height * .3 + Math.sin(t * 5 + i) * .12;
            float scale = (float) (s.scale * .45 * fade * (1 - finish * .75));
            Quaternionf rot = new Quaternionf().rotateY((float) (-a)).rotateZ((float) (Math.sin(t * 3 + i) * .3));
            if (Set.of("shock", "burst", "spikes").contains(s.motion)) {
                x = radius * (.15 + active * 1.15) * Math.cos(a);
                z = radius * (.15 + active * 1.15) * Math.sin(a);
                y = .15 + height * .15 + active * .4;
            } else if (Set.of("projectile", "cone", "fan", "focus").contains(s.motion)) {
                x = (f - .5) * radius * (.7 + active);
                z = radius * (.1 + active * 1.2);
                double xx = x * Math.cos(heading) - z * Math.sin(heading);
                z = x * Math.sin(heading) + z * Math.cos(heading);
                x = xx;
                y = .35 + height * .2 + Math.sin(i + t * 4) * .1;
                rot = new Quaternionf().rotateY((float) heading).rotateZ((float) ((f - .5) * .5));
            }
            place(s.accents.get(i), center, x, y, z, Math.max(.01f, scale), rot, Display.Billboard.CENTER);
        }

        int n = s.models.size();
        for (int i = 0; i < n; i++) {
            double f = (i + .5) / n;
            double a = 2 * Math.PI * f;
            double spin = t * 2.2;
            double b = a + spin;
            double q = (active + f) % 1.0;
            double x = 0, y = height, z = 0;
            double angle = 0, pitch = 0, roll = 0;
            double scale = s.scale * fade;
            Location origin = stationary(s.motion) ? center : follow;
            switch (s.motion) {
                case "rise" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + 1.9 * q; scale *= .45 + .55 * Math.sin(Math.PI * q); }
                case "shock" -> { x = radius * (.05 + active * 1.25) * Math.cos(a); z = radius * (.05 + active * 1.25) * Math.sin(a); y = .18 + height * .15; pitch = Math.PI / 2; angle = -a; scale *= .35 + active; }
                case "hammer" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + .7 * Math.abs(Math.sin(t * 5 + i)); roll = Math.sin(t * 5 + i) * 1.3; }
                case "fan" -> { double d = radius * (.25 + active); x = (f - .5) * d; z = d; y = height + .3 * Math.sin(f * Math.PI); roll = (f - .5) * 1.4; }
                case "rain" -> { x = radius * .8 * Math.cos(a * 3); z = radius * .8 * Math.sin(a * 3); y = height * (1 - q) + .2; roll = Math.PI; }
                case "trail" -> { x = (f - .5) * .7; z = -radius * f; y = height + .2 * Math.sin(t * 6 + i); scale *= 1 - f * .7; }
                case "blink" -> { origin = (i % 2 == 0 ? center : follow); x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + Math.sin(i * 13) * .7; scale *= Math.pow(Math.abs(Math.sin(t * 8 + i)), 3); }
                case "glitch" -> { double step = Math.floor(t * 8); x = radius * Math.sin(i * 13 + step); z = radius * Math.cos(i * 7 - step); y = height + .6 * Math.sin(step + i); roll = step * .6; }
                case "dissolve" -> { x = radius * active * Math.cos(a); z = radius * active * Math.sin(a); y = height + f; scale *= Math.max(.05, 1 - active); }
                case "inward" -> { x = radius * (1 - q) * Math.cos(a); z = radius * (1 - q) * Math.sin(a); y = height + .6 * q; angle = -a; }
                case "spiral" -> { x = radius * q * Math.cos(b); z = radius * q * Math.sin(b); y = height + q; angle = -b; }
                case "projectile" -> { x = (f - .5) * .8; z = radius * Math.min(1, active * 1.3) - f * .4; y = height; roll = Math.sin(t * 4 + i) * .3; }
                case "wall" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + .8 * Math.abs(Math.sin(t * 5 + i)); scale *= 1 + .25 * Math.sin(t * 5 + i); }
                case "wave" -> { x = (f - .5) * radius * 2; z = radius * active; y = height + .6 * Math.sin(f * 6 + t * 3); roll = .4 * Math.sin(f * 6 + t * 3); }
                case "bubble" -> { x = radius * Math.cos(a + q); z = radius * Math.sin(a + q); y = height + q * 1.6; scale *= .3 + q; }
                case "zigzag" -> { x = (i % 2 == 0 ? 1 : -1) * radius * .3; z = -radius * q; y = height + .25 * Math.sin(t * 8 + i); roll = (i % 2 == 0 ? 1 : -1) * .6; }
                case "dash" -> { x = (f - .5) * .8; z = -radius * q; y = height; pitch = -.9; scale *= 1 - q * .8; }
                case "lift" -> { x = radius * (1 - q) * Math.cos(a); z = radius * (1 - q) * Math.sin(a); y = height + q * 3; roll = .6 * Math.sin(a); }
                case "tornado" -> { double rr = radius * (.15 + f * .85); x = rr * Math.cos(t * 5 + a * 2); z = rr * Math.sin(t * 5 + a * 2) + active * 3; y = .2 + f * 5; angle = -t * 5 - a * 2; }
                case "zone" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + .5 * Math.abs(Math.sin(t * 2 + i)); scale *= .7 + .3 * Math.sin(t * 3 + i); }
                case "shield" -> { x = radius * Math.cos(b * .5 + a * .5); z = radius * Math.sin(b * .5 + a * .5); y = height; angle = -b * .5 - a * .5; }
                case "dome" -> { double el = f * Math.PI / 2; x = radius * Math.cos(el) * Math.cos(a * 3); z = radius * Math.cos(el) * Math.sin(a * 3); y = .3 + radius * Math.sin(el); angle = -a * 3; pitch = -el; scale *= .85 + .15 * Math.sin(t * 3); }
                case "lock" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = .55 + (i % 2) * .82; angle = -a; scale *= s.flash > 0 ? 1.3 : 1; }
                case "revive" -> { if (i == 0) { y = 2.6; scale *= 1.85 - finish * .8; angle = t; } else { x = radius * (1 - active * .5) * Math.cos(a); z = radius * (1 - active * .5) * Math.sin(a); y = .2 + 2 * q; } }
                case "gate" -> { x = radius * Math.cos(a); y = height + radius * Math.sin(a); z = .8; roll = a - Math.PI / 2; scale *= .8 + .2 * Math.sin(t * 4); }
                case "pulse" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height; scale *= .7 + .4 * Math.abs(Math.sin(t * 3)); }
                case "storm" -> { x = radius * Math.cos(b + i); z = radius * Math.sin(b + i); y = .2 + height * (1 - q); roll = t * 3 + i; }
                case "spikes" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = .1 + Math.min(1, build * 2.2) * (.9 + f); scale *= Math.min(1, .2 + build * 2.5); }
                case "grid" -> { x = (i % 3 - 1) * radius * .6; z = (i / 3 - 1) * radius * .6; y = .25 + .1 * Math.sin(t * 12 + i); scale *= .5 + .5 * Math.abs(Math.sin(t * 9 + i)); }
                case "crown" -> { x = radius * .65 * Math.cos(a + t); z = radius * .65 * Math.sin(a + t); y = height + .2 * Math.sin(a * 2 + t); angle = -a - t; }
                case "focus" -> { x = (f - .5) * radius; z = radius; y = height + (i % 2) * .5; scale *= 1 - .5 * finish; }
                case "aim" -> { x = (f - .5) * .3; z = .6 + radius * f; y = height; pitch = -Math.PI / 2; scale *= .7 + .3 * Math.sin(t * 4 - i); }
                case "fall" -> { x = radius * Math.cos(a + q); z = radius * Math.sin(a + q); y = .2 + height * (1 - q); roll = .7 * Math.sin(t * 2 + i); }
                case "heart" -> { if (i == 0) { y = height + .8; scale *= 1.7 + .25 * Math.sin(t * 5); } else { x = radius * Math.cos(a); z = radius * Math.sin(a); y = .25 + height * q; scale *= .55; } }
                case "clock" -> { x = radius * Math.cos(a + t * 1.5); z = radius * Math.sin(a + t * 1.5); y = height; angle = -a - t * 1.5; roll = t * 2; }
                case "reverse" -> { x = radius * Math.cos(a - t * 2); z = radius * Math.sin(a - t * 2); y = height; roll = -t * 3; scale *= .7 + .3 * Math.sin(t * 2 + i); }
                case "grow" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + Math.min(1, build * 2.5) * 1.2; scale *= Math.min(1, .15 + build * 2.8); roll = Math.sin(t + i) * .2; }
                case "bloom" -> { x = radius * Math.cos(a) * Math.min(1, build * 4); z = radius * Math.sin(a) * Math.min(1, build * 4); y = height; pitch = Math.PI / 2; roll = a; scale *= Math.min(1, .1 + build * 5); }
                case "geyser" -> { x = radius * Math.cos(a); z = radius * Math.sin(a); y = height + 3 * Math.sin(Math.PI * q); scale *= Math.sin(Math.PI * q) + .1; }
                case "burst" -> { x = radius * active * 1.35 * Math.cos(a); z = radius * active * 1.35 * Math.sin(a); y = height + Math.sin(a * 3) * active; roll = a; scale *= 1 - active * .55; }
                case "cone" -> { x = (f - .5) * radius * active; z = radius * active; y = height + .6 * Math.sin(f * 6); roll = (f - .5); }
                case "wings" -> { x = (i % 2 == 0 ? 1 : -1) * radius * (.3 + f * .6); z = -.45; y = height + .4 * Math.sin(t * 5 + f); roll = (i % 2 == 0 ? 1 : -1) * (.5 + .4 * Math.sin(t * 5)); }
                case "galaxy" -> { double rr = radius * f; x = rr * Math.cos(b + f * 5); z = rr * Math.sin(b + f * 5); y = height + .4 * Math.sin(b); angle = -b; }
                default -> { x = radius * Math.cos(b); z = radius * Math.sin(b); y = height; }
            }

            if (directional(s.motion)) {
                double xx = x * Math.cos(heading) - z * Math.sin(heading);
                z = x * Math.sin(heading) + z * Math.cos(heading);
                x = xx;
                angle += heading;
            }

            Display.Billboard bb = Display.Billboard.FIXED;
            angle += t * s.meshSpin + s.meshPhase;
            roll += s.meshStretch * Math.sin(t * 3 + i + s.meshPhase);
            scale *= 1 + s.meshStretch * Math.sin(t * 4 + i + s.meshPhase);
            place(s.models.get(i), origin, x, y, z, (float) Math.max(.01, scale), new Quaternionf().rotateY((float) angle).rotateX((float) pitch).rotateZ((float) roll), bb);
        }
    }

    private void tick() {
        for (Scene s : new ArrayList<>(scenes.values())) {
            s.age += 2;
            s.flash = Math.max(0, s.flash - 2);
            boolean invalid = !enabled() || !s.player.isOnline() || s.player.isDead() || s.player.getWorld() != s.world || s.age >= s.duration;
            invalid = invalid || (s.circle != null && !s.circle.isValid()) || (s.sigil != null && !s.sigil.isValid()) || (s.focus != null && !s.focus.isValid());
            if (!invalid) {
                for (ItemDisplay d : s.models) if (!d.isValid()) { invalid = true; break; }
                if (!invalid) for (ItemDisplay d : s.accents) if (!d.isValid()) { invalid = true; break; }
            }
            if (invalid) { remove(s.player.getUniqueId()); continue; }
            try { render(s); } catch (RuntimeException ex) { remove(s.player.getUniqueId()); }
        }
    }

    private void remove(UUID id) {
        Scene s = scenes.remove(id);
        if (s != null) {
            for (ItemDisplay d : s.models) d.remove();
            for (ItemDisplay d : s.accents) d.remove();
            if (s.circle != null) s.circle.remove();
            if (s.sigil != null) s.sigil.remove();
            if (s.focus != null) s.focus.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Scene s = scenes.get(p.getUniqueId());
            if (s != null && s.key.equals("crystal") && s.slot == 0 && s.flash == 0) {
                s.flash = 8;
                p.getWorld().playSound(p.getLocation(), "minecraft:block.amethyst_block.hit", .7f, 1.3f);
            }
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e) { remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent e) { remove(e.getEntity().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent e) { remove(e.getPlayer().getUniqueId()); }

    public void shutdown() {
        task.cancel();
        for (UUID id : new ArrayList<>(scenes.keySet())) remove(id);
    }
}
