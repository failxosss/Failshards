package net.failxos.failshards;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Cosmetic displays only; no changes to equipment, damage or potion effects. */
public final class ModelEffects implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final BukkitTask task;
    private final YamlConfiguration profiles;
    private static final class Scene {
        final Player player; final World world; final String key; final int slot, duration;
        final List<ItemDisplay> models = new ArrayList<>();
        int age = 0, flash = 0;
        String motion; double radius,height,scale; Location origin; ItemDisplay circle;
        Scene(Player p,String k,int s,int d){player=p;world=p.getWorld();key=k;slot=s;duration=d;}
    }
    public ModelEffects(FailShards plugin, ShardManager manager){
        this.plugin=plugin;this.manager=manager;
        File f=new File(plugin.getDataFolder(),"visual-profiles.yml");
        if(!f.exists())plugin.saveResource("visual-profiles.yml",false);
        profiles=YamlConfiguration.loadConfiguration(f);
        try(var in=plugin.getResource("visual-profiles.yml")){
            if(in!=null)profiles.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in,StandardCharsets.UTF_8)));
        }catch(java.io.IOException ex){plugin.getLogger().warning(ex.toString());}
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,2L);
    }
    private boolean enabled(){return plugin.getConfig().getBoolean("visual-effects.enabled",true);}
    public boolean particles(){return !enabled() || plugin.getConfig().getBoolean("visual-effects.vanilla-particles",false);}
    private ItemStack asset(String id){
        ItemStack item=manager.visualItem("failshards:"+id);
        if(item==null && warned.add(id))plugin.getLogger().warning("Missing visual model: failshards:"+id+". Regenerate ItemsAdder pack.");
        return item;
    }
    private ItemDisplay spawn(Location l,ItemStack item){
        return l.getWorld().spawn(l,ItemDisplay.class,d->{
            d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);d.setBrightness(new Display.Brightness(15,15));
            d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
            d.setViewRange((float)Math.max(.1,Math.min(1.5,plugin.getConfig().getDouble("visual-effects.view-range",.75))));
            d.setTeleportDuration(2);d.setInterpolationDuration(2);d.setShadowRadius(0);d.setShadowStrength(0);
        });
    }
    public void play(Player p,String key,int slot,int ticks){
        if(!enabled() || !plugin.getConfig().getBoolean("visual-effects.shards."+key+".enabled",true))return;
        String path="profiles."+key+".slot-"+(slot+1)+".";
        if(!profiles.getBoolean(path+"enabled",true))return;
        ItemStack symbol=asset("fx_"+key+"_"+(slot+1));
        if(symbol==null)return;
        remove(p.getUniqueId());
        if(scenes.size()>=Math.max(1,plugin.getConfig().getInt("visual-effects.max-active-players",30)))return;
        int duration=Math.max(10,Math.min(600,plugin.getConfig().getInt("visual-effects.shards."+key+".slot-"+(slot+1)+"-ticks",ticks)));
        Scene s=new Scene(p,key,slot,duration);scenes.put(p.getUniqueId(),s);
        s.motion=profiles.getString(path+"motion","rise");
        s.radius=Math.max(.2,Math.min(8,profiles.getDouble(path+"radius",1.5)));
        s.height=Math.max(0,Math.min(8,profiles.getDouble(path+"height",1)));
        s.scale=Math.max(.1,Math.min(2,profiles.getDouble(path+"scale",.7)));
        s.origin=p.getLocation().clone();
        int n=Math.max(1,Math.min(Math.min(16,Math.max(1,plugin.getConfig().getInt("visual-effects.max-displays-per-player",12))),profiles.getInt(path+"count",6)));
        try{
            Location l=p.getLocation();l.setYaw(0);l.setPitch(0);
            ItemStack mesh=(s.motion.equals("lock")||s.motion.equals("spikes"))?asset("fx_"+key+"_orb"):null;
            for(int i=0;i<n;i++)s.models.add(spawn(l,mesh!=null?mesh:symbol));
            if(profiles.getBoolean(path+"ground-circle",false)){
                ItemStack ring=asset("fx_"+key+"_circle");
                if(ring!=null && n<Math.min(16,Math.max(1,plugin.getConfig().getInt("visual-effects.max-displays-per-player",12))))s.circle=spawn(l,ring);
            }
            render(s);
        }catch(RuntimeException ex){remove(p.getUniqueId());plugin.getLogger().warning("Visual effect failed: "+ex.getMessage());}
    }
    private void place(ItemDisplay d,Location origin,double x,double y,double z,float size,Quaternionf rotation){
        Location l=origin.clone().add(x,y,z);l.setYaw(0);l.setPitch(0);d.teleport(l);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(new Vector3f(),rotation,new Vector3f(size),new Quaternionf()));
    }
    private void render(Scene s){
        double t=s.age/20.0, u=Math.min(1,s.age/(double)s.duration), r=s.radius, h=s.height;
        float fade=(float)Math.max(.03,Math.min(1,Math.min((s.age+2)/6.0,(s.duration-s.age)/8.0)));
        if(s.circle!=null){
            Location center=Set.of("shock","zone","storm","grid","wall","spikes","geyser","galaxy","reverse").contains(s.motion)?s.origin:s.player.getLocation();
            double size=s.radius*2*fade*(s.motion.equals("shock")?Math.max(.1,u):1);
            place(s.circle,center,0,.08,0,(float)size,new Quaternionf().rotateY((float)(t*1.2)).rotateX((float)Math.PI/2));
        }
        int n=s.models.size(); double yaw=Math.toRadians(s.origin.getYaw());
        for(int i=0;i<n;i++){
            double f=(i+.5)/n, a=2*Math.PI*f, b=a+t*2, q=(u+f)%1;
            double x=0,y=h,z=0, angle=0, pitch=0, roll=0, scale=s.scale*fade;
            Location l=s.player.getLocation();
            switch(s.motion){
                case "rise" -> {x=r*Math.cos(a);z=r*Math.sin(a);y=h+2*q;scale*=.5+.5*Math.sin(Math.PI*q);}
                case "shock" -> {l=s.origin;x=r*u*Math.cos(a);z=r*u*Math.sin(a);y=h+.15;pitch=Math.PI/2;angle=-a;scale*=.5+u;}
                case "hammer" -> {x=r*Math.cos(a);z=r*Math.sin(a);y=h+.7*Math.abs(Math.sin(t*5+i));roll=Math.sin(t*5+i)*1.3;}
                case "fan" -> {l=s.origin;double d=r*u; x=(f-.5)*d;z=d;y=h+.3*Math.sin(f*Math.PI);roll=(f-.5)*1.4;}
                case "rain" -> {l=s.origin;x=r*.8*Math.cos(a*3);z=r*.8*Math.sin(a*3);y=h*(1-q)+.2;roll=Math.PI;}
                case "trail" -> {x=(f-.5)*.7;z=-r*f;y=h+.2*Math.sin(t*6+i);scale*=1-f*.7;}
                case "blink" -> {l=(i%2==0?s.origin:l);x=r*Math.cos(a);z=r*Math.sin(a);y=h+Math.sin(i*13)*.7;scale*=Math.pow(Math.abs(Math.sin(t*8+i)),3);}
                case "glitch" -> {l=s.origin;double step=Math.floor(t*8);x=r*Math.sin(i*13+step);z=r*Math.cos(i*7-step);y=h+.6*Math.sin(step+i);roll=step*.6;}
                case "dissolve" -> {x=r*u*Math.cos(a);z=r*u*Math.sin(a);y=h+f;scale*=Math.max(.05,1-u);}
                case "inward" -> {x=r*(1-q)*Math.cos(a);z=r*(1-q)*Math.sin(a);y=h+.6*q;angle=-a;}
                case "spiral" -> {l=s.origin;x=r*q*Math.cos(b);z=r*q*Math.sin(b);y=h+q;angle=-b;}
                case "projectile" -> {l=s.origin;x=(f-.5)*.8;z=r*Math.min(1,u*3)-f*.5;y=h;roll=Math.sin(t*4+i)*.3;}
                case "wall" -> {l=s.origin;x=r*Math.cos(a);z=r*Math.sin(a);y=h+.8*Math.abs(Math.sin(t*5+i));scale*=1+.3*Math.sin(t*5+i);}
                case "wave" -> {l=s.origin;x=(f-.5)*r*2;z=r*u;y=h+.6*Math.sin(f*6+t*3);roll=.4*Math.sin(f*6+t*3);}
                case "bubble" -> {x=r*Math.cos(a+q);z=r*Math.sin(a+q);y=h+q*1.6;scale*=.3+q;}
                case "zigzag" -> {x=(i%2==0?1:-1)*r*.3;z=-r*q;y=h+.25*Math.sin(t*8+i);roll=(i%2==0?1:-1)*.6;}
                case "dash" -> {x=(f-.5)*.8;z=-r*q;y=h;pitch=-.9;scale*=1-q*.8;}
                case "lift" -> {x=r*(1-q)*Math.cos(a);z=r*(1-q)*Math.sin(a);y=h+q*3;roll=.6*Math.sin(a);}
                case "tornado" -> {l=s.origin;double rr=r*(.15+f*.85);x=rr*Math.cos(t*5+a*2);z=rr*Math.sin(t*5+a*2)+u*3;y=.2+f*5;angle=-t*5-a*2;}
                case "zone" -> {l=s.origin;x=r*Math.cos(a);z=r*Math.sin(a);y=h+.5*Math.abs(Math.sin(t*2+i));scale*=.7+.3*Math.sin(t*3+i);}
                case "shield" -> {x=r*Math.cos(b*.5+a*.5);z=r*Math.sin(b*.5+a*.5);y=h;angle=-b*.5-a*.5;}
                case "dome" -> {double el=f*Math.PI/2;x=r*Math.cos(el)*Math.cos(a*3);z=r*Math.cos(el)*Math.sin(a*3);y=.3+r*Math.sin(el);angle=-a*3;pitch=-el;scale*=.85+.15*Math.sin(t*3);}
                case "lock" -> {x=r*Math.cos(a);z=r*Math.sin(a);y=.6+(i%2)*.85;angle=-a;scale*=s.flash>0?1.25:1;}
                case "revive" -> {if(i==0){y=2.5;scale*=1.7;angle=t;}else{x=r*(1-u*.5)*Math.cos(a);z=r*(1-u*.5)*Math.sin(a);y=.2+2*q;}}
                case "gate" -> {x=r*Math.cos(a);y=h+r*Math.sin(a);z=.8;roll=a-Math.PI/2;scale*=.8+.2*Math.sin(t*4);}
                case "pulse" -> {x=r*Math.cos(a);z=r*Math.sin(a);y=h;scale*=.7+.4*Math.abs(Math.sin(t*3));}
                case "storm" -> {l=s.origin;x=r*Math.cos(b+i);z=r*Math.sin(b+i);y=.2+h*(1-q);roll=t*3+i;}
                case "spikes" -> {l=s.origin;x=r*Math.cos(a);z=r*Math.sin(a);y=.2+Math.min(1,u*5)*(.8+f);scale*=Math.min(1,u*5+.1);}
                case "grid" -> {l=s.origin;x=(i%3-1)*r*.6;z=(i/3-1)*r*.6;y=.3+.1*Math.sin(t*12+i);scale*=.5+.5*Math.abs(Math.sin(t*9+i));}
                case "crown" -> {x=r*.65*Math.cos(a+t);z=r*.65*Math.sin(a+t);y=h+.2*Math.sin(a*2+t);angle=-a-t;}
                case "focus" -> {l=s.origin;x=(f-.5)*r;z=r;y=h+(i%2)*.5;scale*=1-.5*u;}
                case "aim" -> {x=(f-.5)*.3;z=.6+r*f;y=h;pitch=-Math.PI/2;scale*=.7+.3*Math.sin(t*4-i);}
                case "fall" -> {x=r*Math.cos(a+q);z=r*Math.sin(a+q);y=.2+h*(1-q);roll=.7*Math.sin(t*2+i);}
                case "heart" -> {if(i==0){y=h+.6;scale*=1.8+.2*Math.sin(t*5);}else{x=r*Math.cos(a);z=r*Math.sin(a);y=.3+h*q;scale*=.55;}}
                case "clock" -> {x=r*Math.cos(a+t*1.5);z=r*Math.sin(a+t*1.5);y=h;angle=-a-t*1.5;roll=t*2;}
                case "reverse" -> {l=s.origin;x=r*Math.cos(a-t*2);z=r*Math.sin(a-t*2);y=h;roll=-t*3;scale*=.7+.3*Math.sin(t*2+i);}
                case "grow" -> {l=s.origin;x=r*Math.cos(a);z=r*Math.sin(a);y=h+Math.min(1,u*3)*1.2;scale*=Math.min(1,.1+u*3);roll=Math.sin(t+i)*.2;}
                case "bloom" -> {x=r*Math.cos(a)*Math.min(1,u*4);z=r*Math.sin(a)*Math.min(1,u*4);y=h;pitch=Math.PI/2;roll=a;scale*=Math.min(1,.1+u*5);}
                case "geyser" -> {l=s.origin;x=r*Math.cos(a);z=r*Math.sin(a);y=h+3*Math.sin(Math.PI*q);scale*=Math.sin(Math.PI*q)+.1;}
                case "burst" -> {l=s.origin;x=r*u*Math.cos(a);z=r*u*Math.sin(a);y=h+Math.sin(a*3)*u;roll=a;scale*=1-u*.7;}
                case "cone" -> {l=s.origin;x=(f-.5)*r*u;z=r*u;y=h+.6*Math.sin(f*6);roll=(f-.5);}
                case "wings" -> {x=(i%2==0?1:-1)*r*(.3+f*.6);z=-.45;y=h+.4*Math.sin(t*5+f);roll=(i%2==0?1:-1)*(.5+.4*Math.sin(t*5));}
                case "galaxy" -> {l=s.origin;double rr=r*f;x=rr*Math.cos(b+f*5);z=rr*Math.sin(b+f*5);y=h+.4*Math.sin(b);angle=-b;}
                default -> {x=r*Math.cos(b);z=r*Math.sin(b);y=h;}
            }
            // Directional scenes use cast heading; stationary zones remain in world space.
            if(Set.of("fan","trail","projectile","wave","zigzag","dash","tornado","focus","aim","cone","wings","gate").contains(s.motion)){
                double heading=Set.of("trail","dash","zigzag","wings","aim","gate").contains(s.motion)?Math.toRadians(s.player.getLocation().getYaw()):yaw;
                double xx=x*Math.cos(heading)-z*Math.sin(heading);z=x*Math.sin(heading)+z*Math.cos(heading);x=xx;angle+=heading;
            }
            // Flat emblems face the camera except intentionally oriented ground/wall assets.
            ItemDisplay display=s.models.get(i);
            display.setBillboard(pitch==0&& !Set.of("gate","lock","wings","shield","dome").contains(s.motion)?Display.Billboard.CENTER:Display.Billboard.FIXED);
            place(display,l,x,y,z,(float)Math.max(.01,scale),new Quaternionf().rotateY((float)angle).rotateX((float)pitch).rotateZ((float)roll));
        }
    }
    private void tick(){
        for(Scene s:new ArrayList<>(scenes.values())){
            s.age+=2;s.flash=Math.max(0,s.flash-2);
            if(!enabled()||!s.player.isOnline()||s.player.isDead()||s.player.getWorld()!=s.world||s.age>=s.duration||(s.circle!=null&&!s.circle.isValid())||s.models.stream().anyMatch(d->!d.isValid())){remove(s.player.getUniqueId());continue;}
            try{render(s);}catch(RuntimeException ex){remove(s.player.getUniqueId());}
        }
    }
    private void remove(UUID id){Scene s=scenes.remove(id);if(s!=null){for(ItemDisplay d:s.models)d.remove();if(s.circle!=null)s.circle.remove();}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p){Scene s=scenes.get(p.getUniqueId());if(s!=null&&s.key.equals("crystal")&&s.slot==0&&s.flash==0){s.flash=8;p.getWorld().playSound(p.getLocation(),"minecraft:block.amethyst_block.hit",.7f,1.3f);}}
    }
    @EventHandler public void quit(PlayerQuitEvent e){remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){remove(e.getEntity().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){remove(e.getPlayer().getUniqueId());}
    public void shutdown(){task.cancel();for(UUID id:new ArrayList<>(scenes.keySet()))remove(id);}
}
