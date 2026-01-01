"""
MC-CLI TCP Client

Connects to the MC-CLI mod running inside Minecraft and sends commands.
All methods return structured data suitable for LLM consumption.
"""

from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import Optional, Any, Union, List


@dataclass
class CommandResult:
    """Result of a command execution."""
    success: bool
    data: dict
    error: Optional[dict] = None


class Client:
    """
    TCP client for MC-CLI Minecraft control mod.

    Usage:
        with Client() as mc:
            status = mc.status()
            mc.shader_reload()
    """

    def __init__(self, host: str = "localhost", port: int = 25580, timeout: float = 10.0):
        """
        Initialize client.

        Args:
            host: MC-CLI server host (default: localhost)
            port: MC-CLI server port (default: 25580)
            timeout: Socket timeout in seconds (default: 10.0)
        """
        self.host = host
        self.port = port
        self.timeout = timeout
        self._socket: Optional[socket.socket] = None
        self._request_id = 0
        self._buffer = ""

    def connect(self) -> None:
        """Establish TCP connection."""
        try:
            self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._socket.settimeout(self.timeout)
            self._socket.connect((self.host, self.port))
        except (socket.error, ConnectionRefusedError) as e:
            self._socket = None
            raise ConnectionError(f"Failed to connect to MC-CLI at {self.host}:{self.port}: {e}")

    def disconnect(self) -> None:
        """Close TCP connection."""
        if self._socket:
            try:
                self._socket.close()
            except socket.error:
                pass
            self._socket = None

    def __enter__(self) -> "Client":
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> bool:
        self.disconnect()
        return False

    def _next_id(self) -> str:
        self._request_id += 1
        return str(self._request_id)

    def _send(self, command: str, params: Optional[dict] = None) -> CommandResult:
        """Send a command and receive the response."""
        if not self._socket:
            raise ConnectionError("Not connected to MC-CLI")

        request = {
            "id": self._next_id(),
            "command": command,
            "params": params or {}
        }

        # Send request
        request_json = json.dumps(request) + "\n"
        self._socket.sendall(request_json.encode("utf-8"))

        # Receive response
        while "\n" not in self._buffer:
            chunk = self._socket.recv(4096).decode("utf-8")
            if not chunk:
                raise ConnectionError("Connection closed by server")
            self._buffer += chunk

        line, self._buffer = self._buffer.split("\n", 1)
        response = json.loads(line)

        return CommandResult(
            success=response.get("success", False),
            data=response.get("data", {}),
            error=response.get("error")
        )

    def command(self, command: str, params: Optional[dict] = None) -> dict:
        """
        Send a raw command with params and return data payload.

        Useful for macro runners and LLM-driven workflows.
        """
        result = self._send(command, params)
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Core Commands
    # =========================================================================

    def status(self) -> dict:
        """
        Get current game state.

        Returns:
            dict with keys:
            - in_game: bool
            - world_type: "singleplayer" | "multiplayer"
            - player: {x, y, z, yaw, pitch}
            - time: int (0-24000)
            - dimension: str
            - iris_loaded: bool
            - shader: {active: bool, name: str}
        """
        result = self._send("status")
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def teleport(self, x: float, y: float, z: float) -> dict:
        """
        Teleport player to coordinates.

        Args:
            x, y, z: Target coordinates

        Returns:
            dict with final position {x, y, z}
        """
        result = self._send("teleport", {"x": x, "y": y, "z": z})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def camera(self, yaw: float, pitch: float) -> dict:
        """
        Set camera direction.

        Args:
            yaw: Horizontal rotation (-180 to 180)
            pitch: Vertical rotation (-90 to 90)

        Returns:
            dict with final rotation {yaw, pitch}
        """
        result = self._send("camera", {"yaw": yaw, "pitch": pitch})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def time_get(self) -> int:
        """
        Get current world time.

        Returns:
            World time (0-24000)
        """
        result = self._send("time", {"action": "get"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("time", 0)

    def time_set(self, value: int | str) -> None:
        """
        Set world time.

        Args:
            value: Time in ticks (0-24000) or named time
                   ("sunrise", "day", "noon", "sunset", "night", "midnight")
        """
        result = self._send("time", {"action": "set", "value": value})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")

    def execute(self, command: str) -> dict:
        """
        Execute a Minecraft command.

        Args:
            command: Command to execute (without leading /)

        Returns:
            dict with {executed: bool, command: str}
        """
        result = self._send("execute", {"command": command})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Shader Commands
    # =========================================================================

    def shader_list(self) -> list[dict]:
        """
        List available shader packs.

        Returns:
            List of {name: str, type: "directory" | "zip"}
        """
        result = self._send("shader", {"action": "list"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("packs", [])

    def shader_get(self) -> dict:
        """
        Get current shader info.

        Returns:
            dict with {active: bool, name: str}
        """
        result = self._send("shader", {"action": "get"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def shader_set(self, name: str) -> None:
        """
        Set active shader pack.

        Args:
            name: Shader pack name
        """
        result = self._send("shader", {"action": "set", "name": name})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")

    def shader_reload(self) -> dict:
        """
        Reload current shader.

        Returns:
            dict with {reloaded: bool, has_errors: bool, errors: list}
        """
        result = self._send("shader", {"action": "reload"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def shader_disable(self) -> None:
        """Disable shaders."""
        result = self._send("shader", {"action": "disable"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")

    def shader_errors(self) -> dict:
        """
        Get shader compilation errors.

        Returns:
            dict with:
            - has_errors: bool
            - count: int
            - errors: list of {file: str, line: int, message: str}
        """
        result = self._send("shader", {"action": "errors"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Visual Commands
    # =========================================================================

    def screenshot(
        self,
        path: str,
        clean: bool = False,
        delay_ms: int = 0,
        settle_ms: int = 200
    ) -> dict:
        """
        Take a screenshot.

        Args:
            path: Absolute path to save screenshot
            clean: Hide HUD before capture (default: False)
            delay_ms: Delay before capture in ms (default: 0)
            settle_ms: Additional delay after cleanup when clean=True (default: 200)

        Returns:
            dict with {path: str, width: int, height: int}
        """
        params = {
            "path": path,
            "clean": clean,
            "delay_ms": delay_ms,
            "settle_ms": settle_ms
        }
        result = self._send("screenshot", params)
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Debugging Commands
    # =========================================================================

    def perf(self) -> dict:
        """
        Get performance metrics.

        Returns:
            dict with:
            - fps: int
            - frame_time_ms: float
            - memory: {used_mb: int, max_mb: int, percent: float}
            - chunk_updates: int
            - entity_count: int
        """
        result = self._send("perf")
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def logs(
        self,
        level: str = "info",
        limit: int = 50,
        filter: Optional[str] = None,
        clear: bool = False,
        since: int = 0,
        return_meta: bool = False
    ) -> list[dict] | dict:
        """
        Get recent game logs.

        Args:
            level: Minimum log level ("error", "warn", "info", "debug")
            limit: Maximum number of entries
            filter: Regex pattern to filter messages
            clear: Clear logs after returning
            since: Only return entries with id > since
            return_meta: Return full response with cursor data

        Returns:
            List of {id, timestamp, level, logger, message} or full response dict
        """
        params = {"level": level, "limit": limit, "clear": clear}
        if filter:
            params["filter"] = filter
        if since:
            params["since"] = since

        result = self._send("logs", params)
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        if return_meta:
            return result.data
        return result.data.get("logs", [])

    # =========================================================================
    # Inspection Commands
    # =========================================================================

    def item_hand(self, hand: str = "main", include_nbt: bool = True) -> dict:
        """
        Get item in main or off hand.
        """
        params = {"action": "hand", "hand": hand, "include_nbt": include_nbt}
        return self.command("item", params)

    def item_slot(self, slot: int, include_nbt: bool = True) -> dict:
        """
        Get item in a specific inventory slot.
        """
        params = {"action": "slot", "slot": slot, "include_nbt": include_nbt}
        return self.command("item", params)

    def inventory_list(
        self,
        section: Optional[str] = None,
        include_empty: bool = False,
        include_nbt: bool = False
    ) -> dict:
        """
        List inventory items by section.
        """
        params: dict[str, Any] = {
            "action": "list",
            "include_empty": include_empty,
            "include_nbt": include_nbt
        }
        if section:
            params["section"] = section
        return self.command("inventory", params)

    def block_target(self, max_distance: float = 5.0, include_nbt: bool = False) -> dict:
        """
        Get the targeted block.
        """
        params = {"action": "target", "max_distance": max_distance, "include_nbt": include_nbt}
        return self.command("block", params)

    def block_at(self, x: int, y: int, z: int, include_nbt: bool = False) -> dict:
        """
        Get block info at a position.
        """
        params = {"action": "at", "x": x, "y": y, "z": z, "include_nbt": include_nbt}
        return self.command("block", params)

    def entity_target(self, max_distance: float = 5.0, include_nbt: bool = False) -> dict:
        """
        Get the targeted entity.
        """
        params = {"action": "target", "max_distance": max_distance, "include_nbt": include_nbt}
        return self.command("entity", params)

    # =========================================================================
    # Resource Pack Commands
    # =========================================================================

    def resourcepack_list(self) -> list[dict]:
        """
        List all available resource packs.

        Returns:
            List of {id: str, name: str, description: str, enabled: bool, required: bool}
        """
        result = self._send("resourcepack", {"action": "list"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("packs", [])

    def resourcepack_enabled(self) -> list[dict]:
        """
        List currently enabled resource packs.

        Returns:
            List of {id: str, name: str, description: str}
        """
        result = self._send("resourcepack", {"action": "enabled"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("packs", [])

    def resourcepack_enable(self, name: str) -> dict:
        """
        Enable a resource pack.

        Args:
            name: Resource pack ID or display name

        Returns:
            dict with {success: bool, id: str, name: str}
        """
        result = self._send("resourcepack", {"action": "enable", "name": name})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def resourcepack_disable(self, name: str) -> dict:
        """
        Disable a resource pack.

        Args:
            name: Resource pack ID or display name

        Returns:
            dict with {success: bool, id: str, name: str}
        """
        result = self._send("resourcepack", {"action": "disable", "name": name})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def resourcepack_reload(self) -> dict:
        """
        Reload all resource packs.

        Returns:
            dict with {success: bool, reloading: bool}
        """
        result = self._send("resourcepack", {"action": "reload"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Chat Commands
    # =========================================================================

    def chat_send(self, message: str) -> dict:
        """
        Send a chat message or command.

        Args:
            message: Message to send. If starts with /, sent as command.

        Returns:
            dict with {sent: bool, type: "chat" | "command", message/command: str}
        """
        result = self._send("chat", {"action": "send", "message": message})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def chat_history(
        self,
        limit: int = 50,
        type: Optional[str] = None,
        filter: Optional[str] = None
    ) -> list[dict]:
        """
        Get recent chat messages.

        Args:
            limit: Maximum number of messages (default: 50)
            type: Filter by type ("chat", "system")
            filter: Regex pattern to filter content

        Returns:
            List of {timestamp: str, type: str, sender: str?, content: str}
        """
        params = {"action": "history", "limit": limit}
        if type:
            params["type"] = type
        if filter:
            params["filter"] = filter

        result = self._send("chat", params)
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("messages", [])

    def chat_clear(self) -> int:
        """
        Clear chat history buffer.

        Returns:
            Number of messages cleared
        """
        result = self._send("chat", {"action": "clear"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("cleared", 0)

    # =========================================================================
    # Server Connection Commands
    # =========================================================================

    def server_connect(
        self,
        address: str,
        port: int = 25565,
        resourcepack_policy: str = "prompt"
    ) -> dict:
        """
        Connect to a multiplayer server.

        Args:
            address: Server address (hostname or IP)
            port: Server port (default: 25565)
            resourcepack_policy: How to handle server resource packs:
                - "prompt" (default): Show normal prompt to user
                - "accept": Automatically accept and download
                - "reject": Automatically decline

        Returns:
            dict with {success: bool, connecting: bool, address: str, port: int, resourcepack_policy: str}
        """
        result = self._send("server", {
            "action": "connect",
            "address": address,
            "port": port,
            "resourcepack_policy": resourcepack_policy
        })
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def server_disconnect(self) -> dict:
        """
        Disconnect from current server/world.

        Returns:
            dict with {success: bool, disconnected: bool, was_multiplayer: bool}
        """
        result = self._send("server", {"action": "disconnect"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def server_status(self) -> dict:
        """
        Get current server connection status.

        Returns:
            dict with:
            - connected: bool
            - multiplayer: bool (if connected)
            - server_name: str (if multiplayer)
            - server_address: str (if multiplayer)
            - player_count: int (if multiplayer)
            - world_name: str (if singleplayer)
        """
        result = self._send("server", {"action": "status"})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    def server_connection_error(self, clear: bool = False) -> dict:
        """
        Get the last connection/disconnection error.

        This captures errors shown on the DisconnectedScreen, such as:
        - Invalid session (requires restart)
        - Server is full
        - You are banned
        - Connection timed out

        Args:
            clear: Clear the error after returning (default: False)

        Returns:
            dict with:
            - has_error: bool
            - error: str (the error message, if has_error)
            - timestamp: int (unix timestamp in ms, if has_error)
            - recent: bool (true if error is < 30 seconds old)
            - server_address: str (if known)
            - cleared: bool (if clear was requested)
        """
        result = self._send("server", {"action": "connection_error", "clear": clear})
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data

    # =========================================================================
    # Interaction Commands
    # =========================================================================

    def interact_use(self, hand: str = "main") -> dict:
        """
        Use item in hand (right-click in air).

        Args:
            hand: "main" or "off" (default: "main")

        Returns:
            dict with {result: str, item: dict}
        """
        return self.command("interact", {"action": "use", "hand": hand})

    def interact_use_on_block(
        self,
        hand: str = "main",
        x: Optional[int] = None,
        y: Optional[int] = None,
        z: Optional[int] = None,
        face: str = "up",
        inside_block: bool = False
    ) -> dict:
        """
        Use item on a block (right-click on block).

        Args:
            hand: "main" or "off" (default: "main")
            x, y, z: Target block position (optional, uses crosshair if not specified)
            face: Block face to click on (default: "up")
            inside_block: Whether click is inside the block (default: False)

        Returns:
            dict with {result: str, item: dict, block_pos: {x, y, z}}
        """
        params: dict[str, Any] = {"action": "use_on_block", "hand": hand, "face": face, "inside_block": inside_block}
        if x is not None and y is not None and z is not None:
            params["x"] = x
            params["y"] = y
            params["z"] = z
        return self.command("interact", params)

    def interact_attack(
        self,
        target: str = "air",
        x: Optional[int] = None,
        y: Optional[int] = None,
        z: Optional[int] = None,
        face: str = "up"
    ) -> dict:
        """
        Attack / left-click action.

        Args:
            target: "block" or "air" (default: "air" - swings arm)
            x, y, z: Block position for target="block" (optional)
            face: Block face (default: "up")

        Returns:
            dict with {result: str, block_pos?: {x, y, z}}
        """
        params: dict[str, Any] = {"action": "attack", "target": target, "face": face}
        if x is not None and y is not None and z is not None:
            params["x"] = x
            params["y"] = y
            params["z"] = z
        return self.command("interact", params)

    def interact_drop(self, slot: Optional[int] = None, all: bool = False) -> dict:
        """
        Drop item(s) from inventory.

        Args:
            slot: Inventory slot to drop from (default: currently held slot)
            all: Drop entire stack (default: False, drops single item)

        Returns:
            dict with {dropped: bool, item: dict, count: int}
        """
        params: dict[str, Any] = {"action": "drop", "all": all}
        if slot is not None:
            params["slot"] = slot
        return self.command("interact", params)

    def interact_swap(self, from_slot: int, to_slot: int) -> dict:
        """
        Swap items between slots.

        Args:
            from_slot: Source slot
            to_slot: Destination slot

        Returns:
            dict with {success: bool, from_item: dict, to_item: dict}
        """
        return self.command("interact", {"action": "swap", "from_slot": from_slot, "to_slot": to_slot})

    def interact_select(self, slot: int) -> dict:
        """
        Select hotbar slot.

        Args:
            slot: Hotbar slot (0-8)

        Returns:
            dict with {slot: int, item: dict}
        """
        return self.command("interact", {"action": "select", "slot": slot})

    # =========================================================================
    # Window Commands
    # =========================================================================

    def window_focus_grab(self, enabled: bool) -> dict:
        """
        Enable or disable window focus grabbing.

        When disabled, Minecraft will not steal focus from other applications.
        Essential for automated/background testing.

        Args:
            enabled: True to allow focus grabs, False to suppress them

        Returns:
            dict with {focus_grab_enabled: bool}
        """
        return self.command("window", {"action": "focus_grab", "enabled": enabled})

    def window_pause_on_lost_focus(self, enabled: bool) -> dict:
        """
        Enable or disable pause-on-lost-focus behavior.

        When disabled, the pause menu won't appear when the window loses focus,
        allowing screenshots and commands to work in the background.

        Args:
            enabled: True to show pause menu on focus loss, False to disable it

        Returns:
            dict with {pause_on_lost_focus_enabled: bool}
        """
        return self.command("window", {"action": "pause_on_lost_focus", "enabled": enabled})

    def window_focus(self) -> dict:
        """
        Manually request window focus.

        Returns:
            dict with {focused: bool}
        """
        return self.command("window", {"action": "focus"})

    def window_close_screen(self) -> dict:
        """
        Close any currently open GUI screen.

        Returns:
            dict with {closed: bool, screen_type?: str}
        """
        return self.command("window", {"action": "close_screen"})

    def window_status(self) -> dict:
        """
        Get current window/focus status.

        Returns:
            dict with {focus_grab_enabled: bool, screen_open: bool, screen_type?: str}
        """
        return self.command("window", {"action": "status"})
