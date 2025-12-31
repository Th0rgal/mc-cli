"""
MC-CLI TCP Client

Connects to the MC-CLI mod running inside Minecraft and sends commands.
All methods return structured data suitable for LLM consumption.
"""

import json
import socket
from dataclasses import dataclass
from typing import Optional, Any


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
        clear: bool = False
    ) -> list[dict]:
        """
        Get recent game logs.

        Args:
            level: Minimum log level ("error", "warn", "info", "debug")
            limit: Maximum number of entries
            filter: Regex pattern to filter messages
            clear: Clear logs after returning

        Returns:
            List of {timestamp: str, level: str, logger: str, message: str}
        """
        params = {"level": level, "limit": limit, "clear": clear}
        if filter:
            params["filter"] = filter

        result = self._send("logs", params)
        if not result.success:
            raise RuntimeError(f"Command failed: {result.error}")
        return result.data.get("logs", [])

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
            type: Filter by type ("chat", "system", "game_info")
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
