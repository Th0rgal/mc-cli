# MC-CLI

**Minecraft Command-Line Interface for LLM-Assisted Development & Automated Testing**

MC-CLI is a tool that enables AI/LLM agents to programmatically control Minecraft for automated testing, mod development, shader debugging, and resource pack validation. It provides structured JSON output optimized for machine consumption, making it ideal for CI/CD pipelines and headless testing environments.

## Installation

### Recommended: Use Shard Launcher

The easiest way to use MC-CLI is with [Shard](https://shard.sh), a Minecraft launcher that comes with the mod pre-installed and configured.

### Manual Installation

MC-CLI has two components: a **Fabric mod** (runs inside Minecraft) and a **Python CLI** (control interface).

#### Prerequisites

- **Minecraft**: 1.21.11
- **Fabric Loader**: 0.18.4+
- **Java**: 21+ (for building the mod)
- **Gradle**: 9.2+ (for building the mod)
- **Python**: 3.10+
- **Iris** (optional): Required for shader features

#### 1. Build and Install the Fabric Mod

```bash
# Clone the repository
git clone https://github.com/Th0rgal/mc-cli.git
cd mc-cli

# Build the mod (requires Gradle 9.2+)
cd mod
gradle build

# The built JAR will be at: mod/build/libs/mccli-1.0.0.jar
```

Copy `mod/build/libs/mccli-1.0.0.jar` to your Minecraft mods folder:
- **Linux**: `~/.minecraft/mods/`
- **macOS**: `~/Library/Application Support/minecraft/mods/`
- **Windows**: `%APPDATA%\.minecraft\mods\`

Also ensure you have [Fabric API](https://modrinth.com/mod/fabric-api) installed.

#### 2. Install the Python CLI

```bash
# Navigate to the CLI directory
cd cli

# Option A: Install globally (recommended)
pip install .

# Option B: Install in development mode (for contributors)
pip install -e .

# Option C: Run without installing
python -m mccli status
```

#### 3. Add to PATH (if needed)

If `mccli` is not available after installation, add Python's bin directory to your PATH:

```bash
# Linux/macOS - Add to ~/.bashrc or ~/.zshrc
export PATH="$HOME/.local/bin:$PATH"

# Or find where pip installs scripts
python -m site --user-base
# Then add <output>/bin to your PATH
```

#### 4. Verify Installation

```bash
# Start Minecraft with the mod installed, then:
mccli status

# Expected output when connected:
# in_game: True
# world_type: singleplayer
# ...
```

The mod listens on `localhost:25580` by default. The CLI connects automatically.

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

## Use Cases

- **Automated Testing**: Run visual regression tests on resource packs and shaders
- **Mod Development**: Test mod functionality programmatically without manual interaction
- **CI/CD Integration**: Automated screenshot capture and validation in build pipelines
- **LLM Agents**: Enable AI assistants to interact with Minecraft for debugging and development
- **Resource Pack Validation**: Automatically verify custom textures and models load correctly
- **Server Testing**: Connect to servers and verify resource pack downloads work headlessly

## Features

### Game Control
- **status** - Get game state (position, time, dimension, shader info)
- **teleport** - Move player to coordinates
- **camera** - Set view direction
- **time** - Control world time (supports named times: sunrise, noon, sunset, midnight)
- **server** - Connect/disconnect/status for multiplayer servers with auto resource pack handling
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

### Player Interactions
- **interact use** - Use item in hand (right-click in air)
- **interact use_on_block** - Use item on block (right-click on block, place blocks)
- **interact attack** - Attack/left-click (swing or break block)
- **interact drop** - Drop items from inventory
- **interact swap** - Swap items between inventory slots
- **interact select** - Select hotbar slot (0-8)

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

# Use item in hand (right-click)
mccli interact use

# Right-click on a specific block
mccli interact use_on_block --x 10 --y 64 --z -20 --face up

# Select hotbar slot and use item
mccli interact select 2
mccli interact use

# Drop items from inventory
mccli interact drop --slot 0 --all

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
5. **Headless Server Testing**: Auto-accept resource packs without UI interaction

### Example: Shader Development Workflow

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

### Example: Automated Server Resource Pack Testing

```python
import subprocess
import json
import time

# Connect to server with automatic resource pack acceptance
subprocess.run(["mccli", "server", "connect", "demo.oraxen.com", "--resourcepack", "accept"])

# Wait for connection and resource pack download
time.sleep(10)

# Verify we're connected
result = subprocess.run(["mccli", "status", "--json"], capture_output=True)
status = json.loads(result.stdout)

if status["data"]["in_game"] and status["data"]["world_type"] == "multiplayer":
    # Execute server command to open custom inventory
    subprocess.run(["mccli", "execute", "/o inv"])
    time.sleep(1)

    # Capture screenshot to verify custom textures loaded
    subprocess.run(["mccli", "capture", "-o", "/tmp/server_test.png"])
    print("Resource pack test complete - screenshot saved")
```

### Example: Testing Custom Block Placement Plugin

```python
import subprocess
import json

# Select the hotbar slot with a custom item
subprocess.run(["mccli", "interact", "select", "0"])

# Check what item is selected
result = subprocess.run(["mccli", "item", "--hand", "main", "--json"], capture_output=True)
item = json.loads(result.stdout)
print(f"Selected: {item['data']['item']['name']}")

# Right-click on a block to place/use the custom item
subprocess.run(["mccli", "interact", "use_on_block", "--x", "10", "--y", "64", "--z", "-20", "--face", "up"])

# Verify the block was placed
result = subprocess.run(["mccli", "block", "--x", "10", "--y", "65", "--z", "-20", "--json"], capture_output=True)
block = json.loads(result.stdout)
print(f"Block placed: {block['data']['id']}")

# Screenshot to verify custom block model/texture
subprocess.run(["mccli", "capture", "--clean", "-o", "/tmp/custom_block.png"])
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
2. **Automation-Ready**: Headless operation, no UI interaction required
3. **Reliability**: Robust error handling, clear error messages
4. **Simplicity**: Easy to understand, easy to extend

## License

MIT
