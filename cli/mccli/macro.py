"""
JSON macro runner for MC-CLI.

Supports simple step-based automation for LLM workflows.
"""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path
from typing import Any

from .analysis import analyze, compare
from .client import Client


def _load_macro(path: str) -> dict:
    if path == "-":
        return json.load(sys.stdin)
    return json.loads(Path(path).read_text())


def run_macro(path: str, host: str, port: int, json_output: bool = False) -> dict:
    macro = _load_macro(path)
    steps = macro.get("steps", [])
    stop_on_error = macro.get("stop_on_error", True)

    results: list[dict[str, Any]] = []
    overall_success = True

    with Client(host, port) as mc:
        for idx, step in enumerate(steps):
            start = time.monotonic()
            step_type = _step_type(step)

            try:
                data = _run_step(step, mc)
                success = True
                error = None
            except Exception as exc:
                data = None
                success = False
                error = str(exc)
                overall_success = False

            results.append(
                {
                    "index": idx,
                    "type": step_type,
                    "success": success,
                    "data": data,
                    "error": error,
                    "took_ms": int((time.monotonic() - start) * 1000),
                }
            )

            if not success and stop_on_error:
                break

    return {"success": overall_success, "steps": results}


def _step_type(step: dict) -> str:
    if "command" in step:
        return f"command:{step.get('command')}"
    if "wait_ms" in step or "sleep_ms" in step or "wait_ticks" in step:
        return "wait"
    if "local" in step:
        return f"local:{step.get('local')}"
    return "unknown"


def _run_step(step: dict, mc: Client) -> dict:
    if "wait_ms" in step or "sleep_ms" in step:
        delay = step.get("wait_ms", step.get("sleep_ms", 0))
        time.sleep(delay / 1000.0)
        return {"wait_ms": delay}

    if "wait_ticks" in step:
        ticks = step.get("wait_ticks", 0)
        time.sleep((ticks * 50) / 1000.0)
        return {"wait_ticks": ticks}

    if "command" in step:
        params = step.get("params") or {}
        return mc.command(step["command"], params)

    if "local" in step:
        local = step.get("local")
        if local == "analyze":
            metrics = analyze(step["path"])
            return metrics.to_dict()
        if local == "compare":
            result = compare(step["a"], step["b"])
            return result.to_dict()
        raise ValueError(f"Unknown local step: {local}")

    raise ValueError("Invalid macro step")
