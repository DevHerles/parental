package com.parental.control.ui.child.mandarin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.core.model.*
import kotlinx.coroutines.delay

@Composable
fun VocabularyQuizScreen(
    onClose: () -> Unit,
    onClaimReward: (VocabQuizResult) -> Unit,
    onNavigateToFlashcards: () -> Unit
) {
    val context = LocalContext.current
    val ttsHelper = remember { ChineseTtsHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    // 1. Cargar las 83 palabras oficiales de YCT 1
    val allWords = remember {
        try {
            val json = context.assets.open("yct1_vocabulary.json").bufferedReader().use { it.readText() }
            YctExamEngine.parseVocabularyFromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val repository = remember { com.parental.control.core.data.ParentalRepository.getInstance(context) }

    // 2. Generar 83 preguntas ÚNICAS sin repetición integrando el mazo persistente de 83 palabras
    val questions = remember(allWords) {
        if (allWords.isNotEmpty()) {
            val seen = repository.getSeenVocabWordKeys()
            val genResult = VocabularyQuizEngine.generateQuizWithPersistentDeck(allWords, seen, 83)
            repository.saveSeenVocabWordKeys(genResult.updatedSeenKeys)
            genResult.questions
        } else {
            emptyList()
        }
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, Int>() }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var showPinyinHint by remember { mutableStateOf(false) }

    var isQuizFinished by remember { mutableStateOf(false) }
    var quizResult by remember { mutableStateOf<VocabQuizResult?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showConfirmExit by remember { mutableStateOf(false) }

    // Cronómetro
    LaunchedEffect(isQuizFinished) {
        while (!isQuizFinished) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // Auto-reproducir audio si es pregunta de Listening
    LaunchedEffect(currentIndex, questions) {
        selectedAnswer = userAnswers[currentIndex]
        hasAnswered = selectedAnswer != null
        showPinyinHint = false

        if (currentIndex in questions.indices) {
            val q = questions[currentIndex]
            val audio = q.audioText
            if (q.mode == VocabQuizMode.LISTENING && audio != null) {
                delay(300L)
                ttsHelper.speakTwice(audio)
            }
        }
    }

    if (questions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE11D48))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cargando reto de vocabulario...", color = Color.White)
            }
        }
        return
    }

    if (isQuizFinished && quizResult != null) {
        VocabQuizCelebrationDialog(
            result = quizResult!!,
            onClose = onClose,
            onClaimReward = { onClaimReward(quizResult!!) },
            onNavigateToFlashcards = onNavigateToFlashcards
        )
        return
    }

    val currentQ = questions[currentIndex]
    val totalQ = questions.size
    val correctStars = userAnswers.count { (qIdx, ansIdx) ->
        qIdx in questions.indices && questions[qIdx].correctChoiceIndex == ansIdx
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. CABECERA: MODO, PROGRESO, ESTRELLAS Y BOTÓN SALIR
            // ─────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge del Modo de Juego con nomenclatura estándar YCT
                val (badgeText, badgeBg) = when (currentQ.mode) {
                    VocabQuizMode.HANZI_TO_ES -> "🔤 YCT 1 · 阅读 (Carácter a Español)" to Color(0xFF0284C7)
                    VocabQuizMode.ES_TO_HANZI -> "🇨🇳 YCT 1 · 表达 (Español a Chino)" to Color(0xFF7C3AED)
                    VocabQuizMode.LISTENING -> "🎧 YCT 1 · 听力 (Comprensión Auditiva)" to Color(0xFFD97706)
                    VocabQuizMode.TRUE_FALSE -> "⚡ YCT 1 · 判断 (Verdadero o Falso)" to Color(0xFF059669)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Indicadores CBT: Número de Pregunta Activa (Inicia siempre en 1 y se actualiza de 1 a 83)
                // y Aciertos acumulados en vivo (separados e independientes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Contador de Ítem de Examen: Pregunta X de 83 (siempre reactivo a currentIndex)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Pregunta ${currentIndex + 1}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = " de $totalQ",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // 2. Aciertos acumulados en vivo (Separado e independiente de la posición)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = "⭐", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$correctStars",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (correctStars == 1) " acierto" else " aciertos",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Botón salir (X)
                IconButton(onClick = { showConfirmExit = true }) {
                    Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color(0xFF94A3B8))
                }
            }

            // Barra de progreso interactiva
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / totalQ.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFE11D48),
                trackColor = Color(0xFF334155),
            )

            // Indicador de metas de recompensa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Meta mínima: 50 aciertos (10 min)",
                    fontSize = 11.sp,
                    color = if (correctStars >= 50) Color(0xFF34D399) else Color(0xFF94A3B8),
                    fontWeight = if (correctStars >= 50) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "🏆 Perfección 83 aciertos = 30 min",
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.Bold
                )
            }

            // ─────────────────────────────────────────────────────────────
            // 2. CONTENIDO EN DOS COLUMNAS (LANDSCAPE TABLET - CERO SCROLL)
            // ─────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // COLUMNA IZQUIERDA (44%): Tarjeta de Pregunta y Banner Pedagógico
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Prompt / Enunciado
                    Text(
                        text = currentQ.promptText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // TARJETA DE EXHIBICIÓN PRINCIPAL DE LA PALABRA
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            when (currentQ.mode) {
                                VocabQuizMode.HANZI_TO_ES -> {
                                    Text(
                                        text = currentQ.displayHanzi ?: "",
                                        fontSize = 46.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = currentQ.displayPinyin ?: "",
                                        fontSize = 18.sp,
                                        color = Color(0xFFFBBF24),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SmallAudioButton(ttsHelper = ttsHelper, text = currentQ.audioText ?: "")
                                }

                                VocabQuizMode.ES_TO_HANZI -> {
                                    Text(text = "🇨🇳", fontSize = 36.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentQ.targetWord.spanish,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "YCT Nivel 1",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                VocabQuizMode.LISTENING -> {
                                    Text(text = "🎧", fontSize = 38.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            ttsHelper.speakTwice(currentQ.audioText ?: "")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.height(46.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("🔊 Escuchar de nuevo (2x)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    TextButton(onClick = { showPinyinHint = !showPinyinHint }) {
                                        Text(
                                            text = if (showPinyinHint) "Pista: ${currentQ.targetWord.pinyin}" else "💡 Ver pista de Pinyin",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                VocabQuizMode.TRUE_FALSE -> {
                                    Text(
                                        text = currentQ.displayHanzi ?: "",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = currentQ.displayPinyin ?: "",
                                        fontSize = 16.sp,
                                        color = Color(0xFFFBBF24)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SmallAudioButton(ttsHelper = ttsHelper, text = currentQ.audioText ?: "")
                                }
                            }
                        }
                    }

                    // BANNER DE FEEDBACK INMEDIATO Y EXPLICACIÓN (Incluye emoji mnemotécnico pedagógico)
                    AnimatedVisibility(visible = hasAnswered) {
                        val isCorrect = selectedAnswer == currentQ.correctChoiceIndex
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCorrect) Color(0xFF064E3B) else Color(0xFF450A0A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCorrect) "🌟" else "💡",
                                    fontSize = 22.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isCorrect) "¡Excelente! Muy bien hecho." else "¡Casi! Sigue aprendiendo.",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCorrect) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = currentQ.explanation,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // COLUMNA DERECHA (56%): Alternativas Ergonómicas (A, B, C, D o Verdadero / Falso)
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentQ.mode == VocabQuizMode.TRUE_FALSE) {
                        // MODO VERDADERO O FALSO: 2 BOTONES GRANDES VERTICALES
                        currentQ.choices.forEachIndexed { optIndex, choice ->
                            val isCorrectChoice = optIndex == currentQ.correctChoiceIndex
                            val isUserSelected = selectedAnswer == optIndex

                            val buttonColor = when {
                                !hasAnswered -> Color(0xFF1E293B)
                                isCorrectChoice -> Color(0xFF059669)
                                isUserSelected -> Color(0xFFDC2626)
                                else -> Color(0xFF1E293B).copy(alpha = 0.5f)
                            }

                            Button(
                                onClick = {
                                    if (!hasAnswered) {
                                        selectedAnswer = optIndex
                                        hasAnswered = true
                                        userAnswers[currentIndex] = optIndex
                                        val qAudio = currentQ.audioText
                                        if (qAudio != null) {
                                            ttsHelper.speak(qAudio)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (choice.label == "V") "✅ Verdadero" else "❌ Falso",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    } else {
                        // MODO SELECCIÓN MÚLTIPLE (4 OPCIONES ERGONÓMICAS SIN EMOJIS DELATORES)
                        currentQ.choices.forEachIndexed { optIndex, choice ->
                            val isCorrectChoice = optIndex == currentQ.correctChoiceIndex
                            val isUserSelected = selectedAnswer == optIndex

                            val (cardBg, borderStroke) = when {
                                !hasAnswered -> Color(0xFF1E293B) to null
                                isCorrectChoice -> Color(0xFF065F46) to Color(0xFF34D399)
                                isUserSelected -> Color(0xFF7F1D1D) to Color(0xFFF87171)
                                else -> Color(0xFF1E293B).copy(alpha = 0.4f) to null
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (borderStroke != null) Modifier.border(2.dp, borderStroke, RoundedCornerShape(16.dp))
                                        else Modifier
                                    )
                                    .clickable(enabled = !hasAnswered) {
                                        selectedAnswer = optIndex
                                        hasAnswered = true
                                        userAnswers[currentIndex] = optIndex
                                        val qAudio = currentQ.audioText
                                        if (choice.secondaryText != null) {
                                            ttsHelper.speak(choice.primaryText)
                                        } else if (qAudio != null) {
                                            ttsHelper.speak(qAudio)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Letra A, B, C, D
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            !hasAnswered -> Color(0xFF334155)
                                            isCorrectChoice -> Color(0xFF10B981)
                                            isUserSelected -> Color(0xFFEF4444)
                                            else -> Color(0xFF334155)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = choice.label,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Texto principal y secundario sin pistas de emojis
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = choice.primaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = if (choice.secondaryText != null) 22.sp else 16.sp,
                                            color = Color.White
                                        )

                                        val cSec = choice.secondaryText
                                        if (cSec != null) {
                                            Text(
                                                text = cSec,
                                                fontSize = 14.sp,
                                                color = Color(0xFFFBBF24),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Indicador de acierto / error
                                    if (hasAnswered) {
                                        if (isCorrectChoice) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Correcto",
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else if (isUserSelected) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "Incorrecto",
                                                tint = Color(0xFFF87171),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. BARRA INFERIOR DE NAVEGACIÓN
            // ─────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Anterior
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            ttsHelper.stop()
                            currentIndex--
                        }
                    },
                    enabled = currentIndex > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Anterior")
                }

                // Botón Siguiente / Finalizar
                if (currentIndex < totalQ - 1) {
                    Button(
                        onClick = {
                            ttsHelper.stop()
                            currentIndex++
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("Siguiente")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = {
                            ttsHelper.stop()
                            quizResult = VocabularyQuizEngine.evaluateQuiz(questions, userAnswers, elapsedSeconds)
                            isQuizFinished = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🎓 Finalizar Reto y Calificar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para salir
    if (showConfirmExit) {
        AlertDialog(
            onDismissRequest = { showConfirmExit = false },
            title = { Text("¿Abandonar el quiz?", fontWeight = FontWeight.Bold) },
            text = { Text("Si sales ahora no se guardarán tus respuestas ni podrás reclamar minutos.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmExit = false
                        ttsHelper.stop()
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmExit = false }) {
                    Text("Continuar reto")
                }
            }
        )
    }
}

@Composable
private fun SmallAudioButton(ttsHelper: ChineseTtsHelper, text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF334155),
        modifier = Modifier.clickable { ttsHelper.speak(text) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Escuchar pronunciación", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun VocabQuizCelebrationDialog(
    result: VocabQuizResult,
    onClose: () -> Unit,
    onClaimReward: () -> Unit,
    onNavigateToFlashcards: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // COLUMNA IZQUIERDA: Trofeo, Título y Estrellas
                Column(
                    modifier = Modifier.weight(0.42f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val iconBg = if (result.passed) Color(0xFF059669) else Color(0xFFDC2626)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(iconBg.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (result.passed) "🏆" else "💪",
                            fontSize = 40.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (result.passed) "¡RETO SUPERADO! 🎉" else "¡BUEN INTENTO! ✨",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Vocabulario Oficial YCT 1 (83 preguntas)",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val starsText = "⭐ ".repeat(result.stars.coerceAtLeast(0)).trim()
                    if (starsText.isNotEmpty()) {
                        Text(text = starsText, fontSize = 26.sp)
                    }
                }

                // COLUMNA DERECHA: Desglose, Recompensa y Acciones
                Column(
                    modifier = Modifier.weight(0.58f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aciertos correctos", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text("${result.correctCount} de ${result.totalQuestions}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Porcentaje de acierto", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text("${result.scorePercentage.toInt()}%", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tiempo empleado", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                val mins = result.durationSeconds / 60
                                val secs = result.durationSeconds % 60
                                Text(String.format("%02d:%02d", mins, secs), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (result.passed && result.earnedMinutes > 0) {
                        Text(
                            text = if (result.earnedMinutes >= 30) "🏆 ¡PERFECCIÓN TOTAL! ¡Ganaste 30 minutos de recreo!" else "¡Ganaste ${result.earnedMinutes} minutos extras de recreo!",
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        if (result.earnedMinutes < 30) {
                            Text(
                                text = "¡A solo ${83 - result.correctCount} aciertos del Gran Premio de 30 minutos!",
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onClaimReward,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "¡Reclamar mis ${result.earnedMinutes} minutos! 🚀",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Se requieren al menos 50 de 83 aciertos para ganar minutos. ¡Repasa las flashcards para asegurar tu recompensa!",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onNavigateToFlashcards,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Repasar Flashcards (83 palabras)", fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onClose) {
                        Text("Volver a pantalla de bloqueo", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
