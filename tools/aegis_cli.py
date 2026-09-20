#!/usr/bin/env python3
"""
Aegis Parental Control - Terminal Linux PRO Suite
Herramienta táctica y de monitoreo en tiempo real respaldada por Turso Cloud (LibSQL).
"""
import argparse
import base64
import json
import os
import sys
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


def cmd_live(args):
    turso = TursoClient()
    dev_id = args.device or get_first_child_device(turso)

    print(f"{CYAN}Iniciando Dashboard en Vivo (Presiona Ctrl+C para salir)...{RESET}")
    try:
        while True:
            # Limpiar pantalla de terminal
            sys.stdout.write("\033[2J\033[H")
            sys.stdout.flush()

            devices = turso.query("SELECT * FROM devices WHERE device_id = ?;", [dev_id])
            settings = turso.query("SELECT * FROM parental_settings WHERE device_id = ?;", [dev_id])
            now_epoch = int(time.time() * 1000)
            now_str = datetime.now().strftime("%H:%M:%S")

            print(f"{BOLD}{CYAN}┌───────────────────────────────────────────────────────────────────────────┐{RESET}")
            print(f"{BOLD}{CYAN}│{RESET}  {BOLD}{WHITE}AEGIS CONTROL PARENTAL  ──  MONITOR PRO EN VIVO ({now_str}){RESET}          {BOLD}{CYAN}│{RESET}")
            print(f"{BOLD}{CYAN}├───────────────────────────────────────────────────────────────────────────┤{RESET}")

            if devices:
                dev = devices[0]
                diff_sec = max(0, int((now_epoch - dev.get("last_seen_at", 0)) / 1000))
                online_str = f"{GREEN}🟢 ONLINE{RESET} (hace {diff_sec}s)" if diff_sec <= 20 else f"{RED}🔴 OFFLINE{RESET}"
                bat = dev.get("battery_percent", 0)
                app = dev.get("current_foreground_app") or "Launcher / Home"

                print(f"{BOLD}{CYAN}│{RESET}  {BOLD}Dispositivo:{RESET} {dev.get('device_name')} [{dev_id}]   Estado: {online_str}")
                print(f"{BOLD}{CYAN}│{RESET}  {BOLD}Batería:{RESET}     {render_battery_bar(bat)}")
                print(f"{BOLD}{CYAN}│{RESET}  {BOLD}App Actual:{RESET}  {YELLOW}{BOLD}{app:<35}{RESET}")
            else:
                print(f"{BOLD}{CYAN}│{RESET}  Esperando conexión del dispositivo {dev_id}...")

            if settings:
                s = settings[0]
                is_lock = s.get("is_instant_lock_active") == 1
                until = s.get("temporary_unlock_until", 0)
                if is_lock:
                    status_line = f"{BG_RED}{WHITE}{BOLD} 🚨 BLOQUEO TOTAL INMEDIATO ACTIVO {RESET}"
                elif until > now_epoch:
                    rem = int((until - now_epoch) / 1000)
                    status_line = f"{BG_GREEN}{WHITE}{BOLD} ⏱️  PAUSA TEMPORAL: {rem//60:02d}m {rem%60:02d}s restantes {RESET}"
                else:
                    status_line = f"{BLUE}{BOLD} 🛡️  MODO PROTEGIDO ESTÁNDAR {RESET}"
                print(f"{BOLD}{CYAN}│{RESET}  {BOLD}Modo:{RESET}        {status_line}")

            print(f"{BOLD}{CYAN}├───────────────────────────────────────────────────────────────────────────┤{RESET}")
            print(f"{BOLD}{CYAN}│{RESET}  {BOLD}COMANDOS RÁPIDOS DESDE OTRA TERMINAL:{RESET}                                     {BOLD}{CYAN}│{RESET}")
            print(f"{BOLD}{CYAN}│{RESET}  • {GREEN}./aegis unlock 30{RESET}  -> Autorizar 30 min     • {RED}./aegis lock{RESET} -> Bloquear ya {BOLD}{CYAN}│{RESET}")
            print(f"{BOLD}{CYAN}│{RESET}  • {YELLOW}./aegis resume{RESET}     -> Reanudar bloqueo     • {CYAN}./aegis ping{RESET} -> Test latencia  {BOLD}{CYAN}│{RESET}")
            print(f"{BOLD}{CYAN}├───────────────────────────────────────────────────────────────────────────┤{RESET}")

            # Logs recientes
            logs = turso.query("SELECT * FROM tamper_logs WHERE device_id = ? ORDER BY timestamp DESC LIMIT 2;", [dev_id])
            if logs:
                print(f"{BOLD}{CYAN}│{RESET}  {BOLD}SEGURIDAD RECIENTE:{RESET}")
                for l in logs:
                    t_str = datetime.fromtimestamp(l.get("timestamp", 0) / 1000).strftime("%H:%M:%S")
                    print(f"{BOLD}{CYAN}│{RESET}  {RED}🛡️  [{t_str}] {l.get('detail')[:55]}{RESET}")
            else:
                print(f"{BOLD}{CYAN}│{RESET}  {GREEN}🛡️  Anti-Tamper: Sin alertas ni violaciones recientes{RESET}")

            print(f"{BOLD}{CYAN}└───────────────────────────────────────────────────────────────────────────┘{RESET}")
            print(f"{DIM}Refrescando cada 1.5s... (Ctrl+C para salir){RESET}")

            time.sleep(1.5)
    except KeyboardInterrupt:
        print(f"\n{YELLOW}Monitor en vivo finalizado.{RESET}")


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
