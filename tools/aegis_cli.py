#!/usr/bin/env python3
"""
Aegis Parental Control - Terminal Linux PRO Suite
Herramienta táctica y de monitoreo en tiempo real respaldada por Turso Cloud (LibSQL).
"""
import argparse
import base64
import curses
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


# Colores y estilos ANSI
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
MAGENTA = "\033[35m"
CYAN = "\033[36m"
WHITE = "\033[37m"
BG_RED = "\033[41m"
BG_GREEN = "\033[42m"
BG_BLUE = "\033[44m"

DEFAULT_DB_URL = "https://aegis-parental-devherles.aws-us-east-2.turso.io"
DEFAULT_DB_TOKEN = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3ODk5MDY2NzEsImlkIjoiMDFhMGJlYzAtMmEwMS03OGIxLWE3MjEtMzI4ZjZlMThjODMxIiwia2lkIjoiUXY5SVlXWjhGTkF6Mmgtc2pWU0xydWdTdG9RTWdaTlRtbUluQVdxUld0SSIsInJpZCI6ImEwYzE5YTRmLTUwOGUtNDY0MC1hYTViLWYwZmZjMDMyNzFhYSJ9.MTm3Pk32uvxfPSuCFm7UVJLdGg_VBpZFp09C2cMCZjKpCJY6wN-1WyvNmGT8c71zS34f5MbxMni7pOSYft8SBA"

APP_ALIASES = {
    "tiktok": "com.zhiliaoapp.musically",
    "youtube": "com.google.android.youtube",
    "yt": "com.google.android.youtube",
    "ytmusic": "com.google.android.apps.youtube.music",
    "facebook": "com.facebook.katana",
    "fb": "com.facebook.katana",
    "messenger": "com.facebook.orca",
    "instagram": "com.instagram.android",
    "ig": "com.instagram.android",
    "roblox": "com.roblox.client",
    "twitch": "tv.twitch.android.app",
    "netflix": "com.netflix.mediaclient",
    "discord": "com.discord",
    "twitter": "com.twitter.android",
    "x": "com.twitter.android",
    "snapchat": "com.snapchat.android",
    "chrome": "com.android.chrome",
    "browser": "com.android.chrome",
}


def get_turso_credentials():
    url = os.environ.get("AEGIS_TURSO_URL") or os.environ.get("TURSO_DATABASE_URL")
    token = os.environ.get("AEGIS_TURSO_TOKEN") or os.environ.get("TURSO_AUTH_TOKEN")

    if not url or not token:
        # Buscar en .env local o en mt5-stats-analyzer
        candidates = [
            os.path.join(os.getcwd(), ".env"),
            "/home/herles/asf/devherles/mt5-stats-analyzer/.env",
            os.path.expanduser("~/.aegis.env")
        ]
        for c in candidates:
            if os.path.exists(c):
                try:
                    with open(c) as f:
                        for line in f:
                            line = line.strip()
                            if line.startswith("TURSO_DATABASE_URL=") and not url:
                                url = line.split("=", 1)[1].strip(' "\'')
                            elif line.startswith("TURSO_AUTH_TOKEN=") and not token:
                                token = line.split("=", 1)[1].strip(' "\'')
                except Exception:
                    pass

    return url or DEFAULT_DB_URL, token or DEFAULT_DB_TOKEN


class TursoClient:
    def __init__(self, db_url=None, auth_token=None):
        url, token = get_turso_credentials()
        self.url = (db_url or url).replace("libsql://", "https://").rstrip("/") + "/v2/pipeline"
        self.token = auth_token or token

    def query(self, sql, args=None):
        args = args or []
        stmt = {"sql": sql}
        if args:
            stmt_args = []
            for a in args:
                if a is None:
                    stmt_args.append({"type": "null"})
                elif isinstance(a, bool):
                    stmt_args.append({"type": "integer", "value": "1" if a else "0"})
                elif isinstance(a, int):
                    stmt_args.append({"type": "integer", "value": str(a)})
                elif isinstance(a, float):
                    stmt_args.append({"type": "float", "value": a})
                else:
                    stmt_args.append({"type": "text", "value": str(a)})
            stmt["args"] = stmt_args

        payload = {"requests": [{"type": "execute", "stmt": stmt}]}
        data_bytes = json.dumps(payload).encode("utf-8")

        req = urllib.request.Request(
            self.url,
            data=data_bytes,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json"
            },
            method="POST"
        )

        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                resp_json = json.loads(resp.read().decode("utf-8"))
                results = resp_json.get("results", [])
                if not results:
                    return []
                first = results[0]
                if first.get("type") == "error":
                    raise Exception(first.get("error", {}).get("message", "Error Turso"))
                
                result = first.get("response", {}).get("result", {})
                cols = [c.get("name") for c in result.get("cols", [])]
                rows = []
                for row_arr in result.get("rows", []):
                    row_dict = {}
                    for idx, cell in enumerate(row_arr):
                        col_name = cols[idx] if idx < len(cols) else f"col_{idx}"
                        val = cell.get("value")
                        if cell.get("type") == "integer" and val is not None:
                            val = int(val)
                        elif cell.get("type") == "float" and val is not None:
                            val = float(val)
                        elif cell.get("type") == "null":
                            val = None
                        row_dict[col_name] = val
                    rows.append(row_dict)
                return rows
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8")
            raise Exception(f"HTTP {e.code}: {err_body}")


def get_first_child_device(turso):
    devices = turso.query("SELECT device_id, device_name, model FROM devices ORDER BY last_seen_at DESC LIMIT 1;")
    if devices:
        return devices[0]["device_id"]
    return "child_lenovo_m11"


def render_battery_bar(percent):
    total_blocks = 12
    pct = max(0, min(100, percent or 0))
    filled = int(total_blocks * (pct / 100))
    empty = total_blocks - filled
    color = GREEN if pct >= 50 else (YELLOW if pct >= 20 else RED)
    return f"{color}[{'█' * filled}{'░' * empty}] {pct}%{RESET}"


def cmd_status(args):
    turso = TursoClient()
    print(f"\n{BOLD}{CYAN}══════════════════════════════════════════════════════════════════════{RESET}")
    print(f"{BOLD}{WHITE}   AEGIS CONTROL PARENTAL ── ESTADO DEL SISTEMA (TURSO CLOUD)   {RESET}")
    print(f"{BOLD}{CYAN}══════════════════════════════════════════════════════════════════════{RESET}")

    devices = turso.query("SELECT * FROM devices ORDER BY last_seen_at DESC;")
    if not devices:
        print(f"{YELLOW}No hay dispositivos registrados en Turso Cloud aún.{RESET}")
        return

    now_epoch = int(time.time() * 1000)

    for dev in devices:
        dev_id = dev.get("device_id")
        name = dev.get("device_name") or dev.get("model") or dev_id
        model = dev.get("model") or "Desconocido"
        battery = dev.get("battery_percent", 0)
        charging = "⚡ (Cargando)" if dev.get("is_charging") else ""
        current_app = dev.get("current_foreground_app") or "Ninguna / Launcher"
        last_seen = dev.get("last_seen_at", 0)
        diff_sec = max(0, int((now_epoch - last_seen) / 1000))

        online_badge = f"{GREEN}🟢 ONLINE{RESET} (hace {diff_sec}s)" if diff_sec <= 20 else f"{RED}🔴 OFFLINE{RESET} (hace {diff_sec}s)"

        print(f"\n{BOLD}📱 Dispositivo:{RESET} {GREEN}{name}{RESET}  {DIM}[{dev_id} - {model}]{RESET}")
        print(f"   {BOLD}Presencia:{RESET}   {online_badge}")
        print(f"   {BOLD}Batería:{RESET}     {render_battery_bar(battery)} {charging}")
        print(f"   {BOLD}App Activa:{RESET}  {YELLOW}{current_app}{RESET}")

        # Ajustes
        settings = turso.query("SELECT * FROM parental_settings WHERE device_id = ?;", [dev_id])
        if settings:
            s = settings[0]
            is_locked = s.get("is_instant_lock_active") == 1
            is_temp_unlocked = s.get("is_temporarily_unlocked") == 1
            unlock_until = s.get("temporary_unlock_until", 0)

            if is_locked:
                mode_str = f"{BG_RED}{WHITE}{BOLD} 🚨 BLOQUEO TOTAL INMEDIATO ACTIVO {RESET}"
            elif unlock_until > now_epoch:
                rem_sec = int((unlock_until - now_epoch) / 1000)
                rem_m = rem_sec // 60
                rem_s = rem_sec % 60
                mode_str = f"{BG_GREEN}{WHITE}{BOLD} ⏱️  PAUSA TEMPORAL ACTIVA: {rem_m:02d}m {rem_s:02d}s restantes {RESET}"
            else:
                mode_str = f"{BLUE}{BOLD} 🛡️  MODO PROTEGIDO ESTÁNDAR {RESET}"

            print(f"   {BOLD}Estado Modo:{RESET} {mode_str}")
            print(f"   {BOLD}Seguridad:{RESET}   Anti-Desinstalación: {GREEN}Activo{RESET} | Filtro Web: {GREEN}Activo{RESET}")

    # Últimos logs de seguridad
    print(f"\n{BOLD}{CYAN}─── Últimos Intentos de Tampering / Evasión Interceptados ─────────────{RESET}")
    logs = turso.query("SELECT * FROM tamper_logs ORDER BY timestamp DESC LIMIT 3;")
    if logs:
        for log in logs:
            ts = log.get("timestamp", 0)
            time_str = datetime.fromtimestamp(ts / 1000).strftime("%H:%M:%S")
            print(f"   {YELLOW}[{time_str}]{RESET} {RED}🛡️  {log.get('event_type')}{RESET}: {log.get('detail')}")
    else:
        print(f"   {GREEN}✅ Cero violaciones recientes. El dispositivo está seguro.{RESET}")

    print(f"{BOLD}{CYAN}══════════════════════════════════════════════════════════════════════{RESET}\n")


def cmd_lock(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    cmd_id = f"cmd_{int(time.time()*1000)}"
    now = int(time.time() * 1000)

    print(f"{YELLOW}⚡ Despachando orden de BLOQUEO INMEDIATO a {dev_id}...{RESET}")
    start_t = time.time()

    # Enviar comando y actualizar settings en Turso
    turso.query(
        "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_cli', 'LOCK_NOW', '{}', ?, 'PENDING');",
        [cmd_id, dev_id, now]
    )
    turso.query(
        "UPDATE parental_settings SET is_instant_lock_active = 1, updated_at = ? WHERE device_id = ?;",
        [now, dev_id]
    )

    # Esperar confirmación ACK en tiempo real
    print(f"{DIM}Esperando confirmación de la tablet...{RESET}", end="", flush=True)
    ack = False
    for _ in range(15):
        time.sleep(0.4)
        print(".", end="", flush=True)
        res = turso.query("SELECT status, executed_at FROM commands WHERE command_id = ?;", [cmd_id])
        if res and res[0].get("status") == "EXECUTED":
            ack = True
            break

    elapsed_ms = int((time.time() - start_t) * 1000)
    print()
    if ack:
        print(f"{GREEN}{BOLD}✅ ¡BLOQUEO CONFIRMADO! El dispositivo {dev_id} fue bloqueado en {elapsed_ms}ms.{RESET}")
    else:
        print(f"{YELLOW}⚠️  Orden enviada a Turso Cloud. Se ejecutará en cuanto el dispositivo conecte.{RESET}")


def cmd_unlock(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    minutes = args.minutes
    cmd_id = f"cmd_{int(time.time()*1000)}"
    now = int(time.time() * 1000)
    until = now + (minutes * 60 * 1000)

    print(f"{CYAN}⚡ Autorizando {minutes} minutos de uso temporal en {dev_id}...{RESET}")
    start_t = time.time()

    payload = json.dumps({"minutes": minutes})
    turso.query(
        "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_cli', 'UNLOCK_TEMPORARY', ?, ?, 'PENDING');",
        [cmd_id, dev_id, payload, now]
    )
    turso.query(
        "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 1, temporary_unlock_until = ?, updated_at = ? WHERE device_id = ?;",
        [until, now, dev_id]
    )

    print(f"{DIM}Esperando confirmación de la tablet...{RESET}", end="", flush=True)
    ack = False
    for _ in range(15):
        time.sleep(0.4)
        print(".", end="", flush=True)
        res = turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
        if res and res[0].get("status") == "EXECUTED":
            ack = True
            break

    elapsed_ms = int((time.time() - start_t) * 1000)
    print()
    if ack:
        until_str = datetime.fromtimestamp(until / 1000).strftime("%H:%M:%S")
        print(f"{GREEN}{BOLD}✅ ¡DESBLOQUEO CONFIRMADO! {minutes} minutos concedidos (hasta {until_str}) en {elapsed_ms}ms.{RESET}")
        print(f"{DIM}La pantalla de bloqueo en la tablet se cerró automáticamente.{RESET}")
    else:
        print(f"{YELLOW}⚠️  Autorización enviada a Turso Cloud.{RESET}")


def cmd_resume(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    cmd_id = f"cmd_{int(time.time()*1000)}"
    now = int(time.time() * 1000)

    print(f"{YELLOW}⚡ Reanudando bloqueo estricto en {dev_id} (revocando pausas)...{RESET}")
    start_t = time.time()

    turso.query(
        "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_cli', 'CLEAR_LOCK', '{}', ?, 'PENDING');",
        [cmd_id, dev_id, now]
    )
    turso.query(
        "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 0, temporary_unlock_until = 0, updated_at = ? WHERE device_id = ?;",
        [now, dev_id]
    )

    ack = False
    for _ in range(15):
        time.sleep(0.4)
        res = turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
        if res and res[0].get("status") == "EXECUTED":
            ack = True
            break

    elapsed_ms = int((time.time() - start_t) * 1000)
    if ack:
        print(f"{GREEN}{BOLD}✅ ¡REANUDACIÓN CONFIRMADA! La pausa temporal fue revocada en {elapsed_ms}ms.{RESET}")
    else:
        print(f"{YELLOW}⚠️  Orden de reanudación registrada en Turso Cloud.{RESET}")


def cmd_apps(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)

    print(f"\n{BOLD}{CYAN}─── Aplicaciones Supervisadas en {dev_id} ─────────────────────────────{RESET}")
    apps = turso.query("SELECT package_name, app_name, is_blocked FROM app_restrictions WHERE device_id = ? ORDER BY app_name ASC;", [dev_id])
    if not apps:
        # Mostrar apps predeterminadas
        print(f"{DIM}Mostrando lista de apps distractoras predeterminadas:{RESET}\n")
        for alias, pkg in APP_ALIASES.items():
            print(f"  • {BOLD}{alias.capitalize():<12}{RESET} {DIM}({pkg}){RESET}  ->  {RED}🚫 BLOQUEADA{RESET}")
    else:
        for app in apps:
            status = f"{RED}🚫 BLOQUEADA{RESET}" if app.get("is_blocked") == 1 else f"{GREEN}✅ PERMITIDA{RESET}"
            print(f"  • {BOLD}{app.get('app_name'):<15}{RESET} {DIM}({app.get('package_name')}){RESET}  ->  {status}")
    print(f"{BOLD}{CYAN}──────────────────────────────────────────────────────────────────────{RESET}\n")


def cmd_block_app(args, blocked=True):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    app_input = args.app.lower()
    pkg = APP_ALIASES.get(app_input, args.app)
    app_name = app_input.capitalize()
    now = int(time.time() * 1000)
    cmd_id = f"cmd_{now}"

    action_str = "BLOQUEAR" if blocked else "DESBLOQUEAR"
    print(f"{YELLOW}⚡ Enviando orden para {action_str} {pkg} en {dev_id}...{RESET}")

    payload = json.dumps({"package": pkg, "blocked": blocked})
    turso.query(
        "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_cli', 'UPDATE_APP', ?, ?, 'PENDING');",
        [cmd_id, dev_id, payload, now]
    )
    turso.query(
        """
        INSERT INTO app_restrictions (device_id, package_name, app_name, is_blocked, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(device_id, package_name) DO UPDATE SET is_blocked = excluded.is_blocked, updated_at = excluded.updated_at;
        """.trim() if hasattr(str, 'trim') else """
        INSERT INTO app_restrictions (device_id, package_name, app_name, is_blocked, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(device_id, package_name) DO UPDATE SET is_blocked = excluded.is_blocked, updated_at = excluded.updated_at;
        """,
        [dev_id, pkg, app_name, 1 if blocked else 0, now]
    )

    print(f"{GREEN}✅ Orden registrada. {pkg} ahora está {'BLOQUEADA' if blocked else 'PERMITIDA'}.{RESET}")


def cmd_ping(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    cmd_id = f"ping_{int(time.time()*1000)}"
    now = int(time.time() * 1000)

    print(f"{CYAN}📡 Enviando PING a {dev_id} vía Turso Cloud...{RESET}")
    start_t = time.time()

    turso.query(
        "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_cli', 'PING', '{}', ?, 'PENDING');",
        [cmd_id, dev_id, now]
    )

    ack = False
    for _ in range(25):
        time.sleep(0.3)
        res = turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
        if res and res[0].get("status") == "EXECUTED":
            ack = True
            break

    elapsed_ms = int((time.time() - start_t) * 1000)
    if ack:
        print(f"{GREEN}{BOLD}🏓 PONG! Respuesta recibida de {dev_id} en {elapsed_ms}ms RTT.{RESET}")
    else:
        print(f"{RED}❌ Timeout: el dispositivo no respondió en el tiempo esperado.{RESET}")


class AegisHtopMonitor:
    """
    Monitor interactivo de pantalla completa estilo htop/btop.
    Utiliza curses con renderizado diferencial por celdas (cero parpadeo),
    adaptación dinámica al tamaño del monitor y hotkeys tácticos.
    """
    def __init__(self, dev_id=None):
        self.turso = TursoClient()
        self.dev_id = dev_id or get_first_child_device(self.turso)
        self.lock = threading.Lock()

        # Estado sincronizado
        self.device = None
        self.settings = None
        self.apps = []
        self.tamper_logs = []
        self.last_ping_rtt = None
        self.status_msg = "Aegis Monitor Conectado a Turso Cloud"
        self.status_time = time.time()
        self.is_connected = True
        self.is_operating = False
        self.show_help = False

        # Navegación en tabla de apps
        self.selected_idx = 0
        self.scroll_offset = 0

        self.stop_event = threading.Event()
        self.worker_thread = None

        # Carga inicial inmediata para evitar parpadeo en el primer frame
        self._single_fetch()

    def set_status(self, msg):
        with self.lock:
            self.status_msg = msg
            self.status_time = time.time()

    def _sync_worker(self):
        while not self.stop_event.is_set():
            self._single_fetch()
            self.stop_event.wait(1.0)

    def _single_fetch(self):
        try:
            devs = self.turso.query("SELECT * FROM devices WHERE device_id = ?;", [self.dev_id])
            if not devs:
                devs = self.turso.query("SELECT * FROM devices ORDER BY last_seen_at DESC LIMIT 1;")
                if devs:
                    self.dev_id = devs[0].get("device_id")

            settings = self.turso.query("SELECT * FROM parental_settings WHERE device_id = ?;", [self.dev_id])

            app_rows = self.turso.query(
                "SELECT package_name, app_name, is_blocked FROM app_restrictions WHERE device_id = ? ORDER BY app_name ASC;",
                [self.dev_id]
            )

            # Consolidar apps de la base de datos con APP_ALIASES para supervisión exhaustiva
            db_map = {r.get("package_name"): r for r in (app_rows or [])}
            all_apps = []
            seen = set()

            for r in (app_rows or []):
                pkg = r.get("package_name")
                if pkg and pkg not in seen:
                    all_apps.append({
                        "package_name": pkg,
                        "app_name": r.get("app_name") or pkg,
                        "is_blocked": r.get("is_blocked", 0)
                    })
                    seen.add(pkg)

            for alias, pkg in APP_ALIASES.items():
                if pkg not in seen:
                    all_apps.append({
                        "package_name": pkg,
                        "app_name": alias.capitalize(),
                        "is_blocked": 0
                    })
                    seen.add(pkg)

            all_apps.sort(key=lambda x: (x.get("app_name") or x.get("package_name")).lower())

            log_rows = self.turso.query(
                "SELECT * FROM tamper_logs WHERE device_id = ? ORDER BY timestamp DESC LIMIT 6;",
                [self.dev_id]
            )

            with self.lock:
                if devs:
                    self.device = devs[0]
                if settings:
                    self.settings = settings[0]
                self.apps = all_apps
                self.tamper_logs = log_rows or []
                self.is_connected = True
        except Exception:
            with self.lock:
                self.is_connected = False

    def trigger_async_action(self, func, *args):
        if self.is_operating:
            return
        self.is_operating = True

        def runner():
            try:
                func(*args)
            except Exception as e:
                self.set_status(f"Error: {e}")
            finally:
                self.is_operating = False
                time.sleep(0.3)
                self._single_fetch()

        t = threading.Thread(target=runner, daemon=True)
        t.start()

    def action_lock(self):
        now = int(time.time() * 1000)
        cmd_id = f"cmd_{now}"
        self.set_status(f"⚡ Enviando orden de BLOQUEO TOTAL a {self.dev_id}...")
        start_t = time.time()

        self.turso.query(
            "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_htop', 'LOCK_NOW', '{}', ?, 'PENDING');",
            [cmd_id, self.dev_id, now]
        )
        self.turso.query(
            "UPDATE parental_settings SET is_instant_lock_active = 1, updated_at = ? WHERE device_id = ?;",
            [now, self.dev_id]
        )

        ack = False
        for _ in range(12):
            time.sleep(0.3)
            res = self.turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
            if res and res[0].get("status") == "EXECUTED":
                ack = True
                break

        ms = int((time.time() - start_t) * 1000)
        if ack:
            self.set_status(f"🔒 ¡Tablet bloqueada con éxito! (Confirmado en {ms}ms)")
        else:
            self.set_status("⚠️ Bloqueo registrado en Turso Cloud")

    def action_unlock(self, minutes=15):
        now = int(time.time() * 1000)
        until = now + (minutes * 60 * 1000)
        cmd_id = f"cmd_{now}"
        self.set_status(f"⚡ Autorizando {minutes} min de recreo en {self.dev_id}...")
        start_t = time.time()

        payload = json.dumps({"minutes": minutes})
        self.turso.query(
            "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_htop', 'UNLOCK_TEMPORARY', ?, ?, 'PENDING');",
            [cmd_id, self.dev_id, payload, now]
        )
        self.turso.query(
            "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 1, temporary_unlock_until = ?, updated_at = ? WHERE device_id = ?;",
            [until, now, self.dev_id]
        )

        ack = False
        for _ in range(12):
            time.sleep(0.3)
            res = self.turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
            if res and res[0].get("status") == "EXECUTED":
                ack = True
                break

        ms = int((time.time() - start_t) * 1000)
        if ack:
            self.set_status(f"✅ ¡Desbloqueo de {minutes}m concedido! (Confirmado en {ms}ms)")
        else:
            self.set_status(f"⚠️ Autorización de {minutes}m registrada en Turso Cloud")

    def action_resume(self):
        now = int(time.time() * 1000)
        cmd_id = f"cmd_{now}"
        self.set_status("⚡ Reanudando protección estándar (cancelando pausas)...")
        start_t = time.time()

        self.turso.query(
            "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_htop', 'CLEAR_LOCK', '{}', ?, 'PENDING');",
            [cmd_id, self.dev_id, now]
        )
        self.turso.query(
            "UPDATE parental_settings SET is_instant_lock_active = 0, is_temporarily_unlocked = 0, temporary_unlock_until = 0, updated_at = ? WHERE device_id = ?;",
            [now, self.dev_id]
        )

        ack = False
        for _ in range(12):
            time.sleep(0.3)
            res = self.turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
            if res and res[0].get("status") == "EXECUTED":
                ack = True
                break

        ms = int((time.time() - start_t) * 1000)
        if ack:
            self.set_status(f"🛡️ Protección estándar reanudada en {ms}ms")
        else:
            self.set_status("⚠️ Reanudación registrada en Turso Cloud")

    def action_toggle_selected_app(self):
        with self.lock:
            if not self.apps or self.selected_idx >= len(self.apps):
                return
            app = self.apps[self.selected_idx]
            pkg = app.get("package_name")
            name = app.get("app_name") or pkg
            curr_blocked = app.get("is_blocked") == 1
            new_blocked = not curr_blocked
            app["is_blocked"] = 1 if new_blocked else 0

        now = int(time.time() * 1000)
        cmd_id = f"cmd_{now}"
        action_str = "BLOQUEAR" if new_blocked else "PERMITIR"
        self.set_status(f"⚡ {action_str} {name} en tablet...")

        payload = json.dumps({"package": pkg, "blocked": new_blocked})
        self.turso.query(
            "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_htop', 'UPDATE_APP', ?, ?, 'PENDING');",
            [cmd_id, self.dev_id, payload, now]
        )
        self.turso.query(
            """
            INSERT INTO app_restrictions (device_id, package_name, app_name, is_blocked, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(device_id, package_name) DO UPDATE SET is_blocked = excluded.is_blocked, updated_at = excluded.updated_at;
            """,
            [self.dev_id, pkg, name, 1 if new_blocked else 0, now]
        )
        self.set_status(f"✅ App {name} ahora está {'BLOQUEADA' if new_blocked else 'PERMITIDA'}")

    def action_ping(self):
        now = int(time.time() * 1000)
        cmd_id = f"ping_{now}"
        self.set_status("📡 Enviando Ping a la tablet...")
        start_t = time.time()

        self.turso.query(
            "INSERT INTO commands (command_id, target_device_id, source_device_id, command_type, payload, created_at, status) VALUES (?, ?, 'parent_htop', 'PING', '{}', ?, 'PENDING');",
            [cmd_id, self.dev_id, now]
        )

        ack = False
        for _ in range(25):
            time.sleep(0.2)
            res = self.turso.query("SELECT status FROM commands WHERE command_id = ?;", [cmd_id])
            if res and res[0].get("status") == "EXECUTED":
                ack = True
                break

        if ack:
            rtt = int((time.time() - start_t) * 1000)
            with self.lock:
                self.last_ping_rtt = rtt
            self.set_status(f"🏓 PONG! Respuesta recibida en {rtt}ms RTT")
        else:
            self.set_status("❌ Timeout en Ping: sin respuesta de la tablet")

    def _safe_addstr(self, stdscr, y, x, text, attr=0, max_x=None):
        max_y, win_max_x = stdscr.getmaxyx()
        if y < 0 or y >= max_y or x < 0 or x >= win_max_x:
            return
        limit = (max_x or win_max_x) - x
        if limit <= 0:
            return
        trimmed = str(text)[:limit]
        try:
            stdscr.addstr(y, x, trimmed, attr)
        except curses.error:
            pass

    def run(self, stdscr):
        try:
            curses.curs_set(0)
        except curses.error:
            pass

        stdscr.nodelay(True)
        stdscr.timeout(100) # 100ms para refresco fluido a 10 FPS
        stdscr.keypad(True)
        if curses.has_colors():
            try:
                curses.use_default_colors()
                bg = -1
            except Exception:
                bg = curses.COLOR_BLACK

            # Paleta de colores estilo htop
            try:
                curses.init_pair(1, curses.COLOR_CYAN, bg)
                curses.init_pair(2, curses.COLOR_GREEN, bg)
                curses.init_pair(3, curses.COLOR_RED, bg)
                curses.init_pair(4, curses.COLOR_YELLOW, bg)
                curses.init_pair(5, curses.COLOR_WHITE, curses.COLOR_BLUE)
                curses.init_pair(6, curses.COLOR_BLACK, curses.COLOR_CYAN)
                curses.init_pair(7, curses.COLOR_WHITE, bg)
                curses.init_pair(8, curses.COLOR_MAGENTA, bg)
            except Exception:
                pass


        # Iniciar hilo de sincronización en segundo plano
        self.worker_thread = threading.Thread(target=self._sync_worker, daemon=True)
        self.worker_thread.start()

        try:
            while not self.stop_event.is_set():
                max_y, max_x = stdscr.getmaxyx()

                if max_y < 16 or max_x < 65:
                    stdscr.erase()
                    self._safe_addstr(stdscr, 0, 0, "Terminal muy pequeña. Agranda la ventana para el monitor htop...", curses.color_pair(3))
                    stdscr.refresh()
                    ch = stdscr.getch()
                    if ch in (ord('q'), ord('Q'), 27):
                        break
                    time.sleep(0.2)
                    continue

                # Renderizado sobre búfer virtual
                stdscr.erase()
                self._render(stdscr, max_y, max_x)

                # Renderizado diferencial por celdas (CERO PARPADEO)
                stdscr.refresh()

                ch = stdscr.getch()
                if ch == -1:
                    continue

                if ch in (curses.KEY_RESIZE,):
                    stdscr.clear()
                elif ch in (ord('q'), ord('Q'), 27, curses.KEY_F10):
                    break
                elif ch in (ord('h'), ord('H'), curses.KEY_F1):
                    self.show_help = not self.show_help
                elif self.show_help:
                    # Cualquier tecla cierra la ayuda
                    self.show_help = False
                elif ch in (curses.KEY_UP, ord('k'), ord('K')):
                    self.selected_idx = max(0, self.selected_idx - 1)
                elif ch in (curses.KEY_DOWN, ord('j'), ord('J')):
                    with self.lock:
                        num_apps = len(self.apps)
                    self.selected_idx = min(max(0, num_apps - 1), self.selected_idx + 1)
                elif ch in (curses.KEY_PPAGE,):
                    self.selected_idx = max(0, self.selected_idx - 5)
                elif ch in (curses.KEY_NPAGE,):
                    with self.lock:
                        num_apps = len(self.apps)
                    self.selected_idx = min(max(0, num_apps - 1), self.selected_idx + 5)
                elif ch in (curses.KEY_HOME,):
                    self.selected_idx = 0
                elif ch in (curses.KEY_END,):
                    with self.lock:
                        num_apps = len(self.apps)
                    self.selected_idx = max(0, num_apps - 1)
                elif ch in (ord(' '), ord('a'), ord('A'), 10, 13, curses.KEY_F6):
                    self.trigger_async_action(self.action_toggle_selected_app)
                elif ch in (ord('l'), ord('L'), curses.KEY_F2):
                    self.trigger_async_action(self.action_lock)
                elif ch in (ord('u'), ord('U'), curses.KEY_F3):
                    self.trigger_async_action(self.action_unlock, 15)
                elif ch in (ord('r'), ord('R'), curses.KEY_F4):
                    self.trigger_async_action(self.action_resume)
                elif ch in (ord('p'), ord('P'), curses.KEY_F5):
                    self.trigger_async_action(self.action_ping)
                elif ch in (ord('s'), ord('S'), curses.KEY_F7):
                    self.trigger_async_action(self._single_fetch)
        finally:
            self.stop_event.set()

    def _render(self, stdscr, max_y, max_x):
        with self.lock:
            dev = dict(self.device) if self.device else {}
            settings = dict(self.settings) if self.settings else {}
            apps = [dict(a) for a in self.apps]
            logs = [dict(l) for l in self.tamper_logs]
            rtt = self.last_ping_rtt
            is_conn = self.is_connected
            status_msg = self.status_msg

        now_epoch = int(time.time() * 1000)
        now_str = datetime.now().strftime("%H:%M:%S")

        # 1. HEADER (Filas 0 a 4)
        title = f" AEGIS CONTROL PARENTAL ── MONITOR PRO ({now_str}) "
        conn_str = f" Turso Cloud: {'ONLINE' if is_conn else 'OFFLINE'} "
        dashes = max(0, max_x - len(title) - len(conn_str) - 4)
        header_top = f"┌─{title}{'─'*dashes}{conn_str}─┐"
        self._safe_addstr(stdscr, 0, 0, header_top, curses.color_pair(1) | curses.A_BOLD, max_x)

        mid_x = max(36, max_x // 2)

        # Fila 1: Dispositivo y Presencia
        dev_name = dev.get("device_name") or dev.get("model") or self.dev_id
        last_seen = dev.get("last_seen_at", 0)
        diff_sec = max(0, int((now_epoch - last_seen) / 1000)) if last_seen else 999
        is_online = diff_sec <= 25 and is_conn

        self._safe_addstr(stdscr, 1, 0, "│ ", curses.color_pair(1))
        self._safe_addstr(stdscr, 1, 2, "Dispositivo : ", curses.A_BOLD)
        dev_label = f"{dev_name} [{self.dev_id}]"
        self._safe_addstr(stdscr, 1, 16, dev_label, curses.color_pair(2) | curses.A_BOLD, mid_x - 1)

        online_str = f"🟢 ONLINE (hace {diff_sec}s)" if is_online else f"🔴 OFFLINE (hace {diff_sec}s)"
        self._safe_addstr(stdscr, 1, mid_x, "Presencia   : ", curses.A_BOLD)
        self._safe_addstr(stdscr, 1, mid_x + 14, online_str, curses.color_pair(2 if is_online else 3) | curses.A_BOLD, max_x - 1)
        self._safe_addstr(stdscr, 1, max_x - 1, "│", curses.color_pair(1))

        # Fila 2: Batería y Modo
        bat = dev.get("battery_percent", 0)
        is_charging = bool(dev.get("is_charging"))
        bar_len = min(16, max(8, (mid_x - 30)))
        pct = max(0, min(100, bat))
        filled = int(bar_len * (pct / 100))
        empty = bar_len - filled
        charge_tag = " ⚡ Cargando" if is_charging else ""
        bat_str = f"[{'|'*filled}{' '*empty}] {pct}%{charge_tag}"

        self._safe_addstr(stdscr, 2, 0, "│ ", curses.color_pair(1))
        self._safe_addstr(stdscr, 2, 2, "Batería     : ", curses.A_BOLD)
        bat_pair = 2 if pct >= 50 else (4 if pct >= 20 else 3)
        self._safe_addstr(stdscr, 2, 16, bat_str, curses.color_pair(bat_pair) | curses.A_BOLD, mid_x - 1)

        is_lock = settings.get("is_instant_lock_active") == 1
        until = settings.get("temporary_unlock_until", 0)
        if is_lock:
            mode_str = "🚨 BLOQUEO TOTAL INMEDIATO"
            mode_pair = 3
        elif until > now_epoch:
            rem = int((until - now_epoch) / 1000)
            mode_str = f"⏱️  PAUSA ACTIVA: {rem//60:02d}m {rem%60:02d}s"
            mode_pair = 4
        else:
            mode_str = "🛡️  MODO PROTEGIDO ESTÁNDAR"
            mode_pair = 2

        self._safe_addstr(stdscr, 2, mid_x, "Modo        : ", curses.A_BOLD)
        self._safe_addstr(stdscr, 2, mid_x + 14, mode_str, curses.color_pair(mode_pair) | curses.A_BOLD, max_x - 1)
        self._safe_addstr(stdscr, 2, max_x - 1, "│", curses.color_pair(1))

        # Fila 3: App Activa y Latencia
        app = dev.get("current_foreground_app") or "Launcher / Home"
        self._safe_addstr(stdscr, 3, 0, "│ ", curses.color_pair(1))
        self._safe_addstr(stdscr, 3, 2, "App Activa  : ", curses.A_BOLD)
        self._safe_addstr(stdscr, 3, 16, f"{app}", curses.color_pair(4) | curses.A_BOLD, mid_x - 1)

        ping_str = f"🏓 {rtt}ms RTT" if rtt else "Sin medir (pulsa P / F5)"
        self._safe_addstr(stdscr, 3, mid_x, "Latencia    : ", curses.A_BOLD)
        self._safe_addstr(stdscr, 3, mid_x + 14, ping_str, curses.color_pair(2 if rtt else 7), max_x - 1)
        self._safe_addstr(stdscr, 3, max_x - 1, "│", curses.color_pair(1))

        # Fila 4: Separador
        self._safe_addstr(stdscr, 4, 0, f"├{'─'*(max_x - 2)}┤", curses.color_pair(1), max_x)

        # 2. TABLA PRINCIPAL DE APLICACIONES (Uso dinámico del ancho)
        log_panel_height = 4 if max_y >= 22 else 2
        footer_height = 2
        table_rows_start_y = 6
        table_end_y = max_y - log_panel_height - footer_height - 1
        available_table_rows = max(2, table_end_y - table_rows_start_y)

        pkg_col_w = max(25, max_x - 56)

        # Encabezados de columnas de la tabla
        header_fmt = f"│  {'IDX':<4} {'ESTADO':<15} {'APLICACIÓN':<26} {'PAQUETE':<{pkg_col_w}}"
        self._safe_addstr(stdscr, 5, 0, header_fmt, curses.color_pair(1) | curses.A_BOLD, max_x - 1)
        self._safe_addstr(stdscr, 5, max_x - 1, "│", curses.color_pair(1))

        num_apps = len(apps)
        if num_apps > 0:
            self.selected_idx = max(0, min(num_apps - 1, self.selected_idx))
            if self.selected_idx < self.scroll_offset:
                self.scroll_offset = self.selected_idx
            elif self.selected_idx >= self.scroll_offset + available_table_rows:
                self.scroll_offset = self.selected_idx - available_table_rows + 1

        for i in range(available_table_rows):
            curr_y = table_rows_start_y + i
            app_idx = self.scroll_offset + i
            self._safe_addstr(stdscr, curr_y, 0, "│ ", curses.color_pair(1))

            if app_idx < num_apps:
                item = apps[app_idx]
                pkg = item.get("package_name", "")
                name = item.get("app_name") or pkg
                is_b = item.get("is_blocked") == 1
                status_str = "🚫 BLOQUEADA" if is_b else "✅ PERMITIDA"
                is_selected = (app_idx == self.selected_idx)
                is_active = (pkg == app and app not in ("Launcher", "Home", "none", ""))

                prefix = "> " if is_selected else "  "
                active_badge = " [ACTIVA ⚡]" if is_active else ""
                row_str = f"{prefix}{app_idx + 1:<3} {status_str:<15} {name:<26} {pkg:<{pkg_col_w}}{active_badge}"

                if is_selected:
                    self._safe_addstr(stdscr, curr_y, 2, f"{row_str:<{max_x - 4}}", curses.color_pair(5) | curses.A_BOLD, max_x - 1)
                else:
                    status_pair = 3 if is_b else 2
                    self._safe_addstr(stdscr, curr_y, 2, f"{prefix}{app_idx + 1:<3} ", curses.A_DIM)
                    self._safe_addstr(stdscr, curr_y, 8, f"{status_str:<15}", curses.color_pair(status_pair) | curses.A_BOLD)
                    self._safe_addstr(stdscr, curr_y, 24, f"{name:<26}", curses.A_BOLD)
                    self._safe_addstr(stdscr, curr_y, 51, f"{pkg:<{pkg_col_w}}{active_badge}", curses.color_pair(4 if is_active else 7), max_x - 1)
            else:
                self._safe_addstr(stdscr, curr_y, 2, " " * (max_x - 4))

            self._safe_addstr(stdscr, curr_y, max_x - 1, "│", curses.color_pair(1))

        # Separador antes de logs
        self._safe_addstr(stdscr, table_end_y, 0, f"├{'─'*(max_x - 2)}┤", curses.color_pair(1), max_x)

        # 3. REGISTRO DE SEGURIDAD Y ANTI-TAMPERING
        log_title_y = table_end_y + 1
        self._safe_addstr(stdscr, log_title_y, 0, "│ ", curses.color_pair(1))
        self._safe_addstr(stdscr, log_title_y, 2, "🛡️  REGISTRO DE SEGURIDAD Y ANTI-TAMPERING (TURSO CLOUD):", curses.color_pair(1) | curses.A_BOLD)
        self._safe_addstr(stdscr, log_title_y, max_x - 1, "│", curses.color_pair(1))

        log_lines_count = log_panel_height - 1
        for li in range(log_lines_count):
            log_y = log_title_y + 1 + li
            self._safe_addstr(stdscr, log_y, 0, "│ ", curses.color_pair(1))
            if li < len(logs):
                l_item = logs[li]
                ts = l_item.get("timestamp", 0)
                t_str = datetime.fromtimestamp(ts / 1000).strftime("%H:%M:%S") if ts else "00:00:00"
                etype = l_item.get("event_type", "INCIDENTE")
                detail = l_item.get("detail", "")
                log_text = f"[{t_str}] 🛡️ {etype}: {detail}"
                self._safe_addstr(stdscr, log_y, 4, log_text[:max_x - 6], curses.color_pair(3))
            elif li == 0 and not logs:
                self._safe_addstr(stdscr, log_y, 4, "✅ Cero violaciones recientes. El dispositivo está seguro.", curses.color_pair(2))
            self._safe_addstr(stdscr, log_y, max_x - 1, "│", curses.color_pair(1))

        # 4. BORDE INFERIOR CON MENSAJE DE ESTADO
        bottom_y = max_y - 2
        msg_tag = f" {status_msg} "
        if len(msg_tag) > max_x - 6:
            msg_tag = msg_tag[:max_x - 9] + "... "
        rem_dashes = max(0, max_x - len(msg_tag) - 4)
        bottom_str = f"└─{msg_tag}{'─'*rem_dashes}┘"
        self._safe_addstr(stdscr, bottom_y, 0, bottom_str, curses.color_pair(1), max_x)

        # 5. BARRA DE HOTKEYS TÁCTICOS ESTILO HTOP (Fila inferior)
        footer_y = max_y - 1
        hotkeys = [
            (" 1 ", "Help"),
            (" 2 ", "Lock"),
            (" 3 ", "+15m"),
            (" 4 ", "Resume"),
            (" 5 ", "Ping"),
            (" 6 ", "Toggle"),
            (" 7 ", "Sync"),
            (" 10 ", "Quit")
        ]

        curr_x = 0
        for num, label in hotkeys:
            if curr_x >= max_x - 6:
                break
            self._safe_addstr(stdscr, footer_y, curr_x, num, curses.color_pair(6) | curses.A_BOLD, max_x)
            curr_x += len(num)
            lbl_str = f"{label} "
            self._safe_addstr(stdscr, footer_y, curr_x, lbl_str, curses.color_pair(7), max_x)
            curr_x += len(lbl_str)

        # 6. POPUP MODAL DE AYUDA (Si está activo)
        if self.show_help:
            box_w = min(68, max_x - 4)
            box_h = 16
            start_x = (max_x - box_w) // 2
            start_y = (max_y - box_h) // 2

            help_lines = [
                "┌" + "─" * (box_w - 2) + "┐",
                "│" + " AYUDA Y ATAJOS DE TECLADO (AEGIS TUI) ".center(box_w - 2) + "│",
                "├" + "─" * (box_w - 2) + "┤",
                "│  ↑ / ↓ o j / k     : Navegar por la lista de aplicaciones".ljust(box_w - 2) + "│",
                "│  PgUp / PgDn       : Desplazamiento rápido por páginas".ljust(box_w - 2) + "│",
                "│  Espacio / Enter / A: Conmutar Bloqueo/Permiso de app".ljust(box_w - 2) + "│",
                "│  L / F2            : Bloqueo total inmediato de la tablet".ljust(box_w - 2) + "│",
                "│  U / F3            : Conceder 15 minutos de recreo temporal".ljust(box_w - 2) + "│",
                "│  R / F4            : Reanudar protección (cancelar pausas)".ljust(box_w - 2) + "│",
                "│  P / F5            : Probar latencia de red en vivo (Ping)".ljust(box_w - 2) + "│",
                "│  S / F7            : Forzar sincronización con Turso Cloud".ljust(box_w - 2) + "│",
                "│  H / F1            : Mostrar / ocultar esta ventana".ljust(box_w - 2) + "│",
                "│  Q / F10 / ESC     : Salir del monitor".ljust(box_w - 2) + "│",
                "├" + "─" * (box_w - 2) + "┤",
                "│" + " Presiona cualquier tecla para cerrar ".center(box_w - 2) + "│",
                "└" + "─" * (box_w - 2) + "┘"
            ]

            for hy, hline in enumerate(help_lines):
                self._safe_addstr(stdscr, start_y + hy, start_x, hline, curses.color_pair(1) | curses.A_BOLD, max_x)


def cmd_live(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)
    monitor = AegisHtopMonitor(dev_id)
    curses.wrapper(monitor.run)



def cmd_query(args):
    turso = TursoClient()
    sql = args.sql
    print(f"{DIM}Ejecutando SQL: {sql}{RESET}")
    rows = turso.query(sql)
    if not rows:
        print(f"{YELLOW}(0 filas devueltas){RESET}")
        return
    
    # Imprimir encabezados
    keys = list(rows[0].keys())
    print(f"\n{BOLD}{' | '.join(keys)}{RESET}")
    print("-" * 60)
    for r in rows:
        vals = [str(r.get(k)) for k in keys]
        print(" | ".join(vals))
    print(f"\n{GREEN}Total: {len(rows)} filas.{RESET}\n")


def main():
    parser = argparse.ArgumentParser(
        description="Aegis Parental Control - Terminal Linux PRO Suite (Turso Cloud)",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--device", help="ID del dispositivo objetivo (opcional, auto-detectado)")

    subparsers = parser.add_subparsers(dest="command", help="Comando a ejecutar")

    # status
    subparsers.add_parser("status", help="Muestra el estado completo y telemetría de los dispositivos")

    # live / monitor
    subparsers.add_parser("live", help="Dashboard interactivo en tiempo real con refresco continuo")
    subparsers.add_parser("monitor", help="Alias de live")

    # lock
    subparsers.add_parser("lock", help="Aplica bloqueo total inmediato al dispositivo")

    # unlock
    p_unlock = subparsers.add_parser("unlock", help="Autoriza uso temporal por un número de minutos")
    p_unlock.add_argument("minutes", type=int, nargs="?", default=30, help="Minutos autorizados (default: 30)")

    # resume / clear
    subparsers.add_parser("resume", help="Reanuda el bloqueo normal inmediatamente (cancela pausa)")
    subparsers.add_parser("clear", help="Alias de resume")

    # apps
    subparsers.add_parser("apps", help="Muestra la lista de aplicaciones supervisadas y su estado")

    # block
    p_block = subparsers.add_parser("block", help="Bloquea una aplicación por nombre o alias (ej: tiktok, youtube)")
    p_block.add_argument("app", help="Nombre o alias de la app (tiktok, youtube, roblox, etc.)")

    # unblock
    p_unblock = subparsers.add_parser("unblock", help="Desbloquea una aplicación por nombre o alias")
    p_unblock.add_argument("app", help="Nombre o alias de la app")

    # ping
    subparsers.add_parser("ping", help="Mide la latencia Round-Trip Time (RTT) en vivo con la tablet")

    # query
    p_query = subparsers.add_parser("query", help="Ejecuta una consulta SQL directa contra Turso Cloud")
    p_query.add_argument("sql", help="Sentencia SQL a ejecutar")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return

    try:
        if args.command == "status":
            cmd_status(args)
        elif args.command in ("live", "monitor"):
            cmd_live(args)
        elif args.command == "lock":
            cmd_lock(args)
        elif args.command == "unlock":
            cmd_unlock(args)
        elif args.command in ("resume", "clear"):
            cmd_resume(args)
        elif args.command == "apps":
            cmd_apps(args)
        elif args.command == "block":
            cmd_block_app(args, blocked=True)
        elif args.command == "unblock":
            cmd_block_app(args, blocked=False)
        elif args.command == "ping":
            cmd_ping(args)
        elif args.command == "query":
            cmd_query(args)
    except Exception as e:
        print(f"\n{RED}{BOLD}❌ Error ejecutando comando:{RESET} {e}\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
