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

**Response:**
```json
{
  "success": true,
  "data": {
    "path": "/absolute/path/screenshot.png",
    "width": 1920,
    "height": 1080,
    "metadata": {
      "timestamp": "2024-01-15T10:30:45Z",
      "player": {"x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "pitch": 0.0},
      "time": 6000,
      "dimension": "minecraft:overworld",
      "iris_loaded": true,
      "shader": {"active": true, "name": "ComplementaryShaders_v4.6"},
      "resource_packs": ["vanilla"]
    }
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
    "clear": false,
    "since": 0
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "logs": [
      {"id": 42, "timestamp": "2024-01-15T10:30:45Z", "level": "info", "logger": "net.minecraft", "message": "Loading world"}
    ],
    "count": 1,
    "last_id": 42,
    "total_buffered": 128
  }
}
```

### execute

Run Minecraft command.

**Request:**
```json
{"command": "execute", "params": {"command": "weather clear"}}
```

### server

Server connection management.

**Request (connect):**
```json
{"command": "server", "params": {"action": "connect", "address": "play.example.com", "port": 25565}}
```

**Request (disconnect):**
```json
{"command": "server", "params": {"action": "disconnect"}}
```

**Request (status):**
```json
{"command": "server", "params": {"action": "status"}}
```

**Response (connect):**
```json
{
  "success": true,
  "data": {
    "success": true,
    "connecting": true,
    "address": "play.example.com",
    "port": 25565
  }
}
```

**Response (disconnect):**
```json
{
  "success": true,
  "data": {
    "success": true,
    "disconnected": true,
    "was_multiplayer": true,
    "previous_world": "multiplayer"
  }
}
```

**Response (status):**
```json
{
  "success": true,
  "data": {
    "connected": true,
    "multiplayer": true,
    "server_name": "Example Server",
    "server_address": "play.example.com:25565",
    "player_count": 12
  }
}
```

### resourcepack

Resource pack management.

**Request (list):**
```json
{"command": "resourcepack", "params": {"action": "list"}}
```

**Request (enabled):**
```json
{"command": "resourcepack", "params": {"action": "enabled"}}
```

**Request (enable):**
```json
{"command": "resourcepack", "params": {"action": "enable", "name": "MyPack"}}
```

**Request (disable):**
```json
{"command": "resourcepack", "params": {"action": "disable", "name": "MyPack"}}
```

**Request (reload):**
```json
{"command": "resourcepack", "params": {"action": "reload"}}
```

### chat

Chat send/history.

**Request (send):**
```json
{"command": "chat", "params": {"action": "send", "message": "hello"}}
```

**Request (history):**
```json
{"command": "chat", "params": {"action": "history", "limit": 20}}
```

### item

Item inspection.

**Request (hand):**
```json
{"command": "item", "params": {"action": "hand", "hand": "main", "include_nbt": true}}
```

**Request (slot):**
```json
{"command": "item", "params": {"action": "slot", "slot": 5}}
```

### inventory

Inventory listing.

**Request:**
```json
{"command": "inventory", "params": {"action": "list", "section": "hotbar", "include_empty": false}}
```

### block

Block probes.

**Request (target):**
```json
{"command": "block", "params": {"action": "target", "max_distance": 5, "include_nbt": false}}
```

**Request (at):**
```json
{"command": "block", "params": {"action": "at", "x": 10, "y": 64, "z": -20, "include_nbt": true}}
```

### entity

Entity probes.

**Request (target):**
```json
{"command": "entity", "params": {"action": "target", "max_distance": 6, "include_nbt": true}}
```

## Example Session

```
Client: {"id":"1","command":"status"}\n
Server: {"id":"1","success":true,"data":{"in_game":true,"time":6000,...}}\n

Client: {"id":"2","command":"shader","params":{"action":"reload"}}\n
Server: {"id":"2","success":true,"data":{"reloaded":true,"has_errors":false}}\n

Client: {"id":"3","command":"screenshot","params":{"path":"/tmp/test.png","clean":true}}\n
Server: {"id":"3","success":true,"data":{"path":"/tmp/test.png","width":1920,"height":1080,"metadata":{"time":6000,"dimension":"minecraft:overworld"}}}\n
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
