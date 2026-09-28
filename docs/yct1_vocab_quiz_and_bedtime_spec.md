# Aegis Parental Control ── Especificación Técnica: Reto de Vocabulario YCT 1 Total (83 Preguntas), Escala Sabia de 15 Minutos, Toque de Queda (21h - 09h) y Exención de Spotify

## 1. Contexto y Objetivos

Para fortalecer la pedagogía infantil y blindar los hábitos de sueño y descanso de la menor, Aegis actualiza dos subsistemas esenciales:
1. **Reto Lúdico de Vocabulario YCT 1 Integral (83 Preguntas - Currículo Completo)**:
   - Evalúa **la totalidad de las 83 palabras del vocabulario oficial de YCT Nivel 1** en cada sesión.
   - **Cero Omisiones**: Todas y cada una de las 83 palabras del banco curricular son evaluadas en cada intento, sin dejar palabras por fuera.
   - **Rigor Académico Real (Cero Pistas de Emojis en el Reto)**: Las alternativas `A`, `B`, `C`, `D` y las preguntas no muestran emojis asociados al significado para evitar atajos de emparejamiento visual y preparar a la menor con rigor para los exámenes oficiales YCT y HSK.
   - **Modo Aprendizaje con Emojis**: La sección de estudio ("Flashcards Oficiales YCT 1") mantiene y potencia al 100% los emojis mnemotécnicos (🍎, 🐱, 🐶, etc.) para facilitar la adquisición y memorización de vocabulario nuevo.
   - **Umbral Motivacional de Aprobación (50 Aciertos = 60.2%)**: Alínea el esfuerzo al estándar internacional oficial de aprobación del YCT/HSK (60%). Con 50 aciertos se garantiza un piso digno de **10 minutos**, evitando frustración y desmotivación infantil.
   - **Escala Motivacional y Progresiva de 10 a 30 Minutos (5 Tramos)**:
     - 50 - 59 aciertos (60.2% - 71.1%): **10 minutos** (3 ⭐ - Meta Mínima Aprobada)
     - 60 - 69 aciertos (72.3% - 83.1%): **15 minutos** (4 ⭐ - Buen Desempeño)
     - 70 - 77 aciertos (84.3% - 92.8%): **20 minutos** (4 ⭐ - Notable)
     - 78 - 82 aciertos (94.0% - 98.8%): **25 minutos** (5 ⭐ - Sobresaliente)
     - 83 / 83 aciertos (100% Perfecto): **30 minutos** (5 ⭐ - Gran Premio de Perfección Total)
     - < 50 aciertos (< 60.2%): **0 minutos** (Sin penalización, reintento libre)
   - **Cooldown Proporcional Inteligente (Salud Digital y Autocontrol)**:
     - Tras ganar 25 a 30 min: Cooldown de **3 horas (180 min)**.
     - Tras ganar 15 a 20 min: Cooldown de **2 horas (120 min)**.
     - Tras ganar 10 min: Cooldown de **1.5 horas (90 min)**.
   - **Ventana Inicial en Dos Columnas (LockOverlayContent Landscape)**:
     - Columna izquierda: Estado, sugerencia de pausas constructivas y acciones (Comenzar Reto YCT 1, Flashcards, Volver a Inicio, PIN Padres).
     - Columna derecha: Apartado clarísimo de Calificación Psicológica con tarjetas por nivel de logro, estrellas, minutos equivalentes y mensaje inspirador de autoeficacia y autocontrol.
   - **Ergonomía Horizontal en Dos Columnas (Zero Scroll)**: Diseñado para la orientación apaisada de la tablet Lenovo (1920x1200), con columna izquierda para la pregunta/feedback y columna derecha para las 4 alternativas, visible al 100% sin scroll vertical.
2. **Bloqueo Nocturno Total por Defecto (21:00 a 09:00 del día siguiente)**:
   - Bloqueo incondicional activo de lunes a domingo entre las **21:00 horas (9:00 PM)** y las **09:00 horas (9:00 AM)** del día siguiente.
   - Ninguna aplicación no esencial puede ser utilizada durante ese intervalo.
   - El botón de retos de chino y flashcards se pausa durante la noche (*"🌙 Retos de minutos en pausa hasta las 09:00 AM"*) para asegurar el descanso y evitar trasnochar.
   - Desbloqueo general nocturno solo mediante PIN del padre bajo supervisión física.
3. **Exención Total de Spotify (`com.spotify.music`)**:
   - Spotify queda **completamente exento de cualquier bloqueo o restricción parental**, tanto en horario diurno como durante el toque de queda nocturno (21:00 a 09:00).
   - La menor puede escuchar libremente música, cuentos o audiolibros en cualquier momento sin ser interrumpida por el overlay ni por el centinela de accesibilidad.

---

## 2. Reto de Vocabulario YCT 1 Total (83 Preguntas)

### 2.1 Banco Oficial Completo (100% del Currículo)
- **Fuente**: Las 83 palabras oficiales de YCT Nivel 1 (`app/src/main/assets/yct1_vocabulary.json`).
- **Cobertura Absoluta**: Cada reto toma las 83 palabras en orden aleatorio rebarajado. Cada palabra del currículo se presenta exactamente una vez como palabra objetivo.

### 2.2 Modos de Juego Gamificados (83 Preguntas)
La distribución pedagógica por sesión es:
- **🔤 Carácter a Español (22 preguntas)**: Hanzi + Pinyin -> Significado en español entre 4 opciones con distractores del nivel YCT 1.
- **🇨🇳 Español a Chino (22 preguntas)**: Significado en español -> Selección de Hanzi + Pinyin entre 4 opciones.
- **🎧 Escucha y Adivina (21 preguntas)**: Hanzi oculto para agudizar el oído, reproducción automática TTS (2x) en chino mandarín simplificado y botón táctil opcional para revelar pista de Pinyin.
- **⚡ Verdadero o Falso (18 preguntas)**: Desafío de agilidad mental con botones grandes táctiles (*"Verdadero"* y *"Falso"*).
- **Total**: $22 + 22 + 21 + 18 = \mathbf{83}$ preguntas.

### 2.3 Escala Motivacional de Calificación y Recompensas
- Total de preguntas: **83**.
- Umbral de aprobación educativo: **50 aciertos (60.2%)** (Estándar YCT internacional).
- Tabla de Concesión de Tiempo Recreativo y Cooldown:
  | Aciertos Correctos | Porcentaje | Estrellas | Minutos Extras Concedidos | Calificación Psicológica | Cooldown Activado |
  | :---: | :---: | :---: | :---: | :---: | :---: |
  | **83 / 83** | 100% | ⭐⭐⭐⭐⭐ (5 ⭐) | **30 minutos** | 🏆 Perfección Total | 3 horas (180 min) |
  | **78 - 82** | 94.0% - 98.8% | ⭐⭐⭐⭐⭐ (5 ⭐) | **25 minutos** | 🌟 Sobresaliente | 3 horas (180 min) |
  | **70 - 77** | 84.3% - 92.8% | ⭐⭐⭐⭐ (4 ⭐) | **20 minutos** | 🎖️ Notable | 2 horas (120 min) |
  | **60 - 69** | 72.3% - 83.1% | ⭐⭐⭐⭐ (4 ⭐) | **15 minutos** | 👍 Buen Desempeño | 2 horas (120 min) |
  | **50 - 59** | 60.2% - 71.1% | ⭐⭐⭐ (3 ⭐) | **10 minutos** | 🎯 Piso Aprobado (Meta Mínima) | 1.5 horas (90 min) |
  | **< 50** | < 60.2% | 0 ⭐ | **0 minutos** | 💡 Repaso Flashcards | **Ninguno** (Reintento libre) |

- **Fórmula de cálculo en `VocabularyQuizEngine`**:
  ```kotlin
  val earnedMinutes = when {
      correctCount >= 83 -> 30
      correctCount >= 78 -> 25
      correctCount >= 70 -> 20
      correctCount >= 60 -> 15
      correctCount >= 50 -> 10
      else -> 0
  }
  ```

### 2.4 Estándares de Evaluación Internacional (YCT / HSK CBT)
- **Numeración de Ítems en Tiempo Real**: En cumplimiento con los estándares internacionales de Computer-Based Testing (CBT), la prueba enumera reactivos en base 1 (`Pregunta 1 de 83`). Jamás inicia en 0. Al avanzar con *"Siguiente"*, actualiza inmediatamente a `Pregunta 2 de 83`, `Pregunta 3 de 83`, etc., independientemente del estado de respuesta previa.
- **Separación de Progreso y Calificación**: La posición del ítem (`Pregunta X de 83`) se desacopla del acumulador de puntos (`⭐ X aciertos`), presentándose en chips independientes para evitar ambigüedades.
- **Calidad Psicométrica de Distractores**: Los distractores priorizan elementos de la misma categoría semántica/gramatical (familia, números, comida, etc.) para medir discriminación conceptual genuina, completando con el banco general oficial de 83 palabras.
- **Nomenclatura Académica Oficial YCT 1**:
  - `🎧 YCT 1 · 听力 (Comprensión Auditiva)`
  - `🔤 YCT 1 · 阅读 (Carácter a Significado)`
  - `🇨🇳 YCT 1 · 表达 (Significado a Carácter)`
  - `⚡ YCT 1 · 判断 (Verdadero o Falso)`
- **Cierre Formal**: En el ítem 83, el botón de avance transmuta a `🎓 Finalizar Reto y Calificar`.


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

