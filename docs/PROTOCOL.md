# MC-CLI Protocol

Technical specification for the MC-CLI TCP protocol.

## Overview

MC-CLI uses a simple JSON-over-TCP protocol:
- TCP connection on port 25580
- Newline-delimited JSON messages
- Request-response pattern
- Asynchronous command execution

## Connection

```
Client                          Server (Minecraft)
   |                                   |
   |----------- TCP Connect ---------->|
   |                                   |
   |-- {"command": "status"} + \n ---->|
   |                                   |
   |<---- {"success": true, ...} + \n -|
   |                                   |
   |----------- TCP Close ------------>|
```

## Request Format

```json
{
  "id": "optional-request-id",
  "command": "command-name",
  "params": {
    "param1": "value1",
    "param2": 123
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | No | Request ID for correlation |
| `command` | string | Yes | Command name |
| `params` | object | No | Command parameters |

## Response Format

### Success

```json
{
  "id": "request-id",
  "success": true,
  "data": {
    "result": "value"
  }
}
```

### Error

```json
{
  "id": "request-id",
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Echoed request ID (if provided) |
| `success` | boolean | Whether command succeeded |
| `data` | object | Command result (on success) |
| `error` | object | Error details (on failure) |

## Error Codes

| Code | Description |
|------|-------------|
| `PARSE_ERROR` | Invalid JSON in request |
| `UNKNOWN_COMMAND` | Command not recognized |
| `INVALID_PARAMS` | Missing or invalid parameters |
| `EXECUTION_ERROR` | Command execution failed |
| `NOT_IN_GAME` | Player not in world |

## Thread Model

```
Socket Thread                      Main Game Thread
     |                                    |
     |-- receive request -->              |
     |-- parse JSON -->                   |
     |-- queue task -------------------->
     |                                    |
     |                              <-- execute on tick
     |                                    |
     <-- response -----------------------|
```

MC-CLI runs a TCP server on a background thread. All Minecraft operations
execute on the main game thread via a task queue processed each tick.

## Commands

### status

Get game state.

**Request:**
```json
{"command": "status"}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "in_game": true,
    "world_type": "singleplayer",
    "player": {"x": 100, "y": 64, "z": -200, "yaw": -45, "pitch": 15},
    "time": 6000,
    "dimension": "minecraft:overworld",
    "iris_loaded": true,
    "shader": {"active": true, "name": "Complementary"}
  }
}
```

### teleport

Move player.

**Request:**
```json
{"command": "teleport", "params": {"x": 100, "y": 64, "z": -200}}
```

### camera

Set view direction.

**Request:**
```json
{"command": "camera", "params": {"yaw": -45, "pitch": 15}}
```

### time

Get or set world time.

**Request (get):**
```json
{"command": "time", "params": {"action": "get"}}
```

**Request (set):**
```json
{"command": "time", "params": {"action": "set", "value": 6000}}
```

### shader

Shader management.

**Request (list):**
```json
{"command": "shader", "params": {"action": "list"}}
```

**Request (get):**
```json
{"command": "shader", "params": {"action": "get"}}
```

**Request (set):**
```json
{"command": "shader", "params": {"action": "set", "name": "BSL_v8"}}
```

**Request (reload):**
```json
{"command": "shader", "params": {"action": "reload"}}
```

**Request (errors):**
```json
{"command": "shader", "params": {"action": "errors"}}
```

**Request (disable):**
```json
{"command": "shader", "params": {"action": "disable"}}
```

### screenshot

Take screenshot.

**Request:**
```json
{
  "command": "screenshot",
  "params": {
    "path": "/absolute/path/screenshot.png",
    "clean": true,
    "delay_ms": 100,
    "settle_ms": 200
  }
}
```

### perf

Get performance metrics.

**Request:**
```json
{"command": "perf"}
```

### logs

Get game logs.

**Request:**
```json
{
  "command": "logs",
  "params": {
    "level": "info",
    "limit": 50,
    "filter": "shader",
    "clear": false
  }
}
```

### execute

Run Minecraft command.

**Request:**
```json
{"command": "execute", "params": {"command": "weather clear"}}
```

## Example Session

```
Client: {"id":"1","command":"status"}\n
Server: {"id":"1","success":true,"data":{"in_game":true,"time":6000,...}}\n

Client: {"id":"2","command":"shader","params":{"action":"reload"}}\n
Server: {"id":"2","success":true,"data":{"reloaded":true,"has_errors":false}}\n

Client: {"id":"3","command":"screenshot","params":{"path":"/tmp/test.png","clean":true}}\n
Server: {"id":"3","success":true,"data":{"path":"/tmp/test.png","width":1920,"height":1080}}\n
```

## Implementation Notes

### Python Client

```python
import socket
import json

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(("localhost", 25580))

# Send request
request = {"id": "1", "command": "status"}
sock.sendall((json.dumps(request) + "\n").encode())

# Receive response
buffer = ""
while "\n" not in buffer:
    buffer += sock.recv(4096).decode()
response = json.loads(buffer.split("\n")[0])

print(response)
sock.close()
```

### Timeout Handling

For long operations (screenshot with delay), increase socket timeout:

```python
sock.settimeout(30.0)  # 30 seconds
```

### Concurrent Requests

The server handles requests sequentially per connection. For parallel operations,
use multiple connections.
