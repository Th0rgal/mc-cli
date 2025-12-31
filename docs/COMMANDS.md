# MC-CLI Command Reference

Complete reference for all MC-CLI commands.

## Quick Reference

| Command | Description |
|---------|-------------|
| `status` | Get game state |
| `teleport x y z` | Move player |
| `camera yaw pitch` | Set view direction |
| `time [value]` | Get/set world time |
| `shader list\|get\|set\|reload\|errors\|disable` | Shader management |
| `capture -o path [--clean]` | Take screenshot |
| `analyze path` | Analyze screenshot |
| `compare a b` | Compare screenshots |
| `perf` | Performance metrics |
| `logs [--level LEVEL]` | Get game logs |
| `execute command` | Run Minecraft command |

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
mccli time

# Set time (ticks)
mccli time 6000

# Set time (named)
mccli time noon
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
  "height": 1080
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
  "entity_count": 42,
  "gpu": "NVIDIA GeForce RTX 3080"
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
```

**Arguments:**
- `--level` - Minimum log level: error, warn, info, debug (default: info)
- `--limit` - Max number of entries (default: 50)
- `--filter` - Regex pattern to filter messages
- `--clear` - Clear logs after returning

**Response:**
```json
{
  "logs": [
    {
      "timestamp": "2024-01-15T10:30:45Z",
      "level": "info",
      "logger": "net.irisshaders.iris",
      "message": "Reloading shader pack"
    }
  ],
  "count": 1
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
mccli time noon
mccli capture -o captures/noon.png --clean

mccli time sunset
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
