[README.md](https://github.com/user-attachments/files/33777310/README.md)

I made this readme with the help of ai, since i dont really know how to make a readme
# Nomnom Client

Nomnom Client is a **client-side Fabric mod for Minecraft 1.21.11**, written in Java. It uses a modular system for combat, movement, rendering, and client-quality-of-life features, with configurable module settings, keybinds, chat commands, friends, and JSON-based configuration profiles.

## Features

### Combat

- **AutoCrystal** — configurable automatic end-crystal placement and breaking, with placement/break ranges and crystal restocking.
- **AutoTotem** — moves a Totem of Undying to the offhand when the configured health threshold is reached.
- **KillAura** — attacks nearby eligible entities, with options for target types, range, rotation, and attack cooldown. Registered friends are excluded from targeting.

### Movement

- **NoFall** — attempts to prevent fall damage after a configurable fall distance.
- **Speed** — configurable movement speed with `STRAFE` and `BHOP` modes.
- **Step** — configurable step height.
- **Velocity** — controls selected knockback and entity-push behaviors.

### Rendering

- **ESP** — draws configurable 2D boxes around selected entities.
- **Fullbright** — improves visibility using night vision.
- **Heaven** — experimental renderer-related module; behavior may change.
- **Nametags** — displays entity names and optionally health and distance.
- **NoRender** — independently hides selected visual effects, including fire overlay, hurt camera, weather, particles, vignette, and other elements.
- **Tracers** — draws lines toward selected entities.

### Miscellaneous and client

- **AutoReconnect** — attempts to reconnect to the last server after a disconnect, with a configurable delay.
- **FastPlace** — reduces the delay between item uses and block placements.
- **ClickGui** — opens the module GUI. Its default keybind is **Right Shift**.
- **Notifications** — displays chat notifications when modules are enabled or disabled.

Modules are grouped into the categories **Combat**, **Movement**, **Render**, **Misc**, and **Client**. Settings and available options vary by module.

## Requirements

The project configuration currently specifies:

- Minecraft **1.21.11**
- Java **21**
- Fabric Loader **0.19.5**
- Fabric API **0.141.6+1.21.11**

Use the dependency versions declared in [`gradle.properties`](gradle.properties) when building the project.

## Build from source

Install a Java 21 JDK, then clone the repository and use the included Gradle wrapper:

```bash
git clone https://github.com/KingHappyCat/nomnomclient.git
cd nomnomclient
./gradlew build
```

The built artifacts are placed in `build/libs/`.

To launch the Fabric development client:

```bash
./gradlew runClient
```

On Windows, use `gradlew.bat build` or `gradlew.bat runClient` instead.

## Installation

1. Install Minecraft 1.21.11 with Fabric Loader.
2. Install a compatible Fabric API version.
3. Build the project with `./gradlew build`, or use a release JAR.
4. Copy the appropriate mod JAR from `build/libs/` into the `mods` folder of the Minecraft game directory used by your Fabric profile.
5. Launch the Fabric profile.

Because this is a work-in-progress project, a successful build does not guarantee that every module has been fully tested in-game.

## Using Nomnom Client

### Click GUI

Press **Right Shift** to open the Click GUI. Modules can also be controlled through the client command system.

### Commands

The default command prefix is `+`. The prefix can be changed with `+prefix <new-prefix>`.

| Command | Purpose |
| --- | --- |
| `+help` | Lists the available commands. |
| `+modules` | Lists registered modules and their enabled state. |
| `+toggle <module>` | Enables or disables a module. |
| `+bind <module>` | Starts key capture for a module. |
| `+bind <module> <key>` | Assigns a keybind directly. |
| `+prefix <prefix>` | Changes the command prefix. |
| `+friend add <player>` | Adds a player to the friends list. |
| `+friend del <player>` | Removes a player from the friends list. |
| `+friend list` | Lists registered friends. |
| `+friend clear` | Clears the friends list. |
| `+config save <name>` | Saves the current module configuration under a name. |
| `+config load <name>` | Loads a named module configuration. |
| `+config list` | Lists available module configurations. |
| `+config del <name>` | Deletes a named module configuration. |
| `+message <text>` | Sends a message to server chat. |
| `+quit` | Closes the Minecraft client. |

Each registered module also has a command named after the module. Enter the module command without arguments to inspect its current state and settings; use the setting and value arguments to change a setting. `+help` and `+modules` are the starting points for discovering commands and module names.

### Configuration files

Nomnom stores its JSON data in a `nomnom` directory inside the active Minecraft game directory. Named module profiles are stored in `nomnom/modules/`. Keep a backup of this directory before editing configuration files manually.

## Project structure

The main client code is under `src/client/java/net/staro/nomnom/` and is organized around:

- **`module/`** — module base classes, categories, and individual modules.
- **`manager/managers/`** — module, command, friend, and configuration managers.
- **`command/`** — chat commands and command argument handling.
- **`events/` and `api/event/`** — event types and event-bus/listener infrastructure.
- **`gui/`** — the Click GUI.
- **`mixin/`** — Minecraft integration through mixins.
- **`utility/` and `setting/`** — shared utilities and configurable setting types.

The Gradle build uses Fabric Loom with split main/client source sets. Module settings and manager data are serialized to JSON.
