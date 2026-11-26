package net.failxos.failshards;

import java.util.*;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class ShardAbilities implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final Map<UUID,long[]> cooldown = new HashMap<>();
    private final Set<Arrow> arrows = new HashSet<>();
    private final Random random = new Random();
    public ShardAbilities(FailShards plugin, ShardManager manager) {
        this.plugin=plugin; this.manager=manager;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("abilities.hud",true)) return;
            for(Player p:Bukkit.getOnlinePlayers()) {
                Shard s=held(p); if(s==null) continue;
                long[] t=cooldown.getOrDefault(p.getUniqueId(),new long[3]);
                p.sendActionBar(Component.text(s.name()+" | Right click: "+remaining(t[0])+" | Shift+right: "+remaining(t[1])+" | Shift+left: "+remaining(t[2])));
            }
        },20L,20L);
    }
    private String remaining(long end) { long s=Math.max(0,(end-System.currentTimeMillis()+999)/1000); return s==0?"READY":s+"s"; }
    private Shard held(Player p) { Shard s=manager.shardOf(p.getInventory().getItemInMainHand()); return s!=null?s:manager.shardOf(p.getInventory().getItemInOffHand()); }
    @EventHandler
    public void interact(PlayerInteractEvent e) {
        boolean third=pSneakLeft(e);
        if(!third && !(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_AIR || e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) || e.getHand()==null) return;
        if(third && e.getHand()!=EquipmentSlot.HAND) return;
        Player p=e.getPlayer(); Shard s=manager.shardOf(e.getItem()); if(s==null) return;
        if(e.getHand()==EquipmentSlot.OFF_HAND && manager.shardOf(p.getInventory().getItemInMainHand())!=null) return;
        // Ignore ordinary block interactions with an offhand shard; use the main hand for activation.
        if(e.getHand()==EquipmentSlot.OFF_HAND && e.getClickedBlock()!=null) return;
        e.setCancelled(true); activate(p,s,third?2:p.isSneaking()?1:0);
    }
    private boolean pSneakLeft(PlayerInteractEvent e){return e.getPlayer().isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR || e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK);}
    private boolean allowed(Player p) { return p.isOnline()&&!p.isDead()&&!plugin.getConfig().getStringList("abilities.disabled-worlds").contains(p.getWorld().getName()); }
    private void animate(Player p, World w, int frames, long period, IntConsumer action) {
        new BukkitRunnable(){int i=0; public void run(){if(i>=frames||!allowed(p)||!p.getWorld().equals(w)){cancel();return;} action.accept(i++);}}.runTaskTimer(plugin,0L,period);
    }
    private void fx(Location l,Particle particle,int count) { l.getWorld().spawnParticle(particle,l,count,.07,.07,.07,0); }
    private Location at(Location l,double x,double y,double z){return l.clone().add(x,y,z);}
    private void ring(Location l,Particle particle,double radius,double y,int points,double spin){for(int i=0;i<points;i++){double a=spin+i*Math.PI*2/points;fx(at(l,Math.cos(a)*radius,y,Math.sin(a)*radius),particle,1);}}
    private void effect(LivingEntity p,PotionEffectType type,int ticks,int amp){p.addPotionEffect(new PotionEffect(type,ticks,amp));}
    private void heal(Player p,double amount){if(!p.isDead())p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+amount));}
    private boolean ally(Player p,Entity e){if(p.equals(e))return true; if(!(e instanceof Player q))return false; var t=p.getScoreboard().getEntryTeam(p.getName());return t!=null&&t.hasEntry(q.getName());}
    private List<LivingEntity> targets(Player p,Location l,double r){List<LivingEntity> out=new ArrayList<>(); for(Entity e:l.getWorld().getNearbyEntities(l,r,r,r))if(e instanceof LivingEntity v&&!v.isDead()&&!ally(p,e)&&e.getLocation().distanceSquared(l)<=r*r&&(!(e instanceof Player q)||q.getGameMode()==GameMode.SURVIVAL||q.getGameMode()==GameMode.ADVENTURE))out.add(v);return out;}
    private void push(LivingEntity e,Location l,double power,double y){Vector v=e.getLocation().toVector().subtract(l.toVector()).setY(0);if(v.lengthSquared()>0.001)v.normalize().multiply(power);e.setVelocity(v.setY(y));}
    private Location forward(Player p,double d){Vector v=p.getLocation().getDirection().setY(0);if(v.lengthSquared()>0.001)v.normalize();return p.getLocation().add(v.multiply(d));}
    private void arrow(Player p,Location l,Vector v){Arrow a=l.getWorld().spawnArrow(l,v,1.4f,0f);a.setShooter(p);a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);arrows.add(a);Bukkit.getScheduler().runTaskLater(plugin,()->{a.remove();arrows.remove(a);},100L);}
    @EventHandler(ignoreCancelled=true) public void arrowHit(org.bukkit.event.entity.EntityDamageByEntityEvent e){if(e.getDamager() instanceof Arrow a&&arrows.contains(a)&&a.getShooter() instanceof Player p&&ally(p,e.getEntity()))e.setCancelled(true);}
    public void shutdown(){for(Arrow a:arrows)a.remove();arrows.clear();}
    private void activate(Player p,Shard s,int slot){
        boolean ult=slot==1;
        if(!allowed(p)||!plugin.getConfig().getBoolean("abilities.enabled",true))return;
        if(!plugin.getConfig().getBoolean("abilities."+s.key()+".enabled",true))return;
        long now=System.currentTimeMillis();long[] t=cooldown.computeIfAbsent(p.getUniqueId(),k->new long[3]);
        if(t[slot]>now){p.sendActionBar(Component.text("Cooldown: "+remaining(t[slot])));return;}
        int seconds=plugin.getConfig().getInt("abilities."+s.key()+"."+(slot==2?"third-cooldown":ult?"ultimate-cooldown":"active-cooldown"),slot==2?90:ult?150:45);
        t[slot]=now+1000L*Math.max(1,seconds); Location l=p.getLocation();World w=p.getWorld();
        p.sendActionBar(Component.text(s.name()+" • "+(slot==2?"SPECIAL":ult?"ULTIMATE":"ACTIVE")));w.playSound(l,"minecraft:block.amethyst_block.chime",1f,1f);
        if(slot==2){thirdAbility(p,s,l,w);return;}
        switch(s.key()){
            case "strength" -> {if(!ult){effect(p,PotionEffectType.STRENGTH,160,1);animate(p,w,8,3,i->ring(p.getLocation(),Particle.CRIT,1.2,1,16,i));}else{animate(p,w,10,2,i->ring(l,Particle.CRIT,.5+i*.55,.4,24,i));for(var e:targets(p,l,5)){e.damage(4,p);push(e,l,1.1,.45);}}}
            case "arsenal" -> {if(ult){Location c=forward(p,5);animate(p,w,20,5,i->{ring(c,Particle.WAX_ON,5,.15,24,i*.1);for(int j=0;j<3;j++){double a=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*4.5;Location at=at(c,Math.cos(a)*r,9,Math.sin(a)*r);if(at.getBlock().isPassable()){arrow(p,at,new Vector(0,-1,0));fx(at,Particle.WAX_ON,4);}}});}else{Vector v=l.getDirection();for(double a:new double[]{-.18,0,.18})arrow(p,p.getEyeLocation().add(v.clone().multiply(.5)),v.clone().rotateAroundY(a));}}
            case "glitched" -> {if(!ult){fx(l,Particle.PORTAL,30);Location dest=l;for(int i=1;i<=6;i++){Location c=forward(p,i);if(!c.getBlock().isPassable()||!at(c,0,1,0).getBlock().isPassable())break;dest=c;}p.teleport(dest);fx(dest,Particle.PORTAL,30);}else{for(var e:targets(p,l,6))if(e instanceof Player q){long[] c=cooldown.computeIfAbsent(q.getUniqueId(),k->new long[3]);for(int i=0;i<3;i++)c[i]=Math.max(c[i],now+5000);effect(q,PotionEffectType.DARKNESS,100,0);}animate(p,w,15,4,i->ring(l,Particle.PORTAL,5,1,24,i));}}
            case "soul" -> {animate(p,w,ult?20:8,5,i->{ring(l,Particle.SOUL_FIRE_FLAME,ult?4:2,1,16,i*.5);if(i%4==0)for(var e:targets(p,l,ult?5:3)){e.damage(ult?2:1,p);heal(p,1);}});}
            case "fire" -> {Location c=ult?l:forward(p,4);animate(p,w,ult?32:8,5,i->{ring(c,Particle.FLAME,ult?5:2,.4,28,i*.3);if(i%4==0)for(var e:targets(p,c,ult?5:2)){e.setFireTicks(60);e.damage(2,p);}});}
            case "water" -> {Location c=forward(p,3);animate(p,w,ult?24:8,4,i->{ring(c,Particle.SPLASH,ult?5:2.5,.6,28,i*.4);if(i%4==0)for(var e:targets(p,c,ult?5:3)){if(ult)effect(e,PotionEffectType.SLOWNESS,40,1);else push(e,c,.8,.2);}});}
            case "speed" -> {effect(p,PotionEffectType.SPEED,ult?200:160,ult?3:2);if(ult)effect(p,PotionEffectType.JUMP_BOOST,200,1);animate(p,w,ult?50:40,4,i->fx(p.getLocation(),Particle.ELECTRIC_SPARK,8));}
            case "air" -> {if(!ult){p.setVelocity(new Vector(0,1.1,0));effect(p,PotionEffectType.SLOW_FALLING,140,0);fx(l,Particle.CLOUD,35);}else{Vector v=l.getDirection().setY(0);animate(p,w,60,2,i->{Location c=l.clone().add(v.clone().multiply(i*.12));for(int h=0;h<16;h++){double a=i*.35+h*.7,r=.5+h*.14;fx(at(c,Math.cos(a)*r,h*.45,Math.sin(a)*r),Particle.CLOUD,1);}if(i%5==0)for(var e:targets(p,at(c,0,2,0),4)){e.setVelocity(new Vector(0,.55,0));effect(e,PotionEffectType.SLOW_FALLING,80,0);}});}}
            case "health" -> {if(!ult){heal(p,6);fx(at(l,0,1,0),Particle.HEART,10);}else animate(p,w,24,5,i->{ring(l,Particle.HEART,4,.5,12,i);if(i%4==0)for(Player q:w.getPlayers())if(ally(p,q)&&q.getLocation().distanceSquared(l)<=16)heal(q,2);});}
            case "guardian" -> {effect(p,PotionEffectType.RESISTANCE,ult?120:160,ult?2:1);animate(p,w,ult?30:40,4,i->{Location c=p.getLocation();for(int h=0;h<5;h++)ring(c,Particle.END_ROD,Math.sin(h*Math.PI/5)*2.5,h*.5,12,i*.2);});}
        }
    }

    private void thirdAbility(Player p,Shard s,Location l,World w){
        switch(s.key()){
            case "strength" -> {effect(p,PotionEffectType.HASTE,200,2);animate(p,w,20,4,i->ring(p.getLocation(),Particle.CRIT,1.5,.7,16,i*.3));}
            case "arsenal" -> {effect(p,PotionEffectType.SPEED,120,1);animate(p,w,12,5,i->ring(p.getLocation(),Particle.WAX_ON,1.3,1,16,i*.4));}
            case "glitched" -> {effect(p,PotionEffectType.INVISIBILITY,120,0);animate(p,w,12,5,i->ring(p.getLocation(),Particle.PORTAL,1.2,1,16,i*.5));}
            case "soul" -> {effect(p,PotionEffectType.REGENERATION,120,1);animate(p,w,24,5,i->ring(p.getLocation(),Particle.SOUL_FIRE_FLAME,1.5,1,16,i*.4));}
            case "fire" -> {Location c=forward(p,3);animate(p,w,20,4,i->{ring(c,Particle.FLAME,1+i*.18,.3,24,i*.4);if(i%5==0)for(var e:targets(p,c,4)){e.setFireTicks(40);}});}
            case "water" -> {effect(p,PotionEffectType.CONDUIT_POWER,300,0);animate(p,w,30,5,i->ring(p.getLocation(),Particle.SPLASH,1.6,1,20,i*.4));}
            case "speed" -> {Vector v=l.getDirection().setY(0);if(v.lengthSquared()>.001)v.normalize().multiply(1.4);p.setVelocity(v.setY(.25));animate(p,w,10,2,i->fx(p.getLocation(),Particle.ELECTRIC_SPARK,15));}
            case "air" -> {animate(p,w,12,3,i->ring(l,Particle.CLOUD,.5+i*.4,.7,24,i*.4));for(var e:targets(p,l,5))push(e,l,1.2,.25);}
            case "health" -> {for(Player q:w.getPlayers())if(ally(p,q)&&q.getLocation().distanceSquared(l)<=25)effect(q,PotionEffectType.ABSORPTION,300,0);animate(p,w,15,4,i->ring(l,Particle.HEART,4,.5,12,i*.3));}
            case "guardian" -> {effect(p,PotionEffectType.ABSORPTION,200,1);animate(p,w,25,4,i->ring(p.getLocation(),Particle.END_ROD,1.5,1,20,i*.4));}
        }
    }
}
