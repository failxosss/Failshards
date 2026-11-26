package net.failxos.failshards;

import java.util.*;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class ShardAbilities implements Listener {
    private final FailShards plugin;
    private final ShardManager manager;
    private final Map<UUID,long[]> cooldown = new HashMap<>();
    private final Set<Arrow> arrows = new HashSet<>();
    private final ModelEffects visuals;
    private final Random random = new Random();
    public ShardAbilities(FailShards plugin, ShardManager manager) {
        this.plugin=plugin; this.manager=manager;
        visuals=new ModelEffects(plugin,manager);
        Bukkit.getPluginManager().registerEvents(visuals,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::surfaceTick,1L,1L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("abilities.hud",true)) return;
            for(Player p:Bukkit.getOnlinePlayers()) {
                Shard s=held(p); if(s==null) continue;
                long[] t=cooldown.getOrDefault(p.getUniqueId(),new long[3]);
                p.sendActionBar(Component.text(s.name()+" | "+manager.text(s.key().equals("totem")?"automatic":"right")+": "+remaining(s.key().equals("totem")?totemEnd(p):t[0])+" | "+manager.text("shift-right")+": "+remaining(t[1])+" | "+(Set.of("water","fire").contains(s.key())?"Surface walk: "+(p.isSneaking()?"Dive":"Auto"):manager.text("shift-left")+": "+remaining(t[2]))));
            }
        },20L,20L);
    }
    private String remaining(long end) { long s=Math.max(0,(end-System.currentTimeMillis()+999)/1000); return s==0?manager.text("ready"):s+"s"; }
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
        if(third && Set.of("water","fire").contains(s.key()))return;
        e.setCancelled(true); activate(p,s,third?2:p.isSneaking()?1:0);
    }
    private boolean pSneakLeft(PlayerInteractEvent e){return e.getPlayer().isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR || e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK);}
    private boolean allowed(Player p) { return p.isOnline()&&!p.isDead()&&!plugin.getConfig().getStringList("abilities.disabled-worlds").contains(p.getWorld().getName()); }
    private void animate(Player p, World w, int frames, long period, IntConsumer action) {
        new BukkitRunnable(){int i=0; public void run(){if(i>=frames||!allowed(p)||!p.getWorld().equals(w)){cancel();return;} action.accept(i++);}}.runTaskTimer(plugin,0L,period);
    }
    private void fx(Location l,Particle particle,int count) { if(visuals.particles()) l.getWorld().spawnParticle(particle,l,count,.07,.07,.07,0); }
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
    private record Surface(BlockData original, Material replacement) {}
    private final Map<Block,Surface> surfaces=new HashMap<>();
    private boolean walkingEnabled(Player p,String shard){
        return allowed(p) && (p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE)
            && !p.isFlying() && !p.isGliding() && !p.isInsideVehicle()
            && plugin.getConfig().getBoolean("abilities.enabled",true)
            && plugin.getConfig().getBoolean("abilities."+shard+".enabled",true)
            && plugin.getConfig().getBoolean("abilities."+shard+".surface-walk",true)
            && manager.activeShards(p).stream().anyMatch(sh->sh.key().equals(shard));
    }
    private Material originalType(Block b){Surface s=surfaces.get(b);return s==null?b.getType():s.original().getMaterial();}
    private void surfaceTick(){
        Map<Block,Material> wanted=new HashMap<>();
        Set<Block> diving=new HashSet<>();
        for(Player p:Bukkit.getOnlinePlayers()){
            if(!allowed(p))continue;
            Location l=p.getLocation(); World w=p.getWorld();
            boolean water=walkingEnabled(p,"water"),lava=walkingEnabled(p,"fire");
            if(p.isSneaking()){
                for(int y=l.getBlockY()-1;y<=l.getBlockY()+1;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                    if(y>=w.getMinHeight()&&y<w.getMaxHeight()&&w.isChunkLoaded((l.getBlockX()+x)>>4,(l.getBlockZ()+z)>>4))diving.add(w.getBlockAt(l.getBlockX()+x,y,l.getBlockZ()+z));
                continue;
            }
            if(!water&&!lava)continue;
            int y=(int)Math.floor(l.getY()-.05);
            if(y<w.getMinHeight()||y>=w.getMaxHeight()-1)continue;
            Block center=w.getBlockAt(l.getBlockX(),y,l.getBlockZ()); Material liquid=originalType(center);
            if((liquid==Material.WATER&&water||liquid==Material.LAVA&&lava)&&center.getRelative(0,1,0).getType().isAir()&&l.getY()<y+.96){
                // Rise the last fraction of a block before creating a collision surface.
                if(l.getY()>=y+.35&&p.getEyeLocation().getY()>y+1&&p.getVelocity().getY()>=-.12){Vector v=p.getVelocity();v.setY(Math.max(.18,v.getY()));p.setVelocity(v);p.setFallDistance(0);}
                continue;
            }
            if(l.getY()<y+.96 || l.getY()>y+1.35)continue;
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                int bx=l.getBlockX()+x,bz=l.getBlockZ()+z;if(!w.isChunkLoaded(bx>>4,bz>>4))continue;
                Block block=w.getBlockAt(bx,y,bz);Material type=originalType(block);
                if(!block.getRelative(0,1,0).getType().isAir())continue;
                if(type==Material.WATER&&water)wanted.put(block,Material.PACKED_ICE);
                if(type==Material.LAVA&&lava)wanted.put(block,Material.BLACKSTONE);
            }
        }
        // Do not create solid blocks inside any player's body, including swimmers.
        for(Player p:Bukkit.getOnlinePlayers()){
            if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
            var bb=p.getBoundingBox();World w=p.getWorld();
            wanted.keySet().removeIf(block->block.getWorld().equals(w)&&bb.getMinX()<block.getX()+1&&bb.getMaxX()>block.getX()&&bb.getMinZ()<block.getZ()+1&&bb.getMaxZ()>block.getZ()&&bb.getMinY()<block.getY()+.96&&bb.getMaxY()>block.getY());
        }
        wanted.keySet().removeAll(diving);
        for(var entry:new ArrayList<>(surfaces.entrySet()))if(wanted.get(entry.getKey())!=entry.getValue().replacement())restore(entry.getKey(),entry.getValue());
        for(var entry:wanted.entrySet()){
            Block block=entry.getKey();Material replacement=entry.getValue();if(surfaces.containsKey(block))continue;
            if(block.getType()!=Material.WATER&&block.getType()!=Material.LAVA)continue;
            var event=new org.bukkit.event.block.BlockFormEvent(block,block.getState());event.getNewState().setType(replacement);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())continue;
            surfaces.put(block,new Surface(block.getBlockData().clone(),replacement));block.setType(replacement,false);
        }
    }
    private void restore(Block b,Surface s){if(b.getType()==s.replacement())b.setBlockData(s.original(),false);surfaces.remove(b);}
    @EventHandler(ignoreCancelled=true) public void protectSurface(org.bukkit.event.block.BlockBreakEvent e){if(surfaces.containsKey(e.getBlock()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void protectSurfacePiston(org.bukkit.event.block.BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(surfaces::containsKey))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void protectSurfaceRetract(org.bukkit.event.block.BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(surfaces::containsKey))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void protectSurfaceExplosion(org.bukkit.event.entity.EntityExplodeEvent e){e.blockList().removeIf(surfaces::containsKey);}
    @EventHandler(ignoreCancelled=true) public void protectSurfaceBlockExplosion(org.bukkit.event.block.BlockExplodeEvent e){e.blockList().removeIf(surfaces::containsKey);}
    public void shutdown(){for(var e:new ArrayList<>(surfaces.entrySet()))restore(e.getKey(),e.getValue());visuals.shutdown();for(Arrow a:arrows)a.remove();arrows.clear();}
    private void activate(Player p,Shard s,int slot){
        if(slot==2&&Set.of("water","fire").contains(s.key()))return;
        boolean ult=slot==1;
        if(s.key().equals("totem") && slot==0)return;
        if(!plugin.getConfig().getBoolean("abilities."+s.key()+".slot-"+(slot+1)+".enabled",true))return;
        if(!allowed(p)||!plugin.getConfig().getBoolean("abilities.enabled",true))return;
        if(!plugin.getConfig().getBoolean("abilities."+s.key()+".enabled",true))return;
        long now=System.currentTimeMillis();long[] t=cooldown.computeIfAbsent(p.getUniqueId(),k->new long[3]);
        if(t[slot]>now){p.sendActionBar(Component.text(manager.text("cooldown")+": "+remaining(t[slot])));return;}
        int seconds=plugin.getConfig().getInt("abilities."+s.key()+"."+(slot==2?"third-cooldown":ult?"ultimate-cooldown":"active-cooldown"),slot==2?90:ult?150:45);
        t[slot]=now+1000L*Math.max(1,seconds); Location l=p.getLocation();World w=p.getWorld();
        p.sendActionBar(Component.text(s.name()+" • "+manager.text(slot==2?"special":ult?"ultimate":"active")));w.playSound(l,"minecraft:block.amethyst_block.chime",1f,1f);
        int visualTicks=20* Math.max(1,Math.min(30,plugin.getConfig().getInt("abilities."+s.key()+".slot-"+(slot+1)+".duration",slot==1?8:slot==2?5:6)));
        if(Set.of("water","fire").contains(s.key())) visualTicks=slot==0?50:slot==1?100:600;
        visuals.play(p,s.key(),slot,visualTicks);
        if(!Set.of("water","fire").contains(s.key())&&plugin.getConfig().isConfigurationSection("abilities."+s.key()+".slot-"+(slot+1))){configured(p,s,slot);return;}
        if(slot==2){thirdAbility(p,s,l,w);return;}
        switch(s.key()){
            case "strength" -> {if(!ult){effect(p,PotionEffectType.STRENGTH,160,1);animate(p,w,8,3,i->ring(p.getLocation(),Particle.CRIT,1.2,1,16,i));}else{animate(p,w,10,2,i->ring(l,Particle.CRIT,.5+i*.55,.4,24,i));for(var e:targets(p,l,5)){e.damage(4,p);push(e,l,1.1,.45);}}}
            case "arsenal" -> {if(ult){Location c=forward(p,5);animate(p,w,20,5,i->{ring(c,Particle.WAX_ON,5,.15,24,i*.1);for(int j=0;j<3;j++){double a=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*4.5;Location at=at(c,Math.cos(a)*r,9,Math.sin(a)*r);if(at.getBlock().isPassable()){arrow(p,at,new Vector(0,-1,0));fx(at,Particle.WAX_ON,4);}}});}else{Vector v=l.getDirection();for(double a:new double[]{-.18,0,.18})arrow(p,p.getEyeLocation().add(v.clone().multiply(.5)),v.clone().rotateAroundY(a));}}
            case "glitched" -> {if(!ult){fx(l,Particle.PORTAL,30);Location dest=l;for(int i=1;i<=6;i++){Location c=forward(p,i);if(!c.getBlock().isPassable()||!at(c,0,1,0).getBlock().isPassable())break;dest=c;}p.teleport(dest);fx(dest,Particle.PORTAL,30);}else{for(var e:targets(p,l,6))if(e instanceof Player q){long[] c=cooldown.computeIfAbsent(q.getUniqueId(),k->new long[3]);for(int i=0;i<3;i++)c[i]=Math.max(c[i],now+5000);effect(q,PotionEffectType.DARKNESS,100,0);}animate(p,w,15,4,i->ring(l,Particle.PORTAL,5,1,24,i));}}
            case "soul" -> {animate(p,w,ult?20:8,5,i->{ring(l,Particle.SOUL_FIRE_FLAME,ult?4:2,1,16,i*.5);if(i%4==0)for(var e:targets(p,l,ult?5:3)){e.damage(ult?2:1,p);heal(p,1);}});}
            case "fire" -> {
                if(!ult){
                    Location c=forward(p,4);
                    animate(p,w,18,2,i->{
                        double r=.8+i*.18;
                        ring(c,Particle.FLAME,r,.35,30,i*.55);
                        ring(c,Particle.SMOKE,r*.7,.75,18,-i*.35);
                        if(i%3==0){ fx(c.clone().add(0,.6,0),Particle.LAVA,4); for(var e:targets(p,c,3.4)){ e.setFireTicks(100); e.damage(2.5,p); push(e,c,.45,.12);} }
                    });
                }else{
                    Location c=l.clone();
                    animate(p,w,34,2,i->{
                        for(int h=0;h<7;h++){ double r=1.4+h*.48; ring(c,Particle.FLAME,r,.2+h*.28,28,i*.22+h*.14); }
                        ring(c,Particle.LAVA,4.8,.12,18,-i*.3);
                        if(i%4==0) for(var e:targets(p,c,5.5)){ e.setFireTicks(120); e.damage(2.5,p); }
                    });
                }
            }
            case "water" -> {
                if(!ult){
                    Vector dir=l.getDirection().setY(0); if(dir.lengthSquared()>.001) dir.normalize();
                    Set<UUID> hit=new HashSet<>();
                    animate(p,w,25,2,i->{
                        if(i<4)return; Location crest=l.clone().add(dir.clone().multiply((i-4)*.43));
                        for(var e:targets(p,crest,3.4))if(hit.add(e.getUniqueId()))e.setVelocity(dir.clone().multiply(1.25).setY(.28));
                        for(int j=-5;j<=5;j++){Vector side=new Vector(dir.getZ(),0,-dir.getX());Location spray=crest.clone().add(side.multiply(j*.55)).add(0,1.2,0);spray.getWorld().spawnParticle(Particle.SPLASH,spray,2,.1,.35,.1,0);}
                    });
                }else{
                    Location c=forward(p,3);
                    animate(p,w,28,2,i->{
                        for(int h=0;h<8;h++){ double r=.9+h*.38; ring(c,Particle.SPLASH,r,.15+h*.22,24,-i*.24+h*.3); }
                        if(i%4==0) for(var e:targets(p,c,5)){ effect(e,PotionEffectType.SLOWNESS,50,1); push(e,c,.35,.1);}
                    });
                }
            }
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
            case "speed" -> {Vector v=l.getDirection().setY(0);if(v.lengthSquared()>.001)v.normalize().multiply(1.4);p.setVelocity(v.setY(.25));animate(p,w,10,2,i->fx(p.getLocation(),Particle.ELECTRIC_SPARK,15));}
            case "air" -> {animate(p,w,12,3,i->ring(l,Particle.CLOUD,.5+i*.4,.7,24,i*.4));for(var e:targets(p,l,5))push(e,l,1.2,.25);}
            case "health" -> {for(Player q:w.getPlayers())if(ally(p,q)&&q.getLocation().distanceSquared(l)<=25)effect(q,PotionEffectType.ABSORPTION,300,0);animate(p,w,15,4,i->ring(l,Particle.HEART,4,.5,12,i*.3));}
            case "guardian" -> {effect(p,PotionEffectType.ABSORPTION,200,1);animate(p,w,25,4,i->ring(p.getLocation(),Particle.END_ROD,1.5,1,20,i*.4));}
        }
    }
    private long totemEnd(Player p){return p.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,"totem_end"),org.bukkit.persistence.PersistentDataType.LONG,0L);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void rescue(org.bukkit.event.entity.EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||!allowed(p)||e.getFinalDamage()<p.getHealth())return;
        if(e.getCause()==org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID)return;
        if(!plugin.getConfig().getBoolean("abilities.enabled",true)||!plugin.getConfig().getBoolean("abilities.totem.enabled",true)||!plugin.getConfig().getBoolean("abilities.totem.slot-1.enabled",true))return;
        if(manager.activeShards(p).stream().noneMatch(s->s.key().equals("totem"))||totemEnd(p)>System.currentTimeMillis())return;
        if(p.getInventory().getItemInMainHand().getType()==Material.TOTEM_OF_UNDYING||p.getInventory().getItemInOffHand().getType()==Material.TOTEM_OF_UNDYING)return;
        e.setCancelled(true);
        p.getPersistentDataContainer().set(new NamespacedKey(plugin,"totem_end"),org.bukkit.persistence.PersistentDataType.LONG,System.currentTimeMillis()+1000L*Math.max(0,plugin.getConfig().getInt("abilities.totem.active-cooldown",3600)));
        p.setHealth(Math.min(p.getMaxHealth(),Math.max(1,plugin.getConfig().getDouble("abilities.totem.health",8))));
        p.setFireTicks(0); p.setFallDistance(0);
        effect(p,PotionEffectType.REGENERATION,20*Math.max(1,plugin.getConfig().getInt("abilities.totem.regeneration-seconds",10)),1);
        effect(p,PotionEffectType.ABSORPTION,20*Math.max(1,plugin.getConfig().getInt("abilities.totem.absorption-seconds",5)),1);
        effect(p,PotionEffectType.FIRE_RESISTANCE,20*Math.max(1,plugin.getConfig().getInt("abilities.totem.fire-resistance-seconds",10)),0);
        visuals.play(p,"totem",0,100);p.playEffect(EntityEffect.TOTEM_RESURRECT);manager.msg(p,"totem-activated");
    }
    private void configured(Player p,Shard s,int slot){
        String k="abilities."+s.key()+".slot-"+(slot+1)+".";var c=plugin.getConfig();
        var type=PotionEffectType.getByKey(NamespacedKey.minecraft(c.getString(k+"effect","speed")));if(type==null)return;
        int ticks=20*Math.max(1,Math.min(600,c.getInt(k+"duration",10)));int amp=Math.max(0,Math.min(10,c.getInt(k+"amplifier",1)));double r=Math.max(1,Math.min(16,c.getDouble(k+"radius",5)));Location l=p.getLocation();
        if(c.getString(k+"target","self").equals("nearby")){for(var v:targets(p,l,r))effect(v,type,ticks,amp);}else effect(p,type,ticks,amp);
        animate(p,p.getWorld(),12,4,i->ring(p.getLocation(),Particle.END_ROD,Math.min(r,3),.5,16,i*.4));
    }
}
