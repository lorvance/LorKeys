# LorKeys Keyboard ⌨️🚀

<p align="center">
  <b>A modern, privacy-first, and highly customizable Android keyboard designed for speed, beauty, and unmatched productivity.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License" />
  <img src="https://img.shields.io/badge/Version-1.17.6-blue" alt="Version" />
  <img src="https://img.shields.io/badge/Privacy-100%25_Offline_First-success" alt="Privacy" />
</p>

---

## 🌟 Overview

**LorKeys** is an open-source, feature-packed keyboard for Android. Heavily enhanced with modern utilities, it brings together the best elements of Gboard, iOS keyboard aesthetics, and deep customization — all while respecting your privacy without analytics, telemetry, or cloud tracking.

---

## ✨ Full Feature Breakdown

### 🎙️ 1. Smart Input & Utilities
- **Inline Voice Typing (Gboard Style)**: Dictate text without leaving the keyboard. Features real-time speech recognition with a live RMS audio wave indicator.
- **Built-in Google Translator**: Translate text on-the-fly directly above the keys. Includes quick language switches (`RU`, `EN`, `UK`, `KK`, `ES`, `DE`, `FR`, etc.), language reversal (`⇄`), and one-tap insertion into any chat.
- **Instant Math Calculator**: Type arithmetic expressions (`25 * 4`, `150 + 20%`, `(50+10)/2`) anywhere, and the keyboard immediately displays a calculated suggestion pill ready to insert.
- **Emoji Kitchen**: Tap an emoji to browse Google Emoji Kitchen mashups and stickers right above your keys. Send them instantly into supported apps via Android Rich Content (`commitContent`).
- **Advanced Clipboard Manager**:
  - Full history of copied items with pinning support.
  - Welcome onboarding guide & quick dismiss.
  - Search and batch clear capabilities.
- **Swipe Gestures**:
  - **Gesture Typing (Swipe)**: Fast, fluid word tracing.
  - **Spacebar Cursor Control**: Slide across the spacebar to move the cursor smoothly.
  - **Backspace Swipe Delete**: Slide left from the delete key to quickly select and erase words.

---

### 🎨 2. Design, Themes & Aesthetics
- **OLED Dark Theme**: Pure `#000000` pitch-black background with high-contrast keys designed specifically for AMOLED displays and battery efficiency.
- **Frosted Glass (Blur Mode)**: Translucent, frosted glass blur backdrop with native hardware blur on modern Android devices.
- **Light Gray & Material 3 Expressive**: Clean modern themes inspired by Google Material 3 and classic Gboard layouts.
- **Granular Corner Radius Control**: Independent sliders for top corners (up to 96 dp) and bottom corners (up to 64 dp) to match any curved phone screen or gesture bar.
- **Toolbar Customization**: Choose between solid icon backgrounds, flat borderless icons, or minimal floating actions.
- **Material You Dynamic Theming**: Adaptive colors matching your system wallpaper, complete with a monochrome Themed Icon on Android 13+.
- **Fun Easter Eggs**: Toggle special visual modes (such as "Round Keyboard").

---

### 🔊 3. iOS 17 Audio & Visual Suite
- **Authentic iOS 17 Sounds**: Genuine Apple click, delete, and clear sound effects alongside classic keyboard soundscapes.
- **Apple Color Emoji**: Option to render iOS-style emojis system-wide within the keyboard.
- **San Francisco (SF Pro) Font**: Switch between system typography and Apple's signature SF Pro font.
- **Refined iOS Back Navigation**: Minimalist iOS-style chevron (`<`) throughout the entire settings interface.

---

### ⚙️ 4. Layouts, Languages & Ergonomics
- **Extensive Multilingual Support**: Fully localized settings in English, Russian, Ukrainian, Spanish, and more.
- **Quick Language Switch**: Toggle between input languages with a dedicated globe key or spacebar swipe.
- **Ergonomic Modes**:
  - **Split Keyboard**: Comfortable two-handed typing on tablets and foldables.
  - **One-Handed Mode**: Shift keys to the left or right edge with adjustable width.
  - **Compact / Expanded Key Gaps**: Customize spacing for precision or thumb comfort.
  - **Customizable Keyboard Height**: Scale keyboard height from ultra-compact to extra-tall.
- **Persistent Rows & Numpad**: Optional persistent number row, quick emoji row, and dedicated numeric keypad.

---

### 💾 5. Backup & Privacy
- **`.lorkeys` Backup & Restore**: Export all your custom layouts, themes, personal dictionaries, and settings into a single `.lorkeys` file. Restore it anytime via the system file picker.
- **100% Privacy Focused**:
  - No internet tracking, telemetry, or user analytics.
  - Dictionaries and user data remain stored locally on your device.
  - Clean codebase with removed third-party trackers.

---

## 🚀 Getting Started & Installation

1. Download the latest APK from the **[Releases](../../releases)** tab.
2. Open the downloaded `.apk` file on your Android device and install it.
3. Open **LorKeys** and follow the quick setup wizard:
   - Enable LorKeys in your Android Language & Input settings.
   - Select LorKeys as your active input method.
4. Customize your themes, sounds, and layouts in **Settings**!

---

## 🛠️ Building from Source

### Prerequisites:
- Android Studio or command-line SDK tools
- Android SDK Platform 36
- Android NDK
- JDK 17+

### Build steps:
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/LorKeys.git
cd LorKeys

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```
The resulting APK will be generated under `app/build/outputs/apk/`.

---

## 📄 License
This project is licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
