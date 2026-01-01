"""
MC-CLI Instance Registry

Discovers and manages MC-CLI instances registered by the Minecraft mod.
"""

from __future__ import annotations

import json
import os
import socket
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, List


REGISTRY_DIR = Path.home() / ".mccli"
REGISTRY_FILE = REGISTRY_DIR / "instances.json"


@dataclass
class Instance:
    """A registered MC-CLI instance."""
    name: str
    port: int
    pid: int
    start_time: int
    version: str
    host: str = "localhost"

    @property
    def address(self) -> str:
        """Get host:port address string."""
        return f"{self.host}:{self.port}"

    def is_alive(self) -> bool:
        """Check if this instance is still running and reachable."""
        # First check if the process is alive
        try:
            os.kill(self.pid, 0)
        except OSError:
            return False

        # Then check if we can connect
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(1.0)
            result = sock.connect_ex((self.host, self.port))
            sock.close()
            return result == 0
        except socket.error:
            return False


def list_instances(include_dead: bool = False) -> List[Instance]:
    """
    List all registered MC-CLI instances.

    Args:
        include_dead: If True, include instances that are no longer reachable.

    Returns:
        List of Instance objects.
    """
    if not REGISTRY_FILE.exists():
        return []

    try:
        with open(REGISTRY_FILE, "r") as f:
            data = json.load(f)
    except (json.JSONDecodeError, IOError):
        return []

    instances = []
    for entry in data:
        instance = Instance(
            name=entry.get("name", "unknown"),
            port=entry.get("port", 0),
            pid=entry.get("pid", 0),
            start_time=entry.get("startTime", 0),
            version=entry.get("version", "unknown"),
        )
        if include_dead or instance.is_alive():
            instances.append(instance)

    return instances


def find_instance(name_or_port: str) -> Optional[Instance]:
    """
    Find an instance by name or port.

    Args:
        name_or_port: Instance name or port number (as string).

    Returns:
        Instance if found, None otherwise.
    """
    instances = list_instances()

    # Try to match by port number
    try:
        port = int(name_or_port)
        for instance in instances:
            if instance.port == port:
                return instance
    except ValueError:
        pass

    # Match by name (case-insensitive, partial match)
    name_lower = name_or_port.lower()
    for instance in instances:
        if instance.name.lower() == name_lower:
            return instance

    # Partial match
    for instance in instances:
        if name_lower in instance.name.lower():
            return instance

    return None


def get_default_instance() -> Optional[Instance]:
    """
    Get the default instance to connect to.

    If there's only one instance, return it.
    If there are multiple, return None (user must specify).
    """
    instances = list_instances()
    if len(instances) == 1:
        return instances[0]
    return None


def resolve_connection(
    host: Optional[str] = None,
    port: Optional[int] = None,
    instance: Optional[str] = None,
) -> tuple[str, int]:
    """
    Resolve connection parameters from various inputs.

    Priority:
    1. Explicit host/port if both provided
    2. Named instance lookup
    3. Default instance (if only one)
    4. Fallback to localhost:25580

    Args:
        host: Explicit host address.
        port: Explicit port number.
        instance: Instance name or port to look up.

    Returns:
        Tuple of (host, port).

    Raises:
        ValueError: If instance specified but not found, or if multiple
                    instances exist and none specified.
    """
    # If both host and port are explicitly provided, use them
    if host and port:
        return (host, port)

    # If instance name/port is provided, look it up
    if instance:
        found = find_instance(instance)
        if not found:
            available = list_instances()
            if available:
                names = ", ".join(f"{i.name}:{i.port}" for i in available)
                raise ValueError(
                    f"Instance '{instance}' not found. Available: {names}"
                )
            else:
                raise ValueError(
                    f"Instance '{instance}' not found. No instances registered."
                )
        return (found.host, found.port)

    # Try to get default instance
    default = get_default_instance()
    if default:
        return (default.host, default.port)

    # Check if there are multiple instances (ambiguous)
    instances = list_instances()
    if len(instances) > 1:
        names = ", ".join(f"{i.name}:{i.port}" for i in instances)
        raise ValueError(
            f"Multiple instances running. Specify one with --instance: {names}"
        )

    # Fallback to default
    return (host or "localhost", port or 25580)
