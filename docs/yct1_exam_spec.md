# Aegis Parental Control ── Especificación Técnica: Examen Oficial YCT 1 y Flashcards de Chino Mandarín

## 1. Contexto y Objetivos

Para fomentar una relación sana, educativa y motivadora con la tecnología, Aegis incorpora un **mecanismo de recompensa académica en la pantalla de bloqueo**:
La niña puede liberar tiempo extra de pantalla (hasta 5 minutos) aprobando el **Examen Oficial Completo de Chino Mandarín YCT Nivel 1 (Youth Chinese Test)** o estudiando las **83 palabras de vocabulario oficial**.

### Principios Fundamentales:
1. **Rigor Pedagógico Real**: En lugar de preguntas triviales o superficiales, el reto replica el **Examen Oficial YCT 1 (35 preguntas)** de Hanban/CLEC, abarcando Comprensión Auditiva (*Listening*) y Comprensión Lectora (*Reading*).
2. **Límite Estricto y Cooldown Inquebrantable**:
   - Concesión máxima: **5 minutos extras**.
   - Frecuencia máxima: **1 vez cada 60 minutos** (1 hora).
   - **No acumulable**: No es posible encadenar exámenes para acumular 10, 15 o más minutos. Si ya hay una pausa activa o el cooldown está corriendo, el botón muestra una cuenta regresiva.
3. **Escala de Recompensa Proporcional**:
   - Para aprobar oficialmente YCT 1 se requiere al menos el 60% (21/35 preguntas).
   - Menos de 21 aciertos (< 60%): **0 minutos** (Desaprobado).
   - 21 a 23 aciertos (60% - 69%): **2 minutos**.
   - 24 a 27 aciertos (70% - 79%): **3 minutos**.
   - 28 a 31 aciertos (80% - 90%): **4 minutos**.
   - 32 a 35 aciertos (91% - 100%): **5 minutos**.
4. **Modo Estudio Libre (Flashcards)**: La niña puede acceder en cualquier momento a las flashcards de las 83 palabras con pronunciación por síntesis de voz (`TextToSpeech`) para prepararse.

---

## 2. Estructura Oficial del Examen YCT Nivel 1 (35 Preguntas)

El examen sigue exactamente la especificación oficial implementada en el repositorio `/home/herles/asf/devherles/mandarin`:

```
                           EXAMEN OFICIAL YCT 1 (35 Preguntas / 200 Puntos)
                                                │
         ┌──────────────────────────────────────┴──────────────────────────────────────┐
         ▼                                                                             ▼
   PARTE 1: 听力 LISTENING (20 preguntas / 100 pts)                       PARTE 2: 阅读 READING (15 preguntas / 100 pts)
   ├── Sección 1 (5 q): Audio + Imagen -> Verdadero / Falso (√ / ×)       ├── Sección 1 (5 q): Texto + Imagen -> Verdadero / Falso (√ / ×)
   ├── Sección 2 (5 q): Audio -> Selección de Imagen (A, B, C)             ├── Sección 2 (5 q): Frase -> Emparejamiento con Imagen (A, B, C)
   ├── Sección 3 (5 q): Mini Diálogo Audio -> Situación (A, B, C)          └── Sección 3 (5 q): Oración incompleta -> Gramática (A, B, C)
   └── Sección 4 (5 q): Pregunta Audio -> Respuesta correcta (A, B, C)
```

### Banco de Preguntas:
- **385 preguntas categorizadas** (55 por cada una de las 7 secciones), extraídas de `vocabulary.db` del repositorio `mandarin`.
- En cada sesión de examen se seleccionan aleatoriamente **5 preguntas por sección** (5 × 7 = 35 preguntas), garantizando variedad y más de 10 exámenes únicos sin repetición.

---

## 3. Síntesis de Voz y Multimedia

### 3.1 Audio en Chino Mandarín (`ChineseTtsHelper`)
- Utiliza la API nativa `android.speech.tts.TextToSpeech` de Android con configuración de idioma `Locale.SIMPLIFIED_CHINESE` (o `Locale.CHINESE`).
- Velocidad optimizada para aprendizaje infantil: `speechRate = 0.85f`.
- **Protocolo de Repetición 2x**: En las secciones de Listening, el botón reproduce el audio 2 veces con una pausa intermedia de 1.8 segundos, replicando la experiencia del examen oficial.

### 3.2 Ilustraciones Educativas
- Se empaquetan en `assets/yct1/` las ilustraciones vectoriales y fotográficas correspondientes a los sustantivos y situaciones del examen (`apple.jpg`, `banana.jpg`, `cat.jpg`, `dog.jpg`, `noodles.jpg`, `rice.jpg`, `water.jpg`, `milk.jpg`, `book.jpg`, `teacher.jpg`, `student.jpg`, etc.).

---

## 4. Política de Cooldown y Seguridad Anti-Abuso

1. **Persistencia del Timestamp**:
   - Se almacena `last_chinese_exam_reward_timestamp` en `SharedPreferences` con respaldo en Turso Cloud.
2. **Evaluación de Elegibilidad (`canAttemptChineseExam`)**:
   ```kotlin
   val elapsed = System.currentTimeMillis() - lastRewardTimestamp
   val isCooldownActive = elapsed < 60 * 60 * 1000L
   val isAlreadyUnlocked = repository.settings.value.isTemporarilyUnlocked
   val canAttempt = !isCooldownActive && !isAlreadyUnlocked
   ```
3. **Imposibilidad de Acumulación**:
   - Si la niña tiene una pausa activa concedida por el padre o por un examen previo, `canAttempt` devuelve `false`.
   - El botón muestra: *"⏳ Próximo reto disponible en MM:SS"*.

---

## 5. Auditoría y Registro en Turso Cloud y Consola Linux

1. **Evento de Auditoría en `tamper_logs` / `device_history`**:
   - `event_type = 'REWARD_EXAM_YCT1'`
   - `detail`: `"Examen YCT 1 Oficial aprobado: 33/35 (Listening: 19/20, Reading: 14/15) -> +5m concedidos"`
2. **Visualización en Consola Linux (`tools/aegis_cli.py`)**:
   - En `./aegis status`:
     `Modo : 🔓 DESBLOQUEO TEMPORAL (4m restantes) [Examen Oficial YCT 1: 33/35]`
   - En `./aegis monitor`:
     Línea destacada en verde/cian en el panel de historial:
     `🎓 [EXAMEN YCT 1] Niña aprobó Examen Oficial: 33/35 (190/200 pts) -> +5m concedidos`
