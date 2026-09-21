package com.parental.control.ui.child.mandarin

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.core.model.YctExamEngine
import com.parental.control.core.model.YctExamResult
import com.parental.control.core.model.YctQuestion
import kotlinx.coroutines.delay

@Composable
fun YctExamRunnerScreen(
    onClose: () -> Unit,
    onClaimReward: (result: YctExamResult) -> Unit
) {
    val context = LocalContext.current
    val ttsHelper = remember { ChineseTtsHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    // Cargar preguntas desde assets
    val questions = remember {
        try {
            val json = context.assets.open("yct_questions.json").bufferedReader().use { it.readText() }
            val bank = YctExamEngine.parseQuestionsFromJson(json)
            YctExamEngine.generateOfficialExam(bank)
        } catch (e: Exception) {
            emptyList()
        }
    }

    var currentIndex by remember { mutableStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<String, String>() }
    var showTextToggle by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var isExamFinished by remember { mutableStateOf(false) }
    var examResult by remember { mutableStateOf<YctExamResult?>(null) }
    var showConfirmExit by remember { mutableStateOf(false) }

    // Cronómetro del examen
    LaunchedEffect(isExamFinished) {
        while (!isExamFinished) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    if (questions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE11D48))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cargando banco de preguntas oficiales YCT 1...", color = Color.White)
            }
        }
        return
    }

    if (isExamFinished && examResult != null) {
        YctExamResultDialog(
            result = examResult!!,
            onClose = onClose,
            onClaimReward = {
                onClaimReward(examResult!!)
            }
        )
        return
    }

    val currentQ = questions[currentIndex]
    val totalQ = questions.size
    val isListening = currentQ.isListening

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
            // 1. BARRA SUPERIOR DE PROGRESO Y CONTROL
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge de Sección Oficial
                val badgeBg = if (isListening) Color(0xFF0284C7) else Color(0xFF059669)
                val badgeText = if (isListening) "🎧 听力 Listening" else "📖 阅读 Reading"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Contador de preguntas y cronómetro
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                Text(
                    text = "${currentIndex + 1}/$totalQ • ⏱️ ${String.format("%02d:%02d", mins, secs)}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Botón salir
                IconButton(onClick = { showConfirmExit = true }) {
                    Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color(0xFF94A3B8))
                }
            }

            // Barra de progreso horizontal
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / totalQ.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(6.dp),
                color = Color(0xFFE11D48),
                trackColor = Color(0xFF334155),
            )

            // 2. CONTENIDO SCROLLABLE DE LA PREGUNTA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Instrucción de la sección
                Text(
                    text = currentQ.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )

                // COMPONENTE DE AUDIO PARA LISTENING
                if (isListening) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = {
                                    if (!isPlayingAudio) {
                                        isPlayingAudio = true
                                        ttsHelper.speakTwice(currentQ.audioText) {
                                            isPlayingAudio = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlayingAudio) Color(0xFFF59E0B) else Color(0xFFE11D48)
                                ),
                                shape = RoundedCornerShape(25.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingAudio) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPlayingAudio) "🔊 Reproduciendo (2x)..." else "🔊 Reproducir Audio (2x)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = { showTextToggle = !showTextToggle }
                            ) {
                                Icon(
                                    imageVector = if (showTextToggle) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showTextToggle) "Ocultar texto" else "Mostrar texto y Pinyin",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }

                            if (showTextToggle) {
                                Text(
                                    text = currentQ.hanzi,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = currentQ.pinyin,
                                    fontSize = 15.sp,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                } else {
                    // Para READING, el texto siempre se muestra claramente con Pinyin
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentQ.hanzi,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentQ.pinyin,
                                fontSize = 16.sp,
                                color = Color(0xFFFBBF24),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ILUSTRACIÓN DE LA PREGUNTA (Si aplica)
                if (currentQ.image.isNotBlank()) {
                    val bitmap = remember(currentQ.image) {
                        loadAssetBitmap(context, currentQ.image)
                    }
                    if (bitmap != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(170.dp)
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Ilustración YCT",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. OPCIONES DE RESPUESTA
                val selectedAns = userAnswers[currentQ.id]

                // A) Tipo Verdadero / Falso (√ / ×)
                if (currentQ.options == listOf("true", "false")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Botón Verdadero (√)
                        val isTrueSelected = selectedAns == "true"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isTrueSelected) Color(0xFF059669) else Color(0xFF1E293B)
                            ),
                            border = if (isTrueSelected) BorderStroke(2.dp, Color(0xFF34D399)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable { userAnswers[currentQ.id] = "true" }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "√ Verdadero",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Botón Falso (×)
                        val isFalseSelected = selectedAns == "false"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFalseSelected) Color(0xFFDC2626) else Color(0xFF1E293B)
                            ),
                            border = if (isFalseSelected) BorderStroke(2.dp, Color(0xFFF87171)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable { userAnswers[currentQ.id] = "false" }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "× Falso",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else if (currentQ.choices.isNotEmpty()) {
                    // B) Opciones de Selección Múltiple A, B, C
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (ch in currentQ.choices) {
                            val isChoiceSelected = selectedAns?.uppercase() == ch.label.uppercase()
                            val choiceBg = if (isChoiceSelected) Color(0xFF4F46E5) else Color(0xFF1E293B)
                            val choiceBorder = if (isChoiceSelected) BorderStroke(2.dp, Color(0xFF818CF8)) else null

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = choiceBg),
                                border = choiceBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { userAnswers[currentQ.id] = ch.label }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Badge de letra A/B/C
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = if (isChoiceSelected) Color.White else Color(0xFF334155),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ch.label,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isChoiceSelected) Color(0xFF4F46E5) else Color.White,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    // Contenido de la opción
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (ch.text.isNotBlank()) {
                                            Text(
                                                text = ch.text,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        if (ch.pinyin.isNotBlank()) {
                                            Text(
                                                text = ch.pinyin,
                                                fontSize = 14.sp,
                                                color = Color(0xFFFBBF24)
                                            )
                                        }
                                        if (ch.es.isNotBlank()) {
                                            Text(
                                                text = ch.es,
                                                fontSize = 13.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. BARRA DE NAVEGACIÓN INFERIOR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Anterior
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            ttsHelper.stop()
                            currentIndex--
                            showTextToggle = false
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
                            showTextToggle = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("Siguiente")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = {
                            ttsHelper.stop()
                            examResult = YctExamEngine.evaluateExam(questions, userAnswers, elapsedSeconds)
                            isExamFinished = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Finalizar Examen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para salir
    if (showConfirmExit) {
        AlertDialog(
            onDismissRequest = { showConfirmExit = false },
            title = { Text("¿Abandonar el examen?", fontWeight = FontWeight.Bold) },
            text = { Text("Si sales ahora no se guardarán tus respuestas ni se concederá tiempo extra.") },
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
                    Text("Continuar examen")
                }
            }
        )
    }
}

@Composable
fun YctExamResultDialog(
    result: YctExamResult,
    onClose: () -> Unit,
    onClaimReward: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icono oficial de resultado
                val iconBg = if (result.passed) Color(0xFF059669) else Color(0xFFDC2626)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(iconBg.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (result.passed) Icons.Default.EmojiEvents else Icons.Default.School,
                        contentDescription = null,
                        tint = if (result.passed) Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (result.passed) "¡EXAMEN OFICIAL APROBADO! 🎉" else "¡BUEN INTENTO! 💪",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Examen YCT Nivel 1 (Youth Chinese Test)",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tarjeta con desglose oficial
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ResultScoreRow("🎧 Comprensión Auditiva", "${result.listeningScore} / 100", "${result.listeningCorrect}/20 correctas")
                        Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 10.dp))
                        ResultScoreRow("📖 Comprensión Lectora", "${result.readingScore} / 100", "${result.readingCorrect}/15 correctas")
                        Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 10.dp))
                        ResultScoreRow("🏆 Puntaje Total YCT", "${result.totalScore} / 200", "${result.totalCorrect}/35 (${result.scorePercentage.toInt()}%)", isTotal = true)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Recompensa obtenida
                if (result.passed && result.earnedMinutes > 0) {
                    val stars = "⭐ ".repeat(result.earnedMinutes)
                    Text(text = stars, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "¡Has ganado ${result.earnedMinutes} minutos extras de recreo!",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onClaimReward,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Celebration, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "¡Reclamar mis ${result.earnedMinutes} minutos! 🚀",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    Text(
                        text = "Se requiere al menos el 60% (21/35) para aprobar y liberar tiempo. ¡Repasa las flashcards y vuelve a intentarlo!",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Volver a la pantalla de bloqueo")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultScoreRow(label: String, scoreText: String, detailText: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                fontSize = if (isTotal) 15.sp else 13.sp,
                fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
                color = if (isTotal) Color.White else Color(0xFFE2E8F0)
            )
            Text(
                text = detailText,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = scoreText,
            fontSize = if (isTotal) 18.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isTotal) Color(0xFFFBBF24) else Color(0xFF38BDF8)
        )
    }
}

private fun loadAssetBitmap(context: Context, fileName: String): ImageBitmap? {
    if (fileName.isBlank()) return null
    return try {
        val stream = context.assets.open("yct1/$fileName")
        val bmp = BitmapFactory.decodeStream(stream)
        bmp?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
