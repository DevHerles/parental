package com.parental.control.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.ui.theme.*

@Composable
fun LockOverlayContent(
    blockedAppName: String,
    onDismissToHome: () -> Unit,
    onParentUnlock: (pin: String, durationMinutes: Int) -> Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { com.parental.control.core.data.ParentalRepository.getInstance(context) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var unlockDuration by remember { mutableStateOf(15) }
    var pinError by remember { mutableStateOf(false) }

    var showVocabQuiz by remember { mutableStateOf(false) }
    var showFlashcards by remember { mutableStateOf(false) }
    var cooldownRemainingMs by remember { mutableStateOf(repository.getChineseExamCooldownRemainingMs()) }
    var canAttemptExam by remember { mutableStateOf(repository.canAttemptChineseExam()) }
    var isBedtime by remember { mutableStateOf(repository.isBedtimeCurfewActive()) }

    LaunchedEffect(Unit) {
        while (true) {
            cooldownRemainingMs = repository.getChineseExamCooldownRemainingMs()
            canAttemptExam = repository.canAttemptChineseExam()
            isBedtime = repository.isBedtimeCurfewActive()
            kotlinx.coroutines.delay(1000L)
        }
    }

    if (showVocabQuiz) {
        com.parental.control.ui.child.mandarin.VocabularyQuizScreen(
            onClose = { showVocabQuiz = false },
            onClaimReward = { result ->
                val granted = repository.claimVocabularyQuizReward(result)
                if (granted > 0) {
                    showVocabQuiz = false
                    onDismissToHome()
                }
            },
            onNavigateToFlashcards = {
                showVocabQuiz = false
                showFlashcards = true
            }
        )
        return
    }

    if (showFlashcards) {
        com.parental.control.ui.child.mandarin.YctFlashcardsScreen(
            onClose = { showFlashcards = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LockOverlayBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Círculo con Icono Motivacional (Luna en la noche, Libro en el día)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = if (isBedtime) Color(0xFF6366F1).copy(alpha = 0.25f) else AegisPrimary.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isBedtime) {
                    Text(text = "🌙", fontSize = 48.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Libro de estudio",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (isBedtime) "¡Hora de Dormir y Descansar! 🌙" else "¡Tiempo de Concentración! 📚",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isBedtime) "Horario nocturno de descanso (20:00 a 08:00)" else "$blockedAppName está en pausa por tus padres.",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tarjeta de sugerencia constructiva
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LockCardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBedtime) {
                        Text(text = "😴", fontSize = 26.sp)
                    } else {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = AegisWarning,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isBedtime) {
                            "Es momento de desconectar la pantalla y dormir bien para recargar tus energías. Mañana será un gran día. ✨"
                        } else {
                            "¿Qué tal dibujar, avanzar con la tarea o leer un rato? Tu mente te lo agradecerá. ✨"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }

            // SECCIÓN EDUCATIVA CHINO MANDARÍN (YCT 1)
            Spacer(modifier = Modifier.height(20.dp))

            if (isBedtime) {
                // En horario nocturno no se permite rendir retos para evitar trasnochar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🌙 Retos de minutos en pausa hasta las 08:00 AM",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (canAttemptExam) {
                Button(
                    onClick = { showVocabQuiz = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎯 ¡Reto de Vocabulario (45 preguntas) y Gana hasta 15 min!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            } else {
                val totalSecs = (cooldownRemainingMs / 1000L).coerceAtLeast(0L)
                val hours = totalSecs / 3600
                val cMins = (totalSecs % 3600) / 60
                val cSecs = totalSecs % 60
                val countdownFormatted = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, cMins, cSecs)
                } else {
                    String.format("%02d:%02d", cMins, cSecs)
                }
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⏳ Próximo reto en $countdownFormatted",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }

            if (!isBedtime) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showFlashcards = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📖 Repasar Vocabulario Oficial (83 Flashcards)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón principal: Salir a Inicio
            Button(
                onClick = onDismissToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisPrimary)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Volver a Inicio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón secundario: Acceso Padres
            TextButton(
                onClick = { showPinDialog = true }
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Acceso Padres (PIN)",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Diálogo para introducir el PIN del Padre
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinDialog = false
                    pinText = ""
                    pinError = false
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                ),
                title = {
                    Text(text = "Autorización de los Padres", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ingresa tu PIN de administrador para desbloquear temporalmente:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Teclado numérico táctil en pantalla
                        com.parental.control.ui.components.PinNumericKeypad(
                            pin = pinText,
                            onPinChange = {
                                pinText = it
                                pinError = false
                            },
                            maxLength = 6,
                            isError = pinError,
                            errorMessage = if (pinError) "PIN incorrecto. Inténtalo de nuevo." else null
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Tiempo concedido:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilterChip(
                                selected = unlockDuration == 15,
                                onClick = { unlockDuration = 15 },
                                label = { Text("15 min") }
                            )
                            FilterChip(
                                selected = unlockDuration == 30,
                                onClick = { unlockDuration = 30 },
                                label = { Text("30 min") }
                            )
                            FilterChip(
                                selected = unlockDuration == 60,
                                onClick = { unlockDuration = 60 },
                                label = { Text("1 hora") }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = pinText.length >= 4,
                        onClick = {
                            val success = onParentUnlock(pinText, unlockDuration)
                            if (success) {
                                showPinDialog = false
                            } else {
                                pinError = true
                            }
                        }
                    ) {
                        Text("Desbloquear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
