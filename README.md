# MC-CLI

**Minecraft Command-Line Interface for LLM-Assisted Shader Development**

MC-CLI is a tool that enables AI/LLM agents to control Minecraft, capture screenshots, and debug shaders programmatically. It provides structured JSON output optimized for machine consumption.

## Installation

### Recommended: Use Shard Launcher

The easiest way to use MC-CLI is with [Shard](https://shard.thomas.md), a Minecraft launcher that comes with the mod pre-installed and configured.

### Manual Installation

#### Fabric Mod

1. Build the mod: `cd mod && ./gradlew build`
2. Copy `build/libs/mccli-*.jar` to your Minecraft mods folder
3. Requires: Minecraft 1.21.1, Fabric Loader 0.16.9+
4. Iris is optional but required for shader features

#### Python CLI

```bash
# Install from source
pip install -e cli/

# Or run directly without installing
python -m mccli status
```

After installation, the `mccli` command will be available in your terminal.

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
│  Analysis       │                    │ + Iris (opt.)   │
└─────────────────┘                    └─────────────────┘
```

## Features

### Game Control
- **status** - Get game state (position, time, dimension, shader info)
- **teleport** - Move player to coordinates
- **camera** - Set view direction
- **time** - Control world time (supports named times: sunrise, noon, sunset, midnight)
- **server** - Connect/disconnect/status for multiplayer servers
- **execute** - Run arbitrary Minecraft commands

### Shader Development
- **shader list** - List available shader packs
- **shader get** - Get current shader info
- **shader set** - Switch shader packs
- **shader reload** - Reload current shader (returns compilation errors)
- **shader errors** - Get shader compilation errors
- **shader disable** - Disable shaders

### Visual Capture & Analysis
- **capture** - Take screenshots with optional clean mode (no HUD)
- **analyze** - Extract quantitative metrics from screenshots
- **compare** - Compare two screenshots for differences

### Debugging & Performance
- **logs** - Stream game/shader logs with filtering and follow mode
- **perf** - Get FPS, frame timing, and memory metrics

### Resource Packs
- **resourcepack list** - List all available resource packs
- **resourcepack enabled** - List currently enabled packs
- **resourcepack enable/disable** - Toggle resource packs
- **resourcepack reload** - Reload resource packs

### Chat
- **chat send** - Send chat messages or commands
- **chat history** - Get recent chat messages with filtering
- **chat clear** - Clear chat buffer

### Inspection & Probes
- **item** - Inspect held items or specific inventory slots
- **inventory** - List inventory contents by section
- **block** - Probe targeted or specific blocks (with NBT)
- **entity** - Probe targeted entities (with NBT)

### Automation
- **macro** - Run JSON macro scripts for automated workflows

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
    └── LLM_INTEGRATION.md
```

## Design Philosophy

1. **LLM-First**: Structured JSON output, quantitative metrics, no ambiguity
2. **Reliability**: Robust error handling, clear error messages
3. **Simplicity**: Easy to understand, easy to extend

## License

MIT
