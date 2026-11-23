package com.example.bridge

object BridgeScriptGenerator {

    fun buildSetupScript(
        secrets: BridgeSecrets,
        httpPort: Int,
        sshPort: Int,
        allowedRootsCsv: String
    ): String {
        val escapedRoots = allowedRootsCsv.replace("'", "")
        return """
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "=== TERMUX BRIDGE 2.0 • Instalador ==="
mkdir -p ~/.termux-bridge ~/.ssh ~/.termux/boot
chmod 700 ~/.termux-bridge ~/.ssh

echo "[1/4] Verificando dependências (python, openssh)..."
pkg update -y >/dev/null 2>&1 || true
pkg install -y python openssh >/dev/null 2>&1

echo "[2/4] Gravando credenciais autorizadas..."
cat > ~/.termux-bridge/bridge.env <<'EOF'
BRIDGE_HOST=127.0.0.1
BRIDGE_PORT=$httpPort
BRIDGE_TOKEN=${secrets.apiToken}
ALLOWED_ROOTS=$escapedRoots
EOF
chmod 600 ~/.termux-bridge/bridge.env

touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
if ! grep -qF "${secrets.sshPublicKeyOpenSsh}" ~/.ssh/authorized_keys; then
  echo "${secrets.sshPublicKeyOpenSsh}" >> ~/.ssh/authorized_keys
fi

if ! grep -q "^Port " "${'$'}PREFIX/etc/ssh/sshd_config" 2>/dev/null; then
  echo "Port $sshPort" >> "${'$'}PREFIX/etc/ssh/sshd_config"
fi

echo "[3/4] Instalando daemon HTTP local em 127.0.0.1:$httpPort..."
cat > ~/.termux-bridge/bridge_server.py <<'PYEOF'
${PYTHON_BRIDGE_DAEMON}
PYEOF
chmod 700 ~/.termux-bridge/bridge_server.py

echo "[4/4] Configurando inicialização..."
cat > ~/.termux-bridge/start_bridge.sh <<'SH_EOF'
#!/data/data/com.termux/files/usr/bin/bash
set -a
source ~/.termux-bridge/bridge.env
set +a
pgrep -f "sshd" >/dev/null 2>&1 || sshd || true
pkill -f "bridge_server.py" >/dev/null 2>&1 || true
nohup python3 ~/.termux-bridge/bridge_server.py > ~/.termux-bridge/bridge.log 2>&1 &
echo "Termux Bridge ativo em http://127.0.0.1:${'$'}BRIDGE_PORT (SSH :$sshPort)"
echo "Usuário SSH: $(whoami)"
SH_EOF
chmod 700 ~/.termux-bridge/start_bridge.sh
cp ~/.termux-bridge/start_bridge.sh ~/.termux/boot/start_termux_bridge.sh

bash ~/.termux-bridge/start_bridge.sh
echo "=== Instalação concluída! Volte ao TERMUX 2.0 e toque em Testar Conexão. ==="
""".trimIndent()
    }

    private const val PYTHON_BRIDGE_DAEMON = """
import hmac
import json
import os
import platform
import signal
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = os.environ.get("BRIDGE_HOST", "127.0.0.1")
PORT = int(os.environ.get("BRIDGE_PORT", "8765"))
TOKEN = os.environ.get("BRIDGE_TOKEN", "")
ALLOWED_ROOTS = [
    os.path.realpath(p.strip())
    for p in os.environ.get("ALLOWED_ROOTS", os.path.expanduser("~")).split(",")
    if p.strip()
]

TASKS = {}
TASKS_LOCK = threading.Lock()


def is_allowed_cwd(path):
    real = os.path.realpath(path)
    return any(real == root or real.startswith(root + os.sep) for root in ALLOWED_ROOTS)


def read_stream(stream, buf, lock):
    try:
        while True:
            chunk = stream.read(1024)
            if not chunk:
                break
            with lock:
                buf.append(chunk)
    finally:
        stream.close()


def detect_listening_ports():
    ports = set()
    for path in ("/proc/net/tcp", "/proc/net/tcp6"):
        try:
            with open(path, "r") as f:
                for line in f.readlines()[1:]:
                    cols = line.split()
                    if len(cols) >= 4 and cols[3] == "0A":
                        port_hex = cols[1].split(":")[1]
                        p = int(port_hex, 16)
                        if p != PORT:
                            ports.add(p)
        except Exception:
            pass
    return sorted(ports)[:20]


class BridgeHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def _authorized(self):
        # Reject browser/webview cross-origin requests explicitly
        if self.headers.get("Origin"):
            self._send_json(403, {"error": "Origin header rejected by bridge policy"})
            return False
        auth = self.headers.get("Authorization", "")
        expected = f"Bearer {TOKEN}"
        if not TOKEN or not hmac.compare_digest(auth, expected):
            self._send_json(401, {"error": "Unauthorized"})
            return False
        return True

    def _send_json(self, code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self._authorized():
            return
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            with TASKS_LOCK:
                running = sum(1 for t in TASKS.values() if t["running"])
            self._send_json(200, {
                "status": "online",
                "user": os.environ.get("USER", "termux"),
                "home": os.path.expanduser("~"),
                "arch": platform.machine(),
                "runningTasks": running,
                "listeningPorts": detect_listening_ports(),
            })
            return

        if parsed.path.startswith("/tasks/"):
            task_id = parsed.path.split("/tasks/", 1)[1].strip("/")
            qs = parse_qs(parsed.query)
            out_off = int(qs.get("offset_out", ["0"])[0])
            err_off = int(qs.get("offset_err", ["0"])[0])
            with TASKS_LOCK:
                task = TASKS.get(task_id)
            if not task:
                self._send_json(404, {"error": "Task not found"})
                return
            with task["lock"]:
                out_str = "".join(task["stdout"])
                err_str = "".join(task["stderr"])
                self._send_json(200, {
                    "taskId": task_id,
                    "running": task["running"],
                    "exitCode": task["exitCode"],
                    "stdoutChunk": out_str[out_off:],
                    "stderrChunk": err_str[err_off:],
                    "nextOutOffset": len(out_str),
                    "nextErrOffset": len(err_str),
                    "cwd": task["cwd"],
                })
            return

        self._send_json(404, {"error": "Unknown route"})

    def do_POST(self):
        if not self._authorized():
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length > 0 else b"{}"
        payload = json.loads(raw.decode("utf-8") or "{}")

        if self.path == "/exec":
            cmd = payload.get("command", "").strip()
            cwd = payload.get("cwd") or os.path.expanduser("~")
            if not cmd:
                self._send_json(400, {"error": "Empty command"})
                return
            if not is_allowed_cwd(cwd):
                self._send_json(403, {"error": f"Directory not allowed: {cwd}"})
                return

            task_id = str(uuid.uuid4())
            proc = subprocess.Popen(
                ["bash", "-lc", cmd],
                cwd=cwd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                preexec_fn=os.setsid,
            )
            task_lock = threading.Lock()
            task = {
                "proc": proc,
                "stdout": [],
                "stderr": [],
                "running": True,
                "exitCode": None,
                "cwd": cwd,
                "lock": task_lock,
            }
            with TASKS_LOCK:
                TASKS[task_id] = task

            def wait_proc():
                t1 = threading.Thread(target=read_stream, args=(proc.stdout, task["stdout"], task_lock), daemon=True)
                t2 = threading.Thread(target=read_stream, args=(proc.stderr, task["stderr"], task_lock), daemon=True)
                t1.start()
                t2.start()
                code = proc.wait()
                t1.join(timeout=2)
                t2.join(timeout=2)
                with task_lock:
                    task["exitCode"] = code
                    task["running"] = False

            threading.Thread(target=wait_proc, daemon=True).start()
            self._send_json(200, {"taskId": task_id, "pid": proc.pid})
            return

        if self.path.startswith("/tasks/") and self.path.endswith("/kill"):
            task_id = self.path[len("/tasks/"):-len("/kill")].strip("/")
            with TASKS_LOCK:
                task = TASKS.get(task_id)
            if not task:
                self._send_json(404, {"error": "Task not found"})
                return
            proc = task["proc"]
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                time.sleep(0.2)
                if proc.poll() is None:
                    os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
            except Exception:
                pass
            self._send_json(200, {"killed": True})
            return

        self._send_json(404, {"error": "Unknown route"})


if __name__ == "__main__":
    if HOST not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit("Refusing non-loopback bind")
    ThreadingHTTPServer((HOST, PORT), BridgeHandler).serve_forever()
"""
}
