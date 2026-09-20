#!/usr/bin/env python3
"""Zundamon voice bench: sherpa-onnx vs Android's platform recogniser, end to end, on a real device.

Speaks a fixed Japanese sentence set through the Mac's speaker (macOS `say -v Kyoko`) at the phone, once per
engine, with the app's Avatar-tab voice card listening, and reads the pipeline's own logcat lines back:

  * accuracy   — character error rate (CER) of the recognised text against the sentence that was spoken;
  * ASR latency — device-clock time from the end of speech (a `log` marker written right after `say` returns)
                  to the recognised-text log line;
  * first audio — from that same marker to the moment VOICEVOX's first chunk is ready for playback;
  * TTS         — VOICEVOX synthesis time and audio length (engine-independent; shows thermal state).

Usage: scripts/voice-bench.py [--serial SERIAL] [--engines sherpa,android] [--out scripts/voice-bench-results.md]
Needs: adb, macOS `say` with the Kyoko voice, the debug app installed with the voice models pushed
(scripts/voice-models.sh), the Japanese pack for the on-device recogniser (Android engine), and a quiet room.
Never touches Go Live.
"""
from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field

PACKAGE = "com.example.virtualtwitchdroid"
ACTIVITY = f"{PACKAGE}/.MainActivity"

# Sentence sets, chosen with --set. Each entry: (id, group, expected text as spoken).
# "stress" = hard cases (proper nouns, numbers, loanwords, tongue twisters). "normal" = everyday streamer talk,
# including words that must NOT trip the ずんだもん homophone rule (読んだ本 / 済んだこと / 遊んでもらって).
SENTENCE_SETS = {
    "stress": [
        ("s1", "short", "はい。"),
        ("s2", "short", "おはようございます。"),
        ("s3", "medium", "今日はいい天気ですね。"),
        ("s4", "difficult", "ずんだもんです。よろしくお願いします。"),
        ("s5", "difficult", "音声認識と音声合成を組み合わせています。"),
        ("s6", "difficult", "明日は午後三時十五分に新宿駅で待ち合わせしましょう。"),
        ("s7", "difficult", "ユーチューブとツイッチで同時配信をしています。"),
        ("s8", "difficult", "生麦生米生卵。"),
        ("s9", "difficult", "東京特許許可局。"),
        ("s10", "long", "機械学習のモデルをスマートフォンの中だけで動かしています。"),
        ("s11", "long", "今日は朝から雨が降っていましたが、午後になってようやく晴れてきましたね。"),
        ("s12", "long", "この配信では、ずんだもんの声で、視聴者の皆さんとお話ししながら、ゲームの実況をしていきたいと思います。"),
    ],
    "normal": [
        ("n1", "short", "こんにちは、みんな元気ですか。"),
        ("n2", "short", "コメントありがとうございます。"),
        ("n3", "medium", "今日は一緒にゲームを遊びましょう。"),
        ("n4", "medium", "そろそろ次のステージに進みますね。"),
        ("n5", "medium", "ちょっと休憩してから続けます。"),
        ("n6", "normal-verb", "昨日読んだ本がとても面白かったです。"),
        ("n7", "normal-verb", "もう済んだことだから気にしないでください。"),
        ("n8", "normal-verb", "みんなに遊んでもらってうれしいです。"),
        ("n9", "long", "みんなのおかげで今日もすごく楽しい時間を過ごせました。"),
        ("n10", "long", "また明日の夜も配信する予定なので、ぜひ見に来てくださいね。"),
    ],
}
SENTENCES = SENTENCE_SETS["stress"]

ENGINE_CHIP = {"sherpa": "sherpa-onnx", "android": "Android"}
PUNCT = re.compile(r"[\s、。，．,\.!！?？「」『』・…]")


@dataclass
class Result:
    sid: str
    group: str
    expected: str
    recognised: str | None = None
    cer: float | None = None
    asr_ms: int | None = None
    first_audio_ms: int | None = None
    tts_ms: int | None = None
    audio_s: float | None = None
    notes: list[str] = field(default_factory=list)
    rows: list = field(default_factory=list)  # the pipeline's log lines for this sentence (from its SAY marker)
    said: dt.datetime | None = None


def sh(*args: str, timeout: int = 60) -> str:
    return subprocess.run(list(args), capture_output=True, text=True, timeout=timeout).stdout


class Device:
    def __init__(self, serial: str) -> None:
        self.serial = serial

    def adb(self, *args: str, timeout: int = 60) -> str:
        return sh("adb", "-s", self.serial, *args, timeout=timeout)

    def tap_text(self, text: str, attempts: int = 5) -> bool:
        """Tap the node whose text/content-description is [text]; retries while the screen settles."""
        for _ in range(attempts):
            self.adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
            xml = self.adb("shell", "cat", "/sdcard/ui.xml")
            for node in xml.split(">"):
                if f'text="{text}"' in node or f'content-desc="{text}"' in node:
                    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
                    if m:
                        x = (int(m[1]) + int(m[3])) // 2
                        y = (int(m[2]) + int(m[4])) // 2
                        self.adb("shell", "input", "tap", str(x), str(y))
                        return True
            time.sleep(2)
        return False

    def marker(self, text: str) -> None:
        self.adb("shell", "log", "-t", "VoiceBench", text)

    def caps(self) -> str:
        freqs = self.adb("shell", "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq").split()
        return " ".join(f"{int(f) // 1000}" for f in freqs) + " MHz"

    def uncapped(self) -> bool:
        """True when the mid/big cores are allowed near their top speed (not thermally capped)."""
        freqs = [int(f) for f in self.adb("shell", "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq").split()]
        return bool(freqs) and max(freqs) >= 2_500_000 and sorted(freqs)[len(freqs) // 2] >= 2_000_000

    def wait_uncapped(self, max_s: int) -> None:
        deadline = time.time() + max_s
        while time.time() < deadline and not self.uncapped():
            time.sleep(10)

    def logcat(self) -> list[tuple[dt.datetime, str, str]]:
        """(timestamp, tag, message) for the pipeline's tags since the last clear."""
        out = self.adb(
            "logcat", "-d", "-v", "time", "-s",
            "VoiceBench:*", "NativeSpeech:*", "JapaneseRecogniser:*", "ZundamonVoice:*", "VoicevoxSpeaker:*",
        )
        rows = []
        year = dt.datetime.now().year
        for line in out.splitlines():
            m = re.match(r"(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d\d\d) [A-Z]/([A-Za-z]+)\s*\(\s*\d+\): (.*)", line)
            if not m:
                continue
            ts = dt.datetime(year, int(m[1]), int(m[2]), int(m[3]), int(m[4]), int(m[5]), int(m[6]) * 1000)
            rows.append((ts, m[7], m[8]))
        return rows


def levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def cer(expected: str, got: str) -> float:
    e, g = PUNCT.sub("", expected), PUNCT.sub("", got)
    return levenshtein(e, g) / max(1, len(e))


def ms(a: dt.datetime, b: dt.datetime) -> int:
    return int((b - a).total_seconds() * 1000)


def run_engine(dev: Device, engine: str, warmup_s: int) -> list[Result]:
    chip = ENGINE_CHIP[engine]
    dev.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(1)
    dev.adb("shell", "wm", "dismiss-keyguard")
    time.sleep(1)
    dev.adb("shell", "pm", "grant", PACKAGE, "android.permission.RECORD_AUDIO")
    dev.adb("shell", "am", "force-stop", PACKAGE)
    dev.adb("logcat", "-G", "16M")
    dev.adb("logcat", "-c")
    dev.adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(6)
    if not dev.tap_text("Avatar"):
        sys.exit("Avatar tab not found")
    time.sleep(8)
    # The engine chips existed only while the Android engine did (removed 2026-09-20); tap one if present.
    if dev.tap_text(chip, attempts=1):
        time.sleep(1)
    if not dev.tap_text("Listen (日本語)"):
        sys.exit("Listen button not found")
    print(f"[{engine}] engines loading ({warmup_s} s)…", flush=True)
    time.sleep(warmup_s)

    results = []
    for sid, group, text in SENTENCES:
        r = Result(sid, group, text)
        dev.marker(f"SAY {sid}")
        subprocess.run(["say", "-v", "Kyoko", text], check=False)
        dev.marker(f"SAID {sid}")
        # Wait for VOICEVOX to finish this sentence (or give up), then let the playback end so the next
        # sentence is not gated as an echo.
        deadline = time.time() + 45
        audio_s = None
        while time.time() < deadline:
            time.sleep(1)
            rows = dev.logcat()
            said = next((t for t, tag, m in rows if tag == "VoiceBench" and m == f"SAID {sid}"), None)
            if said is None:
                continue
            for t, tag, m in rows:
                if t >= said and tag == "VoicevoxSpeaker" and m.startswith("synthesised"):
                    audio_s = float(re.match(r"synthesised ([\d.]+)s", m)[1])
            if audio_s is not None:
                break
        time.sleep((audio_s or 0) + 2.5)
        # Capture this sentence's window now, before the buffer rotates.
        rows = dev.logcat()
        say_t = next((t for t, tag, m in rows if tag == "VoiceBench" and m == f"SAY {sid}"), None)
        r.said = next((t for t, tag, m in rows if tag == "VoiceBench" and m == f"SAID {sid}"), None)
        r.rows = [(t, tag, m) for t, tag, m in rows if say_t is not None and t >= say_t]
        results.append(r)
        print(f"[{engine}] {sid} done", flush=True)
    dev.tap_text("Stop")
    time.sleep(1)

    for r in results:
        said = r.said
        if said is None:
            r.notes.append("no marker")
            continue
        # A result counts only if the controller did not drop it as an echo of Zundamon's own playback: the
        # "dropped" log line follows the recogniser's line within a few ms and repeats its text (which may be
        # identical to the genuine result, so match by adjacency, not by text).
        window = [(t, tag, m) for t, tag, m in r.rows if t >= said - dt.timedelta(milliseconds=600)]

        def dropped_after(i: int, text: str) -> bool:
            for t2, tag2, m2 in window[i + 1 : i + 4]:
                if tag2 == "ZundamonVoice" and "dropped a segment" in m2 and m2.endswith(text):
                    return True
            return False

        for i, (t, tag, m) in enumerate(window):
            if r.recognised is None and tag == "NativeSpeech" and m.startswith("recognised"):
                got = m.split("→ ", 1)[1]
                if not dropped_after(i, got):
                    r.recognised, r.asr_ms = got, ms(said, t)
            if r.recognised is None and tag == "JapaneseRecogniser" and m.startswith("segment"):
                got = m.split("): ", 1)[1]
                if not dropped_after(i, got):
                    r.recognised, r.asr_ms = got, ms(said, t)
            if r.recognised is not None and tag == "VoicevoxSpeaker" and m.startswith("chunk 1/") and r.first_audio_ms is None:
                r.first_audio_ms = ms(said, t)
            if r.recognised is not None and tag == "VoicevoxSpeaker" and m.startswith("synthesised") and r.tts_ms is None:
                mm = re.match(r"synthesised ([\d.]+)s of audio in (\d+) ms", m)
                r.audio_s, r.tts_ms = float(mm[1]), int(mm[2])
        if r.recognised is None:
            r.notes.append("not recognised (or only as echo)")
        else:
            r.cer = cer(r.expected, r.recognised)
    return results


def fmt(v, suffix="") -> str:
    return "—" if v is None else f"{v}{suffix}"


def report(all_results: dict[str, list[Result]], caps_before: str, caps_after: str, serial: str) -> str:
    lines = [
        "# Zundamon voice bench — sherpa-onnx vs Android recogniser",
        "",
        f"Device `{serial}`, {dt.datetime.now():%Y-%m-%d %H:%M}. Speaker: macOS `say -v Kyoko` at the phone. "
        f"CPU caps before: {caps_before}; after: {caps_after} (a capped phone slows VOICEVOX, not the ranking).",
        "",
        "ASR ms = end of speech → recognised text (device clock). First audio ms = end of speech → VOICEVOX's first "
        "chunk ready. CER = character error rate vs the spoken sentence (punctuation ignored; kanji choice counts).",
        "",
    ]
    for engine, results in all_results.items():
        lines += [f"## {ENGINE_CHIP[engine]}", "", "| # | group | spoken | recognised | CER | ASR ms | first audio ms | TTS ms / audio s |", "|---|---|---|---|---|---|---|---|"]
        for r in results:
            lines.append(
                f"| {r.sid} | {r.group} | {r.expected} | {fmt(r.recognised)} | "
                f"{fmt(None if r.cer is None else f'{r.cer:.0%}')} | {fmt(r.asr_ms)} | {fmt(r.first_audio_ms)} | "
                f"{fmt(r.tts_ms)} / {fmt(r.audio_s)} {' '.join(r.notes)} |"
            )
        lines.append("")
    lines += ["## Pipeline log per sentence (for analysis)", ""]
    for engine, results in all_results.items():
        lines.append(f"### {ENGINE_CHIP[engine]}")
        for r in results:
            lines.append(f"- **{r.sid}** {r.expected}")
            for t, tag, m in r.rows:
                if tag in ("VoiceBench", "NativeSpeech", "JapaneseRecogniser", "ZundamonVoice") or (
                    tag == "VoicevoxSpeaker" and m.startswith("synthesised")
                ):
                    lines.append(f"    - `{t:%H:%M:%S.%f}` {tag}: {m[:160]}")
        lines.append("")
    lines += ["## Summary", "", "| engine | recognised | mean CER | median ASR ms | median first audio ms | CER short / medium / long / difficult |", "|---|---|---|---|---|---|"]
    for engine, results in all_results.items():
        ok = [r for r in results if r.cer is not None]
        def med(vals):
            vals = sorted(v for v in vals if v is not None)
            return "—" if not vals else str(vals[len(vals) // 2])
        def gcer(g):
            vals = [r.cer for r in ok if r.group == g]
            return "—" if not vals else f"{sum(vals) / len(vals):.0%}"
        mean = f"{sum(r.cer for r in ok) / len(ok):.0%}" if ok else "—"
        lines.append(
            f"| {ENGINE_CHIP[engine]} | {len(ok)}/{len(results)} | {mean} | {med(r.asr_ms for r in ok)} | "
            f"{med(r.first_audio_ms for r in ok)} | {gcer('short')} / {gcer('medium')} / {gcer('long')} / {gcer('difficult')} |"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default=None)
    ap.add_argument("--engines", default="sherpa,android")
    ap.add_argument("--out", default="scripts/voice-bench-results.md")
    ap.add_argument("--warmup", type=int, default=30, help="seconds to let the engines load after Listen")
    ap.add_argument("--cooldown", type=int, default=240, help="max seconds to wait for thermal caps to lift per engine")
    ap.add_argument("--volume", type=int, default=50, help="Mac output volume (0-100) for the spoken sentences")
    ap.add_argument("--set", default="stress", choices=list(SENTENCE_SETS), help="which sentence set to run")
    args = ap.parse_args()
    global SENTENCES
    SENTENCES = SENTENCE_SETS[args.set]
    serial = args.serial or next(l.split()[0] for l in sh("adb", "devices").splitlines()[1:] if l.strip().endswith("device"))
    dev = Device(serial)
    subprocess.run(["osascript", "-e", f"set volume output volume {args.volume}"], check=False)
    caps_before = dev.caps()
    all_results = {}
    for engine in args.engines.split(","):
        if args.cooldown:
            print(f"waiting up to {args.cooldown} s for the CPU caps to lift ({dev.caps()})…", flush=True)
            dev.wait_uncapped(args.cooldown)
            print(f"caps now {dev.caps()}", flush=True)
        all_results[engine] = run_engine(dev, engine.strip(), args.warmup)
    caps_after = dev.caps()
    text = report(all_results, caps_before, caps_after, serial)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(text)
    print(text)


if __name__ == "__main__":
    main()
