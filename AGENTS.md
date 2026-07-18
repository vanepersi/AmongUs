# Agent handoff — AmongUs

Read this first in any new Cursor session before changing this plugin or connecting it to the Genesi website / custom API.

## What this repo is

Among Us–style social deduction for Paper: crewmates complete Minecraft-flavored tasks while impostors kill, vent, and sabotage. Meetings use a cafeteria freeze + head-vote GUI.

| Item | Value |
|------|--------|
| Bukkit `name` | `AmongUs` |
| Main class | `dev.genesi.amongus.AmongUsPlugin` |
| Paper | `26.1.2+` |
| Data folder | `plugins/GenesiCore/games/AmongUs/` (`config.yml`, `arenas.yml`, `points.yml`) |
| Player / admin commands | `/amongus` (`au`, `among`, `sus`), `/amongusadmin` (`auadmin`, `aua`) |

## Non-negotiables

- Keep runtime data under **`plugins/GenesiCore/games/AmongUs/`** (via GenesiGamesApi).
- Do not relocate jars out of `plugins/`; only data folders live under GenesiCore/games.
- Preserve existing arena/config YAML — never wipe Club data to "test defaults".

## Game loop (creative twists)

- Dyed leather crew colors; knife kills leave ArmorStand bodies with player heads
- Task tablet near arena task points (wires, download, fuel, scan, divert, clean, garbage, align)
- Impostor vents + sabotage (lights blindness / reactor meltdown)
- Emergency button + body report → discussion → voting book GUI
- Ghosts can finish tasks; win by tasks / ejections / outnumber / reactor fail

## Integration hooks (today)

Local points via `PointsService` (`points.yml`); Vault entry fees.

## Genesi API / website (future)

1. Prefer a **companion bridge** (HTTP ↔ Paper) or signed console/WebSender commands.
2. Use **idempotent** grant/purchase IDs for economy / points.
3. Soft-depend / respect **GenesiCore** inventory/limbo sync.
4. Never commit SFTP, RCON, WebSender, or panel secrets.

## Shared stack

- Depends on **GenesiGamesApi** (`dev.genesi.games.*`) for data-folder redirect and shared helpers.
- Club SFTP: FileZilla site **Genesi / Club**.

## Build / deploy

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./gradlew jar
# jar → Club plugins/ via scripts/deploy-club.sh or FileZilla Genesi / Club
```

## Related

- Shared library + convention: `/Users/admin/GenesiCore` (`games-api`, `AGENTS.md`)
- Sibling minigames follow the same `GenesiCore/games/<name>/` layout
