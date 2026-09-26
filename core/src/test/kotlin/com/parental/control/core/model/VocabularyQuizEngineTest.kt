package com.parental.control.core.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class VocabularyQuizEngineTest {

    private val fullBank = mutableListOf<YctWord>()

    @Before
    fun setUp() {
        fullBank.clear()
        // Generar un banco completo de 83 palabras (idéntico en tamaño al oficial YCT 1)
        for (i in 1..83) {
            fullBank.add(
                YctWord(
                    chinese = "字_$i",
                    pinyin = "zi_$i",
                    spanish = "Palabra_$i",
                    category = "YCT 1",
                    explanation = "Expl_$i",
                    literalTranslation = "Lit_$i"
                )
            )
        }
    }

    @Test
    fun testGenerateQuizGenerates45UniqueQuestions() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 45)

        assertEquals(45, questions.size)

        // Verificar que las 45 palabras objetivo sean únicas (sin repetición interna)
        val targetChineseWords = questions.map { it.targetWord.chinese }
        val uniqueWords = targetChineseWords.toSet()
        assertEquals("Todas las 45 palabras deben ser únicas sin duplicados en el quiz", 45, uniqueWords.size)

        // Verificar índices
        questions.forEachIndexed { index, q ->
            assertEquals(index, q.index)
            assertTrue("Debe tener al menos 2 opciones", q.choices.size >= 2)
            assertTrue("Índice correcto debe estar dentro de rango", q.correctChoiceIndex in q.choices.indices)
        }
    }

    @Test
    fun testPersistentDeckNeverRepeatsUntilAllWordsShown() {
        // Ronda 1: Inicio con mazo limpio (0 vistas)
        var seenKeys = emptySet<String>()
        val gen1 = VocabularyQuizEngine.generateQuizWithPersistentDeck(fullBank, seenKeys, 45)
        assertEquals(45, gen1.questions.size)
        assertEquals(45, gen1.updatedSeenKeys.size)
        assertFalse("El ciclo no ha terminado en la primera ronda", gen1.cycleCompleted)

        val wordsRound1 = gen1.questions.map { it.targetWord.chinese }.toSet()
        assertEquals(45, wordsRound1.size)

        // Ronda 2: Continuar con las 45 vistas de la Ronda 1
        seenKeys = gen1.updatedSeenKeys
        val gen2 = VocabularyQuizEngine.generateQuizWithPersistentDeck(fullBank, seenKeys, 45)
        assertEquals(45, gen2.questions.size)
        assertTrue("El ciclo de 83 palabras debió completarse en la ronda 2", gen2.cycleCompleted)

        val wordsRound2 = gen2.questions.map { it.targetWord.chinese }.toSet()
        assertEquals(45, wordsRound2.size)

        // Las 38 palabras que no se mostraron en la Ronda 1 DEBEN estar presentes en la Ronda 2
        val unseenAfterRound1 = fullBank.map { it.chinese }.filter { it !in wordsRound1 }
        assertEquals("Debieron quedar exactamente 38 palabras no vistas", 38, unseenAfterRound1.size)
        assertTrue(
            "Todas las 38 palabras no vistas deben haber sido mostradas en la Ronda 2",
            wordsRound2.containsAll(unseenAfterRound1)
        )

        // La unión de Ronda 1 + las 38 de la Ronda 2 cubre el 100% de las 83 palabras del banco
        val allShownInFirstCycle = wordsRound1 + unseenAfterRound1
        assertEquals("El 100% de las 83 palabras fueron mostradas sin omisión", 83, allShownInFirstCycle.size)

        // En la Ronda 2, las palabras repetidas son exactamente 7 (45 - 38)
        val repeatedInRound2 = wordsRound2.intersect(wordsRound1)
        assertEquals("Solo 7 palabras del nuevo mazo rebarajado deben repetirse en la Ronda 2", 7, repeatedInRound2.size)

        // Las nuevas seenKeys deben contener exactamente las 7 palabras del nuevo ciclo
        assertEquals(7, gen2.updatedSeenKeys.size)
    }

    @Test
    fun testModesAreDistributedEquitably() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 45)
        val modes = questions.groupBy { it.mode }

        assertTrue("Debe incluir los 4 modos de juego", modes.size == 4)
        assertEquals(12, modes[VocabQuizMode.HANZI_TO_ES]?.size)
        assertEquals(12, modes[VocabQuizMode.ES_TO_HANZI]?.size)
        assertEquals(11, modes[VocabQuizMode.LISTENING]?.size)
        assertEquals(10, modes[VocabQuizMode.TRUE_FALSE]?.size)
    }

    @Test
    fun testEvaluationGradingScaleUpTo15Minutes() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 45)

        // 45/45 -> 5 estrellas, 15 min, aprobado
        val perfectAnswers = questions.indices.associateWith { questions[it].correctChoiceIndex }
        val perfectResult = VocabularyQuizEngine.evaluateQuiz(questions, perfectAnswers, 300)
        assertEquals(45, perfectResult.correctCount)
        assertEquals(5, perfectResult.stars)
        assertEquals(15, perfectResult.earnedMinutes)
        assertTrue(perfectResult.passed)

        // 42/45 -> 5 estrellas, 14 min, aprobado
        val answer42 = perfectAnswers.toMutableMap()
        for (i in 0 until 3) {
            answer42[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result42 = VocabularyQuizEngine.evaluateQuiz(questions, answer42, 280)
        assertEquals(42, result42.correctCount)
        assertEquals(5, result42.stars)
        assertEquals(14, result42.earnedMinutes)
        assertTrue(result42.passed)

        // 36/45 -> 4 estrellas, 12 min, aprobado
        val answer36 = perfectAnswers.toMutableMap()
        for (i in 0 until 9) {
            answer36[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result36 = VocabularyQuizEngine.evaluateQuiz(questions, answer36, 250)
        assertEquals(36, result36.correctCount)
        assertEquals(4, result36.stars)
        assertEquals(12, result36.earnedMinutes)
        assertTrue(result36.passed)

        // 27/45 -> 2 estrellas, 9 min, aprobado mínimo (60%)
        val answer27 = perfectAnswers.toMutableMap()
        for (i in 0 until 18) {
            answer27[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result27 = VocabularyQuizEngine.evaluateQuiz(questions, answer27, 200)
        assertEquals(27, result27.correctCount)
        assertEquals(2, result27.stars)
        assertEquals(9, result27.earnedMinutes)
        assertTrue(result27.passed)

        // 26/45 -> 0 estrellas, 0 min, NO aprobado (< 60%)
        val answer26 = perfectAnswers.toMutableMap()
        for (i in 0 until 19) {
            answer26[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result26 = VocabularyQuizEngine.evaluateQuiz(questions, answer26, 180)
        assertEquals(26, result26.correctCount)
        assertEquals(0, result26.stars)
        assertEquals(0, result26.earnedMinutes)
        assertFalse(result26.passed)
    }

    @Test
    fun testBedtimeCurfewCrossingMidnight20to8() {
        val bedtimeSchedule = CurfewSchedule(
            id = "bed_time",
            name = "Hora de Dormir",
            daysOfWeek = setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                Calendar.SATURDAY
            ),
            startHour = 20,
            startMinute = 0,
            endHour = 8,
            endMinute = 0,
            isEnabled = true
        )

        // 20:00 (Noche) -> Activo
        val cal2000 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal2000))

        // 23:30 (Noche) -> Activo
        val cal2330 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal2330))

        // 00:00 (Medianoche) -> Activo
        val cal0000 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal0000))

        // 04:15 (Madrugada) -> Activo
        val cal0415 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 15)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal0415))

        // 07:59 (Antes de las 8am) -> Activo
        val cal0759 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 59)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal0759))

        // 08:01 (Día) -> Inactivo
        val cal0801 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 1)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal0801))

        // 14:00 (Tarde) -> Inactivo
        val cal1400 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal1400))

        // 19:59 (Antes de las 20h) -> Inactivo
        val cal1959 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 59)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal1959))
    }
}
