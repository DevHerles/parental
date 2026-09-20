# Aegis Parental Control ── Especificación Técnica: Suite de Estadísticas PRO y Métricas de Uso

## 1. Contexto y Objetivos

Para ofrecer supervisión parental de nivel profesional, Aegis no solo debe permitir el bloqueo reactivo de aplicaciones, sino también proporcionar **visibilidad analítica profunda sobre los hábitos digitales del menor**:
1. **Ranking de Aplicaciones Más Consumidas**: Desglose cuantitativo del tiempo diario invertido en cada aplicación, porcentaje relativo y visualización gráfica.
2. **Métricas de Salud Digital y Comportamiento**:
   - Tiempo total en pantalla acumulado en el día.
   - Frecuencia de desbloqueos / activaciones del dispositivo (*pickups*).
   - Volumen de intentos de evasión, desinstalación o accesos a ajustes neutralizados.
   - Distribución horaria del consumo (Madrugada, Mañana, Tarde, Noche / Hora pico).
   - Desglose por categorías (Juegos, Redes y Video, Educación, Navegación Web, Utilidades del Sistema).
3. **Visualización en Consola Linux**:
   - Comando directo `./aegis stats` con salida ejecutiva formateada con tablas y barras ANSI.
   - Modo de pantalla completa interactivo dentro de `./aegis monitor` alternable mediante la tecla táctica `[8]` / `[T]` (**Stats**).

---

## 2. Esquema de Base de Datos en Turso Cloud

### 2.1 Tabla `app_usage_stats`
Almacena el desglose diario por aplicación y dispositivo.
```sql
CREATE TABLE IF NOT EXISTS app_usage_stats (
    device_id TEXT NOT NULL,
    package_name TEXT NOT NULL,
    app_name TEXT,
    date TEXT NOT NULL, -- Formato YYYY-MM-DD
    usage_minutes INTEGER DEFAULT 0,
    last_time_used INTEGER DEFAULT 0, -- Epoch millis
    category TEXT DEFAULT 'OTHER', -- GAMES, SOCIAL_VIDEO, EDUCATIONAL, NAVIGATION, UTILITIES, OTHER
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, package_name, date)
);
```

### 2.2 Tabla `device_daily_metrics`
Almacena el resumen agregado de salud digital por día.
```sql
CREATE TABLE IF NOT EXISTS device_daily_metrics (
    device_id TEXT NOT NULL,
    date TEXT NOT NULL, -- Formato YYYY-MM-DD
    total_screen_time_minutes INTEGER DEFAULT 0,
    unlocks_count INTEGER DEFAULT 0,
    tamper_blocked_count INTEGER DEFAULT 0,
    morning_minutes INTEGER DEFAULT 0,   -- 06:00 a 11:59
    afternoon_minutes INTEGER DEFAULT 0, -- 12:00 a 17:59
    evening_minutes INTEGER DEFAULT 0,   -- 18:00 a 23:59
    night_minutes INTEGER DEFAULT 0,     -- 00:00 a 05:59
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, date)
);
```

---

## 3. Categorización Automática de Aplicaciones

El sistema clasifica las aplicaciones automáticamente en 5 categorías fundamentales:
- **`GAMES` (🎮 Juegos)**: Paquetes que contienen `roblox`, `puzzle`, `flow`, `minecraft`, `brawlstars`, `supercell`, `game`, `clash`, `king`, `candy`.
- **`SOCIAL_VIDEO` (📱 Redes y Video)**: Paquetes como `musically` (TikTok), `youtube`, `katana` (Facebook), `orca` (Messenger), `instagram`, `twitch`, `netflix`, `discord`, `snapchat`, `twitter`.
- **`EDUCATIONAL` (📚 Educación)**: Paquetes como `duolingo`, `classroom`, `khan`, `dictionary`, `read`, `math`.
- **`NAVIGATION` (🌐 Navegación)**: Paquetes de navegadores web como `chrome`, `firefox`, `browser`, `opera`.
- **`UTILITIES` (⚙️ Utilidades y Sistema)**: Calculadora, reloj, ajustes, launcher, cámara, archivos y herramientas del sistema.

---

## 4. Diseño del Comando CLI `./aegis stats`

El comando `./aegis stats` presenta un panel ejecutivo compuesto por:
1. **Header del Dispositivo**: Nombre, ID, fecha de consulta y estado online en tiempo real.
2. **4 Tarjetas KPI Clave**:
   - 🕒 *Tiempo Pantalla Hoy* (horas y minutos).
   - 📱 *Desbloqueos / Pickups* (conteo diario).
   - 🚨 *Evasiones Repelidas* (total de bloqueos anti-tampering hoy).
   - 🛡️ *Score de Salud Digital* (cálculo de 0 a 100 ponderando evasiones, cumplimiento de horarios y porcentaje de juegos/redes).
3. **Ranking Top de Apps Consumidas**:
   - Columnas: Posición, Estado de bloqueo, Nombre de la App, Categoría, Tiempo en minutos/horas, Porcentaje del total y Barra visual coloreada (`████████░░░░`).
4. **Distribución por Categorías**:
   - Barras proporcionales mostrando porcentaje y tiempo dedicado a Juegos vs Educación vs Redes vs Navegación.
5. **Curva Horaria de Actividad**:
   - Histograma visual de 4 franjas: Madrugada (00-06h), Mañana (06-12h), Tarde (12-18h) y Noche (18-24h).

---

## 5. Integración TUI en `./aegis monitor`

En el monitor de pantalla completa `AegisHtopMonitor`:
- Se agrega el modo de vista `self.view_mode = "MAIN" | "STATS"`.
- Atajo de teclado: `[8]`, `[T]` o `[F8]` conmuta instantáneamente entre la vista principal (apps + historial de eventos) y el panel analítico de estadísticas.
- La barra de teclas de función en la fila inferior se actualiza a:
  ` 1 Help  2 Lock  3 +15m  4 Resume  5 Ping  6 Toggle  7 Sync  8 Stats  10 Quit `
