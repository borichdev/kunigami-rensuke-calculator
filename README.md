# ⚽ Kunigami Rensuke 3D Calculator (`KC #50`)

An interactive **Android 3D Anime Calculator** featuring **Rensuke Kunigami** (*Blue Lock* #50) rendered in real time with **OpenGL ES 3.0**.

📦 **Download APK:** [`kunigami-calc.apk`](kunigami-calc.apk) (`4.4 MB`, Android 8.0+ / API 26–34)

---

## ✨ Features

* **Real-Time 3D Kunigami Rensuke (OpenGL ES 3.0):**
  * **41,436-triangle** Curved Phong-Tessellated mesh with 57 skeletal bones (`K3D1` binary format).
  * **AI-Remastered Textures (`waifu2x-ncnn-vulkan`):** 1024×1024 hair & uniform textures, razor-sharp `#50` jersey numerals, radial iris detail, and baked anime hair specular streaks.
  * **Custom Anime Cel-Shader:** Inverted-hull outlines, skin subsurface scattering (SSS) at the shadow terminator, vertical hair gradient, angel-ring specular highlights, and dynamic Blue Lock hexagonal stadium background with rising Ego embers.
  * **Lifelike Procedural Animation:** Breathing, hair spring physics, automatic eyelid blinking (`Eyelid_M`), eye gaze tracking (`Eye_L` / `Eye_R`), and lip-synced jaw movement (`Jaw_M`).
* **Interactive Finger-Counting & Operator Poses:**
  * **Digits `0`–`9`:** Kunigami counts numbers on his fingers in front of his chest.
  * **Operators (`+`, `-`, `×`, `÷`, `=`, `⌫`, `C`):** Forearm cross-plus, horizontal minus slash, X-guard multiply, diagonal divide chop, two-handed eraser wipe, and Hero Ego awakening.
* **Japanese Voice Acting (`SoundPool`):**
  * Zero-latency Japanese voice lines for every digit (`0`–`9`), operator, action, and Easter egg, paired with tactile synthesized clicks.
* **Secret Easter Eggs:**
  * Try typing or calculating **`67`** (alternating Six-Seven juggling dance), **`87`** (*Bite of '87* shock pose), **`50`** (Blue Lock #50 Superhero pose), **`993`** (*1000 - 7* finger crack), **`777`**, **`69`**, **`420`**, **`666`**, **`1337`**, **`42`**, **`228`**, **`1488`**.
* **Tactical Glass UI:**
  * Live expression preview (`≈`), tap-display-to-copy to clipboard, compact/full keypad toggle (`⊞`), free 3D camera showcase mode (`👁`), and persistent Ego Awakening toggle (`🔥`).

---

## 🛠 Project Structure & Build

```text
prod/
├── AndroidManifest.xml
├── build.sh                  # Pure CLI Android SDK 34 build pipeline (aapt2, javac, d8, zipalign, apksigner)
├── send_tg.sh                # Telegram delivery helper
├── assets/
│   ├── models/kunigami.k3d   # Custom 57-bone, 23-pose skeletal mesh binary
│   ├── textures/*.png        # 22 upscaled & enhanced character textures
│   └── voice/*.mp3           # 32 Japanese voice clips
├── res/
│   ├── drawable/ic_launcher.xml  # Vector icon with fiery orange KC emblem
│   └── values/
├── src/dev/borich/kunigamicalc/
│   ├── MainActivity.java     # Tactical Blue Lock glass UI & Easter egg dispatcher
│   ├── CalculatorEngine.java # High-precision BigDecimal evaluator with live preview
│   ├── KunigamiRenderer.java # OpenGL ES 3.0 cel-shader, outline & background renderer
│   ├── KunigamiModel.java    # .k3d loader, quaternion SLERP, blink/speech/hair physics
│   └── SoundEngine.java      # Low-latency SoundPool voice & PCM click synthesizer
└── tools/
    ├── export_kunigami_model.py  # Blender 3D rig/pose/tessellation exporter
    ├── upgrade_textures.py       # waifu2x + PIL texture enhancement pipeline
    └── generate_ja_voices.py     # Japanese TTS + rubberband pitch/formant pipeline
```

### Build from Source

```bash
cd prod
./build.sh
```
