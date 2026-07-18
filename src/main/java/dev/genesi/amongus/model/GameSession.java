package dev.genesi.amongus.model;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameSession {

    public enum State {
        WAITING,
        LOBBY_COUNTDOWN,
        START_COUNTDOWN,
        PLAYING,
        DISCUSSION,
        VOTING,
        ENDING
    }

    public enum Sabotage {
        NONE,
        LIGHTS,
        REACTOR
    }

    private final String arenaName;
    private final Map<UUID, PlayerGameState> players = new LinkedHashMap<>();
    private final Map<UUID, Body> bodies = new HashMap<>();
    private State state = State.WAITING;
    private Sabotage sabotage = Sabotage.NONE;
    private int lobbySecondsLeft;
    private int startSecondsLeft;
    private int meetingSecondsLeft;
    private int sabotageSecondsLeft;
    private final Set<UUID> reactorFixes = new HashSet<>();
    private BukkitTask lobbyTask;
    private BukkitTask startTask;
    private BukkitTask meetingTask;
    private BukkitTask sabotageTask;
    private BukkitTask actionBarTask;
    private boolean revealRolesOnEject = true;

    public GameSession(String arenaName) {
        this.arenaName = arenaName.toLowerCase();
    }

    public String getArenaName() {
        return arenaName;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Sabotage getSabotage() {
        return sabotage;
    }

    public void setSabotage(Sabotage sabotage) {
        this.sabotage = sabotage;
    }

    public Map<UUID, PlayerGameState> getPlayers() {
        return players;
    }

    public PlayerGameState getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public boolean contains(UUID uuid) {
        return players.containsKey(uuid);
    }

    public int playerCount() {
        return players.size();
    }

    public Map<UUID, Body> getBodies() {
        return bodies;
    }

    public int getLobbySecondsLeft() {
        return lobbySecondsLeft;
    }

    public void setLobbySecondsLeft(int lobbySecondsLeft) {
        this.lobbySecondsLeft = lobbySecondsLeft;
    }

    public int getStartSecondsLeft() {
        return startSecondsLeft;
    }

    public void setStartSecondsLeft(int startSecondsLeft) {
        this.startSecondsLeft = startSecondsLeft;
    }

    public int getMeetingSecondsLeft() {
        return meetingSecondsLeft;
    }

    public void setMeetingSecondsLeft(int meetingSecondsLeft) {
        this.meetingSecondsLeft = meetingSecondsLeft;
    }

    public int getSabotageSecondsLeft() {
        return sabotageSecondsLeft;
    }

    public void setSabotageSecondsLeft(int sabotageSecondsLeft) {
        this.sabotageSecondsLeft = sabotageSecondsLeft;
    }

    public Set<UUID> getReactorFixes() {
        return reactorFixes;
    }

    public boolean isRevealRolesOnEject() {
        return revealRolesOnEject;
    }

    public void setRevealRolesOnEject(boolean revealRolesOnEject) {
        this.revealRolesOnEject = revealRolesOnEject;
    }

    public BukkitTask getLobbyTask() {
        return lobbyTask;
    }

    public void setLobbyTask(BukkitTask lobbyTask) {
        this.lobbyTask = lobbyTask;
    }

    public BukkitTask getStartTask() {
        return startTask;
    }

    public void setStartTask(BukkitTask startTask) {
        this.startTask = startTask;
    }

    public BukkitTask getMeetingTask() {
        return meetingTask;
    }

    public void setMeetingTask(BukkitTask meetingTask) {
        this.meetingTask = meetingTask;
    }

    public BukkitTask getSabotageTask() {
        return sabotageTask;
    }

    public void setSabotageTask(BukkitTask sabotageTask) {
        this.sabotageTask = sabotageTask;
    }

    public BukkitTask getActionBarTask() {
        return actionBarTask;
    }

    public void setActionBarTask(BukkitTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }

    public long aliveCrewmates() {
        return players.values().stream().filter(p -> p.getRole() == Role.CREWMATE).count();
    }

    public long aliveImpostors() {
        return players.values().stream().filter(p -> p.getRole() == Role.IMPOSTOR).count();
    }

    public long alivePlayers() {
        return players.values().stream().filter(PlayerGameState::isAlive).count();
    }

    public void cancelTasks() {
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }
        if (meetingTask != null) {
            meetingTask.cancel();
            meetingTask = null;
        }
        if (sabotageTask != null) {
            sabotageTask.cancel();
            sabotageTask = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    public static final class Body {
        private final UUID victimId;
        private final String victimName;
        private final Location location;
        private ArmorStand stand;

        public Body(UUID victimId, String victimName, Location location) {
            this.victimId = victimId;
            this.victimName = victimName;
            this.location = location.clone();
        }

        public UUID getVictimId() {
            return victimId;
        }

        public String getVictimName() {
            return victimName;
        }

        public Location getLocation() {
            return location.clone();
        }

        public ArmorStand getStand() {
            return stand;
        }

        public void setStand(ArmorStand stand) {
            this.stand = stand;
        }
    }
}
