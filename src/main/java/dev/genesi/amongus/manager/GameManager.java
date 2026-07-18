package dev.genesi.amongus.manager;

import dev.genesi.amongus.AmongUsPlugin;
import dev.genesi.amongus.model.Arena;
import dev.genesi.amongus.model.CrewColor;
import dev.genesi.amongus.model.GameSession;
import dev.genesi.amongus.model.PlayerGameState;
import dev.genesi.amongus.model.Role;
import dev.genesi.amongus.util.ItemFactory;
import dev.genesi.amongus.util.RoleAssigner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class GameManager {

    private final AmongUsPlugin plugin;
    private final ItemFactory itemFactory;
    private final Map<String, GameSession> byArena = new HashMap<>();
    private final Map<UUID, GameSession> byPlayer = new HashMap<>();

    public GameManager(AmongUsPlugin plugin) {
        this.plugin = plugin;
        this.itemFactory = new ItemFactory(plugin);
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public Optional<GameSession> getByPlayer(UUID uuid) {
        return Optional.ofNullable(byPlayer.get(uuid));
    }

    public Optional<GameSession> getByArena(String arenaName) {
        return Optional.ofNullable(byArena.get(arenaName.toLowerCase(Locale.ROOT)));
    }

    public String join(Player player, Arena arena) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "already-playing";
        }
        if (!arena.isReady()) {
            return "arena-not-ready";
        }

        GameSession session = byArena.computeIfAbsent(arena.getName(), GameSession::new);
        if (session.getState() != GameSession.State.WAITING && session.getState() != GameSession.State.LOBBY_COUNTDOWN) {
            return "arena-busy";
        }

        int max = plugin.getArenaManager().maxPlayers();
        if (session.playerCount() >= max) {
            plugin.getMessageService().send(player, "arena-full", Map.of(
                    "arena", arena.getName(),
                    "max", String.valueOf(max)
            ));
            return "handled";
        }

        double fee = plugin.getArenaManager().resolveEntryFee(arena);
        boolean charged = false;
        if (fee > 0) {
            if (!plugin.getEconomyService().isReady()) {
                return "economy-missing";
            }
            if (!plugin.getEconomyService().has(player, fee) && !player.hasPermission("amongus.bypass.fee")) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
            if (!plugin.getEconomyService().charge(player, fee)) {
                plugin.getMessageService().send(player, "not-enough-money", Map.of(
                        "amount", plugin.getEconomyService().format(fee),
                        "balance", plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player))
                ));
                return "handled";
            }
            charged = !player.hasPermission("amongus.bypass.fee");
        }

        PlayerGameState state = new PlayerGameState(player);
        state.setFeePaid(charged);
        state.snapshot(player);
        session.getPlayers().put(player.getUniqueId(), state);
        byPlayer.put(player.getUniqueId(), session);

        Location lobby = arena.getLobby();
        if (lobby != null) {
            player.teleport(lobby);
        }
        prepareLobbyPlayer(player);

        plugin.getMessageService().send(player, "joined-waiting", Map.of(
                "arena", arena.getName(),
                "count", String.valueOf(session.playerCount()),
                "max", String.valueOf(max)
        ));
        broadcast(session, "joined-waiting", Map.of(
                "arena", arena.getName(),
                "count", String.valueOf(session.playerCount()),
                "max", String.valueOf(max)
        ), player.getUniqueId());

        maybeStartLobbyCountdown(session, arena);
        startActionBar(session);
        return "ok";
    }

    public boolean leave(Player player, boolean announce) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            if (announce) {
                plugin.getMessageService().send(player, "not-playing");
            }
            return false;
        }

        PlayerGameState state = session.getPlayers().remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());
        clearBodiesFor(session, player.getUniqueId());
        restorePlayer(player, state);

        if (announce) {
            plugin.getMessageService().send(player, "left");
            broadcast(session, "player-left", Map.of("player", player.getName()), null);
        }

        if (session.getState() == GameSession.State.WAITING || session.getState() == GameSession.State.LOBBY_COUNTDOWN) {
            if (session.playerCount() < plugin.getArenaManager().minPlayers()) {
                cancelLobby(session);
            }
            if (session.playerCount() == 0) {
                cleanupSession(session);
            }
            return true;
        }

        if (session.playerCount() == 0) {
            endGame(session, "&7Match abandoned");
            return true;
        }

        checkWinConditions(session);
        return true;
    }

    public void tryStart(Player player) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null) {
            plugin.getMessageService().send(player, "not-playing");
            return;
        }
        if (session.getState() != GameSession.State.WAITING && session.getState() != GameSession.State.LOBBY_COUNTDOWN) {
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }
        if (session.playerCount() < plugin.getArenaManager().minPlayers()) {
            plugin.getMessageService().send(player, "need-more-players", Map.of(
                    "min", String.valueOf(plugin.getArenaManager().minPlayers())
            ));
            return;
        }
        beginStartCountdown(session, arena.get());
    }

    public boolean isActive(String arenaName) {
        return byArena.containsKey(arenaName.toLowerCase(Locale.ROOT));
    }

    public boolean forceStart(Arena arena) {
        Optional<GameSession> sessionOpt = getByArena(arena.getName());
        if (sessionOpt.isEmpty()) {
            return false;
        }
        GameSession session = sessionOpt.get();
        if (session.playerCount() < 2) {
            return false;
        }
        beginStartCountdown(session, arena);
        return true;
    }

    public void forceStop(Arena arena) {
        getByArena(arena.getName()).ifPresent(session -> endGame(session, "&cForce stopped"));
    }

    public void forceStop(String arenaName) {
        getByArena(arenaName).ifPresent(session -> endGame(session, "&cForce stopped"));
    }

    public void handleKillAttempt(Player killer, Player victim) {
        GameSession session = byPlayer.get(killer.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        if (session.getSabotage() == GameSession.Sabotage.REACTOR) {
            return;
        }
        PlayerGameState killerState = session.getPlayer(killer.getUniqueId());
        PlayerGameState victimState = session.getPlayer(victim.getUniqueId());
        if (killerState == null || victimState == null) {
            return;
        }
        if (killerState.getRole() != Role.IMPOSTOR || !victimState.isAlive() || victimState.isImpostor()) {
            return;
        }
        if (killerState.isInVent()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < killerState.getKillReadyAt()) {
            int left = (int) Math.ceil((killerState.getKillReadyAt() - now) / 1000.0);
            plugin.getMessageService().send(killer, "kill-cooldown", Map.of("seconds", String.valueOf(left)));
            return;
        }
        double range = plugin.getConfig().getDouble("kill-range", 3.0);
        if (killer.getLocation().distanceSquared(victim.getLocation()) > range * range) {
            return;
        }

        killPlayer(session, killer, victim, killerState, victimState);
    }

    public void handleReport(Player reporter) {
        GameSession session = byPlayer.get(reporter.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        PlayerGameState state = session.getPlayer(reporter.getUniqueId());
        if (state == null || !state.isAlive()) {
            plugin.getMessageService().send(reporter, "ghosts-cant");
            return;
        }

        GameSession.Body nearest = null;
        double best = 4.0 * 4.0;
        for (GameSession.Body body : session.getBodies().values()) {
            double dist = body.getLocation().distanceSquared(reporter.getLocation());
            if (dist <= best) {
                best = dist;
                nearest = body;
            }
        }
        if (nearest == null) {
            return;
        }

        awardGems(state, plugin.getConfig().getInt("gems.report", 5));
        startMeeting(session, reporter, nearest.getVictimName(), true);
    }

    public void handleEmergency(Player caller) {
        GameSession session = byPlayer.get(caller.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        PlayerGameState state = session.getPlayer(caller.getUniqueId());
        if (state == null || !state.isAlive()) {
            plugin.getMessageService().send(caller, "ghosts-cant");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty() || arena.get().getEmergencyButton() == null) {
            return;
        }
        if (caller.getLocation().distanceSquared(arena.get().getEmergencyButton()) > 9.0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < state.getEmergencyReadyAt()) {
            int left = (int) Math.ceil((state.getEmergencyReadyAt() - now) / 1000.0);
            plugin.getMessageService().send(caller, "emergency-cooldown", Map.of("seconds", String.valueOf(left)));
            return;
        }
        state.setEmergencyReadyAt(now + plugin.getConfig().getInt("emergency-cooldown-seconds", 30) * 1000L);
        startMeeting(session, caller, null, false);
    }

    public void handleTaskUse(Player player) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.isImpostor() && state.isAlive()) {
            plugin.getMessageService().sendRaw(player, "&cImpostors fake tasks visually — stand near panels to look busy.");
            return;
        }
        if (state.isGhost() && !plugin.getConfig().getBoolean("ghosts-can-do-tasks", true)) {
            plugin.getMessageService().send(player, "ghosts-cant");
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }

        double radius = plugin.getConfig().getDouble("task-complete-radius", 2.5);
        double best = radius * radius;
        Integer found = null;
        for (Integer index : state.getAssignedTaskIndexes()) {
            if (state.getCompletedTaskIndexes().contains(index)) {
                continue;
            }
            if (index < 0 || index >= arena.get().getTasks().size()) {
                continue;
            }
            Arena.TaskPoint point = arena.get().getTasks().get(index);
            double dist = point.getLocation().distanceSquared(player.getLocation());
            if (dist <= best) {
                best = dist;
                found = index;
            }
        }
        if (found == null) {
            if (!state.getAssignedTaskIndexes().isEmpty()) {
                int next = state.getAssignedTaskIndexes().stream()
                        .filter(i -> !state.getCompletedTaskIndexes().contains(i))
                        .findFirst()
                        .orElse(-1);
                if (next >= 0 && next < arena.get().getTasks().size()) {
                    plugin.getMessageService().send(player, "near-task", Map.of(
                            "task", arena.get().getTasks().get(next).getType().display()
                    ));
                }
            }
            return;
        }

        Arena.TaskPoint completed = arena.get().getTasks().get(found);
        state.getCompletedTaskIndexes().add(found);
        awardGems(state, plugin.getConfig().getInt("gems.task", 8));
        int done = crewTaskDone(session);
        int total = crewTaskTotal(session);
        plugin.getMessageService().send(player, "task-complete", Map.of(
                "task", completed.getType().display(),
                "done", String.valueOf(done),
                "total", String.valueOf(total)
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);

        if (total > 0 && done >= total) {
            endGame(session, message("tasks-done"));
        }
    }

    public void handleVent(Player player, boolean hop) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null || state.getRole() != Role.IMPOSTOR) {
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty() || arena.get().getVents().isEmpty()) {
            plugin.getMessageService().send(player, "no-vents");
            return;
        }
        List<Location> vents = arena.get().getVents();
        if (state.isInVent()) {
            if (hop && vents.size() > 1) {
                Location current = player.getLocation();
                int idx = nearestIndex(vents, current);
                int next = (idx + 1) % vents.size();
                player.teleport(vents.get(next));
                plugin.getMessageService().send(player, "vent-enter");
                return;
            }
            state.setInVent(false);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.setCollidable(true);
            plugin.getMessageService().send(player, "vent-exit");
            return;
        }

        Location near = nearest(vents, player.getLocation(), 2.5);
        if (near == null) {
            return;
        }
        state.setInVent(true);
        player.teleport(near);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 10, 0, false, false, true));
        player.setCollidable(false);
        plugin.getMessageService().send(player, "vent-enter");
    }

    public void handleSabotage(Player player, String type) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null || state.getRole() != Role.IMPOSTOR) {
            return;
        }
        if (session.getSabotage() != GameSession.Sabotage.NONE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < state.getSabotageReadyAt()) {
            int left = (int) Math.ceil((state.getSabotageReadyAt() - now) / 1000.0);
            plugin.getMessageService().send(player, "sabotage-cooldown", Map.of("seconds", String.valueOf(left)));
            return;
        }

        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }

        if ("lights".equalsIgnoreCase(type)) {
            if (arena.get().getElectrical() == null) {
                plugin.getMessageService().sendRaw(player, "&cThis arena has no electrical panel set.");
                return;
            }
            session.setSabotage(GameSession.Sabotage.LIGHTS);
            state.setSabotageReadyAt(now + plugin.getConfig().getInt("sabotage-cooldown-seconds", 35) * 1000L);
            broadcast(session, "sabotage-lights", Map.of(), null);
            for (PlayerGameState other : session.getPlayers().values()) {
                Player p = Bukkit.getPlayer(other.getUuid());
                if (p != null && other.getRole() == Role.CREWMATE) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60, 0, false, false, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 60, 0, false, false, true));
                }
            }
        } else if ("reactor".equalsIgnoreCase(type)) {
            if (arena.get().getReactorPanels().isEmpty()) {
                plugin.getMessageService().sendRaw(player, "&cThis arena needs reactor panels.");
                return;
            }
            session.setSabotage(GameSession.Sabotage.REACTOR);
            session.getReactorFixes().clear();
            state.setSabotageReadyAt(now + plugin.getConfig().getInt("sabotage-cooldown-seconds", 35) * 1000L);
            broadcast(session, "sabotage-reactor", Map.of(), null);
            int seconds = plugin.getConfig().getInt("reactor-meltdown-seconds", 35);
            session.setSabotageSecondsLeft(seconds);
            if (session.getSabotageTask() != null) {
                session.getSabotageTask().cancel();
            }
            session.setSabotageTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (session.getSabotage() != GameSession.Sabotage.REACTOR) {
                    if (session.getSabotageTask() != null) {
                        session.getSabotageTask().cancel();
                        session.setSabotageTask(null);
                    }
                    return;
                }
                session.setSabotageSecondsLeft(session.getSabotageSecondsLeft() - 1);
                if (session.getSabotageSecondsLeft() <= 0) {
                    endGame(session, message("sabotage-failed"));
                }
            }, 20L, 20L));
        }
    }

    public void handleFixInteract(Player player) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.PLAYING) {
            return;
        }
        if (session.getSabotage() == GameSession.Sabotage.NONE) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null || !state.isAlive()) {
            return;
        }
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }

        if (session.getSabotage() == GameSession.Sabotage.LIGHTS) {
            Location electrical = arena.get().getElectrical();
            if (electrical != null && player.getLocation().distanceSquared(electrical) <= 9.0) {
                clearSabotage(session);
            }
            return;
        }

        if (session.getSabotage() == GameSession.Sabotage.REACTOR) {
            Location panel = nearest(arena.get().getReactorPanels(), player.getLocation(), 2.5);
            if (panel == null) {
                return;
            }
            session.getReactorFixes().add(player.getUniqueId());
            long needed = session.alivePlayers();
            if (session.getReactorFixes().size() >= Math.max(1, needed)) {
                clearSabotage(session);
            } else {
                plugin.getMessageService().sendRaw(player, "&aReactor fix " + session.getReactorFixes().size() + "/" + needed);
            }
        }
    }

    public void openVoteGui(Player player) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.VOTING) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null || !state.isAlive()) {
            plugin.getMessageService().send(player, "ghosts-cant");
            return;
        }
        if (state.hasVoted()) {
            plugin.getMessageService().send(player, "already-voted");
            return;
        }

        List<PlayerGameState> alive = session.getPlayers().values().stream()
                .filter(PlayerGameState::isAlive)
                .toList();
        int size = ((alive.size() + 1 + 8) / 9) * 9;
        Inventory inv = Bukkit.createInventory(null, Math.max(9, size),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&8Vote — who is the impostor?"));

        int slot = 0;
        for (PlayerGameState target : alive) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            Player online = Bukkit.getPlayer(target.getUuid());
            if (online != null) {
                meta.setOwningPlayer(online);
            }
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    target.getColor().taggedName(target.getName())));
            meta.lore(List.of(LegacyComponentSerializer.legacyAmpersand().deserialize("&7Click to vote")));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        ItemStack skip = new ItemStack(Material.BARRIER);
        var skipMeta = skip.getItemMeta();
        skipMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&7Skip vote"));
        skip.setItemMeta(skipMeta);
        inv.setItem(inv.getSize() - 1, skip);
        player.openInventory(inv);
    }

    public void handleVoteClick(Player player, int slot, Inventory inventory) {
        GameSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.getState() != GameSession.State.VOTING) {
            return;
        }
        PlayerGameState state = session.getPlayer(player.getUniqueId());
        if (state == null || !state.isAlive() || state.hasVoted()) {
            return;
        }
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null) {
            return;
        }
        if (clicked.getType() == Material.BARRIER) {
            state.setVoted(true);
            state.setSkipVote(true);
            plugin.getMessageService().send(player, "vote-skip");
            player.closeInventory();
            maybeResolveVotes(session);
            return;
        }
        if (clicked.getType() != Material.PLAYER_HEAD || !(clicked.getItemMeta() instanceof SkullMeta skull)) {
            return;
        }
        if (skull.getOwningPlayer() == null) {
            return;
        }
        UUID targetId = skull.getOwningPlayer().getUniqueId();
        if (!session.contains(targetId) || !session.getPlayer(targetId).isAlive()) {
            return;
        }
        state.setVoted(true);
        state.setVoteTarget(targetId);
        plugin.getMessageService().send(player, "vote-cast", Map.of(
                "target", session.getPlayer(targetId).getName()
        ));
        player.closeInventory();
        maybeResolveVotes(session);
    }

    public void shutdown() {
        for (GameSession session : List.copyOf(byArena.values())) {
            endGame(session, "&7Server reload");
        }
        byArena.clear();
        byPlayer.clear();
    }

    private void maybeStartLobbyCountdown(GameSession session, Arena arena) {
        if (session.getState() != GameSession.State.WAITING) {
            return;
        }
        if (session.playerCount() < plugin.getArenaManager().minPlayers()) {
            return;
        }
        session.setState(GameSession.State.LOBBY_COUNTDOWN);
        session.setLobbySecondsLeft(plugin.getConfig().getInt("lobby-countdown-seconds", 20));
        if (session.getLobbyTask() != null) {
            session.getLobbyTask().cancel();
        }
        session.setLobbyTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.playerCount() < plugin.getArenaManager().minPlayers()) {
                cancelLobby(session);
                return;
            }
            int left = session.getLobbySecondsLeft();
            if (left <= 0) {
                session.getLobbyTask().cancel();
                session.setLobbyTask(null);
                beginStartCountdown(session, arena);
                return;
            }
            if (left <= 5 || left % 10 == 0) {
                broadcast(session, "lobby-countdown", Map.of("seconds", String.valueOf(left)), null);
            }
            session.setLobbySecondsLeft(left - 1);
        }, 0L, 20L));
    }

    private void cancelLobby(GameSession session) {
        if (session.getLobbyTask() != null) {
            session.getLobbyTask().cancel();
            session.setLobbyTask(null);
        }
        session.setState(GameSession.State.WAITING);
    }

    private void beginStartCountdown(GameSession session, Arena arena) {
        if (session.getLobbyTask() != null) {
            session.getLobbyTask().cancel();
            session.setLobbyTask(null);
        }
        session.setState(GameSession.State.START_COUNTDOWN);
        session.setStartSecondsLeft(plugin.getConfig().getInt("start-countdown-seconds", 5));
        if (session.getStartTask() != null) {
            session.getStartTask().cancel();
        }
        session.setStartTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int left = session.getStartSecondsLeft();
            if (left <= 0) {
                session.getStartTask().cancel();
                session.setStartTask(null);
                startMatch(session, arena);
                return;
            }
            broadcast(session, "countdown", Map.of("seconds", String.valueOf(left)), null);
            for (UUID id : session.getPlayers().keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
            }
            session.setStartSecondsLeft(left - 1);
        }, 0L, 20L));
    }

    private void startMatch(GameSession session, Arena arena) {
        session.setState(GameSession.State.PLAYING);
        clearBodies(session);

        List<PlayerGameState> players = new ArrayList<>(session.getPlayers().values());
        Collections.shuffle(players);
        int impostors = RoleAssigner.impostorCount(
                players.size(),
                plugin.getConfig().getInt("players-per-impostor", 5),
                plugin.getConfig().getInt("max-impostors", 3)
        );
        List<Boolean> mask = RoleAssigner.assignMask(players.size(), impostors);
        CrewColor[] colors = CrewColor.values();

        List<Location> spawns = arena.getSpawns();
        long killCd = plugin.getConfig().getInt("kill-cooldown-seconds", 25) * 1000L;
        long now = System.currentTimeMillis();

        for (int i = 0; i < players.size(); i++) {
            PlayerGameState state = players.get(i);
            state.setRole(mask.get(i) ? Role.IMPOSTOR : Role.CREWMATE);
            state.setColor(colors[i % colors.length]);
            state.resetMeetingVote();
            state.setInVent(false);
            state.getAssignedTaskIndexes().clear();
            state.getCompletedTaskIndexes().clear();
            state.setKillReadyAt(now + killCd);
            state.setEmergencyReadyAt(0);
            state.setSabotageReadyAt(now + 10_000L);

            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                continue;
            }
            Location spawn = spawns.get(i % spawns.size());
            player.teleport(spawn);
            prepareMatchPlayer(player, state, arena);
        }

        assignTasks(session, arena);
        broadcast(session, "go", Map.of(), null);

        for (PlayerGameState state : players) {
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                continue;
            }
            if (state.getRole() == Role.IMPOSTOR) {
                String teammates = players.stream()
                        .filter(p -> p.getRole() == Role.IMPOSTOR && !p.getUuid().equals(state.getUuid()))
                        .map(PlayerGameState::getName)
                        .collect(Collectors.joining(", "));
                if (teammates.isBlank()) {
                    teammates = "none";
                }
                plugin.getMessageService().send(player, "role-impostor", Map.of("teammates", teammates));
                if (plugin.getConfig().getBoolean("impostor-glow-to-impostors", true)) {
                    for (PlayerGameState other : players) {
                        if (other.getRole() == Role.IMPOSTOR) {
                            Player mate = Bukkit.getPlayer(other.getUuid());
                            if (mate != null) {
                                mate.setGlowing(true);
                            }
                        }
                    }
                }
            } else {
                plugin.getMessageService().send(player, "role-crewmate", Map.of());
            }
        }
    }

    private void assignTasks(GameSession session, Arena arena) {
        List<Arena.TaskPoint> tasks = arena.getTasks();
        if (tasks.isEmpty()) {
            return;
        }
        int per = Math.min(tasks.size(), Math.max(1, plugin.getConfig().getInt("tasks-per-crewmate", 4)));
        for (PlayerGameState state : session.getPlayers().values()) {
            if (state.isImpostor()) {
                continue;
            }
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                indexes.add(i);
            }
            Collections.shuffle(indexes, ThreadLocalRandom.current());
            state.getAssignedTaskIndexes().addAll(indexes.subList(0, Math.min(per, indexes.size())));
        }
    }

    private void prepareLobbyPlayer(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void prepareMatchPlayer(Player player, PlayerGameState state, Arena arena) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().setChestplate(itemFactory.crewArmor(state.getColor()));
        player.getInventory().setItem(0, itemFactory.tablet());
        player.getInventory().setItem(1, itemFactory.report());
        if (state.getRole() == Role.IMPOSTOR) {
            player.getInventory().setItem(2, itemFactory.knife());
            player.getInventory().setItem(3, itemFactory.ventTool());
            player.getInventory().setItem(4, itemFactory.sabotage());
        }
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setCollidable(true);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void killPlayer(GameSession session, Player killer, Player victim,
                            PlayerGameState killerState, PlayerGameState victimState) {
        victimState.setRole(victimState.getRole().asGhost());
        killerState.setKillReadyAt(System.currentTimeMillis()
                + plugin.getConfig().getInt("kill-cooldown-seconds", 25) * 1000L);
        awardGems(killerState, plugin.getConfig().getInt("gems.kill", 20));

        plugin.getMessageService().send(killer, "you-killed", Map.of(
                "victim", victim.getName(),
                "cooldown", String.valueOf(plugin.getConfig().getInt("kill-cooldown-seconds", 25))
        ));
        plugin.getMessageService().send(victim, "you-died", Map.of());

        spawnBody(session, victim, victimState);
        turnGhost(victim, victimState);

        if (!checkWinConditions(session)) {
            // quiet kill — no global announce
        }
    }

    private void spawnBody(GameSession session, Player victim, PlayerGameState victimState) {
        Location loc = victim.getLocation().clone();
        GameSession.Body body = new GameSession.Body(victim.getUniqueId(), victim.getName(), loc);
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setInvisible(true);
            as.setMarker(false);
            as.setCustomNameVisible(true);
            as.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&c☠ " + victimState.getColor().taggedName(victim.getName())));
            as.getEquipment().setHelmet(playerHead(victim));
            as.setCollidable(false);
        });
        body.setStand(stand);
        session.getBodies().put(victim.getUniqueId(), body);
    }

    private ItemStack playerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        head.setItemMeta(meta);
        return head;
    }

    private void turnGhost(Player player, PlayerGameState state) {
        player.getInventory().clear();
        player.getInventory().setItem(0, itemFactory.tablet());
        player.setAllowFlight(plugin.getConfig().getBoolean("ghosts-fly", true));
        player.setFlying(player.getAllowFlight());
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, true));
        player.setGlowing(false);
    }

    private void startMeeting(GameSession session, Player caller, String victimName, boolean report) {
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        if (arena.isEmpty()) {
            return;
        }
        clearSabotage(session);
        clearBodies(session);

        if (report) {
            broadcast(session, "body-reported", Map.of(
                    "reporter", caller.getName(),
                    "victim", victimName == null ? "unknown" : victimName
            ), null);
        } else {
            broadcast(session, "emergency-called", Map.of("caller", caller.getName()), null);
        }

        Location cafeteria = arena.get().getCafeteria();
        for (PlayerGameState state : session.getPlayers().values()) {
            state.resetMeetingVote();
            state.setInVent(false);
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                continue;
            }
            player.closeInventory();
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            if (cafeteria != null) {
                player.teleport(cafeteria);
            }
            if (state.isAlive()) {
                player.setFlying(false);
                player.setAllowFlight(false);
                player.getInventory().setItem(8, itemFactory.voteBook());
            }
        }

        session.setState(GameSession.State.DISCUSSION);
        int discussion = plugin.getConfig().getInt("discussion-seconds", 30);
        session.setMeetingSecondsLeft(discussion);
        if (session.getMeetingTask() != null) {
            session.getMeetingTask().cancel();
        }
        broadcast(session, "discussion", Map.of("seconds", String.valueOf(discussion)), null);
        session.setMeetingTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int left = session.getMeetingSecondsLeft();
            if (session.getState() == GameSession.State.DISCUSSION) {
                if (left <= 0) {
                    beginVoting(session);
                    return;
                }
            } else if (session.getState() == GameSession.State.VOTING) {
                if (left <= 0) {
                    resolveVotes(session);
                    return;
                }
            } else {
                session.getMeetingTask().cancel();
                session.setMeetingTask(null);
                return;
            }
            session.setMeetingSecondsLeft(left - 1);
        }, 20L, 20L));
    }

    private void beginVoting(GameSession session) {
        session.setState(GameSession.State.VOTING);
        int voting = plugin.getConfig().getInt("voting-seconds", 45);
        session.setMeetingSecondsLeft(voting);
        broadcast(session, "voting", Map.of("seconds", String.valueOf(voting)), null);
        for (PlayerGameState state : session.getPlayers().values()) {
            if (!state.isAlive()) {
                continue;
            }
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player != null) {
                openVoteGui(player);
            }
        }
    }

    private void maybeResolveVotes(GameSession session) {
        boolean allVoted = session.getPlayers().values().stream()
                .filter(PlayerGameState::isAlive)
                .allMatch(PlayerGameState::hasVoted);
        if (allVoted) {
            resolveVotes(session);
        }
    }

    private void resolveVotes(GameSession session) {
        if (session.getMeetingTask() != null) {
            session.getMeetingTask().cancel();
            session.setMeetingTask(null);
        }

        Map<UUID, Integer> tallies = new HashMap<>();
        int skips = 0;
        for (PlayerGameState state : session.getPlayers().values()) {
            if (!state.isAlive() || !state.hasVoted()) {
                continue;
            }
            if (state.isSkipVote() || state.getVoteTarget() == null) {
                skips++;
            } else {
                tallies.merge(state.getVoteTarget(), 1, Integer::sum);
            }
        }

        UUID ejected = null;
        int best = 0;
        boolean tie = false;
        for (Map.Entry<UUID, Integer> entry : tallies.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                ejected = entry.getKey();
                tie = false;
            } else if (entry.getValue() == best) {
                tie = true;
            }
        }

        if (tie || ejected == null || best <= skips) {
            if (tie) {
                broadcast(session, "tie", Map.of(), null);
            } else {
                broadcast(session, "skipped", Map.of("votes", String.valueOf(skips)), null);
            }
        } else {
            PlayerGameState target = session.getPlayer(ejected);
            if (target != null && target.isAlive()) {
                boolean impostor = target.getRole() == Role.IMPOSTOR;
                if (session.isRevealRolesOnEject()) {
                    broadcast(session, "ejected", Map.of(
                            "player", target.getName(),
                            "role", target.getRole().display()
                    ), null);
                } else {
                    broadcast(session, "ejected-unknown", Map.of("player", target.getName()), null);
                }
                target.setRole(target.getRole().asGhost());
                Player online = Bukkit.getPlayer(ejected);
                if (online != null) {
                    turnGhost(online, target);
                }
                if (impostor) {
                    for (PlayerGameState voter : session.getPlayers().values()) {
                        if (voter.getVoteTarget() != null && voter.getVoteTarget().equals(ejected)) {
                            awardGems(voter, plugin.getConfig().getInt("gems.correct-eject", 35));
                        }
                    }
                }
            }
        }

        if (checkWinConditions(session)) {
            return;
        }
        resumePlay(session);
    }

    private void resumePlay(GameSession session) {
        Optional<Arena> arena = plugin.getArenaManager().get(session.getArenaName());
        session.setState(GameSession.State.PLAYING);
        List<Location> spawns = arena.map(Arena::getSpawns).orElse(List.of());
        int i = 0;
        for (PlayerGameState state : session.getPlayers().values()) {
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                continue;
            }
            player.closeInventory();
            if (!spawns.isEmpty()) {
                player.teleport(spawns.get(i++ % spawns.size()));
            }
            if (state.isAlive()) {
                prepareMatchPlayer(player, state, arena.orElse(null));
            } else {
                turnGhost(player, state);
            }
        }
    }

    private boolean checkWinConditions(GameSession session) {
        if (session.getState() == GameSession.State.ENDING) {
            return true;
        }
        long imps = session.aliveImpostors();
        long crew = session.aliveCrewmates();
        if (imps <= 0) {
            endGame(session, message("crewmates-win"));
            return true;
        }
        if (imps >= crew) {
            endGame(session, message("impostors-win"));
            return true;
        }
        return false;
    }

    private void endGame(GameSession session, String winnerMessage) {
        if (session.getState() == GameSession.State.ENDING) {
            return;
        }
        session.setState(GameSession.State.ENDING);
        session.cancelTasks();
        clearSabotage(session);
        clearBodies(session);

        boolean impostorWin = winnerMessage.toLowerCase(Locale.ROOT).contains("impostor")
                || winnerMessage.toLowerCase(Locale.ROOT).contains("reactor");
        int winGems = impostorWin
                ? plugin.getConfig().getInt("gems.impostor-win", 60)
                : plugin.getConfig().getInt("gems.crewmate-win", 50);
        int survive = plugin.getConfig().getInt("gems.survive", 25);

        broadcastRaw(session, winnerMessage);
        broadcast(session, "game-over", Map.of("winner", stripColor(winnerMessage)), null);

        for (PlayerGameState state : session.getPlayers().values()) {
            if (state.isAlive()) {
                awardGems(state, survive);
            }
            boolean winner = impostorWin ? state.isImpostor() : !state.isImpostor();
            if (winner) {
                awardGems(state, winGems);
            }
            depositPoints(state);
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player != null) {
                byPlayer.remove(player.getUniqueId());
                restorePlayer(player, state);
            }
        }

        byArena.remove(session.getArenaName());
    }

    private void depositPoints(PlayerGameState state) {
        if (state.getGems() <= 0) {
            return;
        }
        double rate = plugin.getConfig().getDouble("gems.points-per-gem", 1.0);
        int points = (int) Math.round(state.getGems() * rate);
        if (points <= 0) {
            return;
        }
        plugin.getPointsService().addPoints(Bukkit.getOfflinePlayer(state.getUuid()), points);
        Player player = Bukkit.getPlayer(state.getUuid());
        if (player != null) {
            plugin.getMessageService().send(player, "gems-earned", Map.of(
                    "gems", String.valueOf(state.getGems()),
                    "points", String.valueOf(points)
            ));
        }
    }

    private void awardGems(PlayerGameState state, int amount) {
        if (amount > 0) {
            state.addGems(amount);
        }
    }

    private void clearSabotage(GameSession session) {
        if (session.getSabotage() == GameSession.Sabotage.NONE) {
            return;
        }
        GameSession.Sabotage previous = session.getSabotage();
        session.setSabotage(GameSession.Sabotage.NONE);
        session.getReactorFixes().clear();
        if (session.getSabotageTask() != null) {
            session.getSabotageTask().cancel();
            session.setSabotageTask(null);
        }
        for (PlayerGameState state : session.getPlayers().values()) {
            Player player = Bukkit.getPlayer(state.getUuid());
            if (player == null) {
                continue;
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
        if (previous != GameSession.Sabotage.NONE) {
            broadcast(session, "sabotage-fixed", Map.of(), null);
        }
    }

    private void clearBodies(GameSession session) {
        for (GameSession.Body body : session.getBodies().values()) {
            if (body.getStand() != null && !body.getStand().isDead()) {
                body.getStand().remove();
            }
        }
        session.getBodies().clear();
    }

    private void clearBodiesFor(GameSession session, UUID victim) {
        GameSession.Body body = session.getBodies().remove(victim);
        if (body != null && body.getStand() != null) {
            body.getStand().remove();
        }
    }

    private void cleanupSession(GameSession session) {
        session.cancelTasks();
        clearBodies(session);
        byArena.remove(session.getArenaName());
    }

    private void restorePlayer(Player player, PlayerGameState state) {
        player.closeInventory();
        player.setGlowing(false);
        player.setCollidable(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        if (state != null) {
            state.restore(player);
        } else {
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        if (plugin.getConfig().getBoolean("teleport-on-end", true)) {
            Optional<Arena> arena = getByPlayer(player.getUniqueId())
                    .flatMap(s -> plugin.getArenaManager().get(s.getArenaName()));
            // player already removed from maps; use lobby from last known arena via config world spawn fallback
            Location bed = player.getRespawnLocation();
            if (bed != null) {
                player.teleport(bed);
            }
        }
    }

    private void startActionBar(GameSession session) {
        if (session.getActionBarTask() != null) {
            return;
        }
        session.setActionBarTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!byArena.containsKey(session.getArenaName())) {
                if (session.getActionBarTask() != null) {
                    session.getActionBarTask().cancel();
                    session.setActionBarTask(null);
                }
                return;
            }
            String template;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("count", String.valueOf(session.playerCount()));
            placeholders.put("max", String.valueOf(plugin.getArenaManager().maxPlayers()));
            placeholders.put("alive", String.valueOf(session.alivePlayers()));
            placeholders.put("imps", String.valueOf(session.aliveImpostors()));
            int done = crewTaskDone(session);
            int total = Math.max(1, crewTaskTotal(session));
            placeholders.put("tasks", String.valueOf((done * 100) / total));

            if (session.getState() == GameSession.State.DISCUSSION || session.getState() == GameSession.State.VOTING) {
                template = plugin.getConfig().getString("action-bar-meeting", "");
                placeholders.put("phase", session.getState() == GameSession.State.DISCUSSION ? "Discuss" : "Vote");
                placeholders.put("seconds", String.valueOf(session.getMeetingSecondsLeft()));
            } else if (session.getSabotage() != GameSession.Sabotage.NONE) {
                template = plugin.getConfig().getString("action-bar-sabotage", "");
                placeholders.put("sabotage", session.getSabotage().name());
                placeholders.put("seconds", String.valueOf(session.getSabotageSecondsLeft()));
            } else if (session.getState() == GameSession.State.PLAYING) {
                template = plugin.getConfig().getString("action-bar-playing", "");
            } else {
                template = plugin.getConfig().getString("action-bar-waiting", "");
            }

            Component bar = LegacyComponentSerializer.legacyAmpersand().deserialize(apply(template, placeholders));
            for (UUID id : session.getPlayers().keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendActionBar(bar);
                }
            }
        }, 10L, 10L));
    }

    private int crewTaskDone(GameSession session) {
        int done = 0;
        for (PlayerGameState state : session.getPlayers().values()) {
            if (state.isImpostor()) {
                continue;
            }
            done += state.getCompletedTaskIndexes().size();
        }
        return done;
    }

    private int crewTaskTotal(GameSession session) {
        int total = 0;
        for (PlayerGameState state : session.getPlayers().values()) {
            if (state.isImpostor()) {
                continue;
            }
            total += state.getAssignedTaskIndexes().size();
        }
        return total;
    }

    private void broadcast(GameSession session, String key, Map<String, String> placeholders, UUID exclude) {
        for (UUID id : session.getPlayers().keySet()) {
            if (exclude != null && exclude.equals(id)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                plugin.getMessageService().send(player, key, placeholders);
            }
        }
    }

    private void broadcastRaw(GameSession session, String message) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        for (UUID id : session.getPlayers().keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    private static String apply(String template, Map<String, String> placeholders) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private static String stripColor(String input) {
        return input.replaceAll("&[0-9a-fk-or]", "");
    }

    private static Location nearest(List<Location> locations, Location from, double radius) {
        Location best = null;
        double bestDist = radius * radius;
        for (Location location : locations) {
            if (!location.getWorld().equals(from.getWorld())) {
                continue;
            }
            double dist = location.distanceSquared(from);
            if (dist <= bestDist) {
                bestDist = dist;
                best = location;
            }
        }
        return best;
    }

    private static int nearestIndex(List<Location> locations, Location from) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < locations.size(); i++) {
            Location location = locations.get(i);
            if (!location.getWorld().equals(from.getWorld())) {
                continue;
            }
            double dist = location.distanceSquared(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private String message(String key) {
        return plugin.getConfig().getString("messages." + key, key);
    }
}
