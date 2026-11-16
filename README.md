# Fotorgrafi

> **Open-source mobile camera application engineered for maximum hardware ISP utilization and zero-degradation social media output.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![NDK](https://img.shields.io/badge/Native-C%2B%2B%20%2F%20NDK-00599C?logo=c%2B%2B&logoColor=white)](https://developer.android.com/ndk)
[![CameraX](https://img.shields.io/badge/API-CameraX%20%2F%20Camera2-4285F4)](https://developer.android.com/training/camerax)

**Fotorgrafi** hadir untuk menjembatani kesenjangan kualitas kamera antara aplikasi kamera bawaan (*stock*) dan aplikasi pihak ketiga pada ekosistem Android. Dibangun dengan akses langsung ke *pipeline* perangkat keras serta profil *encoding* yang dikalibrasi khusus agar hasil foto dan video tetap tajam saat diunggah ke Instagram, TikTok, WhatsApp, maupun Facebook.

---

## Fitur Utama

### Hardware-Level Optimization
* **Direct Sensor & ISP Pipeline:** Memanfaatkan **CameraX** dengan ekstensi **Camera2 Interop** untuk kontrol manual penuh atas *exposure time*, ISO, *white balance*, dan *focus distance*.
* **Native C++ Image Processing (NDK):** Pemrosesan *frame* secara *real-time* menggunakan C++ dan CMake untuk latensi *shutter* minimal (Zero-Shutter Lag) serta reduksi *noise* tanpa menghilangkan detail tekstur kulit.
* **RAW + JPEG/HEIF Dual Capture:** Dukungan pengambilan gambar DNG (10-bit/12-bit RAW) bersamaan dengan hasil pemrosesan siap pakai.
* **Hardware-Accelerated Video Encoding:** Memanfaatkan `MediaCodec` berbasis perangkat keras untuk perekaman 4K 60fps dengan *bitrate* stabil.

### Social Media Output Optimization
* **Anti-Compression Bitrate & Resolution Profiling:** Menyesuaikan resolusi dan kurva kompresi secara otomatis agar tidak terkena *downscaling* agresif dari algoritma kompresi Instagram Stories, Reels, dan WhatsApp.
* **Color Space & HDR Tone Mapping:** Konversi ruang warna akurat dari sensor (*Wide Color Gamut* / P3 / HLG) ke **sRGB / Rec.709** sebelum penyimpanan, mencegah warna terlihat pudar (*washed out*) saat diunggah ke media sosial.
* **Smart Aspect Ratio & Framing:** *Overlay* panduan komposisi (3:4, 9:16, 1:1) dengan area aman (*safe zone*) untuk antarmuka Stories dan Reels.

---

## Tech Stack & Arsitektur

* **Bahasa Pemrograman:** Kotlin & C++ (JNI / NDK)
* **Camera Framework:** Android Jetpack CameraX & Camera2 API
* **Build System:** Gradle (Kotlin DSL) & CMake
* **Graphics & Color Pipeline:** OpenGL ES / Vulkan & Android `HardwareBuffer`
* **Min SDK:** Android 10 (API 29) | **Target SDK:** Android 15 (API 35)

```text
Fotorgrafi/
├── app/
│   ├── src/main/
│   │   ├── cpp/                 # Native C++ ISP & Color Matrix processing (CMake)
│   │   ├── java/com/repositoro/fotorgrafi/
│   │   │   ├── camera/          # CameraX & Camera2 Interop engine
│   │   │   ├── encoder/         # Social-media calibrated MediaCodec profiles
│   │   │   ├── ui/              # Viewfinder, manual controls, & safe-zone overlays
│   │   │   └── utils/           # EXIF injector & Color Space converter
│   │   └── res/                 # Layouts, themes, & vector assets
└── build.gradle.kts
```

---

## Memulai (Getting Started)

### Prasyarat
* **Android Studio** (disarankan versi Ladybug / Koala atau lebih baru)
* **Android NDK** & **CMake** (dapat diinstal melalui *SDK Manager > SDK Tools*)
* Perangkat fisik Android dengan dukungan **Camera2 API Level `FULL` atau `LEVEL_3`** (Emulator tidak disarankan untuk pengujian *hardware ISP*).

### Instalasi & Build
1. **Clone repositori ini:**
   ```bash
   git clone [https://github.com/repositoro/Fotorgrafi.git](https://github.com/repositoro/Fotorgrafi.git)
   cd Fotorgrafi
   ```

2. **Buka di Android Studio:**
   * Pilih **File > Open** dan arahkan ke direktori proyek `Fotorgrafi`.
   * Tunggu proses *Gradle Sync* dan konfigurasi *CMake* selesai.

3. **Hubungkan perangkat & jalankan:**
   * Aktifkan **USB Debugging** pada perangkat Android Anda.
   * Pastikan perangkat terdeteksi via ADB:
     ```bash
     adb devices
     ```
   * Klik **Run (`Shift + F10`)** untuk memasang aplikasi ke perangkat.

---

## Profil Kalibrasi Media Sosial

| Platform | Target Resolusi | Color Profile | Audio / Video Codec | Optimasi Khusus |
| :--- | :--- | :--- | :--- | :--- |
| **Instagram (Reels/Story)** | `1080 x 1920` (9:16) | sRGB / Rec.709 | H.264 High Profile / AAC 48kHz | Bitrate optimal (~15–20 Mbps) untuk menghindari *re-encoding* server |
| **Instagram (Feed Photo)** | `1080 x 1350` (4:5) | sRGB (8-bit) | JPEG Progressive (95% Quality) | *Sharpening* ringan pasca-downscale agar tekstur tetap tajam |
| **TikTok** | `1080 x 1920` (9:16) | Rec.709 | HEVC (H.265) / H.264 | Stabilisasi EIS + *frame pacing* konstan 30/60 fps |
| **WhatsApp Status/Media** | `1080p` / `1600px` | sRGB | H.264 / JPEG | Penyesuaian batas ukuran file agar lolos filter kompresi HD WhatsApp |

---

## Roadmap Pengembangan

- [x] Integrasi dasar CameraX Preview & ImageCapture
- [x] Setup lingkungan NDK & CMake untuk pemrosesan *native*
- [ ] Implementasi kontrol manual penuh (Shutter Speed, ISO, Focus Peaking)
- [ ] Pipeline *Tone Mapping* 10-bit HDR ke 8-bit Rec.709 secara *real-time*
- [ ] Preset profil *export* langsung ke Instagram, TikTok, dan WhatsApp
- [ ] Dukungan LUT (`.cube`) kustom berbasis GPU shader

---

## Kontribusi

Kontribusi dari komunitas sangat terbuka! Jika Anda ingin menambahkan profil kalibrasi perangkat baru atau mengoptimalkan *shader* pemrosesan gambar:

1. *Fork* repositori ini.
2. Buat *branch* fitur baru (`git checkout -b feat/nama-fitur`).
3. *Commit* perubahan Anda menggunakan format [Conventional Commits](https://www.conventionalcommits.org/) (`git commit -m "feat: tambah profil encoder instagram reels"`).
4. *Push* ke *branch* tersebut (`git push origin feat/nama-fitur`).
5. Buat **Pull Request**.

---

## Lisensi

Didistribusikan di bawah Lisensi **MIT**. Lihat file `LICENSE` untuk informasi lebih lanjut.

---
*Dikembangkan oleh [@repositoro](https://github.com/repositoro)*
