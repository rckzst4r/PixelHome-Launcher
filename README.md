# PixelHome Launcher (Early Build)

A Pixel-style Android home screen replacement built from the ground up using **Kotlin**, **Jetpack Compose**, **Material 3 (Material You)**, and **Coroutines / StateFlow**. 

This launcher replicates the clean Google Pixel look while featuring an independent mathematical squircle rendering engine, fluid spring gestures, and deep layout customization.

---

> ## ⚠️ Critical Disclaimers & Known Limitations
>
> 1. **Early Development Build:** This is an unstable early-stage release. Features may change rapidly, and unexpected crashes or layout glitches can occur.
> 2. **Performance Notice:** The app may feel sluggish or experience frame drops depending on your device specs and number of installed apps. Optimization passes (such as Baseline Profiles and minimizing recomposition mid-gesture) are actively in progress.
> 3. **AI-Assisted Project:** This codebase was created and architected with the assistance of artificial intelligence based on targeted design and technical prompts.
> 4. **Xiaomi / HyperOS / MIUI Icon Color Issue:** Dynamic themed icon color tinting currently **does not work properly on Xiaomi devices**. Xiaomi's customized framework overrides standard Android AOSP `AdaptiveIconDrawable` monochrome layers and intercepts runtime `ColorFilter` graphics calls.

---

## 📸 Overview & Features

### Core Experience
* **Single Activity Architecture:** Fully declarative UI using Jetpack Compose and Material 3 design tokens.
* **Pixel Home Screen:**
  * Top "At-a-Glance" style widget (date, calendar events, weather placeholder).
  * 5×5 configurable desktop icon grid with custom page indicators.
  * 4-icon dock (hotseat) with an integrated bottom search pill.
  * Native wallpaper pass-through with edge-to-edge system bar handling.
* **App Drawer & Work Profiles:** 
  * Swipe-up drawer driven by velocity-based spring snaps.
  * Managed profiles (Work Profile tabs) handled cleanly via Android's `LauncherApps` API.
* **App Shortcuts & Badging:** 
  * Long-press icon popups using `LauncherApps.getShortcuts`.
  * Notification badges hooked into `NotificationListenerService`.

### Squircle Engine
* **True Superellipse:** Math-driven clipping based on the formula:
  $$\left\vert{}\frac{x}{a}\right\vert{}^n + \left\vert{}\frac{y}{b}\right\vert{}^n = 1 \quad (default\ n = 4)$$
* Rather than a generic rounded rectangle, icons and container surfaces are shaped dynamically at draw time using `graphicsLayer` clipping without expensive runtime bitmap recreations.

### Motion & Physics
* Spring-physics gesture tracking (~0.8 damping, medium-low stiffness).
* App opening animations expanding directly from the tapped icon's coordinate bounds.
* Window blur support on Android 12+ (`RenderEffect` / `Window.setBackgroundBlurRadius`) with fallback scrims on older Android releases.

---

## 🛠️ Architecture & Tech Stack

| Layer | Technologies / Libraries |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose (Material 3 / Material You) |
| **Architecture** | MVVM with ViewModel, StateFlow, Coroutines |
| **Persistence** | Room DB (Grid, Folders, Widgets), DataStore Preferences (Settings) |
| **System APIs** | `LauncherApps`, `AppWidgetHost`, `RoleManager` |
| **Target SDKs** | minSdk 26 (Android 8.0), compileSdk / targetSdk 34+ |
