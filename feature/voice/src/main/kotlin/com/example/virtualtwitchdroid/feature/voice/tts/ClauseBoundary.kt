package com.example.virtualtwitchdroid.feature.voice.tts

/**
 * Where a Japanese sentence may be split for chunked synthesis when the recogniser gave no punctuation:
 * after an accent phrase that already ends in a pause, or after one ending in a clause-final particle
 * (が・けど・けれど・ので・から・し・て・で) — the places a reader would put a 、. A chunk cut there gets a short
 * synthetic comma pause ([SYNTHETIC_PAUSE_S]) so the two vocoded pieces meet in silence, not mid-phone.
 * Pure, unit-tested.
 */
object ClauseBoundary {
    /** Length of the pause inserted at a boundary that had none (a spoken 、 is ~0.1–0.3 s). */
    const val SYNTHETIC_PAUSE_S = 0.18f

    private val clauseFinal =
        setOf("ガ", "ケド", "ケレド", "ノデ", "カラ", "シ", "テ", "デ", "が", "けど", "けれど", "ので", "から", "し", "て", "で")

    /**
     * True when a phrase whose moras read [moraTexts] (kana, in order) may end a chunk: it has a pause already
     * ([hasPause]) or ends in a clause-final particle. Single-mora phrases never split (too short to be a clause).
     */
    fun isSplittable(moraTexts: List<String>, hasPause: Boolean): Boolean {
        if (hasPause) return true
        if (moraTexts.size < 2) return false
        val last = moraTexts.last()
        val lastTwo = moraTexts.takeLast(2).joinToString("")
        val lastThree = moraTexts.takeLast(3).joinToString("")
        return last in clauseFinal || lastTwo in clauseFinal || lastThree in clauseFinal
    }
}
