# MC-CLI

**Minecraft CLI for LLM Agents & Automated Testing**

MC-CLI enables AI agents to programmatically control Minecraft for automated testing, mod development, and shader debugging. Structured JSON output makes it ideal for CI/CD pipelines and headless environments.

## Installation

### Recommended: [Shard Launcher](https://shard.thomas.md)

The easiest way is using [Shard](https://github.com/Th0rgal/shard), a Minecraft launcher with MC-CLI pre-installed.

### Download from Releases

Download pre-built JARs from [GitHub Releases](https://github.com/Th0rgal/mc-cli/releases):
- **Fabric**: `mccli-fabric-*.jar` (requires [Fabric API](https://modrinth.com/mod/fabric-api))
- **NeoForge**: `mccli-neoforge-*.jar`

Pick the jar matching your Minecraft version (see [Mod Variants](#mod-variants)).

### Manual Setup

**Requirements:** Python 3.10+, and either
- Minecraft 1.21.11 with Fabric 0.18.1+ or NeoForge 21.11.0+ (Java 21+), or
- Minecraft 26.3 / 26.2 with Fabric 0.19.5+ or NeoForge 26.3 / 26.2 (Java 25)

Building needs a Java 25 JDK available locally: Gradle itself runs on Java 25
(`mod/gradle/gradle-daemon-jvm.properties`), and Java 21 is still used for the 1.21.x modules via toolchains.

```bash
# Clone the repository
git clone https://github.com/Th0rgal/mc-cli.git && cd mc-cli/mod

# Build all mods
./gradlew build

# Or build individually:
./gradlew :fabric:build         # Fabric 1.21.11
./gradlew :fabric-1.21.4:build  # Fabric 1.21.4
./gradlew :neoforge:build       # NeoForge 1.21.11
./gradlew :fabric-26:build      # Fabric 26.3
./gradlew :neoforge-26:build    # NeoForge 26.3

# 26.2 jars come from the same sources:
./gradlew :fabric-26:build :neoforge-26:build -Pmc26_version=26.2

# Copy the appropriate jar from <module>/build/libs/ to your mods folder, e.g.
# - mod/fabric-26/build/libs/mccli-fabric-26.3-<version>.jar
# - mod/neoforge-26/build/libs/mccli-neoforge-26.3-<version>.jar

# Install CLI
cd ../cli && pip install .
```

## Quick Start

```bash
mccli status              # Check connection
mccli shader reload       # Reload shader after editing
mccli shader errors       # Check for compilation errors
mccli capture --clean -o test.png  # Screenshot without HUD
mccli perf                # FPS and memory metrics
```

## Features

| Category | Commands |
|----------|----------|
| **Game Control** | `status`, `teleport`, `camera`, `time`, `server`, `execute` |
| **Shaders** | `shader list/get/set/reload/errors/disable` |
| **Capture** | `capture`, `analyze`, `compare` |
| **Resources** | `resourcepack list/enable/disable/reload` |
| **Inspection** | `item`, `inventory`, `block`, `entity` |
| **Interaction** | `interact use/attack/drop/swap/select` |
| **Chat** | `chat send/history/clear` |
| **Debug** | `logs`, `perf` |

## Multi-Instance Support

```bash
mccli instances           # List running instances
mccli -i my_world status  # Target by name
mccli -i 25581 capture    # Target by port
```

## Headless Mode

Run Minecraft without a visible window (MC-CLI 1.6.0+, Minecraft 1.21.11 and 26.x, Fabric and NeoForge).

```bash
shard launch "Fabric 26.3" --headless          # Shard 0.1.28+
MCCLI_HEADLESS=1 <any launcher command>        # or the JVM argument -Dmccli.headless=true
```

The window is created hidden and is never shown, focused or raised. On macOS there is no Dock icon.
The mouse is never grabbed, pause-on-lost-focus is ignored, and master volume is muted without changing
`options.txt`. The game keeps rendering, so `capture`, `analyze` and every other command work normally.

- `mccli window show` brings the window up (and restores focus handling and sound); `mccli window hide` goes back to headless.
- `mccli instances` marks headless instances with `[headless]`; `status` and `window status` report `headless`.
- JVM options: `-Dmccli.headless.fps=30` (render rate while hidden), `-Dmccli.headless.width=1280` / `-Dmccli.headless.height=720` (framebuffer and screenshot size).
- NeoForge shows its early loading screen before any mod loads, so it can flash briefly. Set `earlyWindowControl = false` in the instance's `config/fml.toml` to avoid it.
- Linux servers without a display still need an OpenGL context: run under a virtual display, e.g. `xvfb-run shard launch "Fabric 26.3" --headless`.
- Not available in the legacy Fabric 1.21.4 mod.

## LLM Integration

```python
import subprocess, json

# Reload and check errors
subprocess.run(["mccli", "shader", "reload"])
result = subprocess.run(["mccli", "shader", "errors", "--json"], capture_output=True)
errors = json.loads(result.stdout)

if not errors["data"]["has_errors"]:
    subprocess.run(["mccli", "capture", "--clean", "-o", "/tmp/test.png"])
```

## Mod Variants

MC-CLI is available for both major mod loaders:

| Variant | Minecraft | Loader | Java | Jar | Commands |
|---------|-----------|--------|------|-----|----------|
| **Fabric** | 26.3 | Fabric Loader 0.19.5+, Fabric API | 25 | `mccli-fabric-26.3-*.jar` | All |
| **Fabric** | 26.2 | Fabric Loader 0.19.5+, Fabric API | 25 | `mccli-fabric-26.2-*.jar` | All |
| **NeoForge** | 26.3 | NeoForge 26.3.0+ | 25 | `mccli-neoforge-26.3-*.jar` | All |
| **NeoForge** | 26.2 | NeoForge 26.2.0+ | 25 | `mccli-neoforge-26.2-*.jar` | All |
| **NeoForge** | 1.21.11 | NeoForge 21.11.0+ | 21 | `mccli-neoforge-<version>.jar` | All |
| **Fabric** | 1.21.11 | Fabric 0.18.1+ | 21 | `mccli-fabric-<version>.jar` | Core subset |
| **Fabric** | 1.21.4 | Fabric 0.18.4+ | 21 | `mccli-fabric-1.21.4-*.jar` | Core subset |

The 26.x mods share one loader-independent code base (`mod/common-26`), so Fabric and NeoForge
have full command parity there. On 1.21.x the NeoForge mod has the extended command set
(chat capture, window management, block/entity/inventory inspection, interaction, server and world management).

## Architecture

```
Python CLI  <--TCP:25580/JSON-->  Fabric/NeoForge Mod  -->  Minecraft
```

See [docs/COMMANDS.md](docs/COMMANDS.md) for full documentation.

## Project Structure

```
mc-cli/
├── cli/                    # Python CLI client
├── mod/                    # Minecraft mod (multi-loader, multi-version)
│   ├── common-26/         # Shared 26.x sources (commands, TCP server, mixins)
│   ├── fabric-26/         # Fabric 26.x glue (entrypoint, fabric.mod.json)
│   ├── neoforge-26/       # NeoForge 26.x glue (entrypoint, neoforge.mods.toml)
│   ├── fabric/            # Fabric 1.21.11
│   ├── fabric-1.21.4/     # Fabric 1.21.4
│   ├── neoforge/          # NeoForge 1.21.11
│   ├── build.gradle       # Root build file
│   └── settings.gradle    # Multi-project configuration
└── docs/                   # Documentation
```

## License

MIT
