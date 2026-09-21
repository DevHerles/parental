package com.parental.control.core.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VocabularyQuizEngineTest {

    private val sampleWords = mutableListOf<YctWord>()

    @Before
    fun setUp() {
        val wordData = listOf(
            Triple("家", "jiā", "Casa / Familia"),
            Triple("学校", "xué xiào", "Escuela"),
            Triple("猫", "māo", "Gato"),
            Triple("狗", "gǒu", "Perro"),
            Triple("鸟", "niǎo", "Pájaro"),
            Triple("鱼", "yú", "Pez / Pescado"),
            Triple("苹果", "píng guǒ", "Manzana"),
            Triple("米饭", "mǐ fàn", "Arroz"),
            Triple("面条", "miàn tiáo", "Fideos"),
            Triple("水", "shuǐ", "Agua"),
            Triple("牛奶", "niú nǎi", "Leche"),
            Triple("爸爸", "bà ba", "Papá"),
            Triple("妈妈", "mā ma", "Mamá"),
            Triple("老师", "lǎo shī", "Profesor"),
            Triple("看", "kàn", "Mirar / Ver"),
            Triple("吃", "chī", "Comer"),
            Triple("喝", "hē", "Beber"),
            Triple("大", "dà", "Grande"),
            Triple("小", "xiǎo", "Pequeño"),
            Triple("好", "hǎo", "Bueno / Bien")
        )

        sampleWords.clear()
        wordData.forEach { (cn, py, es) ->
            sampleWords.add(
                YctWord(
                    chinese = cn,
                    pinyin = py,
                    spanish = es,
                    category = "Test",
                    explanation = "Expl",
                    literalTranslation = "Lit"
                )
            )
        }
    }

    @Test
    fun testGenerateQuizGenerates15UniqueQuestions() {
        val questions = VocabularyQuizEngine.generateQuiz(sampleWords, 15)

        assertEquals(15, questions.size)

        // Verificar que las 15 palabras objetivo sean únicas (sin repetición)
        val targetChineseWords = questions.map { it.targetWord.chinese }
        val uniqueWords = targetChineseWords.toSet()
        assertEquals("Todas las 15 palabras deben ser únicas sin duplicados", 15, uniqueWords.size)

        // Verificar índices
        questions.forEachIndexed { index, q ->
            assertEquals(index, q.index)
            assertTrue("Debe tener al menos 2 opciones", q.choices.size >= 2)
            assertTrue("Índice correcto debe estar dentro de rango", q.correctChoiceIndex in q.choices.indices)
        }
    }

    @Test
    fun testModesAreDistributed() {
        val questions = VocabularyQuizEngine.generateQuiz(sampleWords, 15)
        val modes = questions.map { it.mode }.toSet()

        assertTrue("Debe incluir múltiples modos de juego", modes.size >= 3)
    }

    @Test
    fun testEvaluationGradingScale() {
        val questions = VocabularyQuizEngine.generateQuiz(sampleWords, 15)

        // 15/15 -> 5 estrellas, 5 min, aprobado
        val perfectAnswers = questions.indices.associateWith { questions[it].correctChoiceIndex }
        val perfectResult = VocabularyQuizEngine.evaluateQuiz(questions, perfectAnswers, 120)
        assertEquals(15, perfectResult.correctCount)
        assertEquals(5, perfectResult.stars)
        assertEquals(5, perfectResult.earnedMinutes)
        assertTrue(perfectResult.passed)

        // 13/15 -> 4 estrellas, 4 min, aprobado
        val answer13 = perfectAnswers.toMutableMap()
        answer13[0] = (questions[0].correctChoiceIndex + 1) % questions[0].choices.size
        answer13[1] = (questions[1].correctChoiceIndex + 1) % questions[1].choices.size
        val result13 = VocabularyQuizEngine.evaluateQuiz(questions, answer13, 100)
        assertEquals(13, result13.correctCount)
        assertEquals(4, result13.stars)
        assertEquals(4, result13.earnedMinutes)
        assertTrue(result13.passed)

        // 9/15 -> 2 estrellas, 2 min, aprobado mínimo (60%)
        val answer9 = questions.indices.associateWith { idx ->
            if (idx < 9) questions[idx].correctChoiceIndex
            else (questions[idx].correctChoiceIndex + 1) % questions[idx].choices.size
        }
        val result9 = VocabularyQuizEngine.evaluateQuiz(questions, answer9, 90)
        assertEquals(9, result9.correctCount)
        assertEquals(2, result9.stars)
        assertEquals(2, result9.earnedMinutes)
        assertTrue(result9.passed)

        // 8/15 -> 1 estrella, 0 min, NO aprobado (< 60%)
        val answer8 = questions.indices.associateWith { idx ->
            if (idx < 8) questions[idx].correctChoiceIndex
            else (questions[idx].correctChoiceIndex + 1) % questions[idx].choices.size
        }
        val result8 = VocabularyQuizEngine.evaluateQuiz(questions, answer8, 80)
        assertEquals(8, result8.correctCount)
        assertEquals(0, result8.earnedMinutes)
        assertFalse(result8.passed)
    }
}
