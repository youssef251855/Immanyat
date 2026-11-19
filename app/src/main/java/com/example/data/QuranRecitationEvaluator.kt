package com.example.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class WordDiffStatus {
    CORRECT,
    MISSED,
    WRONG
}

data class RecitationWordDiff(
    val word: String,
    val status: WordDiffStatus
)

data class OfflineEvaluationResult(
    val score: Double,
    val matchedRatio: Double,
    val status: String,
    val heardTranscript: String,
    val mistakeWords: List<String>,
    val wordDiffs: List<RecitationWordDiff>,
    val feedback: String,
    val isMismatch: Boolean = false
)

/**
 * Offline Quranic Arabic normalization & LCS alignment evaluator against the bundled `quran.json` text.
 * Computes match accuracy, score out of 10 rounded to 1 decimal place, and word-level diffs/mistakes.
 */
object QuranRecitationEvaluator {

    private data class WordToken(
        val raw: String,
        val normalized: String
    )

    private val DIACRITICS_AND_TATWEEL_REGEX =
        Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\uFEFF]")
    private val AYAH_MARKS_AND_DIGITS_REGEX =
        Regex("[0-9\\u0660-\\u0669\\u06DD\\uFD3E\\uFD3F]")
    private val NON_ARABIC_LETTERS_REGEX =
        Regex("[^\\u0621-\\u064A\\s]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val UTHMANI_MID_ALEF_REGEX =
        Regex("([بتثجحخدذرزسشصضطظعغفقكلمنهوي])ا([بتثجحخدذرزسشصضطظعغفقكلمنهوي])$")

    private val ISTIADHA_TOKENS = listOf("اعوذ", "بالله", "من", "الشيطان", "الرجيم")
    private val BASMALAH_TOKENS = listOf("بسم", "الله", "الرحمن", "الرحيم")

    fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(DIACRITICS_AND_TATWEEL_REGEX, "")
            .replace(AYAH_MARKS_AND_DIGITS_REGEX, " ")
            .replace('\u0622', 'ا') // آ -> ا
            .replace('\u0623', 'ا') // أ -> ا
            .replace('\u0625', 'ا') // إ -> ا
            .replace('\u0671', 'ا') // ٱ -> ا
            .replace('\u0649', 'ي') // ى -> ي
            .replace('\u0629', 'ه') // ة -> ه
            .replace('\u0624', 'و') // ؤ -> و
            .replace('\u0626', 'ي') // ئ -> ي
            .replace(NON_ARABIC_LETTERS_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun tokenizeRawWithNormalized(text: String): List<WordToken> {
        val rawWords = text
            .replace("\uFEFF", "")
            .split(WHITESPACE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = ArrayList<WordToken>(rawWords.size)
        for (raw in rawWords) {
            val norm = normalizeArabic(raw)
            if (norm.isNotEmpty()) {
                // In case punctuation split one token into multiple Arabic words
                val subTokens = norm.split(' ')
                if (subTokens.size == 1) {
                    result.add(WordToken(raw = raw, normalized = norm))
                } else {
                    for (sub in subTokens) {
                        if (sub.isNotEmpty()) result.add(WordToken(raw = sub, normalized = sub))
                    }
                }
            }
        }
        return result
    }

    private fun looseOrthographicKey(word: String): String {
        if (word.length <= 3) return word
        return word.replace(UTHMANI_MID_ALEF_REGEX, "$1$2")
    }

    private fun tokensEqual(a: String, b: String): Boolean {
        if (a == b) return true
        if (looseOrthographicKey(a) == looseOrthographicKey(b)) return true
        // Single-character tolerance for >= 5 char words (minor phonetic/Uthmani ASR variance)
        if (a.length >= 5 && b.length >= 5 && abs(a.length - b.length) <= 1) {
            var diffs = 0
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) {
                    i++
                    j++
                } else {
                    diffs++
                    if (diffs > 1) return false
                    when {
                        a.length > b.length -> i++
                        b.length > a.length -> j++
                        else -> {
                            i++
                            j++
                        }
                    }
                }
            }
            diffs += (a.length - i) + (b.length - j)
            return diffs <= 1
        }
        return false
    }

    private fun stripLeadingPhrase(
        tokens: List<WordToken>,
        phrase: List<String>
    ): Pair<List<WordToken>, Boolean> {
        if (tokens.size < phrase.size) return tokens to false
        for (i in phrase.indices) {
            if (!tokensEqual(tokens[i].normalized, phrase[i])) {
                return tokens to false
            }
        }
        return tokens.drop(phrase.size) to true
    }

    private fun findBestAnchorWindow(
        userTokens: List<WordToken>,
        expectedTokens: List<WordToken>
    ): IntRange {
        if (expectedTokens.size <= 180) {
            return expectedTokens.indices
        }

        val wordPositions = HashMap<String, MutableList<Int>>()
        for (j in expectedTokens.indices) {
            val key = looseOrthographicKey(expectedTokens[j].normalized)
            wordPositions.getOrPut(key) { mutableListOf() }.add(j)
        }

        val anchorHits = mutableListOf<Int>()
        for (i in 0 until userTokens.size - 1) {
            val positions = wordPositions[looseOrthographicKey(userTokens[i].normalized)] ?: continue
            for (pos in positions) {
                for (gap in 1..2) {
                    if (pos + gap < expectedTokens.size &&
                        tokensEqual(userTokens[i + 1].normalized, expectedTokens[pos + gap].normalized)
                    ) {
                        anchorHits.add(pos)
                        break
                    }
                }
            }
        }

        if (anchorHits.isEmpty()) {
            return 0 until min(expectedTokens.size, 220)
        }

        anchorHits.sort()
        val targetWindowLen = max(80, min(240, userTokens.size * 3))
        var bestCount = 0
        var bestStart = anchorHits[0]
        var right = 0
        for (left in anchorHits.indices) {
            while (right < anchorHits.size && anchorHits[right] - anchorHits[left] <= targetWindowLen) {
                right++
            }
            val count = right - left
            if (count > bestCount) {
                bestCount = count
                bestStart = anchorHits[left]
            }
        }

        val start = max(0, bestStart - 10)
        val end = min(expectedTokens.size, bestStart + targetWindowLen + 15)
        return start until end
    }

    private data class LcsAlignment(
        val lcsCount: Int,
        val matchedExpectedIndices: List<Int>,
        val matchedUserIndices: List<Int>
    )

    private fun lcsAlign(
        userTokens: List<WordToken>,
        windowTokens: List<WordToken>
    ): LcsAlignment {
        val n = userTokens.size
        val m = windowTokens.size
        if (n == 0 || m == 0) {
            return LcsAlignment(0, emptyList(), emptyList())
        }

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (tokensEqual(userTokens[i - 1].normalized, windowTokens[j - 1].normalized)) {
                    dp[i - 1][j - 1] + 1
                } else {
                    max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val matchedUser = mutableListOf<Int>()
        val matchedExpected = mutableListOf<Int>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            if (tokensEqual(userTokens[i - 1].normalized, windowTokens[j - 1].normalized)) {
                matchedUser.add(i - 1)
                matchedExpected.add(j - 1)
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        matchedUser.reverse()
        matchedExpected.reverse()

        return LcsAlignment(
            lcsCount = dp[n][m],
            matchedExpectedIndices = matchedExpected,
            matchedUserIndices = matchedUser
        )
    }

    fun roundToOneDecimal(value: Double): Double {
        val clamped = value.coerceIn(0.0, 10.0)
        return (clamped * 10.0).roundToInt() / 10.0
    }

    fun evaluate(
        surahName: String,
        originalReferenceText: String,
        transcribedText: String
    ): OfflineEvaluationResult {
        val expectedTokens = tokenizeRawWithNormalized(originalReferenceText)
        val rawUserTokens = tokenizeRawWithNormalized(transcribedText)

        if (rawUserTokens.isEmpty()) {
            return OfflineEvaluationResult(
                score = 0.0,
                matchedRatio = 0.0,
                status = "لم يُرصد كلام مسموع ⚠️",
                heardTranscript = transcribedText.trim(),
                mistakeWords = emptyList(),
                wordDiffs = emptyList(),
                feedback = "لم يتمكن المحرك المحلي من تمييز تلاوة واضحة. يُرجى الاقتراب من الميكروفون والقراءة بصوت واضح.",
                isMismatch = true
            )
        }

        val (afterIstiadha, strippedIstiadha) = stripLeadingPhrase(rawUserTokens, ISTIADHA_TOKENS)
        var userTokens = afterIstiadha

        val isFatiha = normalizeArabic(surahName).contains("فاتحه")
        var strippedBasmalah = false
        if (!isFatiha && expectedTokens.size > 8) {
            val (afterBasmalah, hadBasmalah) = stripLeadingPhrase(userTokens, BASMALAH_TOKENS)
            if (hadBasmalah) {
                strippedBasmalah = true
                userTokens = afterBasmalah
            }
        }

        if (userTokens.isEmpty() && (strippedBasmalah || strippedIstiadha)) {
            return OfflineEvaluationResult(
                score = 0.5,
                matchedRatio = 0.0,
                status = "البسملة فقط دون آيات السورة ⚠️",
                heardTranscript = transcribedText.trim(),
                mistakeWords = expectedTokens.take(5).map { it.raw },
                wordDiffs = expectedTokens.take(6).map { RecitationWordDiff(it.raw, WordDiffStatus.MISSED) },
                feedback = "تم رصد الاستعاذة أو البسملة فقط دون البدء بآيات سورة $surahName. يُرجى تلاوة آيات السورة للتقييم الدقيق.",
                isMismatch = true
            )
        }

        if (userTokens.size < 2 && expectedTokens.size >= 4) {
            return OfflineEvaluationResult(
                score = 0.0,
                matchedRatio = 0.0,
                status = "تسميع قصير جداً ⚠️",
                heardTranscript = transcribedText.trim(),
                mistakeWords = userTokens.map { it.raw },
                wordDiffs = userTokens.map { RecitationWordDiff(it.raw, WordDiffStatus.WRONG) },
                feedback = "التلاوة المسموعة قصيرة جداً مقارنة بسورة $surahName. يُرجى تلاوة آية كاملة على الأقل.",
                isMismatch = true
            )
        }

        val windowRange = findBestAnchorWindow(userTokens, expectedTokens)
        val windowTokens = expectedTokens.slice(windowRange)
        val alignment = lcsAlign(userTokens, windowTokens)

        var bigramHits = 0
        for (k in 0 until alignment.matchedExpectedIndices.size - 1) {
            val expGap = alignment.matchedExpectedIndices[k + 1] - alignment.matchedExpectedIndices[k]
            val usrGap = alignment.matchedUserIndices[k + 1] - alignment.matchedUserIndices[k]
            if (expGap in 1..2 && usrGap in 1..2) {
                bigramHits++
            }
        }

        val orderPrecision = if (userTokens.isNotEmpty()) {
            alignment.lcsCount.toDouble() / userTokens.size
        } else 0.0
        val bigramPrecision = if (userTokens.size > 1) {
            bigramHits.toDouble() / (userTokens.size - 1)
        } else 0.0

        val matchedExpectedSet = alignment.matchedExpectedIndices.toSet()
        val matchedUserSet = alignment.matchedUserIndices.toSet()

        val spanRecall: Double
        val evalStartIdx: Int
        val evalEndIdx: Int
        if (alignment.matchedExpectedIndices.isNotEmpty()) {
            val firstExp = alignment.matchedExpectedIndices.first()
            val lastExp = alignment.matchedExpectedIndices.last()
            val spanLen = max(1, lastExp - firstExp + 1)
            spanRecall = alignment.lcsCount.toDouble() / spanLen

            // For short Surahs when user covers most of the Surah, show missed words up to the end
            if (windowTokens.size <= 100 && alignment.lcsCount.toDouble() / windowTokens.size >= 0.45) {
                evalStartIdx = 0
                evalEndIdx = windowTokens.lastIndex
            } else {
                evalStartIdx = firstExp
                evalEndIdx = lastExp
            }
        } else {
            spanRecall = 0.0
            evalStartIdx = 0
            evalEndIdx = min(windowTokens.lastIndex, 15)
        }

        val surahCoverage = if (expectedTokens.isNotEmpty()) {
            alignment.lcsCount.toDouble() / expectedTokens.size
        } else 0.0

        val isLikelyTargetSurah = if (userTokens.size <= 3) {
            orderPrecision >= 0.66 && (bigramHits >= 1 || alignment.lcsCount == userTokens.size)
        } else {
            orderPrecision >= 0.42 &&
                (bigramPrecision >= 0.18 || (alignment.lcsCount >= 4 && spanRecall >= 0.45))
        }

        if (!isLikelyTargetSurah) {
            val wrongSpoken = userTokens.map { it.raw }.take(10)
            return OfflineEvaluationResult(
                score = 0.0,
                matchedRatio = orderPrecision,
                status = "الكلام لا يطابق السورة ❌",
                heardTranscript = transcribedText.trim(),
                mistakeWords = wrongSpoken,
                wordDiffs = wrongSpoken.map { RecitationWordDiff(it, WordDiffStatus.WRONG) },
                feedback = "ما سُمع لا يطابق آيات سورة $surahName (نسبة التطابق ${(orderPrecision * 100).roundToInt()}%). يُرجى تلاوة آيات السورة بوضوح.",
                isMismatch = true
            )
        }

        // Compute match ratio and 1-decimal score out of 10
        val effectiveMatchRatio = if (expectedTokens.size <= 100) {
            val fullPrecision = orderPrecision
            val recallVsExpected = surahCoverage
            (0.45 * fullPrecision) + (0.35 * recallVsExpected) + (0.20 * bigramPrecision)
        } else {
            (0.50 * orderPrecision) + (0.30 * spanRecall) + (0.20 * bigramPrecision)
        }.coerceIn(0.0, 1.0)

        val score = roundToOneDecimal(effectiveMatchRatio * 10.0)

        // Build word diffs + mistaken/missed word list
        val missedWords = mutableListOf<String>()
        val wordDiffs = mutableListOf<RecitationWordDiff>()
        if (evalStartIdx <= evalEndIdx && windowTokens.isNotEmpty()) {
            for (idx in evalStartIdx..evalEndIdx) {
                val token = windowTokens[idx]
                if (matchedExpectedSet.contains(idx)) {
                    wordDiffs.add(RecitationWordDiff(token.raw, WordDiffStatus.CORRECT))
                } else {
                    missedWords.add(token.raw)
                    wordDiffs.add(RecitationWordDiff(token.raw, WordDiffStatus.MISSED))
                }
            }
        }

        val wrongExtraWords = userTokens
            .filterIndexed { index, _ -> !matchedUserSet.contains(index) }
            .map { it.raw }
            .distinct()

        val combinedMistakes = buildList {
            missedWords.take(12).forEach { add("ناقص: $it") }
            wrongExtraWords.take(8).forEach { add("تبديل/زيادة: $it") }
        }

        // Append extra/wrong words as WRONG chips at the end if any weren't matched
        val fullDiffsWithExtras = if (wrongExtraWords.isNotEmpty()) {
            wordDiffs + wrongExtraWords.take(6).map { RecitationWordDiff(it, WordDiffStatus.WRONG) }
        } else {
            wordDiffs
        }

        val statusLabel = when {
            score >= 9.0 -> "حفظ متقن ممتاز ✨"
            score >= 7.5 -> "حفظ جيد جداً 🌟"
            score >= 5.0 -> if (surahCoverage < 0.7 && orderPrecision >= 0.75) {
                "تسميع جزئي متقن ✨"
            } else {
                "حفظ جيد يحتاج تثبيت 📖"
            }
            else -> "يحتاج لمراجعة وتثبيت ⚠️"
        }

        val feedback = buildString {
            append("تم التحقق محلياً (بدون إنترنت) من تلاوتك لسورة $surahName: ")
            append("طابقت ${alignment.lcsCount} كلمة من أصل ${expectedTokens.size} في السورة.")
            if (missedWords.isEmpty() && wrongExtraWords.isEmpty()) {
                append(" أحسنت، لم تُرصد كلمات منسية أو مبدلة.")
            } else {
                if (missedWords.isNotEmpty()) {
                    append("\n• كلمات لم تُرصد أو نُسيت: ${missedWords.take(6).joinToString("، ")}")
                }
                if (wrongExtraWords.isNotEmpty()) {
                    append("\n• كلمات مبدلة أو زائدة: ${wrongExtraWords.take(5).joinToString("، ")}")
                }
            }
        }

        return OfflineEvaluationResult(
            score = score,
            matchedRatio = effectiveMatchRatio,
            status = statusLabel,
            heardTranscript = transcribedText.trim(),
            mistakeWords = combinedMistakes,
            wordDiffs = fullDiffsWithExtras,
            feedback = feedback,
            isMismatch = false
        )
    }
}
