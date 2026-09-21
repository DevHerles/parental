package com.parental.control.core.model

import org.json.JSONArray
import org.json.JSONObject

data class YctChoice(
    val label: String,
    val image: String = "",
    val text: String = "",
    val pinyin: String = "",
    val es: String = ""
)

data class YctQuestion(
    val id: String,
    val section: String,
    val sectionName: String,
    val instruction: String,
    val audioText: String,
    val hanzi: String,
    val pinyin: String,
    val translationEs: String,
    val explanation: String,
    val correctAnswer: String,
    val image: String,
    val options: List<String>,
    val choices: List<YctChoice>
) {
    val isListening: Boolean
        get() = section.startsWith("listening")

    companion object {
        fun fromJsonObject(obj: JSONObject): YctQuestion {
            val opts = mutableListOf<String>()
            val optArr = obj.optJSONArray("options")
            if (optArr != null) {
                for (i in 0 until optArr.length()) {
                    opts.add(optArr.optString(i))
                }
            }

            val chs = mutableListOf<YctChoice>()
            val chArr = obj.optJSONArray("choices") ?: obj.optJSONArray("choices_text")
            if (chArr != null) {
                for (i in 0 until chArr.length()) {
                    val cObj = chArr.optJSONObject(i)
                    if (cObj != null) {
                        chs.add(
                            YctChoice(
                                label = cObj.optString("label", ('A' + i).toString()),
                                image = cObj.optString("image", ""),
                                text = cObj.optString("text", ""),
                                pinyin = cObj.optString("pinyin", ""),
                                es = cObj.optString("es", "")
                            )
                        )
                    }
                }
            }

            return YctQuestion(
                id = obj.optString("id"),
                section = obj.optString("section"),
                sectionName = obj.optString("section_name"),
                instruction = obj.optString("instruction"),
                audioText = obj.optString("audio_text"),
                hanzi = obj.optString("hanzi"),
                pinyin = obj.optString("pinyin"),
                translationEs = obj.optString("translation_es"),
                explanation = obj.optString("explanation"),
                correctAnswer = obj.optString("correct_answer"),
                image = obj.optString("image"),
                options = opts,
                choices = chs
            )
        }
    }
}

data class YctWord(
    val chinese: String,
    val pinyin: String,
    val spanish: String,
    val category: String,
    val explanation: String,
    val literalTranslation: String,
    val emoji: String = ""
) {
    companion object {
        fun fromJsonObject(obj: JSONObject): YctWord {
            val cn = obj.optString("chinese")
            return YctWord(
                chinese = cn,
                pinyin = obj.optString("pinyin"),
                spanish = obj.optString("spanish"),
                category = obj.optString("category"),
                explanation = obj.optString("explanation"),
                literalTranslation = obj.optString("literal_translation"),
                emoji = getEmojiForWord(cn)
            )
        }

        fun getEmojiForWord(chinese: String): String {
            return when {
                chinese.contains("家") -> "🏠"
                chinese.contains("学校") -> "🏫"
                chinese.contains("商店") -> "🏪"
                chinese.contains("中国人") -> "🇨🇳"
                chinese.contains("爸爸") -> "👨"
                chinese.contains("妈妈") -> "👩"
                chinese.contains("哥哥") -> "👦"
                chinese.contains("姐姐") -> "👧"
                chinese.contains("老师") -> "👩‍🏫"
                chinese.contains("手") -> "✋"
                chinese.contains("口") -> "👄"
                chinese.contains("眼") -> "👀"
                chinese.contains("发") -> "💇"
                chinese.contains("耳") -> "👂"
                chinese.contains("鼻") -> "👃"
                chinese.contains("个子") -> "📏"
                chinese.contains("猫") -> "🐱"
                chinese.contains("狗") -> "🐶"
                chinese.contains("鸟") -> "🐦"
                chinese.contains("鱼") -> "🐟"
                chinese.contains("水") -> "💧"
                chinese.contains("奶") -> "🥛"
                chinese.contains("米饭") -> "🍚"
                chinese.contains("面条") -> "🍜"
                chinese.contains("苹果") -> "🍎"
                chinese.contains("今天") -> "📅"
                chinese.contains("明天") -> "🌅"
                chinese.contains("现在") -> "⏰"
                chinese.contains("月") -> "🌙"
                chinese.contains("号") -> "🔢"
                chinese.contains("星期") -> "🗓️"
                chinese.contains("点") -> "🕐"
                chinese.contains("谢谢") -> "🙏"
                chinese.contains("再见") -> "👋"
                chinese.contains("看") -> "👁️"
                chinese.contains("吃") -> "🍽️"
                chinese.contains("喝") -> "🥤"
                chinese.contains("去") -> "🚶"
                chinese.contains("爱") -> "❤️"
                chinese.contains("喜欢") -> "👍"
                chinese.contains("好") -> "✨"
                chinese.contains("大") -> "🐘"
                chinese.contains("小") -> "🐭"
                chinese.contains("长") -> "📏"
                chinese.contains("高") -> "🦒"
                chinese.contains("高兴") -> "😊"
                chinese.contains("我") -> "🙋"
                chinese.contains("你") -> "👉"
                chinese.contains("他") || chinese.contains("她") -> "👤"
                chinese.contains("一") -> "1️⃣"
                chinese.contains("二") -> "2️⃣"
                chinese.contains("三") -> "3️⃣"
                chinese.contains("四") -> "4️⃣"
                chinese.contains("五") -> "5️⃣"
                chinese.contains("六") -> "6️⃣"
                chinese.contains("七") -> "7️⃣"
                chinese.contains("八") -> "8️⃣"
                chinese.contains("九") -> "9️⃣"
                chinese.contains("十") -> "🔟"
                chinese.contains("岁") -> "🎂"
                else -> "🀄"
            }
        }
    }
}

data class YctExamResult(
    val listeningScore: Int,
    val readingScore: Int,
    val totalScore: Int,
    val listeningCorrect: Int,
    val readingCorrect: Int,
    val totalCorrect: Int,
    val totalQuestions: Int = 35,
    val scorePercentage: Double,
    val passed: Boolean,
    val earnedMinutes: Int,
    val durationSeconds: Int
)
