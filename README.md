# MC-CLI

**Minecraft Command-Line Interface for LLM-Assisted Shader Development**

MC-CLI is a tool that enables AI/LLM agents to control Minecraft, capture screenshots, and debug shaders programmatically. It provides structured JSON output optimized for machine consumption.

## Architecture

```
┌─────────────────┐     TCP:25580      ┌─────────────────┐
│   Python CLI    │◄──────────────────►│   Fabric Mod    │
│   (mc-cli)      │    JSON Protocol   │   (mccli-mod)   │
└────────┬────────┘                    └────────┬────────┘
         │                                      │
         ▼                                      ▼
┌─────────────────┐                    ┌─────────────────┐
│  Screenshot     │                    │   Minecraft     │
│  Analysis       │                    │   + Iris        │
└─────────────────┘                    └─────────────────┘
```

## Core Features (The 20% That Gets 90% of Results)

### 1. Game Control
- **status** - Get game state (position, time, dimension, shader info)
- **teleport** - Move player to coordinates
- **camera** - Set view direction
- **time** - Control world time

### 2. Shader Development
- **shader.list** - List available shader packs
- **shader.get** - Get current shader info
- **shader.set** - Switch shader packs
- **shader.reload** - Reload current shader
- **shader.errors** - Get shader compilation errors ⭐ NEW

### 3. Visual Capture
- **screenshot** - Capture screen with clean mode (no HUD)
- **analyze** - Extract quantitative metrics from screenshots

### 4. Debugging
- **logs** - Stream game/shader logs ⭐ NEW
- **perf** - Get FPS and frame timing ⭐ NEW

### 5. Packs + Chat
- **resourcepack** - List/enable/disable/reload resource packs ⭐ NEW
- **chat** - Send messages and read chat history ⭐ NEW

### 6. Inspection + Probes
- **item** - Inspect held items or slots ⭐ NEW
- **inventory** - List inventory contents ⭐ NEW
- **block** - Probe targeted or specific blocks ⭐ NEW
- **entity** - Probe targeted entities ⭐ NEW

### 7. Automation
- **macro** - Run JSON macro scripts ⭐ NEW

## Installation

### Fabric Mod
1. Build the mod: `cd mod && ./gradlew build`
2. Copy `build/libs/mccli-*.jar` to your Minecraft mods folder
3. Requires: Minecraft 1.21.1, Fabric Loader 0.16.9+, Iris

### Python CLI
```bash
pip install -e cli/
# or just use directly:
python -m mccli status
```

## Quick Start

```bash
# Check connection
mccli status

# Reload shader after editing
mccli shader reload

# Check for shader errors
mccli shader errors

# Capture screenshot for analysis
mccli capture --clean -o test.png

# Analyze screenshot
mccli analyze test.png

# Get performance metrics
mccli perf

# Inspect held item
mccli item --hand main

# Probe targeted block
mccli block --max-distance 5

# Run a macro
mccli macro ./macro.json
```

## JSON Protocol

All commands use newline-delimited JSON:

**Request:**
```json
{"id": "1", "command": "shader", "params": {"action": "errors"}}
```

**Response:**
```json
{
  "id": "1",
  "success": true,
  "data": {
    "has_errors": true,
    "errors": [
      {"file": "final.fsh", "line": 42, "message": "undefined variable 'foo'"}
    ]
  }
}
```

## LLM Integration

MC-CLI is designed for LLM consumption:

1. **Structured Output**: All commands support `--json` for machine-readable output
2. **Quantitative Metrics**: Screenshot analysis provides numbers, not subjective descriptions
3. **Error Detection**: Shader errors are returned in structured format
4. **Reproducible Scenes**: Define test scenarios as JSON for consistent captures

### Example Workflow

```python
import subprocess
import json

# Reload shader after edit
subprocess.run(["mccli", "shader", "reload"])

# Check for errors
result = subprocess.run(["mccli", "shader", "errors", "--json"], capture_output=True)
errors = json.loads(result.stdout)

if errors["data"]["has_errors"]:
    for err in errors["data"]["errors"]:
        print(f"{err['file']}:{err['line']}: {err['message']}")
else:
    # Capture and analyze
    subprocess.run(["mccli", "capture", "--clean", "-o", "/tmp/test.png"])
    result = subprocess.run(["mccli", "analyze", "/tmp/test.png", "--json"], capture_output=True)
    metrics = json.loads(result.stdout)

    if metrics["brightness"]["mean"] < 30:
        print("Image is too dark - adjust exposure")
```

## Command Reference

See [docs/COMMANDS.md](docs/COMMANDS.md) for complete command documentation.

## Project Structure

```
mc-cli/
├── mod/                    # Fabric mod (Java)
│   ├── src/main/java/
│   │   └── dev/mccli/
│   │       ├── McCliMod.java
│   │       ├── server/
│   │       │   ├── SocketServer.java
│   │       │   └── CommandDispatcher.java
│   │       └── commands/
│   │           ├── StatusCommand.java
│   │           ├── ShaderCommand.java
│   │           ├── ScreenshotCommand.java
│   │           └── ...
│   └── build.gradle
│
├── cli/                    # Python CLI
│   ├── mccli/
│   │   ├── __init__.py
│   │   ├── __main__.py
│   │   ├── client.py
│   │   └── analysis.py
│   └── pyproject.toml
│
└── docs/
    ├── COMMANDS.md
    ├── PROTOCOL.md
    └── SHADER_DEBUGGING.md
```

## Design Philosophy

1. **Focus on the 20%**: Only implement features that provide 90% of the value
2. **LLM-First**: Structured output, quantitative metrics, no ambiguity
3. **Reliability**: Robust error handling, clear error messages
4. **Simplicity**: Easy to understand, easy to extend

## What's NOT Included (And Why)

| Feature | Why Excluded |
|---------|--------------|
| Key press simulation | Rarely needed, use `execute` for commands |
| Screen management | Handled by `screenshot --clean` |
| World connect/disconnect | Manual operation, not for automation |
| Complex scene system | Over-engineered for most use cases |

## License

MIT
