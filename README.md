# InstaToolbox 🚀

**InstaToolbox** is a premium utility application tailored for social media content creators. Built with a modern, dark-mode-first aesthetic, it provides essential tools to optimize, subtitle, and format media for platforms like Instagram, TikTok, and YouTube Shorts.

---

## ✨ Features

### 📸 No-Crop Post Maker
Fit full landscape or portrait photos inside square layouts without cropping.
* **Exact Padding Control**: Select spacing from custom chips (0%, 2.5%, 5%, 10%, 15%, 20%).
* **Custom Output Resolution**: Save posts in standard and ultra-high resolutions (*Original*, *Instagram (1080px)*, *Full HD (1920px)*, *4K UHD (3840px)*).
* **Frame Backgrounds**: Toggle between Obsidian Black and clean White borders.
* **Memory Safe**: Automatically scales image inputs defensively to prevent GPU/OutOfMemory issues on massive camera files.

### 📝 Video Auto-Subtitler & Creator Captions
Transcribe speech and burn trendy, synchronized subtitles directly onto your video clips.
* **ML Kit On-Device Recognition**: Fast, private local audio transcription supporting both *Standard (Basic)* and *AI-Enhanced (GenAI)* models.
* **Manual Model Syncing**: Force-sync Play Services speech models manually directly from settings, featuring live percentage progress.
* **Catchy Creator Presets (currently not wokring properly)**: 
  - **"Submagic Pro"**: Slanted, bold uppercase white text with thick black outline, active spoken word highlighted in **neon green**.
  - **"TikTok Viral"**: Slanted yellow text with thick black outline, active spoken word highlighted in **hot pink**.
  - **"Caption Glow"**: Neon hot-pink shadow glow with the active spoken word highlighted in **gold**.
* **Dynamic Word-by-Word Highlighting**: Automatically tracks elapsed playhead time and highlights the active word frame-by-frame on both player preview and exported files.
* **Layout & Timeline Editors**:
  - Adjust vertical Y-position (`0.1` to `0.9` bias) and Font Size dynamically.
  - Tweak timings manually with `+0.1s`/`-0.1s` buttons.
  - Insert new timing blocks manually (`+ Add Block`) or prune segments (`Delete Caption Block`).

### 🎞️ Swipeable Photo Carousel Slicer
Slice panorama pictures into seamless carousel grids.
* Slices horizontal images into 2 to 5 square segment chunks.
* Interactive carousel previews with indexed indicator badges (`[Slide 1]`, `[Slide 2]`).

---

## 🛠️ Tech Stack & Architecture

* **UI Framework**: Jetpack Compose (Material 3) enforcing a premium Obsidian Black palette.
* **Video Processing**: Media3 Transformer (burns custom Spannable subtitle canvas layers onto raw video frames dynamically).
* **Machine Learning**: Google ML Kit (on-device speech-to-text).
* **Image Loading**: Coil (uses `Size.ORIGINAL` for lossless image loading and scaling).
* **Async Concurrency**: Kotlin Coroutines & Flow.

---

## 🚀 Getting Started

### Prerequisites
* Android Studio (Koala or newer recommended).
* Android SDK (API 34+ recommended).

### Building the Project
1. Clone the repository.
2. Open the project inside Android Studio.
3. Sync Gradle and run the compile task:
   ```bash
   ./gradlew compileDebugKotlin
   ```
4. Run unit tests to check suite integrity:
   ```bash
   ./gradlew test
   ```
5. Deploy the app onto an emulator or physical device.
