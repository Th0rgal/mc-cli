# MC-CLI

**Minecraft CLI for LLM Agents & Automated Testing**

MC-CLI enables AI agents to programmatically control Minecraft for automated testing, mod development, and shader debugging. Structured JSON output makes it ideal for CI/CD pipelines and headless environments.

## Installation

### Recommended: [Shard Launcher](https://shard.thomas.md)

The easiest way is using [Shard](https://github.com/Th0rgal/shard), a Minecraft launcher with MC-CLI pre-installed.

### Manual Setup

**Requirements:** Minecraft 1.21.11, Fabric 0.18.4+, Python 3.10+, Java 21+

```bash
# Build the mod
git clone https://github.com/Th0rgal/mc-cli.git && cd mc-cli/mod
gradle build
# Copy mod/build/libs/mccli-1.0.0.jar to your mods folder

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

## Architecture

```
Python CLI  <--TCP:25580/JSON-->  Fabric Mod  -->  Minecraft
```

See [docs/COMMANDS.md](docs/COMMANDS.md) for full documentation.

## License

MIT
