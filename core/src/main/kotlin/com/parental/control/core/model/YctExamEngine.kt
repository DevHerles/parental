package com.parental.control.core.model

import org.json.JSONArray
import kotlin.random.Random

object YctExamEngine {

    val SECTION_ORDER = listOf(
        "listening_1",
        "listening_2",
        "listening_3",
        "listening_4",
        "reading_1",
        "reading_2",
        "reading_3"
    )

    fun parseQuestionsFromJson(jsonString: String): List<YctQuestion> {
        val list = mutableListOf<YctQuestion>()
        try {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(YctQuestion.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun parseVocabularyFromJson(jsonString: String): List<YctWord> {
        val list = mutableListOf<YctWord>()
        try {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(YctWord.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Genera un examen oficial YCT 1 completo de exactamente 35 preguntas:
     * 5 preguntas seleccionadas aleatoriamente de cada una de las 7 secciones.
     */
    fun generateOfficialExam(bank: List<YctQuestion>, random: Random = Random): List<YctQuestion> {
        val bySection = bank.groupBy { it.section }
        val selected = mutableListOf<YctQuestion>()

        for (sec in SECTION_ORDER) {
            val available = bySection[sec] ?: emptyList()
            val picked = available.shuffled(random).take(5)
            selected.addAll(picked)
        }

        // Si por alguna razón no se completan 35 preguntas exactas, rellenar del banco
        if (selected.size < 35 && bank.size >= 35) {
            val remaining = bank.filterNot { selected.contains(it) }.shuffled(random)
            selected.addAll(remaining.take(35 - selected.size))
        }

        return selected.take(35)
    }

    /**
     * Evalúa las respuestas del usuario sobre el examen de 35 preguntas y computa
     * el resultado oficial de acuerdo al estándar de Hanban/CLEC.
     */
    fun evaluateExam(
        questions: List<YctQuestion>,
        userAnswers: Map<String, String>,
        durationSeconds: Int
    ): YctExamResult {
        var listeningCorrect = 0
        var readingCorrect = 0
        val totalQuestions = questions.size.coerceAtLeast(1)

        for (q in questions) {
            val userAns = userAnswers[q.id]?.trim()?.lowercase() ?: ""
            val correctAns = q.correctAnswer.trim().lowercase()
            val isCorrect = userAns.isNotEmpty() && (userAns == correctAns)

            if (isCorrect) {
                if (q.isListening) {
                    listeningCorrect++
                } else {
                    readingCorrect++
                }
            }
        }

        val totalCorrect = listeningCorrect + readingCorrect
        val listeningCount = questions.count { it.isListening }.coerceAtLeast(1)
        val readingCount = questions.count { !it.isListening }.coerceAtLeast(1)

        val listeningScore = ((listeningCorrect.toDouble() / listeningCount) * 100.0).toInt().coerceIn(0, 100)
        val readingScore = ((readingCorrect.toDouble() / readingCount) * 100.0).toInt().coerceIn(0, 100)
        val totalScore = listeningScore + readingScore // Escala 0 a 200 puntos
        val scorePercentage = (totalCorrect.toDouble() / totalQuestions) * 100.0

        // El examen oficial se aprueba con >= 60% (120/200 puntos o 21/35 preguntas)
        val passed = totalCorrect >= 21

        // Escala de minutos según especificación aprobada
        val earnedMinutes = when {
            totalCorrect >= 32 -> 5 // 91% - 100% (Sobresaliente)
            totalCorrect >= 28 -> 4 // 80% - 90% (Muy bien)
            totalCorrect >= 24 -> 3 // 70% - 79% (Bien)
            totalCorrect >= 21 -> 2 // 60% - 69% (Aprobado mínimo oficial)
            else -> 0              // < 60% (Desaprobado)
        }

        return YctExamResult(
            listeningScore = listeningScore,
            readingScore = readingScore,
            totalScore = totalScore,
            listeningCorrect = listeningCorrect,
            readingCorrect = readingCorrect,
            totalCorrect = totalCorrect,
            totalQuestions = totalQuestions,
            scorePercentage = scorePercentage,
            passed = passed,
            earnedMinutes = earnedMinutes,
            durationSeconds = durationSeconds
        )
    }
}
