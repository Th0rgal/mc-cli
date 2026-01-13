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

### Manual Setup

**Requirements:** Minecraft 1.21.11, Fabric 0.18.1+ or NeoForge 21.11.0+, Python 3.10+, Java 21+

```bash
# Clone the repository
git clone https://github.com/Th0rgal/mc-cli.git && cd mc-cli/mod

# Build both mods (Fabric and NeoForge)
./gradlew build

# Or build individually:
./gradlew :fabric:build    # Fabric mod
./gradlew :neoforge:build  # NeoForge mod

# Copy the appropriate jar to your mods folder:
# - Fabric: mod/fabric/build/libs/mccli-fabric-1.0.0.jar
# - NeoForge: mod/neoforge/build/libs/mccli-neoforge-1.0.0.jar

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

| Variant | Minecraft | Loader | Features |
|---------|-----------|--------|----------|
| **Fabric** | 1.21.11 | Fabric 0.18.1+ | Core commands (10 commands) |
| **NeoForge** | 1.21.11 | NeoForge 21.11.0+ | Extended features (20 commands, mixins) |

The NeoForge version includes additional commands for chat capture, window management, and enhanced interaction capabilities.

## Architecture

```
Python CLI  <--TCP:25580/JSON-->  Fabric/NeoForge Mod  -->  Minecraft
```

See [docs/COMMANDS.md](docs/COMMANDS.md) for full documentation.

## Project Structure

```
mc-cli/
├── cli/                    # Python CLI client
├── mod/                    # Minecraft mod (multi-loader)
│   ├── fabric/            # Fabric mod subproject
│   │   ├── build.gradle
│   │   └── src/main/java/dev/mccli/
│   ├── neoforge/          # NeoForge mod subproject
│   │   ├── build.gradle
│   │   └── src/main/java/dev/mccli/
│   ├── build.gradle       # Root build file
│   └── settings.gradle    # Multi-project configuration
└── docs/                   # Documentation
```

## License

MIT
