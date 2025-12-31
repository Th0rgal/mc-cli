#!/usr/bin/env python3
"""
MC-CLI: Minecraft Command-Line Interface

Usage:
    mccli <command> [options]

Commands:
    status          Check game connection and state
    shader          Shader management (list, get, set, reload, errors)
    resourcepack    Resource pack management (list, enabled, enable, disable, reload)
    chat            Chat messaging (send, history, clear)
    capture         Take a screenshot
    analyze         Analyze screenshot metrics
    compare         Compare two screenshots
    teleport        Move player to coordinates
    time            Get or set world time (time get / time set <value>)
    perf            Get performance metrics
    logs            Get game logs
    execute         Run a Minecraft command
    item            Inspect held item or inventory slot
    inventory       List inventory contents
    block           Probe targeted or specific block
    entity          Probe targeted entity
    macro           Run a JSON macro script
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .client import Client
from .analysis import analyze, compare, analyze_directory


def output(data, as_json: bool):
    """Print output in appropriate format."""
    if as_json:
        print(json.dumps(data, indent=2))
    else:
        if isinstance(data, dict):
            for key, value in data.items():
                if isinstance(value, dict):
                    print(f"{key}:")
                    for k, v in value.items():
                        print(f"  {k}: {v}")
                elif isinstance(value, list):
                    print(f"{key}:")
                    for item in value:
                        print(f"  - {item}")
                else:
                    print(f"{key}: {value}")
        else:
            print(data)


def cmd_status(args):
    """Check game status."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.status()
            output(data, args.json)
            return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_shader(args):
    """Shader management."""
    try:
        with Client(args.host, args.port) as mc:
            if args.action == "list":
                packs = mc.shader_list()
                if args.json:
                    output({"packs": packs, "count": len(packs)}, True)
                else:
                    print(f"Available shader packs ({len(packs)}):")
                    for p in packs:
                        print(f"  {p['name']} ({p['type']})")

            elif args.action == "get":
                data = mc.shader_get()
                output(data, args.json)

            elif args.action == "set":
                if not args.name:
                    print("Error: --name required for 'set' action")
                    return 1
                mc.shader_set(args.name)
                print(f"Shader set to: {args.name}")

            elif args.action == "reload":
                data = mc.shader_reload()
                if args.json:
                    output(data, True)
                else:
                    if data.get("has_errors"):
                        print("Shader reloaded with errors:")
                        for err in data.get("errors", []):
                            print(f"  {err['file']}:{err['line']}: {err['message']}")
                    else:
                        print("Shader reloaded successfully")

            elif args.action == "errors":
                data = mc.shader_errors()
                if args.json:
                    output(data, True)
                else:
                    if data.get("has_errors"):
                        print(f"Shader errors ({data['count']}):")
                        for err in data.get("errors", []):
                            print(f"  {err['file']}:{err['line']}: {err['message']}")
                    else:
                        print("No shader errors")

            elif args.action == "disable":
                mc.shader_disable()
                print("Shaders disabled")

            return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_capture(args):
    """Take a screenshot."""
    try:
        path = Path(args.output).absolute()
        path.parent.mkdir(parents=True, exist_ok=True)

        with Client(args.host, args.port) as mc:
            data = mc.screenshot(
                str(path),
                clean=args.clean,
                delay_ms=args.delay,
                settle_ms=args.settle
            )

            if args.json:
                output(data, True)
            else:
                print(f"Screenshot saved: {data['path']}")
                print(f"Size: {data['width']}x{data['height']}")
                meta = data.get("metadata", {})
                if meta:
                    print("Metadata:")
                    if meta.get("dimension"):
                        print(f"  dimension: {meta.get('dimension')}")
                    if meta.get("time") is not None:
                        print(f"  time: {meta.get('time')}")
                    if meta.get("shader"):
                        shader = meta.get("shader", {})
                        print(f"  shader: {shader.get('name')} (active={shader.get('active')})")

            return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_analyze(args):
    """Analyze screenshot."""
    path = Path(args.path)

    try:
        if path.is_dir():
            results = analyze_directory(path)
            if args.json:
                output([m.to_dict() for m in results], True)
            else:
                for m in results:
                    print(f"\n{Path(m.path).name}")
                    print(m.summary())
                    issues = m.diagnose()
                    if issues:
                        print("Issues:")
                        for i in issues:
                            print(f"  - {i}")
        else:
            metrics = analyze(path)
            if args.json:
                output(metrics.to_dict(), True)
            else:
                print(metrics.summary())
                issues = metrics.diagnose()
                if issues:
                    print("\nIssues:")
                    for i in issues:
                        print(f"  - {i}")

        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_compare(args):
    """Compare two screenshots."""
    try:
        result = compare(args.image_a, args.image_b)
        if args.json:
            output(result.to_dict(), True)
        else:
            print(f"Comparing: {Path(args.image_a).name} vs {Path(args.image_b).name}")
            print(f"  Brightness: {result.brightness_diff:+.1f}")
            print(f"  Contrast: {result.contrast_diff:+.1f}")
            print(f"  Color temp: {result.color_temp_diff:+.3f}")
            print(f"  Saturation: {result.saturation_diff:+.3f}")
            print(f"  Histogram correlation: {result.histogram_correlation:.3f}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_teleport(args):
    """Teleport player."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.teleport(args.x, args.y, args.z)
            if args.json:
                output(data, True)
            else:
                print(f"Teleported to: {data['x']:.1f}, {data['y']:.1f}, {data['z']:.1f}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_time(args):
    """Get or set time."""
    try:
        with Client(args.host, args.port) as mc:
            # Default to "get" if no subcommand specified
            action = getattr(args, "time_action", None) or "get"

            if action == "set":
                mc.time_set(args.value)
                print(f"Time set to: {args.value}")
            else:  # get
                time_val = mc.time_get()
                if args.json:
                    output({"time": time_val}, True)
                else:
                    # Convert ticks to human-readable
                    hour = (time_val // 1000 + 6) % 24
                    minute = (time_val % 1000) * 60 // 1000
                    print(f"World time: {time_val} ({hour:02d}:{minute:02d})")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_perf(args):
    """Get performance metrics."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.perf()
            output(data, args.json)
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_logs(args):
    """Get game logs."""
    try:
        with Client(args.host, args.port) as mc:
            if args.follow:
                last_id = args.since or 0
                import time
                import re

                start = time.monotonic()
                pattern = re.compile(args.until) if args.until else None

                while True:
                    resp = mc.logs(
                        level=args.level,
                        limit=args.limit,
                        filter=args.filter,
                        clear=args.clear,
                        since=last_id,
                        return_meta=True
                    )
                    logs = resp.get("logs", [])

                    for log in logs:
                        if args.json:
                            output(log, True)
                        else:
                            level = log["level"].upper()
                            print(f"[{level}] {log['logger']}: {log['message']}")

                        if pattern and pattern.search(log.get("message", "")):
                            return 0

                    last_id = resp.get("last_id", last_id)

                    if args.timeout is not None and (time.monotonic() - start) * 1000 > args.timeout:
                        return 1

                    time.sleep(args.interval / 1000.0)
            else:
                logs = mc.logs(
                    level=args.level,
                    limit=args.limit,
                    filter=args.filter,
                    clear=args.clear,
                    since=args.since or 0
                )

                if args.json:
                    output({"logs": logs, "count": len(logs)}, True)
                else:
                    for log in logs:
                        level = log["level"].upper()
                        print(f"[{level}] {log['logger']}: {log['message']}")

        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_execute(args):
    """Execute Minecraft command."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.execute(args.command)
            if args.json:
                output(data, True)
            else:
                print(f"Executed: /{data['command']}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_item(args):
    """Inspect items."""
    try:
        with Client(args.host, args.port) as mc:
            include_nbt = not args.no_nbt
            if args.slot is not None:
                data = mc.item_slot(args.slot, include_nbt=include_nbt)
            else:
                data = mc.item_hand(args.hand, include_nbt=include_nbt)

            if args.json:
                output(data, True)
            else:
                item = data.get("item", {})
                if item.get("empty"):
                    print("No item")
                else:
                    print(f"{item.get('name')} ({item.get('id')}) x{item.get('count')}")
                    if "custom_model_data" in item:
                        print(f"CustomModelData: {item.get('custom_model_data')}")
                    if "enchantments" in item:
                        print("Enchantments:")
                        for enchant in item.get("enchantments", []):
                            print(f"  {enchant.get('id')} {enchant.get('level')}")
                    if include_nbt and item.get("nbt"):
                        print(f"NBT: {item.get('nbt')}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_inventory(args):
    """List inventory contents."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.inventory_list(
                section=args.section,
                include_empty=args.include_empty,
                include_nbt=args.include_nbt
            )
            if args.json:
                output(data, True)
            else:
                for entry in data.get("items", []):
                    slot = entry.get("slot")
                    slot_type = entry.get("slot_type")
                    item = entry.get("item", {})
                    if item.get("empty"):
                        print(f"[{slot_type} {slot}] (empty)")
                    else:
                        print(f"[{slot_type} {slot}] {item.get('name')} ({item.get('id')}) x{item.get('count')}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_block(args):
    """Probe a block."""
    try:
        with Client(args.host, args.port) as mc:
            if args.x is not None and args.y is not None and args.z is not None:
                data = mc.block_at(args.x, args.y, args.z, include_nbt=args.include_nbt)
            else:
                data = mc.block_target(max_distance=args.max_distance, include_nbt=args.include_nbt)

            if args.json:
                output(data, True)
            else:
                if data.get("hit") is False:
                    print("No block targeted")
                else:
                    pos = data.get("pos", {})
                    print(f"{data.get('id')} at {pos.get('x')}, {pos.get('y')}, {pos.get('z')}")
                    if data.get("properties"):
                        print(f"Properties: {data.get('properties')}")
                    if args.include_nbt and data.get("block_entity"):
                        print(f"BlockEntity: {data.get('block_entity')}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_entity(args):
    """Probe a targeted entity."""
    try:
        with Client(args.host, args.port) as mc:
            data = mc.entity_target(max_distance=args.max_distance, include_nbt=args.include_nbt)

            if args.json:
                output(data, True)
            else:
                if data.get("hit") is False:
                    print("No entity targeted")
                else:
                    pos = data.get("pos", {})
                    print(f"{data.get('name')} ({data.get('id')}) at {pos.get('x')}, {pos.get('y')}, {pos.get('z')}")
                    if args.include_nbt and data.get("nbt"):
                        print(f"NBT: {data.get('nbt')}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_macro(args):
    """Run a JSON macro."""
    try:
        from .macro import run_macro

        results = run_macro(args.file, args.host, args.port, args.json)
        if args.json:
            output(results, True)
        else:
            for step in results.get("steps", []):
                status = "OK" if step.get("success") else "ERR"
                print(f"[{status}] {step.get('index')}: {step.get('type')}")
        return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_resourcepack(args):
    """Resource pack management."""
    try:
        with Client(args.host, args.port) as mc:
            if args.action == "list":
                packs = mc.resourcepack_list()
                if args.json:
                    output({"packs": packs, "count": len(packs)}, True)
                else:
                    print(f"Available resource packs ({len(packs)}):")
                    for p in packs:
                        status = "[enabled]" if p.get("enabled") else ""
                        required = "(required)" if p.get("required") else ""
                        print(f"  {p['id']} {status} {required}")
                        if p.get("description"):
                            print(f"    {p['description']}")

            elif args.action == "enabled":
                packs = mc.resourcepack_enabled()
                if args.json:
                    output({"packs": packs, "count": len(packs)}, True)
                else:
                    print(f"Enabled resource packs ({len(packs)}):")
                    for p in packs:
                        print(f"  {p['id']}")

            elif args.action == "enable":
                if not args.name:
                    print("Error: --name required for 'enable' action")
                    return 1
                data = mc.resourcepack_enable(args.name)
                if args.json:
                    output(data, True)
                else:
                    if data.get("success"):
                        if data.get("already_enabled"):
                            print(f"Resource pack already enabled: {args.name}")
                        else:
                            print(f"Resource pack enabled: {data.get('id')}")
                    else:
                        print(f"Failed to enable: {data.get('error')}")

            elif args.action == "disable":
                if not args.name:
                    print("Error: --name required for 'disable' action")
                    return 1
                data = mc.resourcepack_disable(args.name)
                if args.json:
                    output(data, True)
                else:
                    if data.get("success"):
                        if data.get("already_disabled"):
                            print(f"Resource pack already disabled: {args.name}")
                        else:
                            print(f"Resource pack disabled: {data.get('id')}")
                    else:
                        print(f"Failed to disable: {data.get('error')}")

            elif args.action == "reload":
                data = mc.resourcepack_reload()
                if args.json:
                    output(data, True)
                else:
                    print("Resource packs reloading...")

            return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def cmd_chat(args):
    """Chat messaging."""
    try:
        with Client(args.host, args.port) as mc:
            if args.action == "send":
                if not args.message:
                    print("Error: --message required for 'send' action")
                    return 1
                data = mc.chat_send(args.message)
                if args.json:
                    output(data, True)
                else:
                    if data.get("type") == "command":
                        print(f"Sent command: /{data.get('command')}")
                    else:
                        print(f"Sent message: {data.get('message')}")

            elif args.action == "history":
                messages = mc.chat_history(
                    limit=args.limit,
                    type=args.type,
                    filter=args.filter
                )
                if args.json:
                    output({"messages": messages, "count": len(messages)}, True)
                else:
                    print(f"Chat history ({len(messages)} messages):")
                    for msg in messages:
                        sender = msg.get("sender", "")
                        prefix = f"<{sender}> " if sender else f"[{msg['type']}] "
                        print(f"  {prefix}{msg['content']}")

            elif args.action == "clear":
                cleared = mc.chat_clear()
                if args.json:
                    output({"cleared": cleared}, True)
                else:
                    print(f"Cleared {cleared} messages from buffer")

            return 0
    except Exception as e:
        output({"error": str(e)}, args.json)
        return 1


def main():
    parser = argparse.ArgumentParser(
        description="MC-CLI: Minecraft Command-Line Interface for LLM-Assisted Shader Development",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--host", default="localhost", help="MC-CLI host (default: localhost)")
    parser.add_argument("--port", type=int, default=25580, help="MC-CLI port (default: 25580)")
    parser.add_argument("--json", action="store_true", help="Output as JSON")

    sub = parser.add_subparsers(dest="command", help="Commands")

    # status
    sub.add_parser("status", help="Check game status")

    # shader
    shader_p = sub.add_parser("shader", help="Shader management")
    shader_p.add_argument("action", choices=["list", "get", "set", "reload", "errors", "disable"])
    shader_p.add_argument("--name", help="Shader pack name (for 'set')")

    # capture
    cap_p = sub.add_parser("capture", help="Take a screenshot")
    cap_p.add_argument("-o", "--output", required=True, help="Output file path")
    cap_p.add_argument("--clean", action="store_true", help="Hide HUD before capture")
    cap_p.add_argument("--delay", type=int, default=0, help="Delay before capture (ms)")
    cap_p.add_argument("--settle", type=int, default=200, help="Settle time after cleanup (ms)")

    # analyze
    ana_p = sub.add_parser("analyze", help="Analyze screenshot")
    ana_p.add_argument("path", help="Image or directory to analyze")

    # compare
    cmp_p = sub.add_parser("compare", help="Compare two screenshots")
    cmp_p.add_argument("image_a", help="First image")
    cmp_p.add_argument("image_b", help="Second image")

    # teleport
    tp_p = sub.add_parser("teleport", help="Teleport player")
    tp_p.add_argument("x", type=float)
    tp_p.add_argument("y", type=float)
    tp_p.add_argument("z", type=float)

    # time
    time_p = sub.add_parser("time", help="Get or set world time")
    time_sub = time_p.add_subparsers(dest="time_action")
    time_sub.add_parser("get", help="Get current world time")
    time_set_p = time_sub.add_parser("set", help="Set world time")
    time_set_p.add_argument("value", help="Time value (0-24000 or: day, noon, night, midnight, sunrise, sunset)")

    # perf
    sub.add_parser("perf", help="Get performance metrics")

    # logs
    logs_p = sub.add_parser("logs", help="Get game logs")
    logs_p.add_argument("--level", default="info", choices=["error", "warn", "info", "debug"])
    logs_p.add_argument("--limit", type=int, default=50, help="Max entries")
    logs_p.add_argument("--filter", help="Regex filter pattern")
    logs_p.add_argument("--clear", action="store_true", help="Clear logs after returning")
    logs_p.add_argument("--since", type=int, help="Only return entries with id > since")
    logs_p.add_argument("--follow", action="store_true", help="Stream logs")
    logs_p.add_argument("--interval", type=int, default=500, help="Polling interval for --follow (ms)")
    logs_p.add_argument("--until", help="Regex to stop streaming when matched")
    logs_p.add_argument("--timeout", type=int, help="Stop streaming after timeout (ms)")

    # execute
    exec_p = sub.add_parser("execute", help="Run a Minecraft command")
    exec_p.add_argument("cmd", metavar="command", help="Command to execute (without /)")

    # item
    item_p = sub.add_parser("item", help="Inspect held item or inventory slot")
    item_p.add_argument("--hand", default="main", choices=["main", "off"], help="Which hand to inspect")
    item_p.add_argument("--slot", type=int, help="Inventory slot index")
    item_p.add_argument("--no-nbt", action="store_true", help="Exclude NBT from output")

    # inventory
    inv_p = sub.add_parser("inventory", help="List inventory contents")
    inv_p.add_argument("--section", choices=["hotbar", "main", "armor", "offhand"], help="Inventory section")
    inv_p.add_argument("--include-empty", action="store_true", help="Include empty slots")
    inv_p.add_argument("--include-nbt", action="store_true", help="Include NBT for each item")

    # block
    block_p = sub.add_parser("block", help="Probe targeted or specific block")
    block_p.add_argument("--x", type=int, help="Block X")
    block_p.add_argument("--y", type=int, help="Block Y")
    block_p.add_argument("--z", type=int, help="Block Z")
    block_p.add_argument("--max-distance", type=float, default=5.0, help="Max raycast distance")
    block_p.add_argument("--include-nbt", action="store_true", help="Include block entity NBT")

    # entity
    entity_p = sub.add_parser("entity", help="Probe targeted entity")
    entity_p.add_argument("--max-distance", type=float, default=5.0, help="Max target distance")
    entity_p.add_argument("--include-nbt", action="store_true", help="Include entity NBT")

    # macro
    macro_p = sub.add_parser("macro", help="Run a JSON macro")
    macro_p.add_argument("file", help="Path to macro JSON (or - for stdin)")

    # resourcepack
    rp_p = sub.add_parser("resourcepack", help="Resource pack management")
    rp_p.add_argument("action", choices=["list", "enabled", "enable", "disable", "reload"])
    rp_p.add_argument("--name", help="Resource pack name/ID (for 'enable' and 'disable')")

    # chat
    chat_p = sub.add_parser("chat", help="Chat messaging")
    chat_p.add_argument("action", choices=["send", "history", "clear"])
    chat_p.add_argument("--message", "-m", help="Message to send (for 'send')")
    chat_p.add_argument("--limit", type=int, default=50, help="Max messages (for 'history')")
    chat_p.add_argument("--type", choices=["chat", "system"], help="Filter by type")
    chat_p.add_argument("--filter", help="Regex filter pattern (for 'history')")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    commands = {
        "status": cmd_status,
        "shader": cmd_shader,
        "resourcepack": cmd_resourcepack,
        "chat": cmd_chat,
        "capture": cmd_capture,
        "analyze": cmd_analyze,
        "compare": cmd_compare,
        "teleport": cmd_teleport,
        "time": cmd_time,
        "perf": cmd_perf,
        "logs": cmd_logs,
        "execute": lambda a: cmd_execute(argparse.Namespace(**{**vars(a), "command": a.cmd})),
        "item": cmd_item,
        "inventory": cmd_inventory,
        "block": cmd_block,
        "entity": cmd_entity,
        "macro": cmd_macro,
    }

    return commands[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
