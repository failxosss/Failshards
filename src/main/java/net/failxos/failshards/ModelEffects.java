package net.failxos.failshards;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Independent keyframe tracks for multi-part, non-persistent cosmetic scenes. */
public final class ModelEffects implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final BukkitTask task;
    private final BetterModelBridge betterModel;
    private final Map<UUID,Scene> active = new HashMap<>();
    private final Map<String,Recipe> recipes = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private record Track(String asset, List<double[]> frames) {}
    private record Recipe(boolean follow, String sound, List<Track> tracks) {}
    private record Part(ItemDisplay entity, Track track) {}
    private static final class Scene {
        final Player player; final Location origin; final Recipe recipe; final int duration; final double size;
        final List<Part> parts = new ArrayList<>(); int age; int flash;
        Scene(Player player, Recipe recipe, int duration, double size) {
            this.player=player; this.origin=player.getLocation().clone(); this.recipe=recipe;this.duration=duration;this.size=size;
        }
    }
    public ModelEffects(FailShards plugin, ShardManager manager) {
        this.plugin=plugin; this.manager=manager; this.betterModel=new BetterModelBridge(plugin);
        File file=new File(plugin.getDataFolder(),"scenes-v7.yml");
        if(!file.exists()) plugin.saveResource("scenes-v7.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        try(var in=plugin.getResource("scenes-v7.yml")) {
            if(in!=null)y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8)));
        } catch(IOException ex){plugin.getLogger().warning(ex.toString());}
        var section=y.getConfigurationSection("scenes");
        if(section!=null) for(String key:section.getKeys(false)) {
            try {
                String path="scenes."+key+"."; List<Track> tracks=new ArrayList<>();
                for(Map<?,?> row:y.getMapList(path+"parts")) {
                    String asset=String.valueOf(row.get("asset")); List<double[]> frames=new ArrayList<>();
                    if(!(row.get("frames") instanceof List<?> lines))throw new IllegalArgumentException("Missing frames");
                    double previous=-1;
                    for(Object line:lines){String[] cols=String.valueOf(line).split(",");if(cols.length!=10)throw new IllegalArgumentException("Frame width");double[] f=new double[10];for(int i=0;i<10;i++){f[i]=Double.parseDouble(cols[i]);if(!Double.isFinite(f[i]))throw new IllegalArgumentException("Non-finite frame");}if(f[0]<=previous||f[0]<0||f[0]>1)throw new IllegalArgumentException("Frame order");previous=f[0];frames.add(f);}
                    if(frames.size()<2||frames.getFirst()[0]!=0||frames.getLast()[0]!=1)throw new IllegalArgumentException("Missing endpoints");
                    tracks.add(new Track(asset,List.copyOf(frames)));
                }
                if(!tracks.isEmpty())recipes.put(key,new Recipe(y.getString(path+"anchor","follow").equals("follow"),y.getString(path+"sound","block.amethyst_block.chime"),List.copyOf(tracks)));
            }catch(RuntimeException ex){plugin.getLogger().warning("Invalid visual scene "+key+": "+ex.getMessage());}
        }
        plugin.getLogger().info("Loaded "+recipes.size()+" keyframe visual scenes.");
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,2L);
    }
    private boolean enabled(){return plugin.getConfig().getBoolean("visual-effects.enabled",true);}
    public boolean particles(){return !enabled()||plugin.getConfig().getBoolean("visual-effects.vanilla-particles",false);}
    private int limit(String path,int def,int min,int max){return Math.max(min,Math.min(max,plugin.getConfig().getInt(path,def)));}
    private ItemDisplay spawn(Location location,ItemStack stack){return location.getWorld().spawn(location,ItemDisplay.class,d->{
        d.setItemStack(stack);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);d.setBillboard(Display.Billboard.FIXED);
        d.setBrightness(new Display.Brightness(15,15));d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);d.setTeleportDuration(2);d.setInterpolationDuration(2);d.setShadowRadius(0);d.setShadowStrength(0);
        d.setViewRange((float)Math.max(.1,Math.min(2,plugin.getConfig().getDouble("visual-effects.view-range",1))));
    });}
    public void play(Player player,String key,int slot,int ticks){
        String path="visual-effects.shards."+key+".";String local=path+"slot-"+(slot+1)+"-";
        if(!enabled()||!plugin.getConfig().getBoolean(path+"enabled",true)||!plugin.getConfig().getBoolean(local+"enabled",true))return;
        Recipe recipe=recipes.get(key+"_"+(slot+1));if(recipe==null)return;
        remove(player.getUniqueId());
        if(active.size()>=limit("visual-effects.max-active-players",30,1,100))return;
        int count=active.values().stream().mapToInt(s->s.parts.size()).sum();
        if(recipe.tracks.size()>limit("visual-effects.max-displays-per-player",16,1,32)||count+recipe.tracks.size()>limit("visual-effects.max-total-displays",240,1,1000))return;
        double size=Math.max(.2,Math.min(2,plugin.getConfig().getDouble(local+"scale",1)));
        double speed=Math.max(.25,Math.min(4,plugin.getConfig().getDouble(local+"speed",1)));
        int duration=Math.max(10,Math.min(600,(int)(plugin.getConfig().getInt(local+"ticks",ticks)/speed)));
        if(betterModel.play(player,key+"_"+(slot+1),recipe.follow,duration)){if(plugin.getConfig().getBoolean("visual-effects.scene-sounds",true))player.getWorld().playSound(player.getLocation(),"minecraft:"+recipe.sound,.7f,1f);return;}
        List<ItemStack> items=new ArrayList<>();
        for(Track track:recipe.tracks){ItemStack item=manager.visualItem("failshards:"+track.asset);if(item==null){if(warned.add(track.asset))plugin.getLogger().warning("Missing scene model: "+track.asset);return;}items.add(item);}
        Scene s=new Scene(player,recipe,duration,size);active.put(player.getUniqueId(),s);
        try{Location l=player.getLocation();l.setYaw(0);l.setPitch(0);for(int i=0;i<items.size();i++)s.parts.add(new Part(spawn(l,items.get(i)),recipe.tracks.get(i)));render(s);if(plugin.getConfig().getBoolean("visual-effects.scene-sounds",true))player.getWorld().playSound(player.getLocation(),"minecraft:"+recipe.sound,.7f,1f);}catch(RuntimeException ex){remove(player.getUniqueId());plugin.getLogger().warning("Scene failed: "+ex.getMessage());}
    }
    private double[] sample(Track track,double t){List<double[]> f=track.frames;int i=1;while(i<f.size()-1&&f.get(i)[0]<t)i++;double[] a=f.get(i-1),b=f.get(i);double q=Math.max(0,Math.min(1,(t-a[0])/(b[0]-a[0])));double[] out=new double[10];for(int j=1;j<10;j++)out[j]=a[j]+(b[j]-a[j])*q;return out;}
    private void render(Scene s){
        Location center=s.recipe.follow?s.player.getLocation():s.origin;double heading=Math.toRadians(center.getYaw());double cos=Math.cos(heading),sin=Math.sin(heading);double progress=Math.min(1,s.age/(double)s.duration);
        for(Part part:s.parts){double[] f=sample(part.track,progress);double x=Math.max(-12,Math.min(12,f[1]))*s.size,z=Math.max(-12,Math.min(12,f[3]))*s.size;
            Location l=center.clone().add(x*cos-z*sin,Math.max(-4,Math.min(12,f[2]))*s.size,x*sin+z*cos);l.setYaw(0);l.setPitch(0);ItemDisplay d=part.entity;d.teleport(l);d.setInterpolationDelay(0);
            Quaternionf rot=new Quaternionf().rotateY((float)(-heading+Math.toRadians(f[8]))).rotateX((float)Math.toRadians(f[7])).rotateZ((float)Math.toRadians(f[9]));
            float pulse=s.flash>0?1.08f:1f;Vector3f scale=new Vector3f(safeScale(f[4],s.size)*pulse,safeScale(f[5],s.size)*pulse,safeScale(f[6],s.size)*pulse);
            d.setTransformation(new Transformation(new Vector3f(),rot,scale,new Quaternionf()));
        }
    }
    private float safeScale(double x,double size){return (float)Math.max(.01,Math.min(18,x*size));}
    private void tick(){betterModel.tick();for(Scene s:new ArrayList<>(active.values())){s.age+=2;s.flash=Math.max(0,s.flash-2);if(!enabled()||!s.player.isOnline()||s.player.isDead()||s.player.getWorld()!=s.origin.getWorld()||s.age>=s.duration||s.parts.stream().anyMatch(p->!p.entity.isValid())){remove(s.player.getUniqueId());continue;}try{render(s);}catch(RuntimeException ex){remove(s.player.getUniqueId());}}}
    private void remove(UUID id){betterModel.remove(id);Scene s=active.remove(id);if(s!=null)for(Part part:s.parts)part.entity.remove();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageEvent e){if(e.getEntity() instanceof Player p){Scene s=active.get(p.getUniqueId());if(s!=null)s.flash=4;}}
    @EventHandler public void quit(PlayerQuitEvent e){remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){remove(e.getEntity().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){remove(e.getPlayer().getUniqueId());}
    public void shutdown(){betterModel.shutdown();task.cancel();for(UUID id:new ArrayList<>(active.keySet()))remove(id);}
}
