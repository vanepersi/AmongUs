package dev.genesi.amongus.model;

public enum Role {
    CREWMATE,
    IMPOSTOR,
    GHOST_CREWMATE,
    GHOST_IMPOSTOR;

    public boolean isImpostor() {
        return this == IMPOSTOR || this == GHOST_IMPOSTOR;
    }

    public boolean isAlive() {
        return this == CREWMATE || this == IMPOSTOR;
    }

    public boolean isGhost() {
        return this == GHOST_CREWMATE || this == GHOST_IMPOSTOR;
    }

    public Role asGhost() {
        return switch (this) {
            case CREWMATE, GHOST_CREWMATE -> GHOST_CREWMATE;
            case IMPOSTOR, GHOST_IMPOSTOR -> GHOST_IMPOSTOR;
        };
    }

    public String display() {
        return switch (this) {
            case CREWMATE -> "Crewmate";
            case IMPOSTOR -> "Impostor";
            case GHOST_CREWMATE -> "Ghost Crewmate";
            case GHOST_IMPOSTOR -> "Ghost Impostor";
        };
    }
}
