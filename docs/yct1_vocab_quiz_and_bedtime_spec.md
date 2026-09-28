# Aegis Parental Control ── Especificación Técnica: Reto de Vocabulario YCT 1 Total (83 Preguntas), Escala Sabia de 15 Minutos, Toque de Queda (21h - 09h) y Exención de Spotify

## 1. Contexto y Objetivos

Para fortalecer la pedagogía infantil y blindar los hábitos de sueño y descanso de la menor, Aegis actualiza dos subsistemas esenciales:
1. **Reto Lúdico de Vocabulario YCT 1 Integral (83 Preguntas - Currículo Completo)**:
   - Evalúa **la totalidad de las 83 palabras del vocabulario oficial de YCT Nivel 1** en cada sesión.
   - **Cero Omisiones**: Todas y cada una de las 83 palabras del banco curricular son evaluadas en cada intento, sin dejar palabras por fuera.
   - **Rigor Académico Real (Cero Pistas de Emojis en el Reto)**: Las alternativas `A`, `B`, `C`, `D` y las preguntas no muestran emojis asociados al significado para evitar atajos de emparejamiento visual y preparar a la menor con rigor para los exámenes oficiales YCT y HSK.
   - **Modo Aprendizaje con Emojis**: La sección de estudio ("Flashcards Oficiales YCT 1") mantiene y potencia al 100% los emojis mnemotécnicos (🍎, 🐱, 🐶, etc.) para facilitar la adquisición y memorización de vocabulario nuevo.
   - **Umbral Sabio de Aprobación (70 Aciertos = 84.3%)**: Se requiere dominar más de 5 de cada 6 palabras para obtener minutos recreativos. Con menos de 70 aciertos se otorgan 0 minutos con reintento libre e inmediato.
   - **Escala Sabia y Progresiva de 15 Minutos (6 Tramos)**:
     - 70 - 72 aciertos (84.3% - 86.7%): **5 minutos** (3 ⭐)
     - 73 - 75 aciertos (88.0% - 90.4%): **7 minutos** (3 ⭐)
     - 76 - 78 aciertos (91.6% - 94.0%): **9 minutos** (4 ⭐)
     - 79 - 80 aciertos (95.2% - 96.4%): **11 minutos** (4 ⭐)
     - 81 - 82 aciertos (97.6% - 98.8%): **13 minutos** (5 ⭐)
     - 83 / 83 aciertos (100% Perfecto): **15 minutos** (5 ⭐)
   - **Cooldown de 2 Horas**: Tras aprobar con >= 70 aciertos y reclamar la recompensa, el reto entra en enfriamiento por 120 minutos (2 horas) sin posibilidad de acumular más tiempo recreativo.
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

### 2.3 Escala Sabia de Calificación y Recompensas
- Total de preguntas: **83**.
- Umbral de aprobación educativo: **70 aciertos (84.3%)**.
- Tabla de Concesión de Tiempo Recreativo:
  | Aciertos Correctos | Porcentaje | Estrellas | Minutos Extras Concedidos | Cooldown Activado |
  | :---: | :---: | :---: | :---: | :---: |
  | **83 / 83** | 100% | ⭐⭐⭐⭐⭐ (5 ⭐) | **15 minutos** | 2 horas (120 min) |
  | **81 - 82** | 97.6% - 98.8% | ⭐⭐⭐⭐⭐ (5 ⭐) | **13 minutos** | 2 horas (120 min) |
  | **79 - 80** | 95.2% - 96.4% | ⭐⭐⭐⭐ (4 ⭐) | **11 minutos** | 2 horas (120 min) |
  | **76 - 78** | 91.6% - 94.0% | ⭐⭐⭐⭐ (4 ⭐) | **9 minutos** | 2 horas (120 min) |
  | **73 - 75** | 88.0% - 90.4% | ⭐⭐⭐ (3 ⭐) | **7 minutos** | 2 horas (120 min) |
  | **70 - 72** | 84.3% - 86.7% | ⭐⭐⭐ (3 ⭐) | **5 minutos** | 2 horas (120 min) |
  | **< 70** | < 84.3% | 0 ⭐ | **0 minutos** | **Ninguno** (Reintento libre) |

- **Fórmula de cálculo en `VocabularyQuizEngine`**:
  ```kotlin
  val earnedMinutes = when {
      correctCount >= 83 -> 15
      correctCount >= 81 -> 13
      correctCount >= 79 -> 11
      correctCount >= 76 -> 9
      correctCount >= 73 -> 7
      correctCount >= 70 -> 5
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

