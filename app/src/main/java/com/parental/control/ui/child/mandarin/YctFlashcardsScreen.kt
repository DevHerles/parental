package com.parental.control.ui.child.mandarin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parental.control.core.model.YctExamEngine
import com.parental.control.core.model.YctWord

@Composable
fun YctFlashcardsScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val ttsHelper = remember { ChineseTtsHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
        }
    }

    val allWords = remember {
        try {
            val json = context.assets.open("yct1_vocabulary.json").bufferedReader().use { it.readText() }
            YctExamEngine.parseVocabularyFromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    var selectedCategory by remember { mutableStateOf("Todas") }
    val categories = listOf("Todas", "Familia", "Cuerpo", "Animales", "Comida", "Números", "Verbos", "Adjetivos")

    val filteredWords = remember(selectedCategory, allWords) {
        if (selectedCategory == "Todas") {
            allWords
        } else {
            allWords.filter { word ->
                when (selectedCategory) {
                    "Familia" -> word.chinese.contains("家") || word.chinese.contains("爸") || word.chinese.contains("妈") || word.chinese.contains("哥") || word.chinese.contains("姐") || word.chinese.contains("老师") || word.chinese.contains("人")
                    "Cuerpo" -> word.chinese.contains("手") || word.chinese.contains("口") || word.chinese.contains("眼") || word.chinese.contains("发") || word.chinese.contains("耳") || word.chinese.contains("鼻") || word.chinese.contains("个子")
                    "Animales" -> word.chinese.contains("猫") || word.chinese.contains("狗") || word.chinese.contains("鸟") || word.chinese.contains("鱼")
                    "Comida" -> word.chinese.contains("水") || word.chinese.contains("奶") || word.chinese.contains("饭") || word.chinese.contains("面条") || word.chinese.contains("苹果")
                    "Números" -> word.chinese in listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "岁") || word.chinese.contains("天") || word.chinese.contains("月") || word.chinese.contains("号") || word.chinese.contains("星期") || word.chinese.contains("点")
                    "Verbos" -> word.chinese in listOf("谢谢", "再见", "是", "有", "看", "吃", "喝", "去", "叫", "爱", "喜欢", "认识")
                    "Adjetivos" -> word.chinese in listOf("好", "多", "大", "小", "长", "高", "高兴")
                    else -> true
                }
            }
        }
    }

    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(selectedCategory) {
        currentIndex = 0
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
            // Header superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vocabulario YCT 1 Oficial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                }
            }

            // Chips de categorías temáticas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in categories) {
                    val isSel = cat == selectedCategory
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE11D48),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            if (filteredWords.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No hay palabras en esta categoría", color = Color(0xFF94A3B8))
                }
            } else {
                val safeIndex = currentIndex.coerceIn(0, filteredWords.size - 1)
                val word = filteredWords[safeIndex]

                // TARJETA DE FLASHCARD PRINCIPAL
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Emoji y categoría
                        Text(
                            text = word.emoji,
                            fontSize = 44.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Hanzi Gigante
                        Text(
                            text = word.chinese,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        // Pinyin
                        Text(
                            text = word.pinyin,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFBBF24),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Significado en español
                        Text(
                            text = word.spanish,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            textAlign = TextAlign.Center
                        )

                        if (word.literalTranslation.isNotBlank()) {
                            Text(
                                text = word.literalTranslation,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Botón de audio para escuchar pronunciación nativa
                        Button(
                            onClick = {
                                ttsHelper.speakOnce(word.chinese)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Escuchar",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Tarjeta de explicación y ejemplos
                        if (word.explanation.isNotBlank()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    val cleanExp = word.explanation
                                        .replace("<br>", "\n")
                                        .replace("<strong>", "")
                                        .replace("</strong>", "")
                                        .replace("<em>", "")
                                        .replace("</em>", "")
                                    Text(
                                        text = cleanExp,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // BARRA DE NAVEGACIÓN INFERIOR (ANTERIOR / SIGUIENTE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (safeIndex > 0) currentIndex--
                        },
                        enabled = safeIndex > 0
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Anterior", tint = if (safeIndex > 0) Color.White else Color(0xFF475569))
                    }

                    Text(
                        text = "${safeIndex + 1} de ${filteredWords.size}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = {
                            if (safeIndex < filteredWords.size - 1) currentIndex++
                        },
                        enabled = safeIndex < filteredWords.size - 1
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Siguiente", tint = if (safeIndex < filteredWords.size - 1) Color.White else Color(0xFF475569))
                    }
                }
            }
        }
    }
}
