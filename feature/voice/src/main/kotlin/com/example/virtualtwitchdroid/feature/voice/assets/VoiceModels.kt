package com.example.virtualtwitchdroid.feature.voice.assets

/**
 * The model files the voice needs, pinned by name, size and sha256 (checked before use). They are bundled in
 * the APK under `assets/voice/` and `ModelStore` copies them into the app's internal `filesDir/voice/` on first
 * launch. The [url] of each is its official source, for provenance and attribution (see LICENSES.md);
 * `scripts/voice-models.sh` can also push them to a device during development.
 */
data class ModelFile(
    val name: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val unpackDir: String? = null,
)

object VoiceModels {
    const val DIR = "voice"

    /** ReazonSpeech k2 v2 Zipformer, int8 (Apache-2.0) — 4 loose files from Hugging Face. */
    private const val REAZON = "https://huggingface.co/reazon-research/reazonspeech-k2-v2/resolve/main/"
    val ASR_ENCODER = ModelFile(
        "encoder-epoch-99-avg-1.int8.onnx",
        REAZON + "encoder-epoch-99-avg-1.int8.onnx",
        154_670_139,
        "2c7bd08a8a99f9ddd0d9e458456577b1f6279214e51426f114f9eced44c54e1d",
    )
    val ASR_DECODER = ModelFile(
        "decoder-epoch-99-avg-1.int8.onnx",
        REAZON + "decoder-epoch-99-avg-1.int8.onnx",
        2_959_337,
        "8f0bff94d38797b03b762634ed03211a8e303d06cc4603cdd0cf4199d6eb1485",
    )
    val ASR_JOINER = ModelFile(
        "joiner-epoch-99-avg-1.int8.onnx",
        REAZON + "joiner-epoch-99-avg-1.int8.onnx",
        2_696_970,
        "49cc7ea1d3d35a40a27442db5e89996da64bf0e683a903dce76e99e57a12e4de",
    )
    val ASR_TOKENS = ModelFile(
        "tokens.txt",
        REAZON + "tokens.txt",
        45_754,
        "2c3ac659818a48a0c04010e0593bbc4d7c8a24a054340b01131499c05fd52def",
    )

    /** Silero VAD (MIT), as shipped by sherpa-onnx. */
    val VAD = ModelFile(
        "silero_vad.onnx",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        643_854,
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )

    /** ずんだもん (+ 四国めたん, 春日部つむぎ, 雨晴はう) talk voices — VOICEVOX VVM 0.16.4 (zunko.jp terms, credit required). */
    val VOICE_VVM = ModelFile(
        "0.vvm",
        "https://github.com/VOICEVOX/voicevox_vvm/raw/0.16.4/vvms/0.vvm",
        58_214_379,
        "917e592cae92a02fb7668bd9e168df4e39c8ab2f5302c2be71906c70ec4e2df2",
    )

    /** Open JTalk system dictionary (BSD-3), unpacked into [DICT_DIR]. Bundled as `.tgz`, not `.tar.gz`, because
     * Android's asset pipeline auto-gunzips any `.gz` asset (it would balloon to a 107 MB `.tar` and lose the name). */
    const val DICT_DIR = "open_jtalk_dic_utf_8-1.11"
    val DICTIONARY = ModelFile(
        "open_jtalk_dic_utf_8-1.11.tgz",
        "https://downloads.sourceforge.net/project/open-jtalk/Dictionary/open_jtalk_dic-1.11/open_jtalk_dic_utf_8-1.11.tar.gz",
        23_643_819,
        "33e9cd251bc41aa2bd7ca36f57abbf61eae3543ca25ca892ae345e394cb10549",
        unpackDir = DICT_DIR,
    )

    val ALL = listOf(ASR_ENCODER, ASR_DECODER, ASR_JOINER, ASR_TOKENS, VAD, VOICE_VVM, DICTIONARY)

    /** VOICEVOX style id of ずんだもん ノーマル. */
    const val ZUNDAMON_NORMAL_STYLE = 3
}
