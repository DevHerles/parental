# Aegis Parental Control ── Especificación Técnica: Monitor TUI estilo `htop`

## 1. Contexto y Objetivos

El comando `./aegis monitor` previo utilizaba secuencias de escape ANSI básicas combinadas con limpieza de pantalla completa (`\033[2J\033[H`) en cada ciclo de sondeo de 1.5s. Este enfoque generaba dos problemas críticos:
1. **Parpadeo visual molesto (*screen flickering*)**: La terminal borraba y redibujaba todo el marco periódicamente.
2. **Dimensiones fijas y desperdicio de pantalla**: La salida estaba restringida a una caja estática de 77 columnas, sin aprovechar monitores modernos de alta resolución ni ventanas maximizadas.

Esta especificación define la arquitectura del nuevo **Monitor TUI de Pantalla Completa estilo `htop` / `btop`** para la consola Linux de Aegis.

---

## 2. Principios de Diseño

1. **Renderizado Diferencial Estricto**:
   - Se utiliza la biblioteca estándar `curses` con búfer alterno de terminal (`enter_ca_mode`).
   - Se emplea `curses.doupdate()`: el motor compara el búfer virtual con el búfer físico de la pantalla y emite secuencias de escape exclusivamente para las celdas que sufrieron modificaciones. **Cero parpadeo visual**.
2. **Uso Total del Monitor y Redimensionamiento Dinámico**:
   - Detecta el tamaño del monitor en tiempo real (`stdscr.getmaxyx()`).
   - Captura el evento `curses.KEY_RESIZE` (señal `SIGWINCH`) para recalcular el diseño adaptativo instantáneamente.
3. **Desacoplamiento de Hilos (UI reactiva vs. Red)**:
   - **Hilo Principal (TUI @ 10 FPS)**: Bucle de eventos de interfaz con `stdscr.timeout(100)`. Maneja la captura no bloqueante de teclas, animación suave de segunderos y renderizado.
   - **Hilo Secundario (Daemon Worker)**: Realiza consultas HTTP/2 contra Turso Cloud (`/v2/pipeline`) cada 1.0s o por disparo de eventos. Actualiza un objeto de estado en memoria protegido por cerrojo (*thread-safe state snapshot*).
4. **Interactividad Táctica en Caliente**:
   - Navegación por la lista de aplicaciones con `↑` y `↓`.
   - Conmutación directa de bloqueo/permiso con `[Espacio]` o `[A]`.
   - Disparo de comandos con hotkeys inmediatos sin pulsar Enter:
     - `[L]` / `F2`: Bloqueo total instantáneo.
     - `[U]` / `F3`: Concesión de 15 minutos de recreo.
     - `[R]` / `F4`: Reanudación del modo estándar / anulación de pausa.
     - `[P]` / `F5`: Prueba de latencia (Ping).
     - `[Q]` / `F10` / `ESC`: Salida limpia restaurando la terminal original.

---

## 3. Arquitectura de Módulos

```
┌─────────────────────────────────────────────────────────────┐
│                       HtopMonitor                           │
│  ┌─────────────────────────┐   ┌─────────────────────────┐  │
│  │     DataModel (State)   │   │     CursesRenderer      │  │
│  │  - device, settings     │   │  - Header Panel         │  │
│  │  - apps list            │   │  - Monitored Apps Table │  │
│  │  - tamper logs          │   │  - Security Event Log   │  │
│  │  - ping RTT             │   │  - Status Notification  │  │
│  │  - selection index      │   │  - Tactical Hotkey Bar  │  │
│  └───────────▲─────────────┘   └────────────▲────────────┘  │
└──────────────┼──────────────────────────────┼───────────────┘
               │ (actualizaciones)            │ (render @ 100ms)
    ┌──────────┴──────────────┐      ┌────────┴───────────────┐
    │  BackgroundSyncWorker   │      │   Terminal Input Loop  │
    │  - Polling cada 1.0s    │      │   - Non-blocking getch │
    │  - TursoClient pipeline │      │   - Disparo comandos   │
    └─────────────────────────┘      └────────────────────────┘
```

---

## 4. Distribución Visual de Pantalla (*Layout Grid*)

Para un monitor de dimensiones `(max_y, max_x)`:
1. **Header Panel** (Filas 0 a 4):
   - Fila 0: Barra de título con versión y estado de conexión Turso Cloud.
   - Fila 1: Panel dividido en dos columnas: Dispositivo objetivo y Presencia con contador en vivo de segundos.
   - Fila 2: Indicador gráfico de batería con barra progresiva coloreada y Modo de protección actual.
   - Fila 3: Aplicación en primer plano en la tablet de la niña y latencia Ping RTT.
   - Fila 4: Separador horizontal adaptable `├──────┤`.
2. **Main Apps Table (60% del alto disponible)** (Filas 5 a `table_end_y`):
   - Fila 5: Encabezados de columnas (`IDX`, `ESTADO`, `APLICACIÓN`, `PAQUETE` dinámico).
   - Filas 6 a `table_end_y - 1`: Filas de aplicaciones con cursor de selección resaltado (fondo azul/cyan), scroll vertical y estado estricto.
3. **Historial de Acciones y Seguridad en el Dispositivo (40% del alto disponible)**:
   - Fila `table_end_y`: Separador horizontal `├──────┤`.
   - Fila `table_end_y + 1`: Encabezado `🛡️  HISTORIAL DE ACCIONES Y SEGURIDAD EN EL DISPOSITIVO (TURSO CLOUD):`.
   - Filas siguientes: Feed unificado y cronológico inverso de eventos en tiempo real:
     - 🚨 *Intentos de desinstalación o manipulación de PackageInstaller repelidos en 0ms*.
     - ⚠️  *Intentos de acceso a Ajustes del sistema bloqueados*.
     - 🛡️ *Detección de textos o interfaces prohibidas*.
     - 🚫 / ✅ *Aplicaciones bloqueadas o permitidas remotamente*.
     - 🔒 / ⏱️ / 🛡️ *Órdenes de Bloqueo Total, Pausas de Recreo o Reanudaciones ejecutadas*.
     - 🏓 *Pruebas de latencia Ping respondidas por la tablet*.
4. **Borde Inferior con Notificación de Estado** (Fila `max_y - 2`):
   - Marco inferior embebido `└─[ Mensaje de estado en vivo ]──────┘`.
5. **Footer Hotkey Bar** (Fila `max_y - 1`):
   - Barra de teclas de función idéntica a `htop`: ` 1 Help  2 Lock  3 +15m  4 Resume  5 Ping  6 Toggle  7 Sync  10 Quit `.

---

## 5. Esquema de Colores (Terminal 256 / Estándar)

| Par de Color | Primer Plano | Fondo | Propósito |
|---|---|---|---|
| `PAIR_HEADER` | Cyan brillante | Negro | Títulos, bordes y encabezados |
| `PAIR_ONLINE` | Verde brillante | Negro | Estado online, batería >50%, apps permitidas |
| `PAIR_ALERT` | Rojo brillante | Negro | Bloqueo activo, apps bloqueadas, tamper alerts |
| `PAIR_WARN` | Amarillo brillante | Negro | Batería media, pausa temporal activa |
| `PAIR_SELECTED` | Blanco brillante | Azul/Cyan | Fila de app seleccionada con cursor |
| `PAIR_HOTKEY_NUM` | Negro | Cyan | Número de atajo en barra inferior (F1..F10) |
| `PAIR_HOTKEY_TXT` | Blanco brillante | Gris oscuro | Etiqueta de atajo en barra inferior |

---

## 6. Manejo de Errores y Robustez

1. **Restauración de Terminal Incondicional**: El bucle corre dentro de `curses.wrapper` asegurando que cualquier excepción no controlada o señal `SIGINT` (`Ctrl+C`) restaure el cursor, modo eco y búfer de pantalla original.
2. **Tolerancia a Latencia o Caídas de Red**: Las consultas de Turso fallidas en el hilo daemon no detienen la UI ni arrojan excepciones visibles; reportan un estado `OFFLINE / Reintentando...` en el banner.
3. **Dimensiones Mínimas Seguras**: Si la terminal es menor a 20 columnas o 10 filas, muestra un mensaje `"Terminal muy pequeña"` centrado hasta que el usuario expanda la ventana.
