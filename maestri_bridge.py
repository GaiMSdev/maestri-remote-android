#!/usr/bin/env python3
import asyncio
import json
import logging
import os
import random
import signal
import socket
import string
import subprocess
import sys
import time
from datetime import datetime, timezone

from websockets.asyncio.server import serve, ServerConnection
from zeroconf.asyncio import AsyncZeroconf
from zeroconf import ServiceInfo, IPVersion

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    stream=sys.stderr,
)
logger = logging.getLogger("MaestriBridge")

PORT = 8765

cli_path: str = os.environ.get("MAESTRI_CLI", "")
if not cli_path:
    for candidate in [
        "/opt/homebrew/bin/maestri",
        "/usr/local/bin/maestri",
        "/usr/bin/maestri",
        os.path.expanduser("~/.local/bin/maestri"),
    ]:
        if os.access(candidate, os.X_OK):
            cli_path = candidate
            break
    if not cli_path:
        import shutil
        cli_path = shutil.which("maestri") or ""

pin = "".join(random.choices(string.digits, k=6))


class WorkspaceMonitor:
    def __init__(self, cli: str):
        self.cli = cli
        self.clients: set[ServerConnection] = set()
        self.last_nodes: list[dict] = []
        self.last_terminal: dict[str, str] = {}
        self.terminal_seqs: dict[str, int] = {}

    def get_snapshot(self) -> list[dict]:
        try:
            result = subprocess.run(
                [self.cli, "list"], capture_output=True, text=True, timeout=5
            )
            nodes: list[dict] = []
            for line in result.stdout.split("\n"):
                if '"' not in line:
                    continue
                parts = line.split('"')
                if len(parts) < 2:
                    continue
                name = parts[1]
                node_id = name.lower().translate(
                    str.maketrans("", "", string.punctuation.replace("_", ""))
                ).replace(" ", "_")
                nodes.append({
                    "id": node_id,
                    "label": name,
                    "agentType": self._infer_type(name),
                    "status": "RUNNING",
                    "connectedTo": [],
                })
            return nodes
        except subprocess.TimeoutExpired:
            return self.last_nodes
        except Exception as e:
            logger.error("get_snapshot: %s", e)
            return self.last_nodes

    def _infer_type(self, name: str) -> str:
        lower = name.lower()
        if "claude" in lower:
            return "CLAUDE_CODE"
        if "gemini" in lower:
            return "GEMINI"
        if "codex" in lower:
            return "CODEX"
        if "shell" in lower:
            return "SHELL"
        return "UNKNOWN"

    async def broadcast(self, msg: dict) -> None:
        if not self.clients:
            return
        data = json.dumps(msg)
        dead: set[ServerConnection] = set()
        for c in self.clients:
            try:
                await c.send(data)
            except Exception:
                dead.add(c)
        self.clients -= dead

    async def watch_status(self) -> None:
        while True:
            nodes = self.get_snapshot()
            if nodes != self.last_nodes:
                for n in nodes:
                    old = next(
                        (x for x in self.last_nodes if x["id"] == n["id"]), None
                    )
                    if old is None or old["status"] != n["status"]:
                        await self.broadcast({
                            "type": "node_status",
                            "nodeId": n["id"],
                            "status": n["status"],
                        })
            self.last_nodes = nodes
            await asyncio.sleep(5)

    async def watch_terminals(self) -> None:
        while True:
            if not self.clients:
                await asyncio.sleep(2)
                continue
            for node in self.last_nodes:
                try:
                    result = subprocess.run(
                        [self.cli, "check", node["label"]],
                        capture_output=True,
                        text=True,
                        timeout=3,
                    )
                    output = result.stdout
                    previous = self.last_terminal.get(node["id"], "")
                    if output == previous or not output:
                        continue
                    old_lines = previous.split("\n") if previous else []
                    new_lines = output.split("\n")
                    for line in new_lines[len(old_lines):]:
                        stripped = line.strip()
                        if not stripped:
                            continue
                        seq = self.terminal_seqs.get(node["id"], 0) + 1
                        self.terminal_seqs[node["id"]] = seq
                        await self.broadcast({
                            "type": "terminal_line",
                            "nodeId": node["id"],
                            "line": stripped,
                            "seq": seq,
                        })
                    self.last_terminal[node["id"]] = output
                except Exception:
                    pass
            await asyncio.sleep(2)


async def handle_client(conn: ServerConnection) -> None:
    req = conn.request
    client_pin = req.headers.get("X-Maestri-PIN", "") if req else ""
    if client_pin != pin:
        logger.warning("Auth rejected from %s", conn.remote_address)
        await conn.close(1011, "auth_failed")
        return

    logger.info("Client authenticated: %s", conn.remote_address)
    monitor.clients.add(conn)

    try:
        nodes = monitor.get_snapshot()
        await conn.send(json.dumps({
            "type": "workspace_snapshot",
            "workspace": {
                "id": "maestri_live",
                "name": "Maestri",
                "nodes": nodes,
                "notes": [],
                "connections": [],
            },
        }))
        monitor.last_nodes = nodes

        async for raw in conn:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            t = msg.get("type")
            if t == "terminal_input":
                node_id = msg.get("nodeId")
                text = msg.get("text")
                label = next(
                    (n["label"] for n in monitor.last_nodes if n["id"] == node_id),
                    None,
                )
                if label:
                    subprocess.Popen([cli_path, "ask", label, text])
                else:
                    logger.warning("Unknown node for terminal_input: %s", node_id)
            elif t == "ombro_request":
                await conn.send(json.dumps({
                    "type": "ombro_summary",
                    "generatedAt": datetime.now(timezone.utc).isoformat(),
                    "text": f"Bridge active. {len(monitor.last_nodes)} agent(s) on canvas.",
                    "nextSteps": [f"Check {n['label']}" for n in monitor.last_nodes],
                }))
            elif t == "note_update":
                note_id = msg.get("noteId")
                content = msg.get("content", "")
                subprocess.Popen([cli_path, "note", "write", note_id, content])
            elif t == "auth":
                pass
            elif t == "ping":
                pass
            else:
                pass
    except Exception as e:
        logger.error("Client error: %s", e)
    finally:
        monitor.clients.discard(conn)
        logger.info("Client disconnected: %s", conn.remote_address)


def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


async def main() -> None:
    local_ip = get_local_ip()
    logger.info("=" * 50)
    logger.info("Maestri Remote Bridge (Python)")
    logger.info(f"  IP:   {local_ip}")
    logger.info(f"  Port: {PORT}")
    logger.info(f"  PIN:  {pin}")
    logger.info(f"  CLI:  {cli_path}")
    logger.info("=" * 50)

    aiozc = AsyncZeroconf(ip_version=IPVersion.V4Only)
    info = ServiceInfo(
        "_maestri._tcp.local.",
        f"Maestri Bridge._maestri._tcp.local.",
        addresses=[socket.inet_aton(local_ip)],
        port=PORT,
        properties={"pin": pin, "version": "1.0"},
        server=f"maestri-bridge.local.",
    )
    await aiozc.async_register_service(info)
    logger.info("mDNS service registered: _maestri._tcp")

    asyncio.create_task(monitor.watch_status())
    asyncio.create_task(monitor.watch_terminals())

    stop = asyncio.Future()

    def shutdown():
        if not stop.done():
            stop.set_result(None)

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, shutdown)

    async with serve(handle_client, "0.0.0.0", PORT) as server:
        logger.info("WebSocket server listening on 0.0.0.0:%d", PORT)
        await stop

    logger.info("Shutting down...")
    await aiozc.async_unregister_service(info)
    await aiozc.async_close()


if __name__ == "__main__":
    monitor = WorkspaceMonitor(cli_path)
    asyncio.run(main())
