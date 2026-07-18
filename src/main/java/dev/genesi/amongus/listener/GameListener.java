package dev.genesi.amongus.listener;

import dev.genesi.amongus.AmongUsPlugin;
import dev.genesi.amongus.model.GameSession;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class GameListener implements Listener {

    private final AmongUsPlugin plugin;

    public GameListener(AmongUsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leave(event.getPlayer(), false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        GameSession.State state = session.get().getState();
        if (state != GameSession.State.PLAYING) {
            event.setCancelled(true);
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onKillHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(attacker.getUniqueId());
        if (session.isEmpty() || session.get().getState() != GameSession.State.PLAYING) {
            return;
        }
        if (plugin.getGameManager().getByPlayer(victim.getUniqueId()).map(s -> s != session.get()).orElse(true)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        event.setDamage(0);
        plugin.getGameManager().handleKillAttempt(attacker, victim);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.getGameManager().getByPlayer(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        GameSession.State state = session.get().getState();
        if (state == GameSession.State.DISCUSSION || state == GameSession.State.VOTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }

        var item = event.getItem();
        var factory = plugin.getGameManager().getItemFactory();
        GameSession.State state = session.get().getState();

        if (item != null && factory.isKind(item, "vote") && state == GameSession.State.VOTING) {
            event.setCancelled(true);
            plugin.getGameManager().openVoteGui(player);
            return;
        }

        if (state != GameSession.State.PLAYING) {
            if (item != null) {
                event.setCancelled(true);
            }
            return;
        }

        if (item != null) {
            if (factory.isKind(item, "tablet")) {
                event.setCancelled(true);
                plugin.getGameManager().handleTaskUse(player);
                return;
            }
            if (factory.isKind(item, "report")) {
                event.setCancelled(true);
                plugin.getGameManager().handleReport(player);
                return;
            }
            if (factory.isKind(item, "vent")) {
                event.setCancelled(true);
                plugin.getGameManager().handleVent(player, player.isSneaking());
                return;
            }
            if (factory.isKind(item, "sabotage")) {
                event.setCancelled(true);
                plugin.getMessageService().sendRaw(player, "&cSabotage: &e/amongus sabotage lights &7or &e/amongus sabotage reactor");
                return;
            }
        }

        // Emergency button / sabotage fix via block click
        if (event.getClickedBlock() != null) {
            plugin.getGameManager().handleEmergency(player);
            plugin.getGameManager().handleFixInteract(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBodyClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(event.getPlayer().getUniqueId());
        if (session.isEmpty() || session.get().getState() != GameSession.State.PLAYING) {
            return;
        }
        event.setCancelled(true);
        plugin.getGameManager().handleReport(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<GameSession> session = plugin.getGameManager().getByPlayer(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (title.toLowerCase().contains("vote")) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                plugin.getGameManager().handleVoteClick(player, event.getSlot(), event.getView().getTopInventory());
            }
            return;
        }
        if (session.get().getState() != GameSession.State.WAITING
                && session.get().getState() != GameSession.State.LOBBY_COUNTDOWN) {
            event.setCancelled(true);
        }
    }
}
