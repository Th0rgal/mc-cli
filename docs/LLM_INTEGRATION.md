# LLM Integration Guide

How to use MC-CLI with AI/LLM agents for shader development.

## Design Principles

MC-CLI is designed for LLM consumption:

1. **Structured Output**: All commands output JSON for parsing
2. **Quantitative Metrics**: Numbers instead of subjective descriptions
3. **Clear Errors**: Structured error messages with codes
4. **Reproducibility**: Same inputs produce same outputs

## Workflow Example

### 1. Check Connection

```bash
mccli status --json
```

Parse the response to verify `in_game: true` and `shader.active: true`.

### 2. Edit Shader Code

Make changes to shader files (e.g., `shaders/final.fsh`).

### 3. Reload and Check Errors

```bash
mccli shader reload --json
```

Parse `has_errors` and `errors` array:

```python
result = subprocess.run(["mccli", "shader", "reload", "--json"],
                        capture_output=True, text=True)
data = json.loads(result.stdout)

if data["has_errors"]:
    for err in data["errors"]:
        # Feed errors back to LLM
        print(f"ERROR: {err['file']}:{err['line']}: {err['message']}")
```

### 4. Capture Test Screenshots

```bash
mccli time noon
mccli capture -o /tmp/noon.png --clean --json

mccli time sunset
mccli capture -o /tmp/sunset.png --clean --json
```

### 5. Analyze Results

```bash
mccli analyze /tmp/noon.png --json
```

Parse metrics:

```python
metrics = json.loads(result.stdout)

issues = []
if metrics["brightness"]["mean"] < 60:
    issues.append("Image is too dark - increase exposure")
if metrics["saturation_mean"] < 0.2:
    issues.append("Image is desaturated - increase vibrance")
if metrics["color_temp"] > 0.6:
    issues.append("Image has blue color cast - warm up white balance")
```

### 6. Compare with Baseline

```bash
mccli compare baseline/noon.png /tmp/noon.png --json
```

```python
diff = json.loads(result.stdout)["differences"]

if abs(diff["brightness"]) > 20:
    print(f"Significant brightness change: {diff['brightness']:+.1f}")
if abs(diff["color_temp"]) > 0.1:
    print(f"Color temperature shift: {diff['color_temp']:+.3f}")
```

## Python SDK

For tighter integration, use the Python client directly:

```python
from mccli import Client
from mccli.analysis import analyze, compare

def develop_shader_iteration():
    with Client() as mc:
        # Reload shader
        result = mc.shader_reload()

        if result["has_errors"]:
            return {
                "status": "error",
                "errors": result["errors"]
            }

        # Capture test scenes
        captures = {}
        for time_name in ["noon", "sunset", "midnight"]:
            mc.time_set(time_name)
            path = f"/tmp/test_{time_name}.png"
            mc.screenshot(path, clean=True)
            captures[time_name] = path

        # Analyze
        results = {}
        for name, path in captures.items():
            metrics = analyze(path)
            results[name] = {
                "metrics": metrics.to_dict(),
                "issues": metrics.diagnose()
            }

        return {
            "status": "success",
            "results": results
        }
```

### Raw Command Access

Use `Client.command` to send any protocol command directly (useful for macros):

```python
from mccli import Client

with Client() as mc:
    block = mc.command("block", {"action": "target", "max_distance": 6})
    item = mc.command("item", {"action": "hand", "hand": "main"})
```

## Inspecting Items and Blocks

Use structured probes to debug custom items, resource packs, and plugin outputs.

```bash
# Held item details (includes NBT by default)
mccli item --hand main --json

# Inventory snapshot
mccli inventory --section hotbar --json

# Targeted block and block entity data
mccli block --include-nbt --json

# Targeted entity data
mccli entity --include-nbt --json
```

These commands return structured IDs, names, counts, and SNBT payloads that LLMs can
parse and compare across iterations.

## Log Streaming for Debugging

Stream logs until a pattern appears:

```bash
mccli logs --follow --until "Exception" --timeout 10000
```

For incremental reads, use `--since` with the log entry ID.

## JSON Macro Scripts

Automate multi-step test flows using a JSON macro:

```json
{
  "stop_on_error": true,
  "steps": [
    {"command": "time", "params": {"action": "set", "value": "noon"}},
    {"wait_ms": 200},
    {"command": "screenshot", "params": {"path": "/tmp/noon.png", "clean": true}},
    {"local": "analyze", "path": "/tmp/noon.png"}
  ]
}
```

Run:
```bash
mccli macro ./macro.json --json
```

## Metric Interpretation

### Brightness

| Range | Interpretation |
|-------|----------------|
| 0-30 | Very dark, likely underexposed |
| 30-60 | Dark, may need exposure boost |
| 60-180 | Normal range |
| 180-220 | Bright, may need exposure reduction |
| 220-255 | Very bright, likely overexposed |

### Contrast Ratio

| Value | Interpretation |
|-------|----------------|
| < 2 | Very low contrast, flat image |
| 2-10 | Low contrast |
| 10-50 | Normal range |
| 50-100 | High contrast |
| > 100 | Extreme contrast, possible clipping |

### Color Temperature

| Value | Interpretation |
|-------|----------------|
| 0.0-0.3 | Warm (red/orange cast) |
| 0.3-0.7 | Neutral |
| 0.7-1.0 | Cool (blue cast) |

### Saturation

| Value | Interpretation |
|-------|----------------|
| 0.0-0.2 | Low saturation, nearly grayscale |
| 0.2-0.5 | Moderate saturation |
| 0.5-0.8 | High saturation, vivid colors |
| 0.8-1.0 | Very high saturation, may look artificial |

## Diagnostic Codes

These codes appear in `diagnose()` output:

| Code | Description | Suggested Action |
|------|-------------|------------------|
| `UNDEREXPOSED` | Mean < 30 | Increase exposure/brightness |
| `DARK` | Mean < 60 | Slightly increase exposure |
| `OVEREXPOSED` | Mean > 220 | Decrease exposure |
| `BRIGHT` | Mean > 180 | Slightly decrease exposure |
| `LOW_CONTRAST` | Std < 20 | Increase contrast |
| `HIGH_CONTRAST` | Ratio > 100 | Reduce contrast or fix tonemapping |
| `DESATURATED` | Sat < 0.1 | Increase saturation/vibrance |
| `VERY_WARM` | Temp < 0.3 | Cool down white balance |
| `VERY_COOL` | Temp > 0.7 | Warm up white balance |
| `SHADOW_CLIPPING` | >10% in shadows | Lift shadows or reduce contrast |
| `HIGHLIGHT_CLIPPING` | >10% in highlights | Reduce highlights or exposure |

## Structured Error Handling

All errors include structured data:

```json
{
  "success": false,
  "error": {
    "code": "NOT_IN_GAME",
    "message": "Player not in game"
  }
}
```

Common error codes:

| Code | Meaning |
|------|---------|
| `NOT_IN_GAME` | Player not in a world |
| `IRIS_NOT_LOADED` | Iris mod not installed |
| `SHADER_NOT_FOUND` | Shader pack doesn't exist |
| `INVALID_PARAMS` | Missing or invalid parameters |
| `EXECUTION_ERROR` | Command execution failed |

## Performance Monitoring

Use `perf` to monitor for performance regressions:

```python
with Client() as mc:
    mc.shader_reload()

    # Wait for shader to stabilize
    time.sleep(2)

    perf = mc.perf()

    if perf["fps"] < 30:
        print(f"WARNING: Low FPS ({perf['fps']})")
    if perf["frame_time_ms"] > 33:
        print(f"WARNING: High frame time ({perf['frame_time_ms']:.1f}ms)")
```

## Automated Testing

Example test script:

```python
#!/usr/bin/env python3
from mccli import Client
from mccli.analysis import analyze

BASELINE_DIR = "baseline/"
TEST_SCENES = ["noon", "sunset", "midnight"]
TOLERANCE = {
    "brightness": 10,
    "contrast": 5,
    "color_temp": 0.05,
    "saturation": 0.05
}

def run_tests():
    with Client() as mc:
        mc.shader_reload()

        for scene in TEST_SCENES:
            mc.time_set(scene)
            path = f"/tmp/test_{scene}.png"
            mc.screenshot(path, clean=True)

            current = analyze(path)
            baseline = analyze(f"{BASELINE_DIR}/{scene}.png")

            # Check metrics
            if abs(current.brightness_mean - baseline.brightness_mean) > TOLERANCE["brightness"]:
                print(f"FAIL {scene}: Brightness deviation")

            issues = current.diagnose()
            if issues:
                print(f"WARN {scene}: {issues}")

if __name__ == "__main__":
    run_tests()
```

## Tips for LLM Agents

1. **Always check `success`** before processing `data`
2. **Use `--json`** for all commands when parsing output
3. **Check `shader.errors`** after reload before capturing
4. **Wait for `settle_ms`** after scene changes for stable captures
5. **Compare with baselines** rather than absolute thresholds
6. **Monitor `perf`** to detect performance regressions
7. **Filter `logs`** by "iris" or "shader" for relevant messages
