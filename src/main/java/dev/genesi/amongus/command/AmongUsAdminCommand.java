package dev.genesi.amongus.command;

import dev.genesi.amongus.AmongUsPlugin;
import dev.genesi.amongus.model.Arena;
import dev.genesi.amongus.model.TaskType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AmongUsAdminCommand implements CommandExecutor, TabCompleter {

    private final AmongUsPlugin plugin;

    public AmongUsAdminCommand(AmongUsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("amongus.admin")) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "create" -> handleCreate(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "setlobby", "lobby" -> handleSetLobby(sender, args);
            case "setcafeteria", "cafeteria" -> handleSetCafeteria(sender, args);
            case "addspawn", "spawn" -> handleAddSpawn(sender, args);
            case "clearspawns" -> handleClearSpawns(sender, args);
            case "setbutton", "button" -> handleSetButton(sender, args);
            case "addtask", "task" -> handleAddTask(sender, args);
            case "cleartasks" -> handleClearTasks(sender, args);
            case "addvent", "vent" -> handleAddVent(sender, args);
            case "clearvents" -> handleClearVents(sender, args);
            case "setelectrical", "electrical" -> handleSetElectrical(sender, args);
            case "addreactor", "reactor" -> handleAddReactor(sender, args);
            case "clearreactors" -> handleClearReactors(sender, args);
            case "setfee", "fee" -> handleSetFee(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "forcestart" -> handleForceStart(sender, args);
            case "forcestop", "cancel" -> handleForceStop(sender, args);
            case "givepoints" -> handleGivePoints(sender, args);
            case "takepoints" -> handleTakePoints(sender, args);
            case "setplayerpoints" -> handleSetPlayerPoints(sender, args);
            case "redeem" -> handleRedeem(sender, args);
            case "reload" -> {
                plugin.reloadPlugin();
                plugin.getMessageService().send(sender, "reloaded");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin create <name>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (plugin.getArenaManager().exists(name)) {
            plugin.getMessageService().sendRaw(sender, "&cArena already exists.");
            return;
        }
        plugin.getArenaManager().create(name);
        plugin.getMessageService().send(sender, "arena-created", Map.of("arena", name));
        plugin.getMessageService().sendRaw(sender, "&7Setup: setlobby, setcafeteria, addspawn, setbutton, addtask, addvent, setelectrical, addreactor");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin delete <name>");
            return;
        }
        String name = args[1];
        if (plugin.getGameManager().isActive(name)) {
            plugin.getGameManager().forceStop(name);
        }
        if (!plugin.getArenaManager().delete(name)) {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", name));
            return;
        }
        plugin.getMessageService().send(sender, "arena-deleted", Map.of("arena", name.toLowerCase(Locale.ROOT)));
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin setlobby <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.setLobby(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "lobby-set", Map.of("arena", arena.getName()));
    }

    private void handleSetCafeteria(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin setcafeteria <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.setCafeteria(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "cafeteria-set", Map.of("arena", arena.getName()));
    }

    private void handleAddSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin addspawn <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.addSpawn(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "spawn-added", Map.of(
                "arena", arena.getName(),
                "index", String.valueOf(arena.getSpawns().size())
        ));
    }

    private void handleClearSpawns(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin clearspawns <arena>");
        if (arena == null) {
            return;
        }
        arena.clearSpawns();
        plugin.getArenaManager().save();
        plugin.getMessageService().sendRaw(sender, "&aCleared spawns for &e" + arena.getName());
    }

    private void handleSetButton(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin setbutton <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.setEmergencyButton(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "button-set", Map.of("arena", arena.getName()));
    }

    private void handleAddTask(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin addtask <arena> <type>");
            plugin.getMessageService().sendRaw(sender, "&7Types: " + Arrays.stream(TaskType.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
            return;
        }
        Arena arena = requireArena(sender, args, 1, null);
        if (arena == null) {
            return;
        }
        TaskType type;
        try {
            type = TaskType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getMessageService().sendRaw(sender, "&cUnknown task type.");
            return;
        }
        arena.addTask(new Arena.TaskPoint(type, player.getLocation()));
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "task-added", Map.of(
                "arena", arena.getName(),
                "task", type.display()
        ));
    }

    private void handleClearTasks(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin cleartasks <arena>");
        if (arena == null) {
            return;
        }
        arena.clearTasks();
        plugin.getArenaManager().save();
        plugin.getMessageService().sendRaw(sender, "&aCleared tasks for &e" + arena.getName());
    }

    private void handleAddVent(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin addvent <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.addVent(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "vent-added", Map.of(
                "arena", arena.getName(),
                "index", String.valueOf(arena.getVents().size())
        ));
    }

    private void handleClearVents(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin clearvents <arena>");
        if (arena == null) {
            return;
        }
        arena.clearVents();
        plugin.getArenaManager().save();
        plugin.getMessageService().sendRaw(sender, "&aCleared vents for &e" + arena.getName());
    }

    private void handleSetElectrical(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin setelectrical <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.setElectrical(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "electrical-set", Map.of("arena", arena.getName()));
    }

    private void handleAddReactor(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        Arena arena = requireArena(sender, args, 1, "/amongusadmin addreactor <arena>");
        if (player == null || arena == null) {
            return;
        }
        arena.addReactorPanel(player.getLocation());
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "reactor-added", Map.of(
                "arena", arena.getName(),
                "index", String.valueOf(arena.getReactorPanels().size())
        ));
    }

    private void handleClearReactors(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin clearreactors <arena>");
        if (arena == null) {
            return;
        }
        arena.clearReactorPanels();
        plugin.getArenaManager().save();
        plugin.getMessageService().sendRaw(sender, "&aCleared reactor panels for &e" + arena.getName());
    }

    private void handleSetFee(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin setfee <arena> <amount>");
            return;
        }
        Arena arena = requireArena(sender, args, 1, null);
        if (arena == null) {
            return;
        }
        double fee;
        try {
            fee = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid fee.");
            return;
        }
        arena.setEntryFeeOverride(fee);
        plugin.getArenaManager().save();
        plugin.getMessageService().send(sender, "fee-set", Map.of(
                "arena", arena.getName(),
                "fee", plugin.getEconomyService().format(fee)
        ));
    }

    private void handleList(CommandSender sender) {
        var arenas = plugin.getArenaManager().getArenas();
        if (arenas.isEmpty()) {
            plugin.getMessageService().sendRaw(sender, "&eNo arenas.");
            return;
        }
        for (Arena arena : arenas) {
            plugin.getMessageService().sendRaw(sender, "&8- &f" + arena.getName()
                    + (arena.isReady() ? " &aready" : " &csetup incomplete"));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin info <arena>");
        if (arena == null) {
            return;
        }
        plugin.getMessageService().sendRaw(sender, "&eArena &f" + arena.getName());
        plugin.getMessageService().sendRaw(sender, "&7Lobby: " + loc(arena.getLobby()));
        plugin.getMessageService().sendRaw(sender, "&7Cafeteria: " + loc(arena.getCafeteria()));
        plugin.getMessageService().sendRaw(sender, "&7Button: " + loc(arena.getEmergencyButton()));
        plugin.getMessageService().sendRaw(sender, "&7Electrical: " + loc(arena.getElectrical()));
        plugin.getMessageService().sendRaw(sender, "&7Spawns: &f" + arena.getSpawns().size());
        plugin.getMessageService().sendRaw(sender, "&7Tasks: &f" + arena.getTasks().size());
        plugin.getMessageService().sendRaw(sender, "&7Vents: &f" + arena.getVents().size());
        plugin.getMessageService().sendRaw(sender, "&7Reactor panels: &f" + arena.getReactorPanels().size());
    }

    private void handleForceStart(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin forcestart <arena>");
        if (arena == null) {
            return;
        }
        if (!plugin.getGameManager().forceStart(arena)) {
            plugin.getMessageService().sendRaw(sender, "&cCould not force-start (need players in lobby).");
            return;
        }
        plugin.getMessageService().send(sender, "force-started", Map.of("arena", arena.getName()));
    }

    private void handleForceStop(CommandSender sender, String[] args) {
        Arena arena = requireArena(sender, args, 1, "/amongusadmin forcestop <arena>");
        if (arena == null) {
            return;
        }
        plugin.getGameManager().forceStop(arena);
        plugin.getMessageService().send(sender, "force-stopped", Map.of("arena", arena.getName()));
    }

    private void handleGivePoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin givepoints <player> <amount>");
            return;
        }
        var target = Bukkit.getOfflinePlayer(args[1]);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        plugin.getPointsService().addPoints(target, amount);
        plugin.getMessageService().send(sender, "points-added", Map.of(
                "player", args[1],
                "amount", String.valueOf(amount),
                "points", String.valueOf(plugin.getPointsService().getPoints(target))
        ));
    }

    private void handleTakePoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin takepoints <player> <amount>");
            return;
        }
        var target = Bukkit.getOfflinePlayer(args[1]);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        if (!plugin.getPointsService().removePoints(target, amount)) {
            plugin.getMessageService().send(sender, "not-enough-points", Map.of(
                    "player", args[1],
                    "points", String.valueOf(plugin.getPointsService().getPoints(target))
            ));
            return;
        }
        plugin.getMessageService().send(sender, "points-removed", Map.of(
                "player", args[1],
                "amount", String.valueOf(amount),
                "points", String.valueOf(plugin.getPointsService().getPoints(target))
        ));
    }

    private void handleSetPlayerPoints(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin setplayerpoints <player> <amount>");
            return;
        }
        var target = Bukkit.getOfflinePlayer(args[1]);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        plugin.getPointsService().setPoints(target, amount);
        plugin.getMessageService().send(sender, "points-set", Map.of(
                "player", args[1],
                "points", String.valueOf(amount)
        ));
    }

    private void handleRedeem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageService().sendRaw(sender, "&cUsage: /amongusadmin redeem <player> <amount>");
            return;
        }
        var target = Bukkit.getOfflinePlayer(args[1]);
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageService().sendRaw(sender, "&cInvalid amount.");
            return;
        }
        if (!plugin.getPointsService().removePoints(target, amount)) {
            plugin.getMessageService().send(sender, "not-enough-points", Map.of(
                    "player", args[1],
                    "points", String.valueOf(plugin.getPointsService().getPoints(target))
            ));
            return;
        }
        plugin.getMessageService().send(sender, "redeem-success", Map.of(
                "player", args[1],
                "amount", String.valueOf(amount),
                "points", String.valueOf(plugin.getPointsService().getPoints(target))
        ));
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageService().sendRaw(sender, "&cAmong Us admin:");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin create|delete|list|info <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin setlobby|setcafeteria|setbutton|setelectrical <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin addspawn|addvent|addreactor <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin addtask <arena> <type>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin forcestart|forcestop <arena>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin givepoints|takepoints|redeem <player> <n>");
        plugin.getMessageService().sendRaw(sender, "&e/amongusadmin reload");
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().send(sender, "players-only");
            return null;
        }
        return player;
    }

    private Arena requireArena(CommandSender sender, String[] args, int index, String usage) {
        if (args.length <= index) {
            if (usage != null) {
                plugin.getMessageService().sendRaw(sender, "&cUsage: " + usage);
            }
            return null;
        }
        return plugin.getArenaManager().get(args[index]).orElseGet(() -> {
            plugin.getMessageService().send(sender, "arena-not-found", Map.of("arena", args[index]));
            return null;
        });
    }

    private static String loc(Location location) {
        if (location == null || location.getWorld() == null) {
            return "&cunset";
        }
        return "&f" + location.getWorld().getName()
                + " " + String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("amongus.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of(
                    "help", "create", "delete", "setlobby", "setcafeteria", "addspawn", "clearspawns",
                    "setbutton", "addtask", "cleartasks", "addvent", "clearvents", "setelectrical",
                    "addreactor", "clearreactors", "setfee", "list", "info", "forcestart", "forcestop",
                    "givepoints", "takepoints", "setplayerpoints", "redeem", "reload"
            ));
        }
        if (args.length == 2 && List.of(
                "delete", "setlobby", "setcafeteria", "addspawn", "clearspawns", "setbutton", "addtask",
                "cleartasks", "addvent", "clearvents", "setelectrical", "addreactor", "clearreactors",
                "setfee", "info", "forcestart", "forcestop"
        ).contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[1], plugin.getArenaManager().getArenas().stream().map(Arena::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addtask")) {
            return filter(args[2], Arrays.stream(TaskType.values()).map(Enum::name).toList());
        }
        return List.of();
    }

    private static List<String> filter(String input, List<String> options) {
        String needle = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }
}
