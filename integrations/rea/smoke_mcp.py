#!/usr/bin/env python3
"""
Exercise the REA stdio MCP handshake without installing/connecting a tunnel.
Only starts the 'rea mcp' process on this machine, with current user privileges.
No APK, keys, network capture, or HVAC data is transmitted by this script.
"""
import json
import os
import select
import subprocess
import sys
import time

MAX_BYTES = 12 * 1024 * 1024
WAIT_SECONDS = 75


def send(proc, payload):
    proc.stdin.write((json.dumps(payload, separators=(",", ":")) + "\n").encode())
    proc.stdin.flush()


def receive(proc, message_id, buffer):
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline:
        while b"\n" in buffer[0]:
            line, buffer[0] = buffer[0].split(b"\n", 1)
            if not line.strip():
                continue
            value = json.loads(line.decode("utf-8"))
            if value.get("id") == message_id:
                if "error" in value:
                    raise RuntimeError(str(value["error"])[:400])
                return value.get("result", {})
        if proc.poll() is not None:
            raise RuntimeError(f"REA MCP ended early with exit status {proc.returncode}")
        ready, _, _ = select.select([proc.stdout], [], [], 0.25)
        if ready:
            chunk = os.read(proc.stdout.fileno(), 65536)
            if not chunk:
                raise RuntimeError("REA MCP closed stdout")
            buffer[0] += chunk
            if len(buffer[0]) > MAX_BYTES:
                raise RuntimeError("MCP output exceeds expected safety limit")
    raise TimeoutError(f"REA MCP request {message_id} timed out")


def main():
    proc = subprocess.Popen(
        ["rea", "mcp"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    buffer = [b""]
    try:
        send(proc, {
            "jsonrpc": "2.0", "id": 1, "method": "initialize",
            "params": {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "clima-one-rea-smoke", "version": "1.0.0"},
            },
        })
        initialized = receive(proc, 1, buffer)
        assert initialized.get("serverInfo"), "REA did not return serverInfo"
        send(proc, {"jsonrpc": "2.0", "method": "notifications/initialized"})
        send(proc, {"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
        data = receive(proc, 2, buffer)
        tools = data.get("tools")
        assert isinstance(tools, list) and tools, "REA returned no MCP tools"
        names = [tool.get("name") for tool in tools]
        print("PASS: rea mcp initialized with", initialized["serverInfo"])
        print("PASS:", len(names), "tools listed; first names:", names[:8])
        print("This verifies LOCAL stdio MCP only, NOT ChatGPT connection or tunnel.")
    finally:
        proc.terminate()
        try:
            proc.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate(timeout=5)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("REA local MCP smoke test FAILED:", str(exc), file=sys.stderr)
        sys.exit(1)
