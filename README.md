# AmongUs

Among Us–style social deduction minigame for **Paper 26.1.2+**, built for the Genesiverse Club stack (`GenesiGamesApi` → data under `plugins/GenesiCore/games/AmongUs/`).

## Features

- **Crewmates vs Impostors** with scaled impostor count
- **Minecraft tasks** — fix wires, download data, fuel engines, submit scan, divert power, clean vents, empty garbage, align engine
- **Kills** leave ArmorStand bodies with player heads; **report** or hit the **emergency button**
- **Meetings** freeze players in the cafeteria with a **player-head voting GUI**
- **Vents** for impostors (sneak+use to hop)
- **Sabotage** — lights (blindness) or reactor meltdown (everyone must fix)
- **Ghosts** can still complete tasks; arcade **points** via GenesiGamesApi

## Commands

| Command | Description |
|---------|-------------|
| `/amongus join <arena>` | Join a lobby |
| `/amongus leave` | Leave |
| `/amongus start` | Start when enough players |
| `/amongus report` | Report a nearby body |
| `/amongus sabotage <lights\|reactor>` | Impostor sabotage |
| `/amongus tasks` | List your tasks |
| `/amongusadmin …` | Arena setup, force start/stop, points |

## Arena setup

```text
/amongusadmin create skeld
/amongusadmin setlobby skeld
/amongusadmin setcafeteria skeld
/amongusadmin addspawn skeld          # repeat
/amongusadmin setbutton skeld
/amongusadmin addtask skeld FIX_WIRES # repeat with types
/amongusadmin addvent skeld           # at least one
/amongusadmin setelectrical skeld     # for lights sabotage
/amongusadmin addreactor skeld        # for reactor sabotage
```

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./gradlew jar
# → build/libs/AmongUs-1.0.0.jar
```

Requires **GenesiGamesApi** on the server (`depend`). Soft-depends Vault / GenesiCore.

## License

Built for Genesiverse Club. Among Us is a trademark of Innersloth — this is an unofficial Minecraft minigame inspired by the concept.
