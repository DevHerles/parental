package com.parental.control.core.model

/**
 * Modos de juego para el Quiz de Vocabulario YCT 1.
 */
enum class VocabQuizMode {
    HANZI_TO_ES,    // Muestra Hanzi/Pinyin/Emoji -> 4 opciones en español
    ES_TO_HANZI,    // Muestra significado en español/Emoji -> 4 opciones de Hanzi con Pinyin
    LISTENING,      // Reproduce audio nativo -> 4 opciones de significado
    TRUE_FALSE      // Muestra una pareja (Hanzi = Español) -> Botón Verdadero o Falso
}

/**
 * Opción de respuesta en el Quiz.
 */
data class VocabQuizChoice(
    val label: String,              // "A", "B", "C", "D" o "√", "×"
    val primaryText: String,        // Texto principal grande
    val secondaryText: String? = null, // Pinyin o aclaración
    val emoji: String? = null       // Emoji ilustrativo
)

/**
 * Pregunta individual del Quiz de Vocabulario.
 */
data class VocabQuizQuestion(
    val id: String,
    val index: Int,                 // 0 a 14 (1 de 15)
    val mode: VocabQuizMode,
    val targetWord: YctWord,
    val promptText: String,
    val displayHanzi: String? = null,
    val displayPinyin: String? = null,
    val displayEmoji: String = "🀄",
    val audioText: String? = null,
    val choices: List<VocabQuizChoice>,
    val correctChoiceIndex: Int,
    val explanation: String
)

/**
 * Resultado y evaluación del Quiz de Vocabulario de 15 preguntas.
 */
data class VocabQuizResult(
    val totalQuestions: Int = 15,
    val correctCount: Int,
    val scorePercentage: Double,
    val stars: Int,                 // 0 a 5 estrellas
    val earnedMinutes: Int,         // 0 a 5 minutos
    val passed: Boolean,            // >= 9 aciertos (60%)
    val durationSeconds: Int,
    val completedAtMs: Long = System.currentTimeMillis()
)
