# Especificación Técnica: Control Remoto en Tiempo Real y Suite Terminal PRO con Turso Cloud (LibSQL)

## 1. Visión General
Esta especificación define la arquitectura, protocolo de comunicación, modelos de datos e interfaces para el control remoto bidireccional y en tiempo real (< 1.5s de latencia) del sistema **Aegis Control Parental** utilizando **Turso Cloud (LibSQL)** como plano de control distribuido.

Permite al padre gobernar y auditar la tablet/celular de la hija tanto desde su Terminal Linux (mediante el CLI `./aegis`) como desde la aplicación móvil, garantizando persistencia en la nube, auto-desbloqueo reactivo, bloqueo instantáneo y fail-safe sin conexión.

---

## 2. Arquitectura del Sistema

```
 ┌────────────────────────────────────────────────────────┐
 │            PADRE: TERMINAL LINUX PRO CLI               │
 │    ./aegis status | lock | unlock | live | apps        │
 └───────────────────────────┬────────────────────────────┘
                             │ HTTPS / TLS v1.3 (Pipeline API v2)
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │           TURSO CLOUD (LibSQL Distributed DB)          │
 │              Hostname: aegis-parental-devherles        │
 │                                                        │
 │  • devices           • parental_settings               │
 │  • app_restrictions  • commands                        │
 │  • schedules         • tamper_logs                     │
 └───────────────────────────▲────────────────────────────┘
                             │ Fast Polling (1.5s activo / 10s reposo)
                             │ HTTPS / OkHttp HTTP/2 Connection Pool
 ┌───────────────────────────┴────────────────────────────┐
 │              TABLET HIJA (Lenovo Tab M11)              │
 │                                                        │
 │  ┌──────────────────────────────────────────────────┐  │
 │  │        TursoSyncManager (Background Coroutine)   │  │
 │  └────────────────────────┬─────────────────────────┘  │
 │                           │ StateFlow updates (<50ms)  │
 │  ┌────────────────────────▼─────────────────────────┐  │
 │  │ ParentalRepository + ParentalAccessibilityService│  │
 │  │ (Centinela Anti-Tamper, Cierre Inmediato, Watchdog)│
 │  └────────────────────────┬─────────────────────────┘  │
 │                           │ Notificación reactiva       │
 │  ┌────────────────────────▼─────────────────────────┐  │
 │  │ LockScreenActivity (Auto-dismiss / Remote Unlock)│  │
 │  └──────────────────────────────────────────────────┘  │
 └────────────────────────────────────────────────────────┘
```

---

## 3. Esquema de Base de Datos en Turso Cloud (`aegis-parental`)

### 3.1 `devices`
Almacena la telemetría viva y registro de dispositivos.
```sql
CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL DEFAULT 'family_default',
    role TEXT NOT NULL DEFAULT 'CHILD', -- 'CHILD' o 'PARENT'
    device_name TEXT NOT NULL,
    model TEXT,
    battery_percent INTEGER DEFAULT 100,
    is_charging INTEGER DEFAULT 0,
    current_foreground_app TEXT,
    is_online INTEGER DEFAULT 1,
    last_seen_at INTEGER NOT NULL
);
```

### 3.2 `parental_settings`
Configuración global sincronizada del dispositivo supervisado.
```sql
CREATE TABLE IF NOT EXISTS parental_settings (
    device_id TEXT PRIMARY KEY,
    is_instant_lock_active INTEGER DEFAULT 0,
    is_temporarily_unlocked INTEGER DEFAULT 0,
    temporary_unlock_until INTEGER DEFAULT 0,
    is_anti_uninstall_active INTEGER DEFAULT 1,
    is_web_filter_active INTEGER DEFAULT 1,
    daily_time_limit_minutes INTEGER DEFAULT 120,
    version INTEGER DEFAULT 1,
    updated_at INTEGER NOT NULL
);
```

### 3.3 `app_restrictions`
Reglas específicas por aplicación.
```sql
CREATE TABLE IF NOT EXISTS app_restrictions (
    device_id TEXT NOT NULL,
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    is_blocked INTEGER DEFAULT 1,
    daily_limit_minutes INTEGER DEFAULT 0,
    is_allowed_in_bedtime INTEGER DEFAULT 0,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, package_name)
);
```

### 3.4 `commands` (Cola de Comandos Tácticos)
Despacho y confirmación (`ACK`) de órdenes remotas.
```sql
CREATE TABLE IF NOT EXISTS commands (
    command_id TEXT PRIMARY KEY,
    target_device_id TEXT NOT NULL,
    source_device_id TEXT DEFAULT 'parent_cli',
    command_type TEXT NOT NULL, -- 'LOCK_NOW', 'UNLOCK_TEMPORARY', 'CLEAR_LOCK', 'UPDATE_APP', 'PING'
    payload TEXT,               -- JSON opcional con parámetros
    created_at INTEGER NOT NULL,
    executed_at INTEGER,
    status TEXT DEFAULT 'PENDING' -- 'PENDING', 'EXECUTED', 'CANCELLED'
);
```

### 3.5 `tamper_logs`
Historial de seguridad e intentos de manipulación registrados en la nube.
```sql
CREATE TABLE IF NOT EXISTS tamper_logs (
    log_id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    detail TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
```

---

## 4. Protocolo de Sincronización y Latencia

### 4.1 Frecuencia de Sincronización
- **Pantalla Activa / Interactiva (`isInteractive == true`)**: Heartbeat cada **1.500 ms**.
- **Pantalla Apagada / Reposo**: Heartbeat cada **10.000 ms**.
- **Apertura de App Bloqueada / Pantalla de Bloqueo Visible**: Disparo inmediato (`forceSync()` a **0 ms**).

### 4.2 Despacho y Manejo de Comandos
1. El padre inserta una fila en `commands` con `status = 'PENDING'`.
2. El `TursoSyncManager` en la tablet consume los comandos pendientes:
   - `LOCK_NOW`: Activa `isInstantLockActive = true` en `ParentalRepository`. Cierra apps y abre `LockScreenActivity` en <20ms.
   - `UNLOCK_TEMPORARY`: Lee `payload.minutes`, calcula `until = now + (minutes * 60 * 1000)` y actualiza `ParentalRepository`. Si `LockScreenActivity` está abierta, detecta el cambio por `StateFlow` y se cierra automáticamente (`finish()`) sin requerir PIN.
   - `CLEAR_LOCK`: Revoca cualquier desbloqueo temporal y restablece el bloqueo normal.
   - `UPDATE_APP`: Modifica `isBlocked` del paquete indicado.
   - `PING`: Registra `executed_at = now` para que el padre calcule la latencia RTT.
3. El `TursoSyncManager` actualiza el comando a `status = 'EXECUTED'`.

### 4.3 Resiliencia Offline (Fail-Safe)
Si el dispositivo pierde conectividad Wi-Fi o datos móviles:
- Aplica estrictamente las políticas cacheadas en `EncryptedSharedPreferences`.
- Ninguna distracción se desbloquea por falta de red.
- Los logs de tampering se encolan localmente y se suben a Turso al restablecerse la red.

---

## 5. Suite Terminal PRO (`./aegis`)

Script ejecutable para Linux con auto-detección de credenciales y motor de renderizado ANSI:
- `./aegis status`: Dashboard tabular de estado y telemetría.
- `./aegis live` / `./aegis monitor`: Pantalla interactiva en tiempo real (1s refresh, hotkeys `[l]`, `[u]`, `[r]`, `[q]`).
- `./aegis lock`: Bloqueo total inmediato con espera de `ACK`.
- `./aegis unlock [MIN]`: Concesión de tiempo temporal (default 30 min).
- `./aegis resume` / `clear`: Reanudar bloqueo estricto.
- `./aegis apps`: Lista tabular de apps supervisadas.
- `./aegis block <app>` / `unblock <app>`: Control granular de apps.
- `./aegis ping`: Diagnóstico de latencia RTT en milisegundos.
- `./aegis logs`: Historial de seguridad.
- `./aegis query "<SQL>"`: Consola SQL directa.
