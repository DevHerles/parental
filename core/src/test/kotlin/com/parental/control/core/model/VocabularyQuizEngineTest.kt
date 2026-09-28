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
    fun testGenerateQuizGenerates83UniqueQuestions() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 83)

        assertEquals(83, questions.size)

        // Verificar que las 83 palabras objetivo sean únicas (cubre el 100% del currículo YCT 1)
        val targetChineseWords = questions.map { it.targetWord.chinese }
        val uniqueWords = targetChineseWords.toSet()
        assertEquals("Todas las 83 palabras deben ser evaluadas sin omisiones ni duplicados", 83, uniqueWords.size)

        // Verificar opciones e índices sin pistas de emojis en las alternativas del quiz
        questions.forEachIndexed { index, q ->
            assertEquals(index, q.index)
            assertTrue("Debe tener al menos 2 opciones", q.choices.size >= 2)
            assertTrue("Índice correcto debe estar dentro de rango", q.correctChoiceIndex in q.choices.indices)
            q.choices.forEach { choice ->
                assertNull("Las alternativas del test no deben incluir pistas de emojis", choice.emoji)
            }
        }
    }

    @Test
    fun testPersistentDeckWith83Words() {
        // Ronda 1: Inicio con mazo limpio (0 vistas)
        val seenKeys = emptySet<String>()
        val gen1 = VocabularyQuizEngine.generateQuizWithPersistentDeck(fullBank, seenKeys, 83)
        assertEquals(83, gen1.questions.size)
        assertEquals(83, gen1.updatedSeenKeys.size)

        val wordsRound1 = gen1.questions.map { it.targetWord.chinese }.toSet()
        assertEquals(83, wordsRound1.size)
        assertEquals("La ronda 1 debe cubrir el 100% del banco de 83 palabras", 83, wordsRound1.intersect(fullBank.map { it.chinese }.toSet()).size)

        // Ronda 2: Continuar con las 83 vistas de la Ronda 1
        val gen2 = VocabularyQuizEngine.generateQuizWithPersistentDeck(fullBank, gen1.updatedSeenKeys, 83)
        assertEquals(83, gen2.questions.size)
        assertTrue("El ciclo de 83 palabras debió completarse", gen2.cycleCompleted)
        assertEquals(83, gen2.updatedSeenKeys.size)
    }

    @Test
    fun testModesAreDistributedEquitably() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 83)
        val modes = questions.groupBy { it.mode }

        assertTrue("Debe incluir los 4 modos de juego", modes.size == 4)
        assertEquals(22, modes[VocabQuizMode.HANZI_TO_ES]?.size)
        assertEquals(22, modes[VocabQuizMode.ES_TO_HANZI]?.size)
        assertEquals(21, modes[VocabQuizMode.LISTENING]?.size)
        assertEquals(18, modes[VocabQuizMode.TRUE_FALSE]?.size)
    }

    @Test
    fun testEvaluationGradingScaleUpTo15Minutes() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 83)
        val perfectAnswers = questions.indices.associateWith { questions[it].correctChoiceIndex }

        // 83/83 -> 5 estrellas, 15 min, aprobado (100% Flawless)
        val perfectResult = VocabularyQuizEngine.evaluateQuiz(questions, perfectAnswers, 500)
        assertEquals(83, perfectResult.correctCount)
        assertEquals(5, perfectResult.stars)
        assertEquals(15, perfectResult.earnedMinutes)
        assertTrue(perfectResult.passed)

        // 81/83 -> 5 estrellas, 13 min, aprobado
        val answer81 = perfectAnswers.toMutableMap()
        for (i in 0 until 2) {
            answer81[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result81 = VocabularyQuizEngine.evaluateQuiz(questions, answer81, 480)
        assertEquals(81, result81.correctCount)
        assertEquals(5, result81.stars)
        assertEquals(13, result81.earnedMinutes)
        assertTrue(result81.passed)

        // 79/83 -> 4 estrellas, 11 min, aprobado
        val answer79 = perfectAnswers.toMutableMap()
        for (i in 0 until 4) {
            answer79[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result79 = VocabularyQuizEngine.evaluateQuiz(questions, answer79, 450)
        assertEquals(79, result79.correctCount)
        assertEquals(4, result79.stars)
        assertEquals(11, result79.earnedMinutes)
        assertTrue(result79.passed)

        // 76/83 -> 4 estrellas, 9 min, aprobado
        val answer76 = perfectAnswers.toMutableMap()
        for (i in 0 until 7) {
            answer76[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result76 = VocabularyQuizEngine.evaluateQuiz(questions, answer76, 420)
        assertEquals(76, result76.correctCount)
        assertEquals(4, result76.stars)
        assertEquals(9, result76.earnedMinutes)
        assertTrue(result76.passed)

        // 73/83 -> 3 estrellas, 7 min, aprobado
        val answer73 = perfectAnswers.toMutableMap()
        for (i in 0 until 10) {
            answer73[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result73 = VocabularyQuizEngine.evaluateQuiz(questions, answer73, 400)
        assertEquals(73, result73.correctCount)
        assertEquals(3, result73.stars)
        assertEquals(7, result73.earnedMinutes)
        assertTrue(result73.passed)

        // 70/83 -> 3 estrellas, 5 min, aprobado mínimo (umbral de exigencia 84.3%)
        val answer70 = perfectAnswers.toMutableMap()
        for (i in 0 until 13) {
            answer70[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result70 = VocabularyQuizEngine.evaluateQuiz(questions, answer70, 380)
        assertEquals(70, result70.correctCount)
        assertEquals(3, result70.stars)
        assertEquals(5, result70.earnedMinutes)
        assertTrue(result70.passed)

        // 69/83 -> 0 estrellas, 0 min, NO aprobado (< 70 aciertos)
        val answer69 = perfectAnswers.toMutableMap()
        for (i in 0 until 14) {
            answer69[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result69 = VocabularyQuizEngine.evaluateQuiz(questions, answer69, 350)
        assertEquals(69, result69.correctCount)
        assertEquals(0, result69.stars)
        assertEquals(0, result69.earnedMinutes)
        assertFalse(result69.passed)
    }

    @Test
    fun testBedtimeCurfewCrossingMidnight21to9() {
        val bedtimeSchedule = CurfewSchedule(
            id = "bed_time",
            name = "Hora de Dormir",
            daysOfWeek = setOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                Calendar.SATURDAY
            ),
            startHour = 21,
            startMinute = 0,
            endHour = 9,
            endMinute = 0,
            isEnabled = true
        )

        // 21:00 (Inicio de toque de queda) -> Activo
        val cal2100 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal2100))

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

        // 08:59 (Antes de las 9am) -> Activo
        val cal0859 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 59)
        }
        assertTrue(bedtimeSchedule.isCurfewActive(cal0859))

        // 09:01 (Fin de toque de queda / Día) -> Inactivo
        val cal0901 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 1)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal0901))

        // 14:00 (Tarde) -> Inactivo
        val cal1400 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal1400))

        // 20:59 (Antes de las 21h) -> Inactivo
        val cal2059 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 59)
        }
        assertFalse(bedtimeSchedule.isCurfewActive(cal2059))
    }

    @Test
    fun testSpotifyPackageExemption() {
        assertTrue(DistractionConstants.isSpotifyPackage("com.spotify.music"))
        assertTrue(DistractionConstants.isSpotifyPackage("com.spotify.lite"))
        assertTrue(DistractionConstants.isSpotifyPackage("COM.SPOTIFY.MUSIC"))
        assertFalse(DistractionConstants.isSpotifyPackage("com.zhiliaoapp.musically"))
        assertFalse(DistractionConstants.isSpotifyPackage("com.google.android.youtube"))
        assertFalse(DistractionConstants.isSpotifyPackage(null))
        assertFalse(DistractionConstants.isSpotifyPackage(""))
    }

    @Test
    fun testSelectPlausibleDistractorsPrioritizesCategory() {
        val target = YctWord(chinese = "爸爸", pinyin = "bàba", spanish = "papá", category = "Familia", explanation = "", literalTranslation = "")
        val fam1 = YctWord(chinese = "妈妈", pinyin = "māma", spanish = "mamá", category = "Familia", explanation = "", literalTranslation = "")
        val fam2 = YctWord(chinese = "哥哥", pinyin = "gēge", spanish = "hermano mayor", category = "Familia", explanation = "", literalTranslation = "")
        val num1 = YctWord(chinese = "一", pinyin = "yī", spanish = "uno", category = "Números", explanation = "", literalTranslation = "")
        val num2 = YctWord(chinese = "二", pinyin = "èr", spanish = "dos", category = "Números", explanation = "", literalTranslation = "")

        val pool = listOf(fam1, fam2, num1, num2)
        val selected = VocabularyQuizEngine.selectPlausibleDistractors(target, pool, count = 2)

        assertEquals(2, selected.size)
        // Both distractors should come from the "Familia" category since 2 were available
        assertTrue(selected.all { it.category == "Familia" })
        assertTrue(selected.contains(fam1) && selected.contains(fam2))
    }
}
