package net.failxos.failshards;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarFile;
import java.io.File;

/** Optional public API adapter. No player entity replacement. */
final class BetterModelBridge {
 private final FailShards plugin;
 private Class<?> api, adapter;
 private final Map<UUID,Active> active=new HashMap<>();
 private final Set<String> warned=new HashSet<>();
 private record Active(Object tracker, Player owner, Location origin, boolean follow, long end, Set<UUID> viewers) {}
 private final Properties lengths=new Properties();
 BetterModelBridge(FailShards plugin){this.plugin=plugin;try(var in=plugin.getResource("bettermodel-lengths.properties")){if(in!=null)lengths.load(in);}catch(java.io.IOException e){plugin.getLogger().warning(e.toString());}}
 private static Object call(Object target,String name,Object... args) throws ReflectiveOperationException {
  Class<?> c=target instanceof Class<?> k?k:target.getClass();
  for(Method m:c.getMethods()){
   if(!m.getName().equals(name)||m.getParameterCount()!=args.length|| (target instanceof Class<?> && !Modifier.isStatic(m.getModifiers())))continue;
   boolean ok=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isInstance(args[i])){ok=false;break;}
   if(ok)return m.invoke(target instanceof Class<?>?null:target,args);
  }throw new NoSuchMethodException(c.getName()+"."+name);
 }
 private void init() throws Exception {
  if(api!=null&&adapter!=null)return;
  var bm=Bukkit.getPluginManager().getPlugin("BetterModel");if(bm==null||!bm.isEnabled())throw new IllegalStateException("BetterModel not enabled");
  ClassLoader cl=bm.getClass().getClassLoader();api=Class.forName("kr.toxicity.model.api.BetterModel",true,cl);
  try(JarFile jar=new JarFile(new File(bm.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()))){
   var entry=jar.stream().filter(e->e.getName().endsWith("/BukkitAdapter.class")).findFirst().orElseThrow();
   adapter=Class.forName(entry.getName().replace('/','.').replace(".class",""),true,cl);
  }
 }
 private Object adapt(Object x)throws ReflectiveOperationException{return call(adapter,"adapt",x);}
 boolean play(Player p,String id,boolean follow,int ticks){
  if(!plugin.getConfig().getBoolean("visual-effects.bettermodel",true))return false;
  remove(p.getUniqueId());Object tracker=null;
  try{
   init();Object renderer=((Optional<?>)call(api,"model","fs8_"+id)).orElseThrow();
   Location l=p.getLocation();l.setPitch(0);tracker=call(renderer,"create",adapt(l));
   Set<UUID> viewers=new HashSet<>();for(Player q:p.getWorld().getPlayers())if(q.getLocation().distanceSquared(l)<=48*48){call(tracker,"spawn",adapt(q));viewers.add(q.getUniqueId());}
   Object result=call(tracker,"animate","cast");if(Boolean.FALSE.equals(result))throw new IllegalStateException("cast rejected");
   active.put(p.getUniqueId(),new Active(tracker,p,l,follow,System.currentTimeMillis()+Long.parseLong(lengths.getProperty(id,Integer.toString(ticks)))*50L,viewers));return true;
  }catch(Exception|LinkageError e){close(tracker);if(warned.add(id))plugin.getLogger().warning("BetterModel fallback for "+id+": "+e);return false;}
 }
 void tick(){for(var entry:new ArrayList<>(active.entrySet())){Active a=entry.getValue();Player p=a.owner();if(!p.isOnline()||p.isDead()||p.getWorld()!=a.origin().getWorld()||System.currentTimeMillis()>=a.end()){remove(entry.getKey());continue;}try{if(a.follow()){Location l=p.getLocation();l.setPitch(0);call(a.tracker(),"location",adapt(l));}}catch(Exception|LinkageError ex){remove(entry.getKey());}}}
 void remove(UUID id){Active a=active.remove(id);if(a!=null)close(a.tracker());}
 private void close(Object t){if(t!=null)try{call(t,"close");}catch(Exception|LinkageError e){plugin.getLogger().warning("BetterModel cleanup: "+e);}}
 void shutdown(){for(UUID id:new ArrayList<>(active.keySet()))remove(id);}
}
