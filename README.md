# Aegis Control Parental 🛡️ (Android Nativo - Kotlin & Jetpack Compose)

Aplicación nativa de **Control Parental y Enfoque Digital** diseñada para proteger a una niña de 11 años bloqueando distracciones adictivas (**TikTok, YouTube, Facebook, Instagram, Roblox, juegos, páginas web**), con un sistema multicapa **Anti-Desinstalación**, control de acceso para administradores (padres) mediante **PIN/Biometría**, y soporte para sincronización remota.

---

## 🚀 Características Principales

### 1. 🔒 Protección Anti-Desinstalación y Anti-Manipulación Multicapa
- **Administrador de Dispositivos (`DeviceAdminReceiver`)**: Inhabilita la desinstalación normal desde el launcher y el menú de aplicaciones de Android.
- **Centinela en Vivo (`AccessibilityService`)**: Detecta al instante si la niña intenta abrir los *Ajustes de Android*, el desinstalador de paquetes o deshabilitar la accesibilidad, forzando la vuelta a la pantalla de inicio (`GLOBAL_ACTION_HOME`) o solicitando el PIN maestro.
- **Modo Device Owner (Opcional - Máxima Seguridad)**: Compatible con aprovisionamiento empresarial por ADB para bloquear la desinstalación a nivel de sistema operativo (`setUninstallBlocked = true`).
- **Persistencia Continua**: Servicio en primer plano persistente (`WatchdogForegroundService`) con notificación fija, prioridad máxima y reactivación automática tras reiniciar el teléfono (`BootReceiver`).

### 2. 🚫 Bloqueo Inmediato de Distracciones (0ms de latencia)
- Bloquea en tiempo real aplicaciones como:
  - **TikTok** (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `com.zhiliaoapp.musically.go`)
  - **YouTube & YouTube Music** (`com.google.android.youtube`, `com.google.android.apps.youtube.music`)
  - **Facebook & Messenger** (`com.facebook.katana`, `com.facebook.orca`, `com.facebook.lite`)
  - **Instagram** (`com.instagram.android`, `com.instagram.lite`)
  - **Roblox, Twitch, Snapchat, Netflix, Twitter/X, Discord**.
- **Filtro Web en Navegadores**: Inspecciona URLs en Chrome, Firefox, Samsung Internet y Edge para bloquear accesos directos a `tiktok.com`, `youtube.com`, `facebook.com`, etc.

### 3. 🎨 Pantalla de Bloqueo Flotante Amigable (`LockOverlayService`)
- Desarrollada con **Jetpack Compose Material 3** sobre el `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
- Interfaz cálida y motivacional diseñada para una niña de 11 años:
  - *"¡Tiempo de Concentración! 📚 - TikTok está en pausa por tus padres"*.
  - Consejos de lectura, dibujo y estudio.
  - Botón rápido *"Volver a Inicio"*.
  - Botón discreto *"Acceso Padres (PIN)"* para conceder 15, 30 o 60 minutos de tiempo extra.

### 4. 👨‍👩‍👧 Consola Remota de los Padres (Dashboard)
- **Bloqueo Instantáneo**: Interruptor de emergencia para pausar todo el dispositivo de inmediato (hora de comer, dormir o reunión familiar).
- **Switches Rápidos**: Habilitar o deshabilitar apps clave con un solo toque.
- **Horarios y Toque de Queda**: Configuración de horas de estudio (ej. 15:00 - 18:00) y hora de dormir (ej. 21:30 - 07:00, con soporte de cruce de medianoche).
- **Telemetría y Registro de Manipulación**: Visualización del nivel de batería, estado de carga y registro de intentos de entrar a ajustes.

---

## 📂 Arquitectura del Proyecto

El proyecto está organizado en una arquitectura modular limpia:

```
parental/
├── core/                                   # Módulo Kotlin JVM puro (Lógica y Seguridad)
│   └── src/
│       ├── main/kotlin/com/parental/control/core/
│       │   ├── model/
│       │   │   ├── AppRestriction.kt       # Reglas de bloqueo por app
│       │   │   ├── CurfewSchedule.kt       # Algoritmo de horarios y toque de queda
│       │   │   ├── DistractionConstants.kt # Base de datos de paquetes y dominios
│       │   │   ├── DeviceTelemetry.kt      # Estado de batería y logs
│       │   │   └── ParentalSettings.kt     # Configuración y estado de PIN
│       │   └── security/
│       │       ├── PinSecurityManager.kt   # Hashing SHA-256 + Salt y verificación
│       │       └── AntiTamperWatchdog.kt   # Detección de intentos de manipulación
│       └── test/kotlin/com/parental/control/core/ # Pruebas unitarias automatizadas
│
├── app/                                    # Módulo Android (Compose, Servicios, Framework)
│   └── src/main/
│       ├── AndroidManifest.xml             # Permisos, servicios y receptores
│       ├── res/
│       │   ├── xml/device_admin_policies.xml       # Políticas de Administrador
│       │   └── xml/accessibility_service_config.xml # Configuración del Centinela
│       └── java/com/parental/control/
│           ├── ParentalApp.kt              # Inicialización de repositorio y canales
│           ├── MainActivity.kt             # Flujo de navegación Compose
│           ├── core/
│           │   ├── data/ParentalRepository.kt # Persistencia cifrada y estado reactivo
│           │   └── security/ParentalDeviceAdminReceiver.kt # Bloqueo desinstalación
│           ├── service/
│           │   ├── ParentalAccessibilityService.kt # Centinela en tiempo real
│           │   ├── LockOverlayService.kt           # Overlay Compose de bloqueo
│           │   ├── WatchdogForegroundService.kt    # Persistencia y telemetría
│           │   └── BootReceiver.kt                 # Reactivación tras reinicio
│           └── ui/
│               ├── child/                  # Asistente de configuración y Overlay
│               ├── parent/                 # Consola de administración y horarios
│               └── theme/                  # Colores y temas Material 3
```

---

## 🛠️ Cómo Abrir y Ejecutar el Proyecto

### 1. Abrir en Android Studio
1. Inicia **Android Studio** (disponible en tu sistema en `/opt/android-studio/bin/studio.sh`).
2. Selecciona **Open** y abre la carpeta `/home/herles/asf/devherles/parental`.
3. Android Studio sincronizará el proyecto con Gradle automáticamente.

### 2. Ejecutar Pruebas Unitarias desde Terminal
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :core:test
```

### 3. Configurar en el Teléfono de la Niña
1. Instala el APK en el dispositivo de la niña.
2. Al abrir la app, selecciona **"Dispositivo de mi Hija"**.
3. Sigue el asistente interactivo:
   - Define tu **PIN Maestro de 4 a 6 dígitos** (guarda el Código de Rescate generado).
   - Activa el **Administrador de Dispositivo** (impide desinstalar la app).
   - Concede el **Servicio de Accesibilidad** a *Aegis Control Parental*.
   - Habilita **Dibujar sobre otras apps** (para mostrar la pantalla de bloqueo).
   - Permite **Ignorar optimización de batería** (para que el sistema no lo duerma).
4. Pulsa **Activar Protección Aegis**.

---

## 🛡️ Opcional: Modo Device Owner (Bloqueo Total por ADB)

Si deseas un nivel de seguridad 100% impenetrable a nivel de sistema operativo donde ni siquiera en el menú de Ajustes aparezca la opción de desinstalar:

1. Conecta el teléfono de la niña a tu ordenador con depuración USB activa.
2. Ejecuta el siguiente comando en la terminal:
```bash
adb shell dpm set-device-owner com.parental.control/.core.security.ParentalDeviceAdminReceiver
```
*Con este comando, Android otorga permisos de gestión empresarial (MDM), haciendo físicamente imposible desinstalar la app sin usar ADB o restablecer de fábrica el equipo.*
