package net.failxos.failshards;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
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
    private static final class Scene {
        final Player player; final World world; final String key; final int slot, duration;
        final List<ItemDisplay> models = new ArrayList<>();
        int age = 0, flash = 0;
        Scene(Player p,String k,int s,int d){player=p;world=p.getWorld();key=k;slot=s;duration=d;}
    }
    public ModelEffects(FailShards plugin, ShardManager manager){
        this.plugin=plugin;this.manager=manager;
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,2L);
    }
    private boolean enabled(){return plugin.getConfig().getBoolean("visual-effects.enabled",true);}
    public boolean particles(){return !enabled() || plugin.getConfig().getBoolean("visual-effects.vanilla-particles",false);}
    private int count(){return Math.max(2,Math.min(8,plugin.getConfig().getInt("visual-effects.orbit-count",5)));}
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
        ItemStack ring=asset("fx_"+key+"_circle"), orb=asset("fx_"+key+"_orb"), sigil=asset("fx_"+key+"_sigil");
        if(ring==null||orb==null||sigil==null)return;
        remove(p.getUniqueId());
        if(scenes.size()>=Math.max(1,plugin.getConfig().getInt("visual-effects.max-active-players",30)))return;
        int duration=Math.max(10,Math.min(600,plugin.getConfig().getInt("visual-effects.shards."+key+".slot-"+(slot+1)+"-ticks",ticks)));
        Scene s=new Scene(p,key,slot,duration);scenes.put(p.getUniqueId(),s);
        try{
            Location l=p.getLocation();l.setYaw(0);l.setPitch(0);
            s.models.add(spawn(l,ring));s.models.add(spawn(l,ring));
            for(int i=0;i<count();i++)s.models.add(spawn(l,orb));
            s.models.add(spawn(l,sigil));
            render(s);
        }catch(RuntimeException ex){remove(p.getUniqueId());plugin.getLogger().warning("Visual effect failed: "+ex.getMessage());}
    }
    private void place(ItemDisplay d,Location origin,double x,double y,double z,float size,Quaternionf rotation){
        Location l=origin.clone().add(x,y,z);l.setYaw(0);l.setPitch(0);d.teleport(l);
        d.setInterpolationDelay(0);
        d.setTransformation(new Transformation(new Vector3f(),rotation,new Vector3f(size),new Quaternionf()));
    }
    private void render(Scene s){
        Location l=s.player.getLocation();double t=s.age/20.0;
        float fade=(float)Math.max(.05,Math.min(1,Math.min((s.age+2)/8.0,(s.duration-s.age)/8.0)));
        float radius=(float)Math.max(.5,Math.min(3,plugin.getConfig().getDouble("visual-effects.radius",1.45)));
        float speed=(float)plugin.getConfig().getDouble("visual-effects.rotation-speed",1.7);
        float expand=s.slot==1?(float)(1+.45*Math.sin(Math.min(1,s.age/16.0)*Math.PI/2)):1;
        float flash=s.flash>0?1.25f:1f;
        float size=radius*2*fade*expand*flash;
        place(s.models.get(0),l,0,.09,0,size,new Quaternionf().rotateY((float)t*speed).rotateX((float)Math.PI/2));
        place(s.models.get(1),l,0,.12,0,size*.76f,new Quaternionf().rotateY((float)-t*speed*1.3f).rotateX((float)Math.PI/2));
        int n=s.models.size()-3;
        for(int i=0;i<n;i++){
            double a=t*speed+i*Math.PI*2/n;
            double r=radius*(s.slot==1?expand:1)*fade;
            double y=s.slot==2?1.15+.65*Math.sin(a+t):.85+.35*Math.sin(t*3+i);
            if(s.key.equals("air")&&s.slot==1){y=.3+i*.42;r*=.55+i*.16;}
            if(s.key.equals("crystal")&&s.slot==0){y=.65+(i%2)*.85;r*=.72;}
            place(s.models.get(i+2),l,Math.cos(a)*r,y,Math.sin(a)*r,.48f*fade*flash,new Quaternionf().rotateY((float)-a).rotateZ((float)(.28*Math.sin(t*2+i))));
        }
        place(s.models.get(s.models.size()-1),l,0,2.45+.13*Math.sin(t*3),0,.72f*fade*flash,new Quaternionf().rotateY((float)t*speed));
    }
    private void tick(){
        for(Scene s:new ArrayList<>(scenes.values())){
            s.age+=2;s.flash=Math.max(0,s.flash-2);
            if(!enabled()||!s.player.isOnline()||s.player.isDead()||s.player.getWorld()!=s.world||s.age>=s.duration||s.models.stream().anyMatch(d->!d.isValid())){remove(s.player.getUniqueId());continue;}
            try{render(s);}catch(RuntimeException ex){remove(s.player.getUniqueId());}
        }
    }
    private void remove(UUID id){Scene s=scenes.remove(id);if(s!=null)for(ItemDisplay d:s.models)d.remove();}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p){Scene s=scenes.get(p.getUniqueId());if(s!=null&&s.key.equals("crystal")&&s.slot==0&&s.flash==0){s.flash=8;p.getWorld().playSound(p.getLocation(),"minecraft:block.amethyst_block.hit",.7f,1.3f);}}
    }
    @EventHandler public void quit(PlayerQuitEvent e){remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){remove(e.getEntity().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){remove(e.getPlayer().getUniqueId());}
    public void shutdown(){task.cancel();for(UUID id:new ArrayList<>(scenes.keySet()))remove(id);}
}
