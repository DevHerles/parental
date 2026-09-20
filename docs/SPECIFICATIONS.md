# Especificación Técnica Completa: Aegis Control Parental (v1.0.0 Hardened)

**Documento**: Especificación Técnica y Arquitectura del Sistema  
**Plataforma**: Android 8.0 (API 26) a Android 14 / 15 (API 34/35+)  
**Dispositivo de Referencia**: Xiaomi Redmi Note 12 (`23028RA60L`, HyperOS / Android 14)  
**Lenguaje / Framework**: Kotlin 2.0+ / Jetpack Compose / Material Design 3  
**Arquitectura**: Clean Architecture + MVVM + Repository Pattern + StateFlow reactivo  

---

## 1. Visión General del Sistema

Aegis Control Parental es una solución nativa para Android diseñada para supervisar, regular y bloquear en tiempo real aplicaciones distractoras (TikTok, YouTube, redes sociales, juegos) y prevenir la manipulación o desinstalación del sistema de control parental.

El sistema fue endurecido específicamente para superar las optimizaciones agresivas de gestión de memoria y batería de fabricantes como Xiaomi (HyperOS / MIUI), garantizando que el servicio centinela no sea terminado ni penalizado por el sistema operativo.

```
+-------------------------------------------------------------------------+
|                              USUARIO / NIÑA                             |
+-------------------------------------------------------------------------+
       |                                                    |
       v (Abre TikTok / App Bloqueada)                      v (Abre Ajustes / Desinstalar)
+-------------------------------------------------------------------------+
|                  ParentalAccessibilityService (Centinela)               |
|  - Filtra TYPE_WINDOW_STATE_CHANGED y TYPE_WINDOW_CONTENT_CHANGED       |
|  - Comprueba Lista Blanca Esencial (isSystemEssentialPackage)           |
+-------------------------------------------------------------------------+
       |                                                    |
       v (Coincidencia Lista Negra)                         v (Tamper Detectado)
+-----------------------------------+              +----------------------+
|        LockScreenActivity         |              |  GLOBAL_ACTION_HOME  |
|  - Pantalla completa Compose      |              |          +           |
|  - Inmune a SwipeUpClean          |              |  LockScreenActivity  |
|  - adjustResize para Teclados IME |              +----------------------+
|  - Desbloqueo temporal por PIN    |
+-----------------------------------+
       ^
       | Lee / Actualiza Estado
+-------------------------------------------------------------------------+
|                        ParentalRepository                               |
|  - EncryptedSharedPreferences (AES-256-GCM / AES-256-SIV)               |
|  - StateFlow: settings, restrictions, schedules, telemetry              |
|  - Jerarquía de Decisión: Instant Lock > Whitelist > Unlock > Blocked   |
+-------------------------------------------------------------------------+
```

---

## 2. Arquitectura de Componentes

### 2.1. `ParentalAccessibilityService` (Motor de Detección)
- **Rol**: Centinela reactivo en tiempo real sin polling de CPU.
- **Configuración (`accessibility_service_config.xml`)**:
  - `accessibilityEventTypes`: `typeWindowStateChanged | typeWindowContentChanged | typeWindowsChanged`.
  - `accessibilityFlags`: `flagDefault | flagRetrieveInteractiveWindows | flagIncludeNotImportantViews | flagReportViewIds`.
  - `canRetrieveWindowContent`: `true`.
- **Filtro Anti-Rebote**: Ventana de supresión de eventos idénticos de 2000 ms para evitar bucles de navegación.
- **Filtrado Web**: Inspección de nodos en navegadores buscando la barra de URL (`com.android.chrome:id/url_bar`) para bloquear dominios vetados sin necesidad de VPN.

### 2.2. `LockScreenActivity` (Capa de Bloqueo Visual)
- **Evolución frente a WindowManager**: El bloqueo no utiliza una superposición flotante (`SYSTEM_ALERT_WINDOW`) como capa principal, sino una `Activity` nativa a pantalla completa lanzada con:
  - `Intent.FLAG_ACTIVITY_NEW_TASK`
  - `Intent.FLAG_ACTIVITY_CLEAR_TOP`
  - `Intent.FLAG_ACTIVITY_SINGLE_TOP`
- **Control de Navegación**:
  - Intercepta `onBackPressedDispatcher`: cualquier intento de retroceso envía al usuario a la pantalla de inicio mediante `Intent.ACTION_MAIN + Intent.CATEGORY_HOME`.
- **Gestión de Teclado**:
  - `android:windowSoftInputMode="adjustResize"` en el Manifest para permitir que el teclado del sistema se despliegue sin tapar ni desmontar el campo de PIN.

### 2.3. `ParentalDeviceAdminReceiver` (Anti-Desinstalación a Nivel de SO)
- Registrado como Administrador de Dispositivo bajo la política `device_admin_policies.xml`.
- Impide que el instalador de paquetes de Android permita desinstalar la app sin antes desvincular el administrador en Ajustes (lo cual está interceptado y bloqueado por el centinela).

### 2.4. `BootReceiver` (Arranque Automático)
- Registrado para `android.intent.action.BOOT_COMPLETED`, `android.intent.action.MY_PACKAGE_REPLACED` y variantes OEM (`QUICKBOOT_POWERON`).
- Configurado con `android:directBootAware="true"`.

### 2.5. Arquitectura de Seguridad en UI: Separación Panel Público vs Consola Parental
- **Panel Inicial / Público (`ChildProtectedStatusScreen`)**:
  - Pantalla accesible por la niña o cualquier usuario al pulsar el icono de la app.
  - **Estricta Política de Solo Lectura**: Prohibición total de controles de configuración, interruptores (*toggles*) o botones de acción ejecutiva (como desactivar bloqueos de TikTok o cancelar pausas).
  - Componentes visibles:
    - Escudo de estado del dispositivo (*"Dispositivo Protegido"*).
    - Estado de los servicios de sistema (Centinela, Anti-desinstalación, Pantalla flotante).
    - Indicador informativo de tiempo de uso restante (en caso de existir pausa temporal activa).
    - Botón protegido: *"Acceso a Consola de Padres"* (despliega diálogo modal de PIN Maestro).
- **Consola Parental Privada (`ParentDashboardScreen`)**:
  - Accesible **únicamente** tras la validación criptográfica exitosa del PIN Maestro (`repository.verifyPin`).
  - Centralización absoluta de todos los controles ejecutivos (*toggles*):
    - Switches rápidos de apps: TikTok, YouTube, Redes Sociales, Videojuegos.
    - Switch de Bloqueo Instantáneo (*Freeze* inmediato del dispositivo).
    - Switch de Protección Anti-Desinstalación y Filtro Web.
    - Botón *"Bloquear Ahora"* para anular pausas temporales activas.
    - Acceso a gestión granular de aplicaciones instaladas y configuración de horarios/toque de queda.

### 2.6. Neutralización de Ventanas Flotantes (Freeform) y Pantalla Dividida (Split-Screen)
- **Vulnerabilidad Previa**:
  - En Xiaomi HyperOS / MIUI, las aplicaciones pueden ejecutarse en modo `WINDOWING_MODE_FREEFORM` (ventana flotante) o pantalla dividida. Al ser un nivel de ventana superior a las actividades fullscreen normales, TikTok podía flotar sobre `LockScreenActivity` o permanecer activo en split-screen si la actividad de bloqueo se auto-cerraba.
  - Además, MIUI resetea automáticamente el permiso `MIUIOP 10021` ("Mostrar ventanas emergentes en segundo plano") tras cada instalación manual de APK.
- **Mecanismo de Neutralización**:
  1. **Expulsión Global Dual (`GLOBAL_ACTION_HOME` + `GLOBAL_ACTION_BACK`)**:
     Ejecutado de inmediato a nivel de AccessibilityService en el kernel de Android. Colapsa cualquier sesión de pantalla dividida (split-screen) devolviendo el sistema al Launcher.
  2. **Gesto Nativo de Cierre de Ventana Flotante (`dispatchGesture`)**:
     Inspecciona la lista de `windows`. Si la ventana restringida tiene dimensiones menores a la pantalla completa (ventana flotante), calcula el centro de su barra inferior y despacha un gesto vertical hacia arriba (`swipe up`), forzando a MIUI a cerrar y descartar la ventana flotante al instante.
  3. **Terminación de Procesos (`killBackgroundProcesses`)**:
     Una vez que la aplicación es enviada a segundo plano por el HOME/gesto, se invoca `ActivityManager.killBackgroundProcesses(packageName)` para liquidar hilos, sockets de red y reproducción de audio en background.
  4. **Corrección de Evaluación en Presencia de Ventana Flotante**:
     El Centinela ya no descarta eventos ciegamente si `LockScreenActivity.isLockScreenVisible` es `true`; si el paquete recibido pertenece a la lista de bloqueadas (e.g. TikTok intentando flotar encima), el bloqueo se ejecuta forzosamente.
  5. **Gestor de Permiso Xiaomi (`MIUIOP 10021`)**:
     `PermissionHelper.isMiuiBackgroundPopupGranted` valida por reflexión `AppOpsManager.checkOpNoThrow(10021)`. Si el permiso fue revocado por MIUI, el panel inicial muestra una alerta interactiva destacada con acceso directo a `PermissionsEditorActivity` de Xiaomi para restablecerlo con 1 toque.

---

## 3. Capa de Estabilidad y Whitelist Esencial (`PermissionHelper`)

### 3.1. Problema de Auto-Bloqueo de Teclados y Sistema
En Android, el teclado en pantalla (IME), los selectores de autocompletado y el launcher son procesos independientes con sus propios `packageName`. Si el sistema los evalúa como apps normales, se producían bloqueos infinitos y cierre instantáneo del diálogo del PIN.

### 3.2. Implementación de `isSystemEssentialPackage`
Antes de cualquier evaluación de bloqueo, el sistema consulta la whitelist estricta:

```kotlin
fun isSystemEssentialPackage(context: Context, packageName: String): Boolean {
    // 1. La propia aplicación
    if (packageName == context.packageName) return true

    // 2. Teclados en pantalla (IME) activos o instalados
    val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val imePackages = imeManager?.inputMethodList?.map { it.packageName } ?: emptyList()
    if (imePackages.contains(packageName)) return true
    if (packageName.contains("inputmethod") || packageName.contains("latin") || packageName.contains("keyboard")) return true

    // 3. Lanzadores de Inicio (Launchers)
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val homePackages = context.packageManager.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }
    if (homePackages.contains(packageName)) return true

    // 4. Teléfono, Llamadas y Emergencias
    if (packageName == "com.android.phone" || packageName == "com.android.server.telecom" || packageName.contains("dialer")) return true

    // 5. SystemUI y Permisos del Sistema
    if (packageName == "com.android.systemui" || packageName.startsWith("com.android.permissioncontroller")) return true

    return false
}
```

---

## 4. Endurecimiento para Xiaomi HyperOS / MIUI (`SwipeUpClean`)

### 4.1. Análisis del Fallo de `SwipeUpClean`
- **Comportamiento**: En Xiaomi HyperOS, al deslizar hacia arriba una app en la vista de Tareas Recientes (Multitarea), el kernel ejecuta:
  ```
  ActivityManager: Killing <PID>:com.parental.control/u0a394: SwipeUpClean
  libc: kill: send 9 to pid <PID>
  ```
- **Consecuencia Crítica**: Si la interfaz de usuario (`MainActivity`) compartía el PID con el `ParentalAccessibilityService`, el `SIGKILL` destruía el centinela. Android marcaba el servicio como `Crashed services` y suspendía el despacho de eventos de accesibilidad durante un período de penalización de 3 minutos, dejando desprotegido el teléfono.

### 4.2. Solución: `android:excludeFromRecents="true"`
En [`AndroidManifest.xml`](file:///home/herles/asf/devherles/parental/app/src/main/AndroidManifest.xml):
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:launchMode="singleTop"
    android:windowSoftInputMode="adjustResize" />

<activity
    android:name=".ui.child.LockScreenActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:launchMode="singleTop"
    android:windowSoftInputMode="adjustResize" />
```
- **Efecto**: Aegis nunca se indexa en la lista de tareas recientes de Xiaomi. Al pulsar el botón o gesto de multitarea, no existe tarjeta que el usuario pueda deslizar, neutralizando `SwipeUpClean` por completo.

### 4.3. Matriz de Permisos Especiales de HyperOS
Xiaomi introduce AppOps propietarios que bloquean actividades en segundo plano si no se conceden explícitamente:
| AppOp | Código Xiaomi | Nombre Funcional | Estado Requerido |
|---|---|---|---|
| `MIUIOP(10021)` | 10021 | Mostrar ventanas emergentes en segundo plano | `allow` |
| `MIUIOP(10022)` | 10022 | Mostrar en pantalla de bloqueo | `allow` |
| `MIUIOP(10008)` | 10008 | Inicio automático (AutoStart) | `allow` |
| `SYSTEM_ALERT_WINDOW` | 24 | Superposición sobre otras aplicaciones | `allow` |
| `PACKAGE_USAGE_STATS` | 43 | Acceso a estadísticas de uso | `allow` |
| `deviceidle` | N/A | Exclusión de Doze / Ahorro de Batería Extremo | `whitelist` |

---

## 5. Jerarquía de Decisión de Bloqueo (`ParentalRepository`)

El método [`isPackageBlocked(packageName)`](file:///home/herles/asf/devherles/parental/app/src/main/java/com/parental/control/core/data/ParentalRepository.kt#L205-L259) evalúa el acceso con estricta precedencia:

```
[ Paquete Solicitado ]
        |
        v
¿Es isSystemEssentialPackage? -------------> SÍ: PERMITIR (false)
        | NO
        v
¿isInstantLockActive == true? -------------> SÍ: BLOQUEAR (true)
        | NO
        v
¿isTemporarilyUnlocked == true? -----------> SÍ: PERMITIR (false)
        | NO
        v
¿Coincide con TikTok / Redes / Juegos? -----> SÍ: BLOQUEAR (true)
        | NO
        v
¿Horario Toque de Queda activo? -----------> SÍ: BLOQUEAR si está marcada
        | NO
        v
PERMITIR (false)
```

### 5.1. Mecanismo de Pausa Temporal y Desbloqueo Inmediato
- **Pausa por PIN**: El padre puede otorgar 15 min, 30 min o 1 hora. Esto guarda `temporaryUnlockUntil = System.currentTimeMillis() + (minutos * 60 * 1000L)`.
- **Botón "Bloquear Ahora"**: Llama a `clearTemporaryUnlock()`, reseteando `temporaryUnlockUntil = 0L` tanto en memoria (`StateFlow`) como en disco (`EncryptedSharedPreferences`). Cualquier app restringida abierta vuelve a bloquearse inmediatamente.

---

## 6. Procedimiento de Despliegue y Automatización por USB (ADB)

### 6.1. Compilación Release
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/herles/Android/Sdk
export PATH=$PATH:/home/herles/Android/Sdk/platform-tools

./gradlew :app:assembleRelease --stacktrace
cp app/build/outputs/apk/release/app-release.apk ./AegisParentalControl.apk
```

### 6.2. Instalación Silenciosa (Saltando Prompt de 10s de MIUI)
```bash
adb -s <DEVICE_ID> push ./AegisParentalControl.apk /data/local/tmp/app.apk
adb -s <DEVICE_ID> shell pm install -r -d /data/local/tmp/app.apk
```

### 6.3. Activación y Asignación de Permisos por Línea de Comandos
```bash
# Habilitar Servicio de Accesibilidad
adb -s <DEVICE_ID> shell settings put secure enabled_accessibility_services com.parental.control/com.parental.control.service.ParentalAccessibilityService
adb -s <DEVICE_ID> shell settings put secure accessibility_enabled 1

# Permisos especiales MIUI y Android
adb -s <DEVICE_ID> shell appops set com.parental.control 10021 allow
adb -s <DEVICE_ID> shell appops set com.parental.control 10022 allow
adb -s <DEVICE_ID> shell appops set com.parental.control 10008 allow
adb -s <DEVICE_ID> shell appops set com.parental.control SYSTEM_ALERT_WINDOW allow
adb -s <DEVICE_ID> shell appops set com.parental.control GET_USAGE_STATS allow

# Inmunidad contra ahorro de batería
adb -s <DEVICE_ID> shell dumpsys deviceidle whitelist +com.parental.control
```

### 6.4. Comprobación del Estado de Salud
```bash
# Verificar que el servicio está activo y no en Crashed
adb -s <DEVICE_ID> shell dumpsys accessibility | grep -E "Bound services|Crashed services"

# Verificar ventana activa
adb -s <DEVICE_ID> shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"
```

---

## 7. Matriz de Pruebas y Validación de Calidad

| ID | Escenario de Prueba | Resultado Esperado | Estado |
|---|---|---|---|
| **TC-01** | Apertura de TikTok desde Launcher | Intercepción en <100ms, despliegue de `LockScreenActivity`. | **PASADO** |
| **TC-02** | Entrada de PIN en `LockScreenActivity` | Apertura de Gboard sin cierre de pantalla ni parpadeos. | **PASADO** |
| **TC-03** | PIN Incorrecto | Mensaje de error en rojo; la pantalla permanece bloqueada. | **PASADO** |
| **TC-04** | PIN Correcto (Pausa 15 min) | Desbloqueo de TikTok durante exactamente 15 minutos. | **PASADO** |
| **TC-05** | Pulsar "Bloquear Ahora" en Consola | Reactivación instantánea del bloqueo; TikTok vuelve a bloquearse. | **PASADO** |
| **TC-06** | Intento de SwipeUpClean en Recientes | La app no figura en recientes; el proceso persiste indemne. | **PASADO** |
| **TC-07** | Intento de abrir Ajustes del Sistema | Bloqueo inmediato y redirección al Inicio (`GLOBAL_ACTION_HOME`). | **PASADO** |
| **TC-08** | Navegación a `tiktok.com` en Chrome | Cierre inmediato de pestaña y vuelta a Home. | **PASADO** |
| **TC-09** | Reinicio del dispositivo móvil | Reactivación automática del centinela tras arranque. | **PASADO** |
| **TC-10** | Apertura de TikTok en Ventana Flotante (Freeform) | Expulsión inmediata al primer intento (<100ms) mediante BACK prioritario + flick nativo ascendente total (80ms). | **PASADO** |
| **TC-11** | Apertura de TikTok en Ventana Dividida (Split-Screen) | Colapso inmediato mediante HOME + BACK y presentación de LockScreen. | **PASADO** |

---

## 8. Arquitectura de Expulsión Instantánea de Ventanas Flotantes (Xiaomi HyperOS / MIUI)

### 8.1. Desacoplamiento de Flujos (Flotante vs Pantalla Completa)
En Xiaomi HyperOS, una ventana flotante (`windowingMode = 5`) flota por encima de todas las actividades normales y del launcher.
- **Flujo Pantalla Completa**: Ejecuta `GLOBAL_ACTION_BACK` + `GLOBAL_ACTION_HOME` inmediatamente a 0ms.
- **Flujo Ventana Flotante**: Inyecta `GLOBAL_ACTION_BACK` a 0ms y **NO ejecuta `GLOBAL_ACTION_HOME` antes del gesto**. Llamar a HOME antes del flick activa la animación del Launcher (~250ms), lo que causa que HyperOS minimice la app a una miniatura en la esquina y descarte eventos táctiles.

### 8.2. Filtrado de Barras de Caption y Prevención de Concurrencia de Gestos
HyperOS genera múltiples sub-ventanas de tipo `TYPE_APPLICATION` para cada app flotante:
1. `Embedded{Miui Caption...}` (manija superior decorativa, alto ~71px).
2. `Embedded{Miui Bottom Caption...}` (manija inferior decorativa, alto ~43px).
3. Ventana real de la app (e.g. 756x1680px).

**Regla de Oro**: Dado que Android descarta cualquier gesto si ya hay uno en progreso (`dispatchGesture` retorna `false`), el Centinela:
- Filtra toda ventana con `width < 100` o `height < 100`.
- Selecciona **una única ventana candidata principal** por ciclo de inspección.

### 8.3. Especificación Cinética y Destrucción por BACK
- **Ventana Flotante Enfocada**: `GLOBAL_ACTION_BACK` a 0ms destruye inmediatamente la actividad flotante en <50ms.
- **Ventana Flotante Estándar**:
  - Punto inicial: `(b.centerX(), b.bottom - 15f)`.
  - Punto final: `(b.centerX(), (b.top - 50f).coerceAtLeast(40f))`.
  - **Duración: 80ms** (velocidad > 18 px/ms, cubre toda la altura de la ventana).
- **Mini-dock / Miniatura en Esquina (<350px de ancho)**:
  - Disparo de tap directo de 30ms en el centro para forzar foco: `(b.centerX(), b.centerY())`.
  - Seguido inmediatamente de `GLOBAL_ACTION_BACK` para cerrarla.

### 8.4. Parámetros de Temporización y Watchdog Ultraligero
- `WATCHDOG_INTERVAL_MS = 150L`: Latencia de detección proactiva de 150ms.
- `REPELLING_LOCK_MS = 250L`: Cooldown de repulsión ultra-corto de 250ms.
- `REBOUND_COOLDOWN_MS = 300L`.
- **Cero IPC Bloqueante**: Resolución de paquetes mediante `w.title` en 0ms, prohibiendo el acceso síncrono a `w.root` en el hilo principal para prevenir `APP_SCOUT_HANG`.

