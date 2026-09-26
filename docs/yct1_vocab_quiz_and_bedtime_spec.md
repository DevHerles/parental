# Aegis Parental Control ── Especificación Técnica: Reto de Vocabulario YCT 1 (45 Preguntas) y Toque de Queda Nocturno (20h - 08h)

## 1. Contexto y Objetivos

Para fortalecer la pedagogía infantil y blindar los hábitos de sueño y descanso de la menor, Aegis actualiza dos subsistemas esenciales:
1. **Reto Lúdico de Vocabulario YCT 1 de Alta Complejidad**:
   - Pasa de 15 a **45 preguntas aleatorias y variadas**.
   - **Garantía Cíclica de Cero Repetición**: Ninguna palabra de vocabulario puede volver a presentarse como objetivo en exámenes posteriores hasta que **TODAS las 83 palabras del vocabulario oficial de YCT 1 hayan sido evaluadas**.
   - **Recompensa Proporcional**: Hasta un máximo de **15 minutos** de recreación según los aciertos logrados.
   - **Cooldown de 2 Horas**: Tras aprobar y reclamar la recompensa, el reto entra en enfriamiento por 120 minutos (2 horas) sin posibilidad de acumular más tiempo.
   - **Reintentos Libres si Reprueba**: Si no alcanza el umbral de aprobación (60%, 27 aciertos), no recibe minutos y no se activa cooldown para que pueda seguir practicando sin frustración.
2. **Bloqueo Nocturno Total por Defecto (20:00 a 08:00 del día siguiente)**:
   - Bloqueo incondicional activo de lunes a domingo.
   - Ninguna aplicación no esencial puede ser utilizada.
   - El botón de retos de chino y flashcards se pausa durante la noche (*"🌙 Retos disponibles a partir de las 08:00 AM"*) para evitar trasnochar.
   - Solo se permite el desbloqueo mediante PIN del padre bajo supervisión física.

---

## 2. Reto de Vocabulario YCT 1 (45 Preguntas)

### 2.1 Banco Oficial y Mazo Cíclico Persistente
- **Fuente**: Las 83 palabras oficiales de YCT Nivel 1 (`app/src/main/assets/yct1_vocabulary.json`).
- **Problema de Repetición**: Con 45 preguntas por reto, un solo quiz consume más del 54% del banco (45/83).
- **Algoritmo de Mazo Cíclico Persistente (*Persistent Bag / Deal Without Replacement*)**:
  1. El sistema mantiene en `SharedPreferences` un conjunto de palabras vistas en el ciclo actual: `KEY_SEEN_VOCAB_WORDS`.
  2. Al iniciar un nuevo reto de 45 preguntas:
     - Se calculan las palabras no vistas: `unseenWords = allWords.filter { it.chinese !in seenWords }`.
     - **Caso A (`unseenWords.size >= 45`)**: Se toman 45 palabras al azar de `unseenWords`. Las 45 se añaden a `seenWords` persistente.
     - **Caso B (`unseenWords.size < 45`)**: Se toman todas las palabras restantes de `unseenWords` (ej. 38 palabras). En ese momento, **el 100% de las 83 palabras ha sido mostrado**. Se resetea el mazo y el saldo faltante (`45 - unseenWords.size`, ej. 7 palabras) se toma del mazo recién rebarajado. El nuevo conjunto de `seenWords` pasa a contener únicamente esas 7 palabras del nuevo ciclo.
  3. **Garantía Matemática**: Ninguna palabra vuelve a aparecer hasta que las 83 palabras del currículo han sido mostradas. Dentro de cada sesión de 45 preguntas, todas son 100% únicas.

### 2.2 Modos de Juego Gamificados (45 Preguntas)
La distribución pedagógica por sesión es:
- **🔤 Carácter a Español (12 preguntas)**: Hanzi + Pinyin -> Significado en español entre 4 opciones con distractores de nivel YCT 1.
- **🇨🇳 Español a Chino (12 preguntas)**: Significado en español -> Selección de Hanzi + Pinyin entre 4 opciones.
- **🎧 Escucha y Adivina (11 preguntas)**: Hanzi oculto para agudizar el oído, reproducción automática TTS (2x) en chino mandarín simplificado y botón táctil opcional para revelar pista de Pinyin.
- **⚡ Verdadero o Falso (10 preguntas)**: Desafío de agilidad mental con botones grandes táctiles (*"✅ ¡Es Correcto!"* y *"❌ ¡Es Incorrecto!"*).

### 2.3 Escala de Calificación y Recompensas
- Total de preguntas: **45**.
- Umbral de aprobación educativo (60%): **27 aciertos**.
- Tabla de Concesión de Tiempo Recreativo:
  | Aciertos Correctos | Porcentaje | Estrellas | Minutos Extras | Cooldown Activado |
  | :---: | :---: | :---: | :---: | :---: |
  | **45 / 45** | 100% | ⭐⭐⭐⭐⭐ (5 ⭐) | **15 minutos** | 2 horas (120 min) |
  | **42 - 44** | 93% - 98% | ⭐⭐⭐⭐⭐ (5 ⭐) | **14 minutos** | 2 horas (120 min) |
  | **39 - 41** | 87% - 91% | ⭐⭐⭐⭐ (4 ⭐) | **13 minutos** | 2 horas (120 min) |
  | **36 - 38** | 80% - 84% | ⭐⭐⭐⭐ (4 ⭐) | **12 minutos** | 2 horas (120 min) |
  | **33 - 35** | 73% - 78% | ⭐⭐⭐ (3 ⭐) | **11 minutos** | 2 horas (120 min) |
  | **30 - 32** | 67% - 71% | ⭐⭐⭐ (3 ⭐) | **10 minutos** | 2 horas (120 min) |
  | **27 - 29** | 60% - 64% | ⭐⭐ (2 ⭐) | **9 minutos** | 2 horas (120 min) |
  | **< 27** | < 60% | 0 ⭐ | **0 minutos** | **Ninguno** (Reintento libre) |

---

## 3. Toque de Queda Nocturno (20:00 a 08:00)

### 3.1 Política Incondicional
- **Horario**: Todos los días de la semana (Lunes a Domingo), de **20:00 a 08:00 del día siguiente**.
- **Comportamiento en `ParentalRepository`**:
  - `isBedtimeCurfewActive(cal: Calendar)` evalúa si la hora actual cae en el intervalo nocturno.
  - `isPackageBlocked(packageName)` retorna `true` para **cualquier paquete no esencial**, bloqueando navegadores, redes sociales, juegos, utilidades y aplicaciones del sistema.
  - Sobrescribe cualquier tiempo de recompensa remanente ganado durante la tarde.
- **Comportamiento en `ParentalAccessibilityService`**:
  - El watchdog periódico (250ms) y los interceptores de eventos expulsan cualquier app abierta al launcher y levantan inmediatamente `LockScreenActivity`.
  - Clics en la pantalla de inicio sobre iconos de aplicaciones son pre-interceptados.

### 3.2 Interfaz de Usuario Nocturna (`LockOverlayContent`)
- **Modo Nocturno 🌙**:
  - Encabezado con luna creciente y estética nocturna.
  - Título: *"¡Hora de Dormir y Descansar! 🌙"*.
  - Subtítulo: *"Horario nocturno de descanso (20:00 a 08:00)"*.
  - Tarjeta motivacional: *"Es momento de apagar la tablet y descansar. Mañana será un gran día para seguir aprendiendo. 😴✨"*.
  - Botón de Reto Educativo y Flashcards: Deshabilitados con la leyenda *"🌙 Retos de minutos disponibles a partir de las 08:00 AM"*.
  - Botón de Acceso Padres: Permite ingresar el PIN parental si el padre requiere usar el dispositivo.

### 3.3 Consola Linux (`tools/aegis_cli.py`)
- El comando `./aegis status` refleja en tiempo real:
  ```text
  🌙 Horario Nocturno: [🌙 EN VIGOR (20:00 - 08:00)] / [☀️ EN REPOSO]
  ```
