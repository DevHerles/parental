package com.parental.control.core.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class YctExamEngineTest {

    private fun createDummyBank(): List<YctQuestion> {
        val bank = mutableListOf<YctQuestion>()
        for (sec in YctExamEngine.SECTION_ORDER) {
            for (i in 1..10) {
                bank.add(
                    YctQuestion(
                        id = "${sec}_$i",
                        section = sec,
                        sectionName = "Sección $sec",
                        instruction = "Instrucción de prueba",
                        audioText = "苹果",
                        hanzi = "苹果",
                        pinyin = "píngguǒ",
                        translationEs = "Manzana",
                        explanation = "Explicación",
                        correctAnswer = if (sec.contains("1")) "true" else "A",
                        image = "apple.jpg",
                        options = if (sec.contains("1")) listOf("true", "false") else emptyList(),
                        choices = if (!sec.contains("1")) listOf(
                            YctChoice(label = "A", text = "Opción A"),
                            YctChoice(label = "B", text = "Opción B"),
                            YctChoice(label = "C", text = "Opción C")
                        ) else emptyList()
                    )
                )
            }
        }
        return bank
    }

    @Test
    fun testGenerateOfficialExamProducesExactly35Questions() {
        val bank = createDummyBank()
        val exam = YctExamEngine.generateOfficialExam(bank, Random(42))

        assertEquals(35, exam.size)

        // Verificar que cada sección contenga exactamente 5 preguntas
        val countsBySection = exam.groupBy { it.section }.mapValues { it.value.size }
        for (sec in YctExamEngine.SECTION_ORDER) {
            assertEquals(5, countsBySection[sec] ?: 0)
        }

        // Las primeras 20 son de listening
        val listeningCount = exam.take(20).count { it.isListening }
        assertEquals(20, listeningCount)

        // Las últimas 15 son de reading
        val readingCount = exam.drop(20).count { !it.isListening }
        assertEquals(15, readingCount)
    }

    @Test
    fun testEvaluateExamFullScoreGrants5Minutes() {
        val bank = createDummyBank()
        val exam = YctExamEngine.generateOfficialExam(bank, Random(42))

        val userAnswers = exam.associate { it.id to it.correctAnswer }
        val result = YctExamEngine.evaluateExam(exam, userAnswers, 600)

        assertEquals(35, result.totalCorrect)
        assertEquals(20, result.listeningCorrect)
        assertEquals(15, result.readingCorrect)
        assertEquals(100, result.listeningScore)
        assertEquals(100, result.readingScore)
        assertEquals(200, result.totalScore)
        assertTrue(result.passed)
        assertEquals(5, result.earnedMinutes)
    }

    @Test
    fun testEvaluateExamPassingScale() {
        val bank = createDummyBank()
        val exam = YctExamEngine.generateOfficialExam(bank, Random(42))

        // Caso 1: 33 correctas -> 5 minutos
        val ans33 = exam.mapIndexed { idx, q ->
            q.id to (if (idx < 33) q.correctAnswer else "wrong")
        }.toMap()
        val res33 = YctExamEngine.evaluateExam(exam, ans33, 600)
        assertEquals(33, res33.totalCorrect)
        assertEquals(5, res33.earnedMinutes)
        assertTrue(res33.passed)

        // Caso 2: 29 correctas -> 4 minutos
        val ans29 = exam.mapIndexed { idx, q ->
            q.id to (if (idx < 29) q.correctAnswer else "wrong")
        }.toMap()
        val res29 = YctExamEngine.evaluateExam(exam, ans29, 600)
        assertEquals(29, res29.totalCorrect)
        assertEquals(4, res29.earnedMinutes)
        assertTrue(res29.passed)

        // Caso 3: 25 correctas -> 3 minutos
        val ans25 = exam.mapIndexed { idx, q ->
            q.id to (if (idx < 25) q.correctAnswer else "wrong")
        }.toMap()
        val res25 = YctExamEngine.evaluateExam(exam, ans25, 600)
        assertEquals(25, res25.totalCorrect)
        assertEquals(3, res25.earnedMinutes)
        assertTrue(res25.passed)

        // Caso 4: 21 correctas (Aprobado mínimo oficial 60%) -> 2 minutos
        val ans21 = exam.mapIndexed { idx, q ->
            q.id to (if (idx < 21) q.correctAnswer else "wrong")
        }.toMap()
        val res21 = YctExamEngine.evaluateExam(exam, ans21, 600)
        assertEquals(21, res21.totalCorrect)
        assertEquals(2, res21.earnedMinutes)
        assertTrue(res21.passed)

        // Caso 5: 20 correctas (Desaprobado < 60%) -> 0 minutos
        val ans20 = exam.mapIndexed { idx, q ->
            q.id to (if (idx < 20) q.correctAnswer else "wrong")
        }.toMap()
        val res20 = YctExamEngine.evaluateExam(exam, ans20, 600)
        assertEquals(20, res20.totalCorrect)
        assertEquals(0, res20.earnedMinutes)
        assertFalse(res20.passed)
    }

    @Test
    fun testWordEmojiMapping() {
        assertEquals("🍎", YctWord.getEmojiForWord("苹果"))
        assertEquals("🐱", YctWord.getEmojiForWord("小猫"))
        assertEquals("🐶", YctWord.getEmojiForWord("狗"))
        assertEquals("👨", YctWord.getEmojiForWord("爸爸"))
        assertEquals("👩", YctWord.getEmojiForWord("妈妈"))
        assertEquals("🏫", YctWord.getEmojiForWord("学校"))
    }
}
