#!/usr/bin/env python3
"""
MC-CLI: Minecraft Command-Line Interface

Usage:
    mccli <command> [options]

Commands:
    status          Check game connection and state
    shader          Shader management (list, get, set, reload, errors)
    capture         Take a screenshot
    analyze         Analyze screenshot metrics
    compare         Compare two screenshots
    teleport        Move player to coordinates
    time            Get or set world time
    perf            Get performance metrics
    logs            Get game logs
    execute         Run a Minecraft command
"""

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
            if args.value is not None:
                mc.time_set(args.value)
                print(f"Time set to: {args.value}")
            else:
                time = mc.time_get()
                if args.json:
                    output({"time": time}, True)
                else:
                    print(f"World time: {time}")
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
            logs = mc.logs(
                level=args.level,
                limit=args.limit,
                filter=args.filter,
                clear=args.clear
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
    time_p.add_argument("value", nargs="?", help="Time value (0-24000 or name)")

    # perf
    sub.add_parser("perf", help="Get performance metrics")

    # logs
    logs_p = sub.add_parser("logs", help="Get game logs")
    logs_p.add_argument("--level", default="info", choices=["error", "warn", "info", "debug"])
    logs_p.add_argument("--limit", type=int, default=50, help="Max entries")
    logs_p.add_argument("--filter", help="Regex filter pattern")
    logs_p.add_argument("--clear", action="store_true", help="Clear logs after returning")

    # execute
    exec_p = sub.add_parser("execute", help="Run a Minecraft command")
    exec_p.add_argument("cmd", metavar="command", help="Command to execute (without /)")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    commands = {
        "status": cmd_status,
        "shader": cmd_shader,
        "capture": cmd_capture,
        "analyze": cmd_analyze,
        "compare": cmd_compare,
        "teleport": cmd_teleport,
        "time": cmd_time,
        "perf": cmd_perf,
        "logs": cmd_logs,
        "execute": lambda a: cmd_execute(argparse.Namespace(**{**vars(a), "command": a.cmd})),
    }

    return commands[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
