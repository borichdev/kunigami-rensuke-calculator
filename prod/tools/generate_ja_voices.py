import os
import subprocess
import urllib.parse
import urllib.request

VOICE_DIR = "/home/borichdev/projects/kunigamicalculator/prod/assets/voice"
os.makedirs(VOICE_DIR, exist_ok=True)

CLIPS = {
    "d0": "ゼロ",
    "d1": "いち",
    "d2": "にっ",
    "d3": "さん",
    "d4": "よん",
    "d5": "ごっ",
    "d6": "ろく",
    "d7": "なな",
    "d8": "はち",
    "d9": "きゅう",
    "plus": "たす",
    "minus": "ひく",
    "mul": "かける",
    "div": "わる",
    "equals": "はっ！答えが出たぜ！",
    "bs": "消す、消す！",
    "clear": ("ru", "Кунигири канон!"),
    "dot": "てん",
    "percent": "パーセント",
    "sign": "マイナス",
    "ee_67": "ろくじゅうなな！？ シックス・セブン！",
    "ee_87": "はちじゅうなな！？ バイト・オブ・エイティセブン！？",
    "ee_50": "ごじゅう！ 俺はスーパーヒーローになる！",
    "ee_993": "せんひくなな、きゅうひゃくきゅうじゅうさん！",
    "ee_777": "なな・なな・なな！ ジャックポットだぜ！",
    "ee_69": "ろくじゅうきゅう！ ナイスだぜ！",
    "ee_420": "よんひゃくにじゅう！ 燃えてきたぜ！",
    "ee_666": "ろっぴゃくろくじゅうろく！ エゴイスト覚醒だ！",
    "ee_1337": "エリート・ストライカーだ！",
    "ee_42": "よんじゅうに！ 宇宙の答えだな！",
    "ee_228": "にひゃくにじゅうはち！ おいおい、やばい数字だな！",
    "ee_1488": "おい！ 計算に集中しろ！",
    "ee_52": "ごじゅうに！ イェーイ、フィフティ・ツー！",
}

for name, val in CLIPS.items():
    lang, text = val if isinstance(val, tuple) else ("ja", val)
    raw_path = f"/tmp/raw_{name}.mp3"
    out_path = os.path.join(VOICE_DIR, f"{name}.mp3")
    url = f"https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl={lang}&q=" + urllib.parse.quote(text)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    data = urllib.request.urlopen(req, timeout=15).read()
    with open(raw_path, "wb") as f:
        f.write(data)

    # Trim leading silence, shift pitch down to energetic male anime voice (0.80x), speed up slightly (1.14x), normalize volume
    af = "silenceremove=start_periods=1:start_duration=0.01:start_threshold=-40dB,rubberband=pitch=0.80:tempo=1.14:formant=65536,volume=1.6"
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-i", raw_path,
        "-af", af,
        "-ar", "24000", "-ac", "1", "-b:a", "48k",
        out_path
    ], check=True)
    os.remove(raw_path)

print(f"Generated {len(CLIPS)} Japanese voice clips in {VOICE_DIR}")
