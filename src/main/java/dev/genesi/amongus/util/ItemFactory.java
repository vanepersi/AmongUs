package dev.genesi.amongus.util;

import dev.genesi.amongus.AmongUsPlugin;
import dev.genesi.amongus.model.CrewColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.List;

public final class ItemFactory {

    public static final String KEY_KIND = "amongus_kind";

    private final AmongUsPlugin plugin;
    private final NamespacedKey kindKey;

    public ItemFactory(AmongUsPlugin plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, KEY_KIND);
    }

    public ItemStack knife() {
        return tagged(Material.NETHERITE_SWORD, "&c&lKnife", List.of("&7Left-click a crewmate to kill", "&8Cooldown applies"), "knife");
    }

    public ItemStack tablet() {
        return tagged(Material.BOOK, "&b&lTask Tablet", List.of("&7Right-click near a task to complete it", "&7Shows your assigned tasks"), "tablet");
    }

    public ItemStack report() {
        return tagged(Material.BELL, "&6&lReport", List.of("&7Right-click a body or use near one"), "report");
    }

    public ItemStack ventTool() {
        return tagged(Material.IRON_TRAPDOOR, "&8&lVent", List.of("&7Right-click near a vent to enter/exit", "&7Sneak+right-click to hop vents"), "vent");
    }

    public ItemStack sabotage() {
        return tagged(Material.REDSTONE, "&c&lSabotage", List.of("&7Right-click to open sabotage options"), "sabotage");
    }

    public ItemStack voteBook() {
        return tagged(Material.WRITABLE_BOOK, "&e&lVoting Book", List.of("&7Right-click to cast your vote"), "vote");
    }

    public ItemStack crewArmor(CrewColor color) {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
        meta.setColor(color.leather());
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(color.chat() + "&lCrew Suit"));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, "suit");
        chest.setItemMeta(meta);
        return chest;
    }

    public boolean isKind(ItemStack stack, String kind) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        return kind.equalsIgnoreCase(value);
    }

    private ItemStack tagged(Material material, String name, List<String> lore, String kind) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        meta.lore(lore.stream().map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line)).toList());
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        stack.setItemMeta(meta);
        return stack;
    }
}
