package net.failxos.failshards;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShardCommand implements CommandExecutor, TabCompleter {

    private final ShardManager manager;

    public ShardCommand(ShardManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            manager.msg(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                manager.reload();
                manager.startTask();
                manager.msg(sender, "reloaded");
            }
            case "give" -> {
                if (args.length < 3) {
                    manager.msg(sender, "usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    manager.msg(sender, "unknown-player");
                    return true;
                }
                Shard s = manager.get(args[2]);
                if (s == null) {
                    manager.msg(sender, "unknown-shard");
                    return true;
                }
                giveTo(sender, target, s);
            }
            case "random" -> {
                if (args.length < 2) {
                    manager.msg(sender, "usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    manager.msg(sender, "unknown-player");
                    return true;
                }
                List<Shard> all = new ArrayList<>(manager.shards().values());
                if (all.isEmpty()) {
                    manager.msg(sender, "unknown-shard");
                    return true;
                }
                giveTo(sender, target, all.get(ThreadLocalRandom.current().nextInt(all.size())));
            }
            default -> manager.msg(sender, "usage");
        }
        return true;
    }

    private void giveTo(CommandSender sender, Player target, Shard s) {
        ItemStack item = manager.createItem(s);
        if (item == null) {
            manager.msg(sender, "item-missing");
            return;
        }
        manager.give(target, item);
        manager.msg(sender, "given",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("shard", s.name()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "random", "reload"));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(manager.shards().keySet());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
