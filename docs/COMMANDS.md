# MC-CLI Command Reference

Complete reference for all MC-CLI commands.

## Quick Reference

| Command | Description |
|---------|-------------|
| `status` | Get game state |
| `teleport x y z` | Move player |
| `camera yaw pitch` | Set view direction |
| `time get\|set <value>` | Get/set world time |
| `shader list\|get\|set\|reload\|errors\|disable` | Shader management |
| `resourcepack list\|enabled\|enable\|disable\|reload` | Resource pack management |
| `chat send\|history\|clear` | Chat messaging and history |
| `capture -o path [--clean]` | Take screenshot |
| `analyze path` | Analyze screenshot |
| `compare a b` | Compare screenshots |
| `perf` | Performance metrics |
| `logs [--level LEVEL]` | Get game logs |
| `execute command` | Run Minecraft command |
| `server connect\|disconnect\|status` | Server connection management |
| `item [--hand main\|off] [--slot N]` | Inspect held item or slot |
| `inventory [--section ...]` | List inventory contents |
| `block [--x y z]` | Probe targeted or specific block |
| `entity` | Probe targeted entity |
| `macro file.json` | Run a JSON macro script |

---

## status

Get current game state.

```bash
mccli status
mccli status --json
```

**Response:**
```json
{
  "in_game": true,
  "world_type": "singleplayer",
  "player": {
    "x": 100.5,
    "y": 64.0,
    "z": -200.3,
    "yaw": -45.0,
    "pitch": 15.0
  },
  "time": 6000,
  "dimension": "minecraft:overworld",
  "iris_loaded": true,
  "shader": {
    "active": true,
    "name": "ComplementaryShaders_v4.6"
  }
}
```

---

## teleport

Move player to coordinates.

```bash
mccli teleport 100 64 -200
mccli teleport 100 64 -200 --json
```

**Arguments:**
- `x` - X coordinate (required)
- `y` - Y coordinate (required)
- `z` - Z coordinate (required)

**Response:**
```json
{
  "x": 100.0,
  "y": 64.0,
  "z": -200.0
}
```

---

## camera

Set camera/player view direction.

```bash
mccli camera -45 15
```

**Arguments:**
- `yaw` - Horizontal rotation (-180 to 180)
- `pitch` - Vertical rotation (-90 to 90)

**Response:**
```json
{
  "yaw": -45.0,
  "pitch": 15.0
}
```

---

## time

Get or set world time.

```bash
# Get current time
mccli time get

# Set time (ticks)
mccli time set 6000

# Set time (named)
mccli time set noon
```

**Named Times:**
| Name | Ticks |
|------|-------|
| sunrise | 0 |
| day | 1000 |
| noon | 6000 |
| sunset | 12000 |
| night | 13000 |
| midnight | 18000 |

**Response (get):**
```json
{
  "time": 6000
}
```

---

## shader

Shader management commands.

### shader list

List available shader packs.

```bash
mccli shader list
mccli shader list --json
```

**Response:**
```json
{
  "packs": [
    {"name": "ComplementaryShaders_v4.6", "type": "zip"},
    {"name": "BSL_v8.2.04", "type": "zip"},
    {"name": "MyCustomShader", "type": "directory"}
  ],
  "count": 3
}
```

### shader get

Get current shader info.

```bash
mccli shader get
```

**Response:**
```json
{
  "active": true,
  "name": "ComplementaryShaders_v4.6"
}
```

### shader set

Set active shader pack.

```bash
mccli shader set --name "BSL_v8.2.04"
```

**Arguments:**
- `--name` - Shader pack name (required)

### shader reload

Reload current shader. Also returns any compilation errors.

```bash
mccli shader reload
mccli shader reload --json
```

**Response:**
```json
{
  "reloaded": true,
  "has_errors": false
}
```

**Response (with errors):**
```json
{
  "reloaded": true,
  "has_errors": true,
  "errors": [
    {
      "file": "shaders/final.fsh",
      "line": 42,
      "message": "error: undefined variable 'customColor'"
    }
  ]
}
```

### shader errors

Get shader compilation errors.

```bash
mccli shader errors
mccli shader errors --json
```

**Response:**
```json
{
  "has_errors": true,
  "count": 1,
  "errors": [
    {
      "file": "shaders/final.fsh",
      "line": 42,
      "message": "error: undefined variable 'customColor'"
    }
  ]
}
```

### shader disable

Disable shaders entirely.

```bash
mccli shader disable
```

---

## resourcepack

Resource pack management.

### resourcepack list

List all available resource packs.

```bash
mccli resourcepack list
mccli resourcepack list --json
```

**Response:**
```json
{
  "packs": [
    {
      "id": "vanilla",
      "name": "Default",
      "description": "The default look of Minecraft",
      "enabled": true,
      "required": true
    }
  ],
  "count": 1
}
```

### resourcepack enabled

List currently enabled resource packs.

```bash
mccli resourcepack enabled
```

**Response:**
```json
{
  "packs": [
    {"id": "vanilla", "name": "Default", "description": "The default look of Minecraft"}
  ],
  "count": 1
}
```

### resourcepack enable

Enable a resource pack by ID or name.

```bash
mccli resourcepack enable --name "MyPack"
```

**Response:**
```json
{
  "success": true,
  "id": "file/MyPack.zip",
  "name": "MyPack"
}
```

### resourcepack disable

Disable a resource pack by ID or name.

```bash
mccli resourcepack disable --name "MyPack"
```

**Response:**
```json
{
  "success": true,
  "id": "file/MyPack.zip",
  "name": "MyPack"
}
```

### resourcepack reload

Reload resource packs.

```bash
mccli resourcepack reload
```

**Response:**
```json
{"success": true, "reloading": true}
```

---

## chat

Chat messaging and history.

### chat send

Send a chat message or command.

```bash
mccli chat send -m "hello world"
mccli chat send -m "/time set noon"
```

**Response:**
```json
{"sent": true, "type": "chat", "message": "hello world"}
```

### chat history

Get recent chat messages.

```bash
mccli chat history --limit 20 --type chat --filter "trade"
```

**Response:**
```json
{
  "messages": [
    {"timestamp": "2024-01-15T10:30:45Z", "type": "chat", "sender": "Steve", "content": "Trade?"}
  ],
  "count": 1,
  "total_buffered": 120
}
```

### chat clear

Clear chat history buffer.

```bash
mccli chat clear
```

**Response:**
```json
{"cleared": 120}
```

---

## capture

Take a screenshot.

```bash
# Basic capture
mccli capture -o screenshot.png

# Clean capture (no HUD)
mccli capture -o screenshot.png --clean

# With delay
mccli capture -o screenshot.png --clean --delay 500 --settle 300
```

**Arguments:**
- `-o, --output` - Output file path (required)
- `--clean` - Hide HUD before capture
- `--delay` - Delay before capture in ms (default: 0)
- `--settle` - Settle time after cleanup in ms (default: 200)

**Response:**
```json
{
  "path": "/absolute/path/screenshot.png",
  "width": 1920,
  "height": 1080,
  "metadata": {
    "timestamp": "2024-01-15T10:30:45Z",
    "player": {"x": 100.5, "y": 64.0, "z": -200.3, "yaw": -45.0, "pitch": 15.0},
    "time": 6000,
    "dimension": "minecraft:overworld",
    "iris_loaded": true,
    "shader": {"active": true, "name": "ComplementaryShaders_v4.6"},
    "resource_packs": ["vanilla", "file/MyPack.zip"]
  }
}
```

---

## analyze

Analyze screenshot for quantitative metrics.

```bash
# Single file
mccli analyze screenshot.png
mccli analyze screenshot.png --json

# Directory
mccli analyze ./captures/
```

**Arguments:**
- `path` - Image file or directory to analyze

**Response:**
```json
{
  "brightness": {
    "mean": 127.5,
    "std": 45.2,
    "min": 5,
    "max": 250
  },
  "contrast_ratio": 50.0,
  "color_temp": 0.52,
  "saturation_mean": 0.45,
  "histogram": [1234, 2345, 3456, ...],
  "dimensions": {"width": 1920, "height": 1080},
  "path": "screenshot.png"
}
```

**Diagnostic Issues:**
The analyze command detects these issues:
- `UNDEREXPOSED` - Mean brightness < 30
- `DARK` - Mean brightness < 60
- `OVEREXPOSED` - Mean brightness > 220
- `BRIGHT` - Mean brightness > 180
- `LOW_CONTRAST` - Standard deviation < 20
- `HIGH_CONTRAST` - Contrast ratio > 100
- `DESATURATED` - Saturation < 0.1
- `VERY_WARM` - Color temp < 0.3
- `VERY_COOL` - Color temp > 0.7
- `SHADOW_CLIPPING` - >10% pixels in darkest bin
- `HIGHLIGHT_CLIPPING` - >10% pixels in brightest bin

---

## compare

Compare two screenshots.

```bash
mccli compare before.png after.png
mccli compare before.png after.png --json
```

**Response:**
```json
{
  "path_a": "before.png",
  "path_b": "after.png",
  "differences": {
    "brightness": 12.5,
    "contrast": -2.3,
    "color_temp": 0.05,
    "saturation": -0.02
  },
  "histogram_correlation": 0.95
}
```

---

## perf

Get performance metrics.

```bash
mccli perf
mccli perf --json
```

**Response:**
```json
{
  "fps": 60,
  "frame_time_ms": 16.67,
  "memory": {
    "used_mb": 2048,
    "max_mb": 4096,
    "percent": 50.0
  },
  "chunk_updates": 256,
  "entity_count": 42
}
```

---

## logs

Get recent game logs.

```bash
# Get recent logs
mccli logs

# Filter by level
mccli logs --level error

# Filter by pattern
mccli logs --filter "shader"

# Limit entries
mccli logs --limit 20

# Clear after reading
mccli logs --clear

# Stream logs until a pattern appears
mccli logs --follow --until "shader"
```

**Arguments:**
- `--level` - Minimum log level: error, warn, info, debug (default: info)
- `--limit` - Max number of entries (default: 50)
- `--filter` - Regex pattern to filter messages
- `--clear` - Clear logs after returning
- `--since` - Only return entries with id > since
- `--follow` - Stream logs
- `--interval` - Polling interval for streaming in ms (default: 500)
- `--until` - Regex to stop streaming
- `--timeout` - Timeout for streaming in ms

**Response:**
```json
{
  "logs": [
    {
      "id": 42,
      "timestamp": "2024-01-15T10:30:45Z",
      "level": "info",
      "logger": "net.irisshaders.iris",
      "message": "Reloading shader pack"
    }
  ],
  "count": 1,
  "last_id": 42,
  "total_buffered": 128
}
```

---

## execute

Execute a Minecraft command.

```bash
mccli execute "weather clear"
mccli execute "tp @p 0 100 0"
```

**Arguments:**
- `command` - Command to execute (without leading /)

**Response:**
```json
{
  "executed": true,
  "command": "weather clear"
}
```

---

## server

Manage multiplayer server connections.

```bash
# Connect to a server
mccli server connect play.example.com
mccli server connect play.example.com --server-port 25565

# Disconnect from current server/world
mccli server disconnect

# Connection status
mccli server status
```

**Arguments:**
- `connect <address>` - server hostname or IP
- `--server-port` - server port (default: 25565)
- `disconnect` - leave current world
- `status` - get current connection details

**Response (connect):**
```json
{
  "success": true,
  "connecting": true,
  "address": "play.example.com",
  "port": 25565
}
```

**Response (disconnect):**
```json
{
  "success": true,
  "disconnected": true,
  "was_multiplayer": true,
  "previous_world": "multiplayer"
}
```

**Response (status):**
```json
{
  "connected": true,
  "multiplayer": true,
  "server_name": "Example Server",
  "server_address": "play.example.com:25565",
  "player_count": 12
}
```

---

## item

Inspect held items or a specific inventory slot.

```bash
# Main hand (default)
mccli item

# Off hand
mccli item --hand off

# Inventory slot
mccli item --slot 5
```

**Arguments:**
- `--hand` - main | off (default: main)
- `--slot` - inventory slot index (0..35 main+hotbar; armor/offhand via inventory command)
- `--no-nbt` - exclude NBT from output

**Response:**
```json
{
  "item": {
    "empty": false,
    "id": "minecraft:diamond_sword",
    "name": "Diamond Sword",
    "count": 1,
    "max_count": 1,
    "damage": 3,
    "max_damage": 1561,
    "durability": 1558,
    "custom_model_data": 12,
    "enchantments": [{"id": "minecraft:sharpness", "level": 5}],
    "nbt": "{CustomModelData:12}"
  }
}
```

---

## inventory

List inventory contents.

```bash
# All non-empty slots
mccli inventory

# Include empty slots
mccli inventory --include-empty

# Hotbar only
mccli inventory --section hotbar
```

**Arguments:**
- `--section` - hotbar | main | armor | offhand
- `--include-empty` - include empty slots
- `--include-nbt` - include NBT for each item

**Response:**
```json
{
  "items": [
    {
      "slot": 0,
      "slot_type": "hotbar",
      "item": {"id": "minecraft:diamond_sword", "count": 1}
    }
  ],
  "count": 1
}
```

---

## block

Probe the targeted block or a specific position.

```bash
# Targeted block
mccli block --max-distance 5

# Specific position
mccli block --x 10 --y 64 --z -20
```

**Arguments:**
- `--x --y --z` - block position (optional)
- `--max-distance` - raycast distance for target (default: 5.0)
- `--include-nbt` - include block entity NBT

**Response (targeted):**
```json
{
  "hit": true,
  "id": "minecraft:chest",
  "pos": {"x": 10, "y": 64, "z": -20},
  "properties": {"facing": "north", "waterlogged": "false"},
  "block_entity": {"id": "minecraft:chest", "nbt": "{Items:[...]}"}
}
```

---

## entity

Probe the targeted entity.

```bash
mccli entity --max-distance 6 --include-nbt
```

**Arguments:**
- `--max-distance` - max target distance (default: 5.0)
- `--include-nbt` - include entity NBT

**Response:**
```json
{
  "hit": true,
  "id": "minecraft:zombie",
  "uuid": "2b1c7b3f-7f3e-4a60-9b7b-3cc9b6dd2a20",
  "name": "Zombie",
  "pos": {"x": 12.3, "y": 64.0, "z": -18.5},
  "nbt": "{Health:20.0f}"
}
```

---

## macro

Run a JSON macro script locally (CLI-side). Each step can send an MC-CLI command,
wait for a duration, or perform local analysis.

```bash
mccli macro macro.json
```

**Macro format:**
```json
{
  "stop_on_error": true,
  "steps": [
    {"command": "time", "params": {"action": "set", "value": "noon"}},
    {"wait_ms": 200},
    {"command": "screenshot", "params": {"path": "/tmp/noon.png", "clean": true}},
    {"local": "analyze", "path": "/tmp/noon.png"},
    {"wait_ticks": 20},
    {"local": "compare", "a": "/tmp/noon.png", "b": "/tmp/noon_after.png"}
  ]
}
```

**Macro response:**
```json
{
  "success": true,
  "steps": [
    {"index": 0, "type": "command:time", "success": true, "took_ms": 12},
    {"index": 1, "type": "wait", "success": true, "took_ms": 200},
    {"index": 2, "type": "command:screenshot", "success": true, "took_ms": 450},
    {"index": 3, "type": "local:analyze", "success": true, "took_ms": 30}
  ]
}
```

**Supported step types:**
- `{"command": "...", "params": {...}}` - Send a protocol command
- `{"wait_ms": 200}` / `{"sleep_ms": 200}` - Sleep in milliseconds
- `{"wait_ticks": 20}` - Sleep in ticks (20 ticks = 1s)
- `{"local": "analyze", "path": "image.png"}` - Run local analysis
- `{"local": "compare", "a": "before.png", "b": "after.png"}` - Compare images

---

## Global Options

All commands support these options:

| Option | Description |
|--------|-------------|
| `--host HOST` | MC-CLI server host (default: localhost) |
| `--port PORT` | MC-CLI server port (default: 25580) |
| `--json` | Output as JSON |

---

## Examples

### Shader Development Workflow

```bash
# Check connection
mccli status

# Edit shader file...

# Reload and check for errors
mccli shader reload --json | jq '.has_errors'

# If no errors, capture test screenshots
mccli time set noon
mccli capture -o captures/noon.png --clean

mccli time set sunset
mccli capture -o captures/sunset.png --clean

# Analyze results
mccli analyze captures/

# Compare with baseline
mccli compare baseline/noon.png captures/noon.png
```

### Python Integration

```python
from mccli import Client

with Client() as mc:
    # Reload shader
    result = mc.shader_reload()

    if result['has_errors']:
        for err in result['errors']:
            print(f"{err['file']}:{err['line']}: {err['message']}")
    else:
        # Capture screenshot
        mc.time_set("noon")
        mc.screenshot("/tmp/test.png", clean=True)

        # Analyze
        from mccli.analysis import analyze
        metrics = analyze("/tmp/test.png")
        issues = metrics.diagnose()

        if issues:
            print("Issues found:", issues)
```
