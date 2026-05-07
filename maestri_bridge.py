import asyncio
import json
import random
import socket
import subprocess
import logging
import websockets
from zeroconf.asyncio import AsyncZeroconf
from zeroconf import ServiceInfo, IPVersion
from datetime import datetime

# Configuration
PORT = 8765
PIN = "966243"
MAESTRI_CLI = "/var/folders/1d/fyk6zxzs6gdf53xr1z0gs6n80000gn/T/maestri-ae631192/maestri"

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("MaestriBridge")

class WorkspaceMonitor:
    def __init__(self, cli_path):
        self.cli_path = cli_path
        self.last_state = {}
        self.last_terminal_seq = {} # nodeId -> last seq sent
        self.clients = set()

    def get_snapshot(self):
        try:
            result = subprocess.run([self.cli_path, "list"], capture_output=True, text=True, timeout=5)
            output = result.stdout.strip()
            nodes = []
            lines = output.split("\n")
            for line in lines:
                if 'name: "' in line:
                    parts = line.split('"')
                    if len(parts) > 1:
                        name = parts[1]
                        node_id = name.lower().replace(" ", "_")
                        # Real-ish status: If they appear in list, assume running for now
                        status = "RUNNING" 
                        nodes.append({
                            "id": node_id,
                            "label": name,
                            "agentType": self.infer_type(name),
                            "status": status,
                            "connectedTo": []
                        })
            return nodes
        except Exception as e:
            logger.error(f"Failed to get workspace list: {e}")
            return []

    def infer_type(self, name):
        name_lower = name.lower()
        if "claude" in name_lower: return "CLAUDE_CODE"
        if "gemini" in name_lower: return "GEMINI"
        if "codex" in name_lower: return "CODEX"
        return "SHELL"

    async def watch_status(self):
        while True:
            nodes = self.get_snapshot()
            current_state = {n["id"]: n["status"] for n in nodes}
            
            if current_state != self.last_state:
                for node in nodes:
                    if node["status"] != self.last_state.get(node["id"]):
                        await self.broadcast({
                            "type": "node_status",
                            "nodeId": node["id"],
                            "status": node["status"]
                        })
                self.last_state = current_state
            await asyncio.sleep(3)

    async def watch_terminals(self):
        while True:
            if not self.clients:
                await asyncio.sleep(2)
                continue
                
            nodes = self.get_snapshot()
            for node in nodes:
                try:
                    # 'maestri check' returns the latest buffer
                    result = subprocess.run([self.cli_path, "check", node["label"]], capture_output=True, text=True, timeout=2)
                    output = result.stdout.strip()
                    if output:
                        lines = output.split("\n")
                        # Send only new lines? For now, just send the last 5 if changed
                        # In a real bridge, we'd have a persistent seq number.
                        # Mocking seq for now.
                        for i, line in enumerate(lines[-5:]):
                            await self.broadcast({
                                "type": "terminal_line",
                                "nodeId": node["id"],
                                "line": line,
                                "seq": int(time.time() * 1000) + i
                            })
                except Exception as e:
                    pass
            await asyncio.sleep(2)

    async def broadcast(self, message):
        if not self.clients: return
        msg_json = json.dumps(message)
        disconnected = set()
        for client in self.clients:
            try:
                await client.send(msg_json)
            except:
                disconnected.add(client)
        self.clients -= disconnected

monitor = WorkspaceMonitor(MAESTRI_CLI)

async def handle_client(websocket):
    client_pin = websocket.request_headers.get("X-Maestri-PIN")
    if client_pin != PIN:
        await websocket.close(1011, "Unauthorized")
        return

    logger.info(f"Client connected from {websocket.remote_address}")
    monitor.clients.add(websocket)
    
    try:
        nodes = monitor.get_snapshot()
        await websocket.send(json.dumps({
            "type": "workspace_snapshot",
            "workspace": {
                "id": "maestri_live",
                "name": "Maestri Canvas",
                "nodes": nodes,
                "notes": [],
                "connections": []
            }
        }))

        async for message in websocket:
            data = json.loads(message)
            msg_type = data.get("type")
            
            if msg_type == "terminal_input":
                node_id = data.get("nodeId")
                text = data.get("text")
                label = next((n["label"] for n in monitor.get_snapshot() if n["id"] == node_id), None)
                if label:
                    subprocess.Popen([MAESTRI_CLI, "ask", label, text])
            
            elif msg_type == "ombro_request":
                await websocket.send(json.dumps({
                    "type": "ombro_summary",
                    "generatedAt": datetime.now().isoformat(),
                    "text": f"Bridge is active. Monitoring {len(nodes)} agents.",
                    "nextSteps": ["Check agent status", "View terminal output"]
                }))
                    
    finally:
        monitor.clients.remove(websocket)

async def main():
    # LAN IP Discovery
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
    finally:
        s.close()

    logger.info(f"Maestri Bridge v3.1 | IP: {local_ip} | PIN: {PIN}")

    aiozc = AsyncZeroconf(ip_version=IPVersion.V4Only)
    info = ServiceInfo(
        "_maestri._tcp.local.",
        "Maestri Bridge._maestri._tcp.local.",
        addresses=[socket.inet_aton(local_ip)],
        port=PORT,
        properties={'version': '1.1'},
        server="maestri-bridge.local.",
    )
    await aiozc.async_register_service(info)

    asyncio.create_task(monitor.watch_status())
    asyncio.create_task(monitor.watch_terminals())

    async with websockets.serve(handle_client, "0.0.0.0", PORT):
        await asyncio.Future()

if __name__ == "__main__":
    import time
    asyncio.run(main())
