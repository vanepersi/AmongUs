package dev.genesi.amongus.model;

import org.bukkit.Color;

public enum CrewColor {
    RED(Color.fromRGB(197, 17, 17), "&c"),
    BLUE(Color.fromRGB(19, 46, 209), "&9"),
    GREEN(Color.fromRGB(17, 127, 45), "&a"),
    PINK(Color.fromRGB(237, 84, 186), "&d"),
    ORANGE(Color.fromRGB(239, 125, 13), "&6"),
    YELLOW(Color.fromRGB(246, 246, 87), "&e"),
    BLACK(Color.fromRGB(62, 71, 78), "&8"),
    WHITE(Color.fromRGB(214, 224, 240), "&f"),
    PURPLE(Color.fromRGB(107, 47, 187), "&5"),
    CYAN(Color.fromRGB(56, 254, 220), "&b");

    private final Color leather;
    private final String chat;

    CrewColor(Color leather, String chat) {
        this.leather = leather;
        this.chat = chat;
    }

    public Color leather() {
        return leather;
    }

    public String chat() {
        return chat;
    }

    public String taggedName(String name) {
        return chat + name;
    }
}
