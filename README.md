<img width="1200" height="800" alt="rsmap" src="rsmap.png" />

# RSC Single Player

A fully self-contained RuneScape Classic client that runs entirely offline — no server, no database, no internet required. Built for preservation, nostalgia, and experimentation.

**Version 2.4.8** · Java 17+ · GPL v3

---

## Table of Contents
- [Features](#features)
- [Quick Start](#quick-start)
- [Commands](#commands)
- [Bot System](#bot-system)
- [Hardcore Mode](#hardcore-mode)
- [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Administrator Account](#administrator-account)
- [Media](#media)
- [FAQ](#faq)
- [License](#license)

---

## Features

- **Single-process** — everything runs in one JVM, no external dependencies
- **All 50 quests** playable
- **18-skill bot system** with auto-banking and combat support
- **Resizable UI** — drag to any window size
- **Batched skill actions** — woodcutting, mining, cooking, etc. use a tick-based batch system
- **8x XP multiplier** (configurable in source)
- **Hardcore mode** — death permanently deletes your save
- **Multi-account** — create as many characters as you like
- **Full music** — 55 MIDI tracks mapped to game regions

### Quality of Life
- `::bank` opens your bank from anywhere
- `::tele` teleports to named locations or coordinates
- `::stuck` unsticks your character
- Right-click item swapping in the bank interface
- Bob's Axes stocks hatchets (Bronze → Rune) and pickaxes (Bronze → Rune)

---

## Quick Start

**Requirements:** Java 17 or newer

1. Download or clone the repository:
   ```bash
   git clone https://github.com/theKennethy/Single-RSC.git
   cd Single-RSC
   ```
2. Launch the game:
   - **Linux / macOS:** `./run.sh`
   - **Windows:** `run.bat`
   - **Or:** `java -cp "rsc.jar:lib/*" org.nemotech.rsc.Main`
3. Click **New User**, create an account, and log in.

> **Tip:** Create a user named `root` for admin privileges.

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `::help` | Show help message |
| `::bank` | Open bank anywhere |
| `::stuck` | Unstick your character |
| `::pos` | Show current coordinates |
| `::toggleroofs` | Toggle roof rendering on/off |
| `::mapedit` | Open the real-time map editor |

### Admin Commands (user: `root`)

| Command | Description |
|---------|-------------|
| `::tele <location>` | Teleport to a named location |
| `::tele <x> <y>` | Teleport to coordinates |
| `::town <location>` | Teleport to a town |
| `::item <id> [amount]` | Spawn an item |
| `::npc <id>` | Spawn an NPC |
| `::object <id> [dir]` | Spawn an object |
| `::set <skill> <level>` | Set a skill level |
| `::addbank <id> [amount]` | Add item to bank |
| `::removebank <id> [amount]` | Remove item from bank |
| `::quest <id> <stage>` | Set quest stage |
| `::find <entity> <string>` | Search for entities by name |
| `::stresstest <type> <radius>` | Stress test entities |
| `::debugobjects [radius]` | List nearby game objects |
| Mini-map right-click | Teleport to clicked location |

---

## Bot System

A built-in bot framework that automates training for all 18 skills. Bots handle banking, eating, and resource management automatically. They stop cleanly when supplies run out or after repeated failures.

### Bot Management

| Command | Description |
|---------|-------------|
| `::bot list` | List all registered bots |
| `::bot start <name>` | Start a bot by name |
| `::bot stop [name]` | Stop a bot (or all bots) |
| `::bot pause` | Pause / resume the active bot |
| `::bot status` | Show status of all bots |
| `::botarea <location>` | Set bot working area by name |
| `::botarea <x1> <x2> <y1> <y2>` | Set bot working area by coordinates |
| `::botarea clear` | Clear bot area bounds |

### Gathering Bots

| Command | Skill | Details |
|---------|-------|---------|
| `::woodcut [type]` | Woodcutting | normal, oak, willow, maple, yew, magic |
| `::fish [type]` | Fishing | net, fly, cage, harpoon, shark |
| `::mine [type]` | Mining | copper, tin, iron, coal, gold, mith, addy, rune |

### Combat Bots

| Command | Skill | Details |
|---------|-------|---------|
| `::combat [npc]` | Attack/Strength/Defence | Melee combat with auto-looting |
| `::ranged [npc]` | Ranged | Ranged combat training |
| `::magic [npc]` | Magic | Casts combat spells on NPCs |

### Production Bots

| Command | Skill | Details |
|---------|-------|---------|
| `::cook` | Cooking | Cooks raw food on ranges |
| `::fm` | Firemaking | Burns logs with tinderbox |
| `::smith` | Smithing | Smiths bars at anvils |
| `::fletch` | Fletching | Makes bows and arrows from logs |
| `::craft [mode]` | Crafting | leather, spinning, pottery |
| `::herblaw [mode]` | Herblaw | identify herbs, make potions |

### Support Bots

| Command | Skill | Details |
|---------|-------|---------|
| `::agility [course]` | Agility | gnome, barb, wild |
| `::thieve [target]` | Thieving | Pickpockets NPCs |
| `::prayer` | Prayer | Buries bones from inventory |

### Bot Behavior
- **Auto-banking** — gathering bots walk to the nearest bank when inventory is full
- **Auto-eating** — combat bots eat food when HP drops low
- **Clean shutdown** — bots stop themselves when out of supplies or after 3+ consecutive banking failures
- **Statistics** — track items collected/processed and XP gained

### Examples
```
::woodcut willow       Cut willow trees, bank logs
::fish lobster         Catch lobsters, bank them
::mine iron            Mine iron ore, bank it
::combat goblin        Fight goblins, eat food, loot drops
::agility gnome        Run the Gnome Agility Course
::cook                 Cook raw food on a nearby range
::prayer               Bury all bones in inventory
::craft leather        Craft leather items
::herblaw identify     Identify unidentified herbs
```

---

## Hardcore Mode

- Toggle when creating a new account (check the Hardcore box)
- **On death:** your save file is permanently deleted and the client closes
- Logging in again starts a fresh character
- Back up your save file manually if you want a safety net

---

## Building from Source

**Requirements:** Java 17+, `lib/gson-2.6.2.jar` (included)

```bash
BUILD_DIR="build" && \
rm -rf "$BUILD_DIR" && \
mkdir -p "$BUILD_DIR" && \
find src -name '*.java' -print0 | xargs -0 javac -source 17 -target 17 -cp lib/gson-2.6.2.jar -d "$BUILD_DIR" && \
jar cfm "rsc.jar" META-INF/MANIFEST.MF -C "$BUILD_DIR" . && \
rm -rf "$BUILD_DIR"
```

This produces `rsc.jar` in the project root. The build command is also saved in `compile.txt`.

---

## Project Structure

```
Single-RSC/
├── rsc.jar                  # Prebuilt game client
├── run.sh / run.bat         # Launch scripts
├── compile.txt              # Build command reference
├── cache/
│   ├── audio/music/         # 55 MIDI music tracks
│   ├── audio/sounds/        # Sound effects
│   ├── data/                # Game definitions (JSON)
│   │   ├── item_def.json
│   │   ├── npc_def.json
│   │   ├── object_def.json
│   │   └── ...
│   ├── jags/                # Jagex cache archives
│   └── players/             # Player save files
├── lib/
│   └── gson-2.6.2.jar       # JSON library dependency
├── src/org/nemotech/rsc/
│   ├── Main.java            # Entry point
│   ├── Constants.java       # Configuration values
│   ├── bot/                 # Bot framework
│   │   ├── Bot.java         # Abstract base class
│   │   ├── BotAPI.java      # 100+ game interaction methods
│   │   ├── BotManager.java  # Lifecycle management
│   │   └── scripts/         # 15 skill bot implementations
│   ├── client/              # Client rendering and input
│   ├── core/                # Game engine core
│   ├── event/               # Event system
│   ├── model/               # Game entities and world model
│   └── ...
└── META-INF/MANIFEST.MF     # JAR manifest
```

---

## Administrator Account

Create a user named exactly **`root`** (case-sensitive) to unlock:
- All admin `::` commands (item spawning, teleportation, skill setting, etc.)
- Mini-map right-click teleportation
- Debug and stress-testing tools

---

## Media

![Homepage Banner](HomepageBanner.png "Homepage Banner")
![Login Screen](login-screen.png "Login Screen")
![Quest Tab](quest-tab.png "Quest Tab")
![Tutorial](tutorial.png "Tutorial")

---

## FAQ

**Q: Does this connect to any external server?**
A: No. Everything runs locally in a single process.

**Q: What Java version do I need?**
A: Java 17 or newer.

**Q: Can I resize the window?**
A: Yes, the UI scales dynamically.

**Q: How do I restore a Hardcore character after death?**
A: You can't — that's the point. Back up your save file beforehand if you want a safety net.

**Q: Can I change XP rates?**
A: Edit `EXPERIENCE_MULTIPLIER` in `Constants.java` and rebuild (default is 8x).

**Q: Where are save files stored?**
A: In the `saves/` directory (created at runtime in the working directory).

---

## Disclaimer

This project is a preservation and educational single-player reimplementation. All original game assets, names, and concepts belong to their respective owners. Use responsibly and in accordance with applicable laws.

---

## License

GPL v3.0 — see [LICENSE](LICENSE) or https://www.gnu.org/licenses/gpl-3.0.txt
