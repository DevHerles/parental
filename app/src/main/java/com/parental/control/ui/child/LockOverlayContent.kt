package com.parental.control.ui.child

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LockOverlayBg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // DISEÑO EN DOS COLUMNAS OPTIMIZADO PARA TABLET LANDSCAPE
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // COLUMNA IZQUIERDA: ACCIONES Y ESTADO
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LockLeftActionPanel(
                        isBedtime = isBedtime,
                        blockedAppName = blockedAppName,
                        canAttemptExam = canAttemptExam,
                        cooldownRemainingMs = cooldownRemainingMs,
                        onStartQuiz = { showVocabQuiz = true },
                        onOpenFlashcards = { showFlashcards = true },
                        onDismissToHome = onDismissToHome,
                        onOpenPinDialog = { showPinDialog = true }
                    )
                }

                // COLUMNA DERECHA: APARTADO DE CALIFICACIÓN PSICOLÓGICA Y MENSAJE MOTIVADOR
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isBedtime) {
                        BedtimeSleepInfoPanel()
                    } else {
                        PsychologicalRewardsPanel()
                    }
                }
            }
        } else {
            // DISEÑO ADAPTABLE VERTICAL CON SCROLL PARA PORTRAIT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LockLeftActionPanel(
                    isBedtime = isBedtime,
                    blockedAppName = blockedAppName,
                    canAttemptExam = canAttemptExam,
                    cooldownRemainingMs = cooldownRemainingMs,
                    onStartQuiz = { showVocabQuiz = true },
                    onOpenFlashcards = { showFlashcards = true },
                    onDismissToHome = onDismissToHome,
                    onOpenPinDialog = { showPinDialog = true }
                )

                if (isBedtime) {
                    BedtimeSleepInfoPanel()
                } else {
                    PsychologicalRewardsPanel()
                }
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
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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

/**
 * Panel de Acciones y Estado (Columna Izquierda en landscape).
 */
@Composable
private fun LockLeftActionPanel(
    isBedtime: Boolean,
    blockedAppName: String,
    canAttemptExam: Boolean,
    cooldownRemainingMs: Long,
    onStartQuiz: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onDismissToHome: () -> Unit,
    onOpenPinDialog: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Círculo con Icono Motivacional
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    color = if (isBedtime) Color(0xFF6366F1).copy(alpha = 0.25f) else AegisPrimary.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isBedtime) {
                Text(text = "🌙", fontSize = 38.sp)
            } else {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Libro de estudio",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (isBedtime) "¡Hora de Descansar! 🌙" else "¡Tiempo de Concentración! 📚",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isBedtime) "Horario nocturno (21:00 a 09:00)" else "$blockedAppName está en pausa por tus padres.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de sugerencia constructiva
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = LockCardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBedtime) {
                    Text(text = "😴", fontSize = 22.sp)
                } else {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AegisWarning,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isBedtime) {
                        "Es momento de desconectar la pantalla y dormir bien para recargar tus energías. ✨"
                    } else {
                        "¿Qué tal dibujar, avanzar con la tarea o leer un rato? Tu mente te lo agradecerá. ✨"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botón del Reto de Chino
        if (isBedtime) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "🌙 Retos en pausa hasta las 09:00 AM",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (canAttemptExam) {
            Button(
                onClick = onStartQuiz,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🎯 ¡Comenzar Reto YCT 1! (Gana 10 a 30 min)",
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "⏳ Próximo reto en $countdownFormatted",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        if (!isBedtime) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenFlashcards,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "📖 Repasar Vocabulario Oficial (83 Flashcards)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botón principal: Salir a Inicio
        Button(
            onClick = onDismissToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AegisPrimary)
        ) {
            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Volver a Inicio",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón secundario: Acceso Padres
        TextButton(
            onClick = onOpenPinDialog
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Acceso Padres (PIN)",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Apartado Clarísimo de Calificación Psicológica y Mensaje Motivador.
 */
@Composable
fun PsychologicalRewardsPanel(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Encabezado del Panel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFFBBF24).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Escala de Calificación y Recompensas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Reto Oficial YCT 1 (83 preguntas) • ¡Cada acierto suma!",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Fila 1: Perfección Total
            RewardTierRow(
                emoji = "🏆",
                title = "Perfección Total",
                questionsText = "83 / 83",
                starsText = "⭐⭐⭐⭐⭐",
                rewardBadge = "+30 MIN",
                badgeColor = Color(0xFF10B981),
                description = "¡Gran Premio! Máximo orgullo y dominio total del idioma."
            )

            // Fila 2: Sobresaliente
            RewardTierRow(
                emoji = "🌟",
                title = "Sobresaliente",
                questionsText = "78 a 82",
                starsText = "⭐⭐⭐⭐⭐",
                rewardBadge = "+25 MIN",
                badgeColor = Color(0xFF34D399),
                description = "¡Extraordinario! A solo unos pasos del 100% de perfección."
            )

            // Fila 3: Notable
            RewardTierRow(
                emoji = "🎖️",
                title = "Notable",
                questionsText = "70 a 77",
                starsText = "⭐⭐⭐⭐",
                rewardBadge = "+20 MIN",
                badgeColor = Color(0xFF38BDF8),
                description = "¡Excelente dominio y solidez en vocabulario!"
            )

            // Fila 4: Buen Desempeño
            RewardTierRow(
                emoji = "👍",
                title = "Buen Desempeño",
                questionsText = "60 a 69",
                starsText = "⭐⭐⭐⭐",
                rewardBadge = "+15 MIN",
                badgeColor = Color(0xFFA78BFA),
                description = "¡Muy bien! Superando la media de evaluación internacional."
            )

            // Fila 5: Meta Mínima / Aprobado
            RewardTierRow(
                emoji = "🎯",
                title = "Meta Mínima (Piso)",
                questionsText = "50 a 59",
                starsText = "⭐⭐⭐",
                rewardBadge = "+10 MIN",
                badgeColor = Color(0xFFFBBF24),
                description = "¡Aprobada con 60.2% (Estándar YCT)! Minutos asegurados."
            )

            // Mensaje Inspirador para la Niña y Salud Digital
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.9f),
                border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💖", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "¡Tú eres muy capaz de lograr los 30 minutos!",
                            color = Color(0xFFFB7185),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "Con 50 aciertos ya tienes 10 min seguros. Lee con calma y ve por los 30 min. Al ganar 25 a 30 min, se activa una pausa de 3 horas para cuidar tus ojos y mente. 🌱",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            lineHeight = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila individual para cada nivel de la escala de recompensas.
 */
@Composable
private fun RewardTierRow(
    emoji: String,
    title: String,
    questionsText: String,
    starsText: String,
    rewardBadge: String,
    badgeColor: Color,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.65f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "($questionsText)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = starsText,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    lineHeight = 12.5.sp
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = badgeColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, badgeColor)
            ) {
                Text(
                    text = rewardBadge,
                    color = badgeColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Panel para horario nocturno (21:00 a 09:00).
 */
@Composable
private fun BedtimeSleepInfoPanel(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🌙", fontSize = 42.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Hora de Dormir y Descansar",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Dormir las horas adecuadas fortalece tu memoria, tu aprendizaje y tu salud.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "✨ Mañana a las 09:00 AM:",
                        color = Color(0xFF818CF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Los retos de Chino Mandarín volverán a estar activos para que sigas aprendiendo y ganando tiempo recreativo.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
