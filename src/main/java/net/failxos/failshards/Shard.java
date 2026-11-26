package net.failxos.failshards;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

public record Shard(String key, String itemId, String name,
                    List<EffectDef> effects, List<AttrDef> attributes,
                    int igniteTicks,
                    List<EffectDef> randomEffects, int randomInterval, int randomDuration) {

    public record EffectDef(PotionEffectType type, int amplifier) {}

    public record AttrDef(Attribute attribute, double amount, NamespacedKey key) {}
}
