package dev.genesi.amongus.command;

import dev.genesi.amongus.AmongUsPlugin;
import dev.genesi.amongus.model.Arena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class AmongUsCommand implements CommandExecutor, TabCompleter {

    private final AmongUsPlugin plugin;

    public AmongUsCommand(AmongUsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "join", "play" -> handleJoin(sender, args);
            case "leave", "quit" -> handleLeave(sender);
            case "start" -> handleStart(sender);
            case "report" -> handleReport(sender);
            case "sabotage" -> handleSabotage(sender, args);
            case "tasks" -> handleTasks(sender);
            case "points", "balance" -> handlePoints(sender, args);
            case "arenas", "list" -> handleArenas(sender);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("amongus.use")) {
            plugin.getMessageService().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongus join <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        String result = plugin.getGameManager().join(player, arena.get());
        switch (result) {
            case "ok", "handled" -> {
            }
            case "already-playing" -> plugin.getMessageService().send(player, "already-playing");
            case "arena-not-ready" -> plugin.getMessageService().send(player, "arena-not-ready", Map.of("arena", arena.get().getName()));
            case "arena-busy" -> plugin.getMessageService().send(player, "arena-busy", Map.of("arena", arena.get().getName()));
            case "economy-missing" -> plugin.getMessageService().send(player, "economy-missing");
            default -> plugin.getMessageService().sendRaw(player, "&cCould not join.");
        }
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        plugin.getGameManager().leave(player, true);
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        plugin.getGameManager().tryStart(player);
    }

    private void handleReport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        plugin.getGameManager().handleReport(player);
    }

    private void handleSabotage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongus sabotage <lights|reactor>");
            return;
        }
        plugin.getGameManager().handleSabotage(player, args[1]);
    }

    private void handleTasks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        var session = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (session.isEmpty()) {
            plugin.getMessageService().send(player, "not-playing");
            return;
        }
        var state = session.get().getPlayer(player.getUniqueId());
        var arena = plugin.getArenaManager().get(session.get().getArenaName());
        if (state == null || arena.isEmpty()) {
            return;
        }
        if (state.isImpostor() && state.isAlive()) {
            plugin.getMessageService().sendRaw(player, "&cImpostors have fake tasks — look busy near panels.");
            return;
        }
        plugin.getMessageService().sendRaw(player, "&eYour tasks:");
        for (Integer index : state.getAssignedTaskIndexes()) {
            if (index < 0 || index >= arena.get().getTasks().size()) {
                continue;
            }
            boolean done = state.getCompletedTaskIndexes().contains(index);
            String name = arena.get().getTasks().get(index).getType().display();
            plugin.getMessageService().sendRaw(player, (done ? "&a✔ " : "&7• ") + name);
        }
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (!sender.hasPermission("amongus.points") && !sender.hasPermission("amongus.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return;
        }
        if (args.length >= 2) {
            if (!sender.hasPermission("amongus.admin")) {
                plugin.getMessageService().send(sender, "no-permission");
                return;
            }
            var target = plugin.getServer().getOfflinePlayer(args[1]);
            int points = plugin.getPointsService().getPoints(target);
            plugin.getMessageService().send(sender, "points-other", Map.of(
                    "player", args[1],
                    "points", String.valueOf(points)
            ));
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return;
        }
        plugin.getMessageService().send(player, "points-self", Map.of(
                "points", String.valueOf(plugin.getPointsService().getPoints(player))
        ));
    }

    private void handleArenas(CommandSender sender) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&eNo arenas configured.");
            return;
        }
        for (Arena arena : arenas) {
            plugin.getMessageService().sendRaw(sender, "&8- &f" + arena.getName()
                    + (arena.isReady() ? " &aready" : " &csetup incomplete"));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongus info <arena>");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(args[1]);
        if (arena.isEmpty()) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Arena a = arena.get();
        plugin.getMessageService().sendRaw(sender, "&cAmong Us &f" + a.getName()
                + (a.isReady() ? " &a(ready)" : " &c(incomplete)"));
        plugin.getMessageService().sendRaw(sender, "&7Spawns: &f" + a.getSpawns().size()
                + " &8| &7Tasks: &f" + a.getTasks().size()
                + " &8| &7Vents: &f" + a.getVents().size());
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&cAmong Us &7commands:");
        plugin.getMessageService().sendRaw(sender, "&e/amongus join <arena> &8- &7enter a lobby");
        plugin.getMessageService().sendRaw(sender, "&e/amongus leave &8- &7leave your game");
        plugin.getMessageService().sendRaw(sender, "&e/amongus start &8- &7start when enough players");
        plugin.getMessageService().sendRaw(sender, "&e/amongus report &8- &7report a nearby body");
        plugin.getMessageService().sendRaw(sender, "&e/amongus sabotage <lights|reactor>");
        plugin.getMessageService().sendRaw(sender, "&e/amongus tasks &8- &7list your tasks");
        plugin.getMessageService().sendRaw(sender, "&e/amongus points &8- &7arcade points");
        plugin.getMessageService().sendRaw(sender, "&e/amongus arenas &8- &7list arenas");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("join", "leave", "start", "report", "sabotage", "tasks", "points", "arenas", "info", "help"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("info"))) {
            return filter(args[1], plugin.getArenaManager().getArenas().stream().map(Arena::getName).collect(Collectors.toList()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sabotage")) {
            return filter(args[1], List.of("lights", "reactor"));
        }
        return List.of();
    }

    private static List<String> filter(String input, List<String> options) {
        String needle = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }
}
