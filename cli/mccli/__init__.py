"""
MC-CLI: Minecraft Command-Line Interface for LLM-Assisted Shader Development

This package provides a Python client for controlling Minecraft via the MC-CLI mod.
Designed for integration with AI/LLM agents for shader development workflows.

Usage:
    from mccli import Client

    with Client() as mc:
        status = mc.status()
        print(f"In game: {status['in_game']}")

        mc.shader_reload()
        errors = mc.shader_errors()
        if errors['has_errors']:
            for err in errors['errors']:
                print(f"{err['file']}:{err['line']}: {err['message']}")
"""

from .client import Client, CommandResult

__version__ = "1.0.0"
__all__ = ["Client", "CommandResult"]
