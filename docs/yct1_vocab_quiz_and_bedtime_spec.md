# Aegis Parental Control ── Especificación Técnica: Reto de Vocabulario YCT 1 (45 Preguntas Exigente), Toque de Queda Nocturno (21h - 09h) y Exención de Spotify

## 1. Contexto y Objetivos

Para fortalecer la pedagogía infantil y blindar los hábitos de sueño y descanso de la menor, Aegis actualiza dos subsistemas esenciales:
1. **Reto Lúdico de Vocabulario YCT 1 de Alta Complejidad y Máxima Exigencia**:
   - Pasa de 15 a **45 preguntas aleatorias y variadas**.
   - **Garantía Cíclica de Cero Repetición**: Ninguna palabra de vocabulario puede volver a presentarse como objetivo en exámenes posteriores hasta que **TODAS las 83 palabras del vocabulario oficial de YCT 1 hayan sido evaluadas**.
   - **Alto Umbral de Aprobación**: Dado que la menor domina el idioma chino mandarín, el umbral mínimo para obtener cualquier premio se eleva a **40 aciertos correctos de 45** (88.9%). Menos de 40 aciertos otorga 0 minutos.
   - **Escala de Recompensa (5 a 15 Minutos)**:
     - 40 aciertos: **5 minutos** (3 ⭐)
     - 41 aciertos: **7 minutos** (3 ⭐)
     - 42 aciertos: **9 minutos** (4 ⭐)
     - 43 aciertos: **11 minutos** (4 ⭐)
     - 44 aciertos: **13 minutos** (5 ⭐)
     - 45 aciertos (perfecto 45/45): **15 minutos** (5 ⭐)
   - **Cooldown de 2 Horas**: Tras aprobar con >= 40 aciertos y reclamar la recompensa, el reto entra en enfriamiento por 120 minutos (2 horas) sin posibilidad de acumular más tiempo recreativo.
   - **Reintentos Libres si Reprueba**: Si no alcanza el umbral de aprobación (< 40 aciertos), recibe 0 minutos y no se activa cooldown para que pueda seguir practicando sin frustración.
2. **Bloqueo Nocturno Total por Defecto (21:00 a 09:00 del día siguiente)**:
   - Bloqueo incondicional activo de lunes a domingo entre las **21:00 horas (9:00 PM)** y las **09:00 horas (9:00 AM)** del día siguiente.
   - Ninguna aplicación no esencial puede ser utilizada durante ese intervalo.
   - El botón de retos de chino y flashcards se pausa durante la noche (*"🌙 Retos de minutos en pausa hasta las 09:00 AM"*) para asegurar el descanso y evitar trasnochar.
   - Desbloqueo general nocturno solo mediante PIN del padre bajo supervisión física.
3. **Exención Total de Spotify (`com.spotify.music`)**:
   - Spotify queda **completamente exento de cualquier bloqueo o restricción parental**, tanto en horario diurno como durante el toque de queda nocturno (21:00 a 09:00).
   - La menor puede escuchar libremente música, cuentos o audiolibros en cualquier momento sin ser interrumpida por el overlay ni por el centinela de accesibilidad.

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

### 2.3 Escala Exigente de Calificación y Recompensas
- Total de preguntas: **45**.
- Umbral de aprobación educativo: **40 aciertos (88.9%)**.
- Tabla de Concesión de Tiempo Recreativo:
  | Aciertos Correctos | Porcentaje | Estrellas | Minutos Extras Concedidos | Cooldown Activado |
  | :---: | :---: | :---: | :---: | :---: |
  | **45 / 45** | 100% | ⭐⭐⭐⭐⭐ (5 ⭐) | **15 minutos** | 2 horas (120 min) |
  | **44 / 45** | 97.8% | ⭐⭐⭐⭐⭐ (5 ⭐) | **13 minutos** | 2 horas (120 min) |
  | **43 / 45** | 95.6% | ⭐⭐⭐⭐ (4 ⭐) | **11 minutos** | 2 horas (120 min) |
  | **42 / 45** | 93.3% | ⭐⭐⭐⭐ (4 ⭐) | **9 minutos** | 2 horas (120 min) |
  | **41 / 45** | 91.1% | ⭐⭐⭐ (3 ⭐) | **7 minutos** | 2 horas (120 min) |
  | **40 / 45** | 88.9% | ⭐⭐⭐ (3 ⭐) | **5 minutos** | 2 horas (120 min) |
  | **< 40** | < 88.9% | 0 ⭐ | **0 minutos** | **Ninguno** (Reintento libre) |

- **Fórmula de cálculo**:
  ```kotlin
  val earnedMinutes = if (correctCount >= 40) {
      5 + ((correctCount - 40) * 2).coerceAtMost(10)
  } else {
      0
  }
  ```

---

## 3. Toque de Queda Nocturno (21:00 a 09:00)

### 3.1 Política Incondicional
- **Horario**: Todos los días de la semana (Lunes a Domingo), de **21:00 a 09:00 del día siguiente**.
- **Comportamiento en `ParentalRepository`**:
  - `isBedtimeCurfewActive(cal: Calendar)` evalúa si la hora actual cae en el intervalo nocturno (>= 21 o < 9).
  - `isPackageBlocked(packageName)` retorna `true` para cualquier aplicación no esencial, bloqueando redes sociales, juegos, navegadores y apps del sistema.
  - **Excepción Absoluta**: Spotify (`com.spotify.music`) nunca es bloqueado, ni de día ni durante el toque de queda.
  - Sobrescribe cualquier tiempo de recompensa remanente ganado durante el día.
- **Comportamiento en `ParentalAccessibilityService`**:
  - Interceptores y watchdog de 250ms expulsan cualquier app no esencial al launcher y levantan inmediatamente `LockScreenActivity`.
  - Spotify es categorizado como esencial/exento, impidiendo su cierre o interrupción.

### 3.2 Interfaz de Usuario Nocturna (`LockOverlayContent`)
- **Modo Nocturno 🌙**:
  - Encabezado con luna creciente y estética nocturna.
  - Título: *"¡Hora de Dormir y Descansar! 🌙"*.
  - Subtítulo: *"Horario nocturno de descanso (21:00 a 09:00)"*.
  - Tarjeta motivacional: *"Es momento de desconectar la pantalla y dormir bien para recargar tus energías. Mañana será un gran día. ✨"*.
  - Botón de Reto Educativo y Flashcards: Deshabilitados con la leyenda *"🌙 Retos de minutos en pausa hasta las 09:00 AM"*.
  - Botón de Acceso Padres: Permite ingresar el PIN parental si el padre requiere usar el dispositivo.

### 3.3 Consola Linux (`tools/aegis_cli.py`)
- El comando `./aegis status` refleja en tiempo real:
  ```text
  🌙 Horario Nocturno: [🌙 EN VIGOR (21:00 - 09:00)] / [☀️ EN REPOSO]
  ```

