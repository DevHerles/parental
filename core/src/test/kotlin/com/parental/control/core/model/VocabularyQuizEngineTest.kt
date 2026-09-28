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
    fun testEvaluationGradingScaleUpTo30Minutes() {
        val questions = VocabularyQuizEngine.generateQuiz(fullBank, 83)
        val perfectAnswers = questions.indices.associateWith { questions[it].correctChoiceIndex }

        // 83/83 -> 5 estrellas, 30 min, aprobado (100% Flawless Grand Prize)
        val perfectResult = VocabularyQuizEngine.evaluateQuiz(questions, perfectAnswers, 500)
        assertEquals(83, perfectResult.correctCount)
        assertEquals(5, perfectResult.stars)
        assertEquals(30, perfectResult.earnedMinutes)
        assertTrue(perfectResult.passed)

        // 80/83 -> 5 estrellas, 25 min, aprobado (Sobresaliente)
        val answer80 = perfectAnswers.toMutableMap()
        for (i in 0 until 3) {
            answer80[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result80 = VocabularyQuizEngine.evaluateQuiz(questions, answer80, 480)
        assertEquals(80, result80.correctCount)
        assertEquals(5, result80.stars)
        assertEquals(25, result80.earnedMinutes)
        assertTrue(result80.passed)

        // 72/83 -> 4 estrellas, 20 min, aprobado (Notable)
        val answer72 = perfectAnswers.toMutableMap()
        for (i in 0 until 11) {
            answer72[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result72 = VocabularyQuizEngine.evaluateQuiz(questions, answer72, 450)
        assertEquals(72, result72.correctCount)
        assertEquals(4, result72.stars)
        assertEquals(20, result72.earnedMinutes)
        assertTrue(result72.passed)

        // 65/83 -> 4 estrellas, 15 min, aprobado (Buen desempeño)
        val answer65 = perfectAnswers.toMutableMap()
        for (i in 0 until 18) {
            answer65[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result65 = VocabularyQuizEngine.evaluateQuiz(questions, answer65, 420)
        assertEquals(65, result65.correctCount)
        assertEquals(4, result65.stars)
        assertEquals(15, result65.earnedMinutes)
        assertTrue(result65.passed)

        // 55/83 -> 3 estrellas, 10 min, aprobado (Piso Digno)
        val answer55 = perfectAnswers.toMutableMap()
        for (i in 0 until 28) {
            answer55[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result55 = VocabularyQuizEngine.evaluateQuiz(questions, answer55, 400)
        assertEquals(55, result55.correctCount)
        assertEquals(3, result55.stars)
        assertEquals(10, result55.earnedMinutes)
        assertTrue(result55.passed)

        // 50/83 -> 3 estrellas, 10 min, aprobado mínimo (umbral estándar internacional 60.2%)
        val answer50 = perfectAnswers.toMutableMap()
        for (i in 0 until 33) {
            answer50[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result50 = VocabularyQuizEngine.evaluateQuiz(questions, answer50, 380)
        assertEquals(50, result50.correctCount)
        assertEquals(3, result50.stars)
        assertEquals(10, result50.earnedMinutes)
        assertTrue(result50.passed)

        // 49/83 -> 0 estrellas, 0 min, NO aprobado (< 50 aciertos)
        val answer49 = perfectAnswers.toMutableMap()
        for (i in 0 until 34) {
            answer49[i] = (questions[i].correctChoiceIndex + 1) % questions[i].choices.size
        }
        val result49 = VocabularyQuizEngine.evaluateQuiz(questions, answer49, 350)
        assertEquals(49, result49.correctCount)
        assertEquals(0, result49.stars)
        assertEquals(0, result49.earnedMinutes)
        assertFalse(result49.passed)
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
