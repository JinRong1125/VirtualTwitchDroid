# Zundamon voice bench — sherpa-onnx vs Android recogniser

Device: Pixel 8a, 2026-09-20 00:23. Speaker: macOS `say -v Kyoko` at the phone. CPU caps before: 1098 1098 1098 1098 910 910 910 910 1164 MHz; after: 955 955 955 955 712 712 712 712 880 MHz (a capped phone slows VOICEVOX, not the ranking).

ASR ms = end of speech → recognised text (device clock). First audio ms = end of speech → VOICEVOX's first chunk ready. CER = character error rate vs the spoken sentence (punctuation ignored; kanji choice counts).

## sherpa-onnx

| # | group | spoken | recognised | CER | ASR ms | first audio ms | TTS ms / audio s |
|---|---|---|---|---|---|---|---|
| s1 | short | はい。 | — | — | — | — | — / — not recognised (or only as echo) |
| s2 | short | おはようございます。 | おはようございます | 0% | 1520 | 5009 | 3487 / 1.49  |
| s3 | medium | 今日はいい天気ですね。 | 今日はいい天気ですね | 0% | 1498 | 5488 | 3990 / 1.82  |
| s4 | difficult | ずんだもんです。よろしくお願いします。 | よろしくお願いします | 41% | 1621 | 5464 | 3843 / 1.73  |
| s5 | difficult | 音声認識と音声合成を組み合わせています。 | 音声認識と音声合成を組み合わせています | 0% | 2028 | 8387 | 6360 / 3.81  |
| s6 | difficult | 明日は午後三時十五分に新宿駅で待ち合わせしましょう。 | まずは午後三時十五分に新宿駅で待ち合わせしましょう | 8% | 2106 | 9750 | 7646 / 4.49  |
| s7 | difficult | ユーチューブとツイッチで同時配信をしています。 | ユーチューブとツイッチで同時配信をしています | 0% | 1818 | 8647 | 6828 / 3.45  |
| s8 | difficult | 生麦生米生卵。 | 生麦生ごめなまたまご | 117% | 1569 | 7382 | 5812 / 2.15  |
| s9 | difficult | 東京特許許可局。 | 東京特許許可局 | 0% | 1689 | 7093 | 5404 / 1.94  |
| s10 | long | 機械学習のモデルをスマートフォンの中だけで動かしています。 | 社会学習のモデルをスマートフォンの中だけで動かしています | 7% | 2217 | 11590 | 9375 / 4.54  |
| s11 | long | 今日は朝から雨が降っていましたが、午後になってようやく晴れてきましたね。 | 今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね | 0% | 2457 | 14928 | 12471 / 5.19  |
| s12 | long | この配信では、ずんだもんの声で、視聴者の皆さんとお話ししながら、ゲームの実況をしていきたいと思います。 | この配信では澄んだもんの声で視聴者の皆さんとお話ししながらゲームの実況をしていきたいと思います | 2% | 1873 | 16793 | 20480 / 7.86  |

## Pipeline log per sentence (for analysis)

### sherpa-onnx
- **s1** はい。
    - `00:18:14.809000` VoiceBench: SAY s1
    - `00:18:16.779000` VoiceBench: SAID s1
- **s2** おはようございます。
    - `00:19:04.811000` VoiceBench: SAY s2
    - `00:19:07.615000` VoiceBench: SAID s2
    - `00:19:09.135000` JapaneseRecogniser: segment 2.09s → 317 ms (RTF 0.15; text 617 ms after end of speech): おはようございます
    - `00:19:12.626000` VoicevoxSpeaker: synthesised 1.49s of audio in 3487 ms total, speed 1.00: おはようございます。
- **s3** 今日はいい天気ですね。
    - `00:19:17.505000` VoiceBench: SAY s3
    - `00:19:20.952000` VoiceBench: SAID s3
    - `00:19:22.450000` JapaneseRecogniser: segment 2.67s → 356 ms (RTF 0.13; text 656 ms after end of speech): 今日はいい天気ですね
    - `00:19:26.440000` VoicevoxSpeaker: synthesised 1.82s of audio in 3990 ms total, speed 1.00: 今日はいい天気ですね。
    - `00:19:28.841000` JapaneseRecogniser: segment 1.13s → 224 ms (RTF 0.20; text 524 ms after end of speech): いいてんきですね
    - `00:19:28.841000` ZundamonVoice: dropped a segment heard while the monitor was speaking: いいてんきですね
- **s4** ずんだもんです。よろしくお願いします。
    - `00:19:32.307000` VoiceBench: SAY s4
    - `00:19:36.603000` VoiceBench: SAID s4
    - `00:19:38.224000` JapaneseRecogniser: segment 3.66s → 444 ms (RTF 0.12; text 744 ms after end of speech): よろしくお願いします
    - `00:19:42.068000` VoicevoxSpeaker: synthesised 1.73s of audio in 3843 ms total, speed 1.00: よろしくお願いします。
    - `00:19:44.984000` JapaneseRecogniser: segment 1.93s → 300 ms (RTF 0.16; text 600 ms after end of speech): よろしくお願いします
    - `00:19:44.984000` ZundamonVoice: dropped a segment heard while the monitor was speaking: よろしくお願いします
- **s5** 音声認識と音声合成を組み合わせています。
    - `00:19:47.549000` VoiceBench: SAY s5
    - `00:19:52.822000` VoiceBench: SAID s5
    - `00:19:54.850000` JapaneseRecogniser: segment 4.91s → 589 ms (RTF 0.12; text 889 ms after end of speech): 音声認識と音声合成を組み合わせています
    - `00:20:01.210000` VoicevoxSpeaker: synthesised 3.81s of audio in 6360 ms total, speed 1.00: 音声認識と音声合成を組み合わせています。
    - `00:20:05.858000` JapaneseRecogniser: segment 1.61s → 277 ms (RTF 0.17; text 577 ms after end of speech): くみあわせています
    - `00:20:05.859000` ZundamonVoice: dropped a segment heard while the monitor was speaking: くみあわせています
- **s6** 明日は午後三時十五分に新宿駅で待ち合わせしましょう。
    - `00:20:09.541000` VoiceBench: SAY s6
    - `00:20:15.859000` VoiceBench: SAID s6
    - `00:20:17.965000` JapaneseRecogniser: segment 5.77s → 647 ms (RTF 0.11; text 947 ms after end of speech): まずは午後三時十五分に新宿駅で待ち合わせしましょう
    - `00:20:25.612000` VoicevoxSpeaker: synthesised 4.49s of audio in 7646 ms total, speed 1.00: まずは午後三時十五分に新宿駅で待ち合わせしましょう。
    - `00:20:31.823000` JapaneseRecogniser: segment 3.09s → 416 ms (RTF 0.13; text 716 ms after end of speech): 新宿駅で待ち合わせしましょう
    - `00:20:31.823000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 新宿駅で待ち合わせしましょう
- **s7** ユーチューブとツイッチで同時配信をしています。
    - `00:20:33.229000` VoiceBench: SAY s7
    - `00:20:38.180000` VoiceBench: SAID s7
    - `00:20:39.998000` JapaneseRecogniser: segment 4.43s → 607 ms (RTF 0.14; text 907 ms after end of speech): ユーチューブとツイッチで同時配信をしています
    - `00:20:46.828000` VoicevoxSpeaker: synthesised 3.45s of audio in 6828 ms total, speed 1.00: ユーチューブとツイッチで同時配信をしています。
    - `00:20:49.765000` JapaneseRecogniser: segment 0.91s → 234 ms (RTF 0.26; text 534 ms after end of speech): でどうじはいしん
    - `00:20:49.766000` ZundamonVoice: dropped a segment heard while the monitor was speaking: でどうじはいしん
- **s8** 生麦生米生卵。
    - `00:20:53.664000` VoiceBench: SAY s8
    - `00:20:57.166000` VoiceBench: SAID s8
    - `00:20:58.735000` JapaneseRecogniser: segment 2.86s → 454 ms (RTF 0.16; text 754 ms after end of speech): 生麦生ごめなまたまご
    - `00:21:04.550000` VoicevoxSpeaker: synthesised 2.15s of audio in 5812 ms total, speed 1.00: 生麦生ごめなまたまご。
    - `00:21:07.255000` JapaneseRecogniser: segment 1.74s → 354 ms (RTF 0.20; text 654 ms after end of speech): 生むり生米生たまごめ
    - `00:21:07.255000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 生むり生米生たまごめ
- **s9** 東京特許許可局。
    - `00:21:10.285000` VoiceBench: SAY s9
    - `00:21:13.775000` VoiceBench: SAID s9
    - `00:21:15.464000` JapaneseRecogniser: segment 2.86s → 481 ms (RTF 0.17; text 781 ms after end of speech): 東京特許許可局
    - `00:21:20.869000` VoicevoxSpeaker: synthesised 1.94s of audio in 5404 ms total, speed 1.00: 東京特許許可局。
    - `00:21:22.579000` JapaneseRecogniser: segment 0.75s → 236 ms (RTF 0.31; text 536 ms after end of speech): 東京とって
    - `00:21:22.579000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 東京とって
- **s10** 機械学習のモデルをスマートフォンの中だけで動かしています。
    - `00:21:26.488000` VoiceBench: SAY s10
    - `00:21:32.590000` VoiceBench: SAID s10
    - `00:21:34.807000` JapaneseRecogniser: segment 5.52s → 816 ms (RTF 0.15; text 1116 ms after end of speech): 社会学習のモデルをスマートフォンの中だけで動かしています
    - `00:21:44.183000` VoicevoxSpeaker: synthesised 4.54s of audio in 9375 ms total, speed 1.00: 社会学習のモデルをスマートフォンの中だけで動かしています。
    - `00:21:50.245000` JapaneseRecogniser: segment 4.72s → 653 ms (RTF 0.14; text 953 ms after end of speech): 他界学習のモデルをスマートフォンの中だけで動かしています
    - `00:21:50.245000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 他界学習のモデルをスマートフォンの中だけで動かしています
- **s11** 今日は朝から雨が降っていましたが、午後になってようやく晴れてきましたね。
    - `00:21:52.082000` VoiceBench: SAY s11
    - `00:21:59.363000` VoiceBench: SAID s11
    - `00:22:01.820000` JapaneseRecogniser: segment 6.70s → 1143 ms (RTF 0.17; text 1443 ms after end of speech): 今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね
    - `00:22:14.292000` VoicevoxSpeaker: synthesised 5.19s of audio in 12471 ms total, speed 1.00: 今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね。
    - `00:22:21.427000` JapaneseRecogniser: segment 5.49s → 1014 ms (RTF 0.18; text 1314 ms after end of speech): 今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね
    - `00:22:21.428000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね
- **s12** この配信では、ずんだもんの声で、視聴者の皆さんとお話ししながら、ゲームの実況をしていきたいと思います。
    - `00:22:22.739000` VoiceBench: SAY s12
    - `00:22:32.704000` VoiceBench: SAID s12
    - `00:22:34.577000` JapaneseRecogniser: segment 8.76s → 1419 ms (RTF 0.16; text 1719 ms after end of speech): この配信では澄んだもんの声で視聴者の皆さんとお話ししながらゲームの実況をしていきたいと思います
    - `00:22:55.059000` VoicevoxSpeaker: synthesised 7.86s of audio in 20480 ms total, speed 1.00: この配信では澄んだもんの声で視聴者の皆さんとお話ししながらゲームの実況をしていきたいと思います。
    - `00:22:59.152000` JapaneseRecogniser: segment 5.36s → 987 ms (RTF 0.18; text 1287 ms after end of speech): 会社の皆さんとお話ししながらゲームの実況をしていきたいと思います
    - `00:22:59.152000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 会社の皆さんとお話ししながらゲームの実況をしていきたいと思います

## Summary

| engine | recognised | mean CER | median ASR ms | median first audio ms | CER short / medium / long / difficult |
|---|---|---|---|---|---|
| sherpa-onnx | 11/12 | 16% | 1818 | 8387 | 0% / 0% / 3% / 28% |
