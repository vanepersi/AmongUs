package dev.genesi.amongus.model;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerGameState {

    private final UUID uuid;
    private final String name;
    private Role role = Role.CREWMATE;
    private CrewColor color = CrewColor.RED;
    private boolean feePaid;
    private boolean voted;
    private UUID voteTarget;
    private boolean skipVote;
    private boolean inVent;
    private long killReadyAt;
    private long emergencyReadyAt;
    private long sabotageReadyAt;
    private int gems;
    private final Set<Integer> completedTaskIndexes = new LinkedHashSet<>();
    private final List<Integer> assignedTaskIndexes = new ArrayList<>();

    private ItemStack[] inventory;
    private ItemStack[] armor;
    private Collection<PotionEffect> effects;
    private GameMode gameMode;
    private double health;
    private int food;
    private float saturation;
    private boolean allowFlight;
    private boolean flying;
    private float exp;
    private int level;

    public PlayerGameState(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public CrewColor getColor() {
        return color;
    }

    public void setColor(CrewColor color) {
        this.color = color;
    }

    public boolean isFeePaid() {
        return feePaid;
    }

    public void setFeePaid(boolean feePaid) {
        this.feePaid = feePaid;
    }

    public boolean hasVoted() {
        return voted;
    }

    public void setVoted(boolean voted) {
        this.voted = voted;
    }

    public UUID getVoteTarget() {
        return voteTarget;
    }

    public void setVoteTarget(UUID voteTarget) {
        this.voteTarget = voteTarget;
    }

    public boolean isSkipVote() {
        return skipVote;
    }

    public void setSkipVote(boolean skipVote) {
        this.skipVote = skipVote;
    }

    public boolean isInVent() {
        return inVent;
    }

    public void setInVent(boolean inVent) {
        this.inVent = inVent;
    }

    public long getKillReadyAt() {
        return killReadyAt;
    }

    public void setKillReadyAt(long killReadyAt) {
        this.killReadyAt = killReadyAt;
    }

    public long getEmergencyReadyAt() {
        return emergencyReadyAt;
    }

    public void setEmergencyReadyAt(long emergencyReadyAt) {
        this.emergencyReadyAt = emergencyReadyAt;
    }

    public long getSabotageReadyAt() {
        return sabotageReadyAt;
    }

    public void setSabotageReadyAt(long sabotageReadyAt) {
        this.sabotageReadyAt = sabotageReadyAt;
    }

    public int getGems() {
        return gems;
    }

    public void addGems(int amount) {
        this.gems += Math.max(0, amount);
    }

    public Set<Integer> getCompletedTaskIndexes() {
        return completedTaskIndexes;
    }

    public List<Integer> getAssignedTaskIndexes() {
        return assignedTaskIndexes;
    }

    public boolean isAlive() {
        return role.isAlive();
    }

    public boolean isImpostor() {
        return role.isImpostor();
    }

    public boolean isGhost() {
        return role.isGhost();
    }

    public void resetMeetingVote() {
        voted = false;
        voteTarget = null;
        skipVote = false;
    }

    public void snapshot(Player player) {
        inventory = player.getInventory().getContents().clone();
        armor = player.getInventory().getArmorContents().clone();
        effects = List.copyOf(player.getActivePotionEffects());
        gameMode = player.getGameMode();
        health = player.getHealth();
        food = player.getFoodLevel();
        saturation = player.getSaturation();
        allowFlight = player.getAllowFlight();
        flying = player.isFlying();
        exp = player.getExp();
        level = player.getLevel();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        if (inventory != null) {
            player.getInventory().setContents(inventory);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (effects != null) {
            player.addPotionEffects(effects);
        }
        if (gameMode != null) {
            player.setGameMode(gameMode);
        }
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(Math.min(health, max));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying && allowFlight);
        player.setExp(exp);
        player.setLevel(level);
        player.setFireTicks(0);
        player.setGlowing(false);
    }
}
