package dev.genesi.amongus;

import dev.genesi.amongus.command.AmongUsAdminCommand;
import dev.genesi.amongus.command.AmongUsCommand;
import dev.genesi.amongus.listener.GameListener;
import dev.genesi.amongus.manager.ArenaManager;
import dev.genesi.amongus.manager.GameManager;
import dev.genesi.games.GenesiGamePlugin;
import dev.genesi.games.economy.EconomyService;
import dev.genesi.games.economy.PointsService;
import dev.genesi.games.message.MessageService;

public final class AmongUsPlugin extends GenesiGamePlugin {

    private ArenaManager arenaManager;
    private GameManager gameManager;
    private PointsService pointsService;
    private EconomyService economyService;
    private MessageService messageService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messageService = new MessageService(this, "&8[&cAmong Us&8] &r");
        this.economyService = new EconomyService(this, "amongus.bypass.fee");
        this.pointsService = new PointsService(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);

        arenaManager.load();
        pointsService.load();
        economyService.hook();

        AmongUsCommand playerCommand = new AmongUsCommand(this);
        AmongUsAdminCommand adminCommand = new AmongUsAdminCommand(this);
        getCommand("amongus").setExecutor(playerCommand);
        getCommand("amongus").setTabCompleter(playerCommand);
        getCommand("amongusadmin").setExecutor(adminCommand);
        getCommand("amongusadmin").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("AmongUs enabled. Economy: " + economyService.describe());
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (arenaManager != null) {
            arenaManager.save();
        }
        if (pointsService != null) {
            pointsService.save();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        arenaManager.load();
        pointsService.load();
        economyService.hook();
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PointsService getPointsService() {
        return pointsService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public MessageService getMessageService() {
        return messageService;
    }
}
