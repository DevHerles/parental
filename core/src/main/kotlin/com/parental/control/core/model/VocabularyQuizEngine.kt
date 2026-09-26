package com.parental.control.core.model

import kotlin.random.Random

/**
 * Motor pedagógico de generación y evaluación del Quiz de Vocabulario Oficial YCT 1.
 * Garantiza 15 preguntas únicas, sin repeticiones y con gamificación para niños.
 */
object VocabularyQuizEngine {

    const val QUIZ_QUESTIONS_COUNT = 45
    const val PASSING_CORRECT_THRESHOLD = 27 // 60% de 45 preguntas

    /**
     * Selecciona palabras garantizando que NUNCA se repita ninguna hasta que TODAS las palabras
     * del banco hayan sido evaluadas al menos una vez (Mazo Cíclico Persistente).
     */
    fun selectWordsWithPersistentDeck(
        allWords: List<YctWord>,
        seenKeys: Set<String>,
        count: Int = QUIZ_QUESTIONS_COUNT
    ): Pair<List<YctWord>, Set<String>> {
        if (allWords.isEmpty()) return Pair(emptyList(), emptySet())

        val targetCount = count.coerceAtMost(allWords.size)
        val unseenWords = allWords.filter { it.chinese !in seenKeys }.shuffled()

        return if (unseenWords.size >= targetCount) {
            // Caso A: Hay suficientes palabras no vistas en el ciclo actual
            val selected = unseenWords.take(targetCount)
            val updatedKeys = seenKeys + selected.map { it.chinese }.toSet()
            Pair(selected, updatedKeys)
        } else {
            // Caso B: Quedan menos palabras no vistas que las solicitadas.
            // Se consumen todas las restantes para agotar el banco al 100% y se resetea el mazo.
            val remainingUnseen = unseenWords
            val neededFromNewCycle = targetCount - remainingUnseen.size
            val candidatePoolForNewCycle = (allWords.filter { it !in remainingUnseen }).shuffled()
            val pickedFromNewCycle = candidatePoolForNewCycle.take(neededFromNewCycle)

            val selected = (remainingUnseen + pickedFromNewCycle).shuffled()
            // El nuevo mazo arranca únicamente con las palabras tomadas del nuevo ciclo
            val updatedKeys = pickedFromNewCycle.map { it.chinese }.toSet()
            Pair(selected, updatedKeys)
        }
    }

    /**
     * Genera un reto de 45 preguntas variadas a partir de la lista de palabras, integrando el mazo persistente.
     */
    fun generateQuizWithPersistentDeck(
        allWords: List<YctWord>,
        seenKeys: Set<String>,
        count: Int = QUIZ_QUESTIONS_COUNT
    ): VocabQuizGeneration {
        val (selectedWords, updatedKeys) = selectWordsWithPersistentDeck(allWords, seenKeys, count)
        val questions = buildQuestionsFromWords(selectedWords, allWords)
        val cycleCompleted = allWords.count { it.chinese !in seenKeys } < count
        return VocabQuizGeneration(
            questions = questions,
            updatedSeenKeys = updatedKeys,
            cycleCompleted = cycleCompleted
        )
    }

    /**
     * Genera un reto aleatorio a partir de palabras muestreadas directamente.
     */
    fun generateQuiz(allWords: List<YctWord>, count: Int = QUIZ_QUESTIONS_COUNT): List<VocabQuizQuestion> {
        if (allWords.isEmpty()) return emptyList()
        val actualCount = count.coerceAtMost(allWords.size)
        val sampledWords = allWords.shuffled().take(actualCount)
        return buildQuestionsFromWords(sampledWords, allWords)
    }

    private fun buildQuestionsFromWords(
        targetWords: List<YctWord>,
        allWords: List<YctWord>
    ): List<VocabQuizQuestion> {
        val count = targetWords.size
        val modes = mutableListOf<VocabQuizMode>()
        val hanziCount = (count * 12) / 45
        val esCount = (count * 12) / 45
        val listeningCount = (count * 11) / 45
        val tfCount = count - (hanziCount + esCount + listeningCount)

        repeat(hanziCount) { modes.add(VocabQuizMode.HANZI_TO_ES) }
        repeat(esCount) { modes.add(VocabQuizMode.ES_TO_HANZI) }
        repeat(listeningCount) { modes.add(VocabQuizMode.LISTENING) }
        repeat(tfCount.coerceAtLeast(1)) { modes.add(VocabQuizMode.TRUE_FALSE) }

        val shuffledModes = modes.shuffled()
        val questions = mutableListOf<VocabQuizQuestion>()

        for (i in 0 until count) {
            val word = targetWords[i]
            val mode = shuffledModes.getOrElse(i) { VocabQuizMode.HANZI_TO_ES }
            val otherWords = allWords.filter { it.chinese != word.chinese }

            val question = when (mode) {
                VocabQuizMode.HANZI_TO_ES -> createHanziToEsQuestion(i, word, otherWords)
                VocabQuizMode.ES_TO_HANZI -> createEsToHanziQuestion(i, word, otherWords)
                VocabQuizMode.LISTENING -> createListeningQuestion(i, word, otherWords)
                VocabQuizMode.TRUE_FALSE -> createTrueFalseQuestion(i, word, otherWords)
            }
            questions.add(question)
        }

        return questions
    }

    private fun createHanziToEsQuestion(index: Int, word: YctWord, others: List<YctWord>): VocabQuizQuestion {
        val distractors = others.shuffled().take(3)
        val allOptions = (distractors + word).shuffled()
        val correctIndex = allOptions.indexOfFirst { it.chinese == word.chinese }

        val choices = allOptions.mapIndexed { idx, opt ->
            VocabQuizChoice(
                label = ('A' + idx).toString(),
                primaryText = opt.spanish,
                emoji = YctWord.getEmojiForWord(opt.chinese)
            )
        }

        return VocabQuizQuestion(
            id = "vocab_h2e_${word.chinese}_$index",
            index = index,
            mode = VocabQuizMode.HANZI_TO_ES,
            targetWord = word,
            promptText = "¿Qué significa esta palabra?",
            displayHanzi = word.chinese,
            displayPinyin = word.pinyin,
            displayEmoji = YctWord.getEmojiForWord(word.chinese),
            audioText = word.chinese,
            choices = choices,
            correctChoiceIndex = correctIndex,
            explanation = "${word.chinese} (${word.pinyin}) significa: ${word.spanish}"
        )
    }

    private fun createEsToHanziQuestion(index: Int, word: YctWord, others: List<YctWord>): VocabQuizQuestion {
        val distractors = others.shuffled().take(3)
        val allOptions = (distractors + word).shuffled()
        val correctIndex = allOptions.indexOfFirst { it.chinese == word.chinese }

        val choices = allOptions.mapIndexed { idx, opt ->
            VocabQuizChoice(
                label = ('A' + idx).toString(),
                primaryText = opt.chinese,
                secondaryText = opt.pinyin,
                emoji = YctWord.getEmojiForWord(opt.chinese)
            )
        }

        return VocabQuizQuestion(
            id = "vocab_e2h_${word.chinese}_$index",
            index = index,
            mode = VocabQuizMode.ES_TO_HANZI,
            targetWord = word,
            promptText = "¿Cómo se dice en chino?",
            displayHanzi = null,
            displayPinyin = null,
            displayEmoji = YctWord.getEmojiForWord(word.chinese),
            audioText = word.chinese,
            choices = choices,
            correctChoiceIndex = correctIndex,
            explanation = "${word.spanish} en chino se dice: ${word.chinese} (${word.pinyin})"
        )
    }

    private fun createListeningQuestion(index: Int, word: YctWord, others: List<YctWord>): VocabQuizQuestion {
        val distractors = others.shuffled().take(3)
        val allOptions = (distractors + word).shuffled()
        val correctIndex = allOptions.indexOfFirst { it.chinese == word.chinese }

        val choices = allOptions.mapIndexed { idx, opt ->
            VocabQuizChoice(
                label = ('A' + idx).toString(),
                primaryText = opt.spanish,
                emoji = YctWord.getEmojiForWord(opt.chinese)
            )
        }

        return VocabQuizQuestion(
            id = "vocab_list_${word.chinese}_$index",
            index = index,
            mode = VocabQuizMode.LISTENING,
            targetWord = word,
            promptText = "Escucha con atención 🎧 ¿Qué palabra es?",
            displayHanzi = null, // Oculto para entrenar el oído
            displayPinyin = null,
            displayEmoji = "🎧",
            audioText = word.chinese,
            choices = choices,
            correctChoiceIndex = correctIndex,
            explanation = "Escuchaste: ${word.chinese} (${word.pinyin}) = ${word.spanish}"
        )
    }

    private fun createTrueFalseQuestion(index: Int, word: YctWord, others: List<YctWord>): VocabQuizQuestion {
        val isTrue = Random.nextBoolean()
        val shownPairWord = if (isTrue || others.isEmpty()) word else others.random()

        val choices = listOf(
            VocabQuizChoice(label = "√", primaryText = "¡Es Correcto!", emoji = "✅"),
            VocabQuizChoice(label = "×", primaryText = "¡Es Incorrecto!", emoji = "❌")
        )
        val correctIndex = if (isTrue) 0 else 1

        val explanation = if (isTrue) {
            "¡Correcto! ${word.chinese} (${word.pinyin}) sí significa '${word.spanish}'."
        } else {
            "¡Exacto! Era incorrecto. ${word.chinese} (${word.pinyin}) significa '${word.spanish}', no '${shownPairWord.spanish}'."
        }

        return VocabQuizQuestion(
            id = "vocab_tf_${word.chinese}_$index",
            index = index,
            mode = VocabQuizMode.TRUE_FALSE,
            targetWord = word,
            promptText = "¿Es correcta esta traducción?",
            displayHanzi = "${word.chinese} = ${shownPairWord.spanish}",
            displayPinyin = word.pinyin,
            displayEmoji = YctWord.getEmojiForWord(word.chinese),
            audioText = word.chinese,
            choices = choices,
            correctChoiceIndex = correctIndex,
            explanation = explanation
        )
    }

    /**
     * Evalúa las respuestas del usuario y calcula estrellas y minutos ganados.
     */
    fun evaluateQuiz(
        questions: List<VocabQuizQuestion>,
        userAnswers: Map<Int, Int>, // questionIndex -> selectedChoiceIndex
        durationSeconds: Int
    ): VocabQuizResult {
        var correctCount = 0
        questions.forEachIndexed { idx, q ->
            val selected = userAnswers[idx]
            if (selected != null && selected == q.correctChoiceIndex) {
                correctCount++
            }
        }

        val total = questions.size.coerceAtLeast(1)
        val percentage = (correctCount.toDouble() / total.toDouble()) * 100.0
        val passed = correctCount >= PASSING_CORRECT_THRESHOLD

        // Calificación de estrellas (0 a 5, requiere aprobar con >= 27 aciertos)
        val stars = when {
            !passed -> 0
            correctCount >= 42 -> 5
            correctCount >= 36 -> 4
            correctCount >= 30 -> 3
            else -> 2
        }

        // Recompensa proporcional hasta 15 minutos (mínimo 27 aciertos para ganar)
        val earnedMinutes = when {
            correctCount >= 45 -> 15
            correctCount >= 42 -> 14
            correctCount >= 39 -> 13
            correctCount >= 36 -> 12
            correctCount >= 33 -> 11
            correctCount >= 30 -> 10
            correctCount >= 27 -> 9
            else -> 0
        }

        return VocabQuizResult(
            totalQuestions = total,
            correctCount = correctCount,
            scorePercentage = percentage,
            stars = stars,
            earnedMinutes = earnedMinutes,
            passed = passed,
            durationSeconds = durationSeconds
        )
    }
}
