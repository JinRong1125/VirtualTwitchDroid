# Zundamon voice bench — sherpa-onnx vs Android recogniser

Device: Pixel 8a, 2026-09-20 14:28. Speaker: macOS `say -v Kyoko` at the phone. CPU caps before: 1704 1704 1704 1704 2367 2367 2367 2367 2914 MHz; after: 955 955 955 955 712 712 712 712 1164 MHz (a capped phone slows VOICEVOX, not the ranking).

ASR ms = end of speech → recognised text (device clock). First audio ms = end of speech → VOICEVOX's first chunk ready. CER = character error rate vs the spoken sentence (punctuation ignored; kanji choice counts).

## sherpa-onnx

| # | group | spoken | recognised | CER | ASR ms | first audio ms | TTS ms / audio s |
|---|---|---|---|---|---|---|---|
| n1 | short | こんにちは、みんな元気ですか。 | こんにちはみんな元気ですか | 0% | 1573 | 4447 | 2873 / 2.27  |
| n2 | short | コメントありがとうございます。 | ごめん答ありがとうございます | 29% | 1415 | 5394 | 3978 / 2.46  |
| n3 | medium | 今日は一緒にゲームを遊びましょう。 | 今日は一緒にゲームを遊びましょう | 0% | 1869 | 6218 | 4347 / 2.6  |
| n4 | medium | そろそろ次のステージに進みますね。 | そろそろ次のステージに進みますね | 0% | 1629 | 6224 | 4594 / 2.82  |
| n5 | medium | ちょっと休憩してから続けます。 | 休憩してから続けます | 29% | 1301 | 5059 | 3758 / 2.08  |
| n6 | normal-verb | 昨日読んだ本がとても面白かったです。 | 昨日読んだ本がとても面白かったです | 0% | 1517 | 6377 | 4859 / 3.04  |
| n7 | normal-verb | もう済んだことだから気にしないでください。 | もう済んだことだから気にしないでください | 0% | 1845 | 6621 | 4775 / 2.92  |
| n8 | normal-verb | みんなに遊んでもらってうれしいです。 | みんなに遊んでもらってうれしいです | 0% | 1453 | 6001 | 4546 / 2.67  |
| n9 | long | みんなのおかげで今日もすごく楽しい時間を過ごせました。 | みんなのおかげで今日もすごく楽しい時間を過ごせました | 0% | 2047 | 9707 | 7656 / 4.12  |
| n10 | long | また明日の夜も配信する予定なので、ぜひ見に来てくださいね。 | また明日の夜も配信する予定なので是非見に来て下さいね | 15% | 2015 | 8309 | 10502 / 4.8  |

## Pipeline log per sentence (for analysis)

### sherpa-onnx
- **n1** こんにちは、みんな元気ですか。
    - `14:25:51.879000` VoiceBench: SAY n1
    - `14:25:55.877000` VoiceBench: SAID n1
    - `14:25:57.450000` JapaneseRecogniser: segment 3.28s → 263 ms (RTF 0.08; text 563 ms after end of speech): こんにちはみんな元気ですか
    - `14:26:00.325000` VoicevoxSpeaker: synthesised 2.27s of audio in 2873 ms total, speed 1.00: こんにちはみんな元気ですか。
    - `14:26:03.774000` JapaneseRecogniser: segment 2.13s → 242 ms (RTF 0.11; text 542 ms after end of speech): うちはみんなげんきですか
    - `14:26:03.774000` ZundamonVoice: dropped a segment heard while the monitor was speaking: うちはみんなげんきですか
- **n2** コメントありがとうございます。
    - `14:26:06.098000` VoiceBench: SAY n2
    - `14:26:09.626000` VoiceBench: SAID n2
    - `14:26:11.041000` JapaneseRecogniser: segment 2.80s → 286 ms (RTF 0.10; text 586 ms after end of speech): ごめん答ありがとうございます
    - `14:26:15.021000` VoicevoxSpeaker: synthesised 2.46s of audio in 3978 ms total, speed 1.00: ごめん答ありがとうございます。
    - `14:26:18.240000` JapaneseRecogniser: segment 0.62s → 159 ms (RTF 0.26; text 459 ms after end of speech): あります
    - `14:26:18.240000` ZundamonVoice: dropped a segment heard while the monitor was speaking: あります
- **n3** 今日は一緒にゲームを遊びましょう。
    - `14:26:21.273000` VoiceBench: SAY n3
    - `14:26:25.414000` VoiceBench: SAID n3
    - `14:26:27.283000` JapaneseRecogniser: segment 3.73s → 401 ms (RTF 0.11; text 701 ms after end of speech): 今日は一緒にゲームを遊びましょう
    - `14:26:31.632000` VoicevoxSpeaker: synthesised 2.60s of audio in 4347 ms total, speed 1.00: 今日は一緒にゲームを遊びましょう。
    - `14:26:35.540000` JapaneseRecogniser: segment 2.51s → 293 ms (RTF 0.12; text 593 ms after end of speech): 一緒にゲームを遊びましょう
    - `14:26:35.540000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 一緒にゲームを遊びましょう
- **n4** そろそろ次のステージに進みますね。
    - `14:26:37.723000` VoiceBench: SAY n4
    - `14:26:41.853000` VoiceBench: SAID n4
    - `14:26:43.482000` JapaneseRecogniser: segment 3.57s → 393 ms (RTF 0.11; text 693 ms after end of speech): そろそろ次のステージに進みますね
    - `14:26:48.078000` VoicevoxSpeaker: synthesised 2.82s of audio in 4594 ms total, speed 1.00: そろそろ次のステージに進みますね。
    - `14:26:52.062000` JapaneseRecogniser: segment 3.05s → 346 ms (RTF 0.11; text 646 ms after end of speech): そろそろ次のステージに進みますね
    - `14:26:52.062000` ZundamonVoice: dropped a segment heard while the monitor was speaking: そろそろ次のステージに進みますね
- **n5** ちょっと休憩してから続けます。
    - `14:26:54.264000` VoiceBench: SAY n5
    - `14:26:58.115000` VoiceBench: SAID n5
    - `14:26:59.416000` JapaneseRecogniser: segment 2.80s → 315 ms (RTF 0.11; text 615 ms after end of speech): 休憩してから続けます
    - `14:27:03.175000` VoicevoxSpeaker: synthesised 2.08s of audio in 3758 ms total, speed 1.00: 休憩してから続けます。
    - `14:27:06.143000` JapaneseRecogniser: segment 2.06s → 280 ms (RTF 0.14; text 580 ms after end of speech): 休憩してから続けます
    - `14:27:06.143000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 休憩してから続けます
- **n6** 昨日読んだ本がとても面白かったです。
    - `14:27:08.401000` VoiceBench: SAY n6
    - `14:27:13.248000` VoiceBench: SAID n6
    - `14:27:14.765000` JapaneseRecogniser: segment 4.05s → 406 ms (RTF 0.10; text 706 ms after end of speech): 昨日読んだ本がとても面白かったです
    - `14:27:19.626000` VoicevoxSpeaker: synthesised 3.04s of audio in 4859 ms total, speed 1.00: 昨日読んだ本がとても面白かったです。
    - `14:27:23.800000` JapaneseRecogniser: segment 1.74s → 247 ms (RTF 0.14; text 547 ms after end of speech): 面白かったです
    - `14:27:23.801000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 面白かったです
- **n7** もう済んだことだから気にしないでください。
    - `14:27:26.563000` VoiceBench: SAY n7
    - `14:27:31.142000` VoiceBench: SAID n7
    - `14:27:32.987000` JapaneseRecogniser: segment 4.21s → 450 ms (RTF 0.11; text 750 ms after end of speech): もう済んだことだから気にしないでください
    - `14:27:37.764000` VoicevoxSpeaker: synthesised 2.92s of audio in 4775 ms total, speed 1.00: もう済んだことだから気にしないでください。
    - `14:27:41.817000` JapaneseRecogniser: segment 3.05s → 345 ms (RTF 0.11; text 645 ms after end of speech): もう済んだことだから気にしないでください
    - `14:27:41.817000` ZundamonVoice: dropped a segment heard while the monitor was speaking: もう済んだことだから気にしないでください
- **n8** みんなに遊んでもらってうれしいです。
    - `14:27:44.154000` VoiceBench: SAY n8
    - `14:27:48.224000` VoiceBench: SAID n8
    - `14:27:49.677000` JapaneseRecogniser: segment 3.37s → 365 ms (RTF 0.11; text 665 ms after end of speech): みんなに遊んでもらってうれしいです
    - `14:27:54.226000` VoicevoxSpeaker: synthesised 2.67s of audio in 4546 ms total, speed 1.00: みんなに遊んでもらってうれしいです。
- **n9** みんなのおかげで今日もすごく楽しい時間を過ごせました。
    - `14:27:59.905000` VoiceBench: SAY n9
    - `14:28:05.688000` VoiceBench: SAID n9
    - `14:28:07.735000` JapaneseRecogniser: segment 5.42s → 629 ms (RTF 0.12; text 929 ms after end of speech): みんなのおかげで今日もすごく楽しい時間を過ごせました
    - `14:28:15.396000` VoicevoxSpeaker: synthesised 4.12s of audio in 7656 ms total, speed 1.00: みんなのおかげで今日もすごく楽しい時間を過ごせました。
    - `14:28:21.054000` JapaneseRecogniser: segment 3.89s → 514 ms (RTF 0.13; text 814 ms after end of speech): おかげで今日もすごく楽しい時間を過ごせました
    - `14:28:21.054000` ZundamonVoice: dropped a segment heard while the monitor was speaking: おかげで今日もすごく楽しい時間を過ごせました
- **n10** また明日の夜も配信する予定なので、ぜひ見に来てくださいね。
    - `14:28:23.137000` VoiceBench: SAY n10
    - `14:28:29.399000` VoiceBench: SAID n10
    - `14:28:31.414000` JapaneseRecogniser: segment 5.90s → 641 ms (RTF 0.11; text 941 ms after end of speech): また明日の夜も配信する予定なので是非見に来て下さいね
    - `14:28:41.787000` JapaneseRecogniser: segment 1.68s → 345 ms (RTF 0.21; text 645 ms after end of speech): 維新する予定なので
    - `14:28:41.788000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 維新する予定なので
    - `14:28:41.917000` VoicevoxSpeaker: synthesised 4.80s of audio in 10502 ms total, speed 1.00: また明日の夜も配信する予定なので是非見に来て下さいね。
    - `14:28:44.594000` JapaneseRecogniser: segment 1.65s → 299 ms (RTF 0.18; text 599 ms after end of speech): 楽しみに来てくださいね
    - `14:28:44.594000` ZundamonVoice: dropped a segment heard while the monitor was speaking: 楽しみに来てくださいね

## Summary

| engine | recognised | mean CER | median ASR ms | median first audio ms | CER short / medium / long / difficult |
|---|---|---|---|---|---|
| sherpa-onnx | 10/10 | 7% | 1629 | 6224 | 14% / 10% / 7% / — |
