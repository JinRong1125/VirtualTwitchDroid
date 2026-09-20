package com.example.virtualtwitchdroid.feature.voice.text

/**
 * The only text stage between recognition and VOICEVOX (no translation — Japanese in, Japanese out):
 * - drops segments too short to be speech (VAD false triggers) → null;
 * - collapses whitespace and repeated fillers;
 * - replaces well-known Latin tokens with katakana (Open JTalk reads unknown Latin words letter by letter,
 *   and VOICEVOX CORE 0.17.0 has no English→kana yet); other Latin tokens are left for Open JTalk to spell;
 * - fixes the stream's proper nouns the Zipformer hears as homophones (「詰んだもん」→「ずんだもん」): the model
 *   has no lexicon, and VOICEVOX would read the wrong kanji aloud (measured on the Pixel 8a bench);
 * - appends 「。」when the segment has no terminal punctuation (the Zipformer emits none), so VOICEVOX
 *   inserts its sentence-final pause and the lips close where the streamer paused.
 */
object TextNormaliser {
    /** Recognised segments shorter than this (after trimming) are ignored. */
    const val MIN_CHARS = 2

    private val latinToKatakana = mapOf(
        "youtube" to "ユーチューブ", "twitch" to "ツイッチ", "google" to "グーグル", "android" to "アンドロイド",
        "iphone" to "アイフォン", "vtuber" to "ブイチューバー", "ok" to "オーケー", "tv" to "テレビ",
        "ai" to "エーアイ", "pc" to "パソコン", "sns" to "エスエヌエス", "line" to "ライン", "wifi" to "ワイファイ",
        "app" to "アプリ", "game" to "ゲーム", "live" to "ライブ",
    )

    // The recogniser has no lexicon, so it renders the stream's constant name ずんだもん as 〈kanji〉んだ + a
    // もん-sound tail — observed on the Pixel 8a as もん, モン, 門 and 紋. This is best-effort, not a fix: only
    // these tails are rewritten (モン/門/紋 after んだ are never real words; a real もん-homophone kanji like 文
    // or 問 is left alone), so a spelling we have not seen still slips through, and a genuine colloquial
    // 「〜んだもん」 ("…なんだもん") is rewritten too — acceptable because the name dominates a Zundamon stream, but
    // flip this off for general dictation. VOICEVOX would otherwise read the wrong kanji aloud. The real fix is
    // a recogniser with a lexicon (a model/library change, out of scope here).
    private val kanji = "\\u4e00-\\u9fff々"
    private val homophones = listOf(
        Regex("""(?:ずん|[$kanji]ん)だ(?:もん|モン|門|紋)""") to "ずんだもん",
        Regex("""四国[めメ目][たタ][んン]""") to "四国めたん",
    )
    private val whitespace = Regex("""[\s　]+""")
    private val latinToken = Regex("""[A-Za-z][A-Za-z0-9]*""")
    private val fillerRepeat = Regex("""(あの|えっと|えー|んー|その)(?:\1)+""")
    private val terminal = setOf('。', '！', '？', '!', '?', '．', '…', '、')

    fun normalise(raw: String): String? {
        var text = raw.replace(whitespace, "").trim()
        if (text.length < MIN_CHARS) return null
        text = fillerRepeat.replace(text) { it.groupValues[1] }
        text = latinToken.replace(text) { m -> latinToKatakana[m.value.lowercase()] ?: m.value }
        for ((pattern, reading) in homophones) text = pattern.replace(text, reading)
        if (text.last() !in terminal) text += "。"
        return text
    }
}
