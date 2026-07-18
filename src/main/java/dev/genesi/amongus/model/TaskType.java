package dev.genesi.amongus.model;

import org.bukkit.Material;

public enum TaskType {
    FIX_WIRES("Fix Wires", Material.COPPER_INGOT),
    DOWNLOAD_DATA("Download Data", Material.AMETHYST_SHARD),
    FUEL_ENGINES("Fuel Engines", Material.COAL),
    SUBMIT_SCAN("Submit Scan", Material.ENDER_EYE),
    DIVERT_POWER("Divert Power", Material.REDSTONE_TORCH),
    CLEAN_VENTS("Clean Vents", Material.BRUSH),
    EMPTY_GARBAGE("Empty Garbage", Material.COMPOSTER),
    ALIGN_ENGINE("Align Engine", Material.COMPASS);

    private final String display;
    private final Material icon;

    TaskType(String display, Material icon) {
        this.display = display;
        this.icon = icon;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }
}
