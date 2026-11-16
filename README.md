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
