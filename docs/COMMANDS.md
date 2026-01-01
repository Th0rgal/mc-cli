# MC-CLI Command Reference

Complete reference for all MC-CLI commands.

## Quick Reference

| Command | Description |
|---------|-------------|
| `instances` | List running MC-CLI instances |
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
| `server connect\|disconnect\|status\|connection_error` | Server connection management |
| `item [--hand main\|off] [--slot N]` | Inspect held item or slot |
| `inventory [--section ...]` | List inventory contents |
| `block [--x y z]` | Probe targeted or specific block |
| `entity` | Probe targeted entity |
| `interact use\|use_on_block\|attack\|drop\|swap\|select` | Player interactions |
| `window focus_grab\|pause_on_lost_focus\|focus\|close_screen\|status` | Window management |
| `world list\|load\|create\|delete` | Singleplayer world management |
| `macro file.json` | Run a JSON macro script |

---

## Multi-Instance Support

MC-CLI supports controlling multiple Minecraft instances simultaneously. Each instance registers itself in `~/.mccli/instances.json` with a unique name and port.

### instances

List all running MC-CLI instances.

```bash
mccli instances
mccli instances --all  # Include dead instances
mccli instances --json
```

**Response:**
```json
{
  "instances": [
    {
      "name": "my_world",
      "port": 25580,
      "pid": 12345,
      "address": "localhost:25580",
      "alive": true
    },
    {
      "name": "hypixel_net",
      "port": 25581,
      "pid": 12346,
      "address": "localhost:25581",
      "alive": true
    }
  ],
  "count": 2
}
```

### Targeting a Specific Instance

Use `--instance` or `-i` to target a specific instance:

```bash
# By name (partial match supported)
mccli --instance my_world status
mccli -i hypixel status

# By port
mccli -i 25581 status
```

**Auto-detection behavior:**
- If only one instance is running, it's used automatically
- If multiple instances exist and none specified, an error lists available instances
- If no instances are registered, falls back to `localhost:25580`

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

# Connect with automatic resource pack handling
mccli server connect play.example.com --resourcepack accept

# Disconnect from current server/world
mccli server disconnect

# Connection status
mccli server status

# Check for connection errors
mccli server connection_error
mccli server connection_error --clear
```

**Arguments:**
- `connect <address>` - server hostname or IP
- `--server-port` - server port (default: 25565)
- `--resourcepack` - resource pack policy: prompt (default), accept, or reject
- `disconnect` - leave current world
- `status` - get current connection details
- `connection_error` - get last connection/disconnection error
- `--clear` - clear error after showing (for connection_error)

**Response (connect):**
```json
{
  "success": true,
  "connecting": true,
  "address": "play.example.com",
  "port": 25565,
  "resourcepack_policy": "accept"
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

**Response (connection_error):**
```json
{
  "has_error": true,
  "error": "Connection refused",
  "server_address": "play.example.com:25565",
  "timestamp": 1704067200000,
  "recent": true
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

## interact

Player interaction commands for using items, placing blocks, attacking, etc.

### interact use

Use the item in hand (right-click in air).

```bash
mccli interact use
mccli interact use --hand off
```

**Arguments:**
- `--hand` - main | off (default: main)

**Response:**
```json
{
  "result": "success",
  "hand": "main",
  "item": {"id": "minecraft:bow", "name": "Bow", "count": 1}
}
```

### interact use_on_block

Use item on a block (right-click on block, place blocks).

```bash
# Use on targeted block
mccli interact use_on_block

# Use on specific coordinates
mccli interact use_on_block --x 10 --y 64 --z -20 --face up
```

**Arguments:**
- `--hand` - main | off (default: main)
- `--x --y --z` - block position (optional, uses targeted block if not specified)
- `--face` - up | down | north | south | east | west (default: up)
- `--inside-block` - click position inside block

**Response:**
```json
{
  "result": "success",
  "hand": "main",
  "block_pos": {"x": 10, "y": 64, "z": -20},
  "face": "up",
  "item": {"id": "minecraft:cobblestone", "name": "Cobblestone", "count": 63}
}
```

### interact attack

Attack/swing (left-click). Can target air or a specific block.

```bash
# Swing in air
mccli interact attack

# Attack a specific block
mccli interact attack --target block --x 10 --y 64 --z -20
```

**Arguments:**
- `--target` - air | block (default: air)
- `--x --y --z` - block position (for block target)
- `--face` - block face (default: up)

**Response:**
```json
{
  "result": "success",
  "target": "air"
}
```

### interact drop

Drop items from inventory.

```bash
# Drop one item from current slot
mccli interact drop

# Drop entire stack
mccli interact drop --all

# Drop from specific slot
mccli interact drop --slot 5 --all
```

**Arguments:**
- `--slot` - inventory slot (default: current hotbar slot)
- `--all` - drop entire stack instead of one item

**Response:**
```json
{
  "dropped": true,
  "count": 64,
  "item": {"id": "minecraft:cobblestone", "name": "Cobblestone"}
}
```

### interact swap

Swap items between inventory slots.

```bash
mccli interact swap --from-slot 0 --to-slot 5
```

**Arguments:**
- `--from-slot` - source slot (required)
- `--to-slot` - destination slot (required)

**Response:**
```json
{
  "swapped": true,
  "from_slot": 0,
  "to_slot": 5,
  "from_item": {"id": "minecraft:diamond_sword", "count": 1},
  "to_item": {"empty": true}
}
```

### interact select

Select a hotbar slot.

```bash
mccli interact select 2
```

**Arguments:**
- `slot` - hotbar slot 0-8 (required)

**Response:**
```json
{
  "slot": 2,
  "item": {"id": "minecraft:pickaxe", "name": "Diamond Pickaxe", "count": 1}
}
```

---

## window

Window management for headless operation.

### window focus_grab

Enable or disable window focus grabbing. Useful for headless/automated operation.

```bash
mccli window focus_grab --enabled false
mccli window focus_grab --enabled true
```

**Arguments:**
- `--enabled` - true | false (required)

**Response:**
```json
{
  "focus_grab_enabled": false
}
```

### window pause_on_lost_focus

Enable or disable the pause menu appearing when the window loses focus. When disabled,
screenshots and commands can work while Minecraft runs in the background.

```bash
# Disable pause menu on focus loss (for headless operation)
mccli window pause_on_lost_focus --enabled false

# Re-enable pause menu
mccli window pause_on_lost_focus --enabled true
```

**Arguments:**
- `--enabled` - true | false (required)

**Response:**
```json
{
  "pause_on_lost_focus_enabled": false
}
```

### window focus

Request window focus.

```bash
mccli window focus
```

**Response:**
```json
{
  "focused": true
}
```

### window close_screen

Close any open GUI screen.

```bash
mccli window close_screen
```

**Response:**
```json
{
  "closed": true,
  "screen_type": "minecraft:inventory"
}
```

### window status

Get window state.

```bash
mccli window status
```

**Response:**
```json
{
  "focus_grab_enabled": true,
  "pause_on_lost_focus_enabled": true,
  "screen_open": false,
  "screen_type": null
}
```

---

## world

Singleplayer world management for automated testing.

### world list

List all available singleplayer worlds.

```bash
mccli world list
mccli world list --json
```

**Response:**
```json
{
  "worlds": [
    {
      "name": "my_world",
      "display_name": "My World",
      "last_played": 1704067200000,
      "game_mode": "survival",
      "hardcore": false,
      "cheats": true,
      "locked": false,
      "requires_conversion": false
    }
  ],
  "count": 1
}
```

### world load

Load an existing singleplayer world.

```bash
mccli world load --name "My World"
mccli world load --name my_world
```

**Arguments:**
- `--name` - World folder name or display name (required)

**Response:**
```json
{
  "success": true,
  "name": "my_world",
  "display_name": "My World",
  "loading": true
}
```

### world create

Open the world selection screen for creating new worlds.

```bash
mccli world create
```

**Response:**
```json
{
  "success": true,
  "screen_opened": true,
  "note": "Select world screen opened. Use 'Create New World' button or select an existing world."
}
```

### world delete

Delete a singleplayer world.

```bash
mccli world delete --name "My World"
mccli world delete --name my_world
```

**Arguments:**
- `--name` - World folder name or display name (required)

**Response:**
```json
{
  "success": true,
  "name": "my_world",
  "display_name": "My World",
  "deleted": true
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
| `--instance NAME` / `-i NAME` | Connect to named instance (name or port) |
| `--host HOST` | MC-CLI server host (default: auto-detect or localhost) |
| `--port PORT` | MC-CLI server port (default: auto-detect or 25580) |
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
