<!-- prettier-ignore -->
<div align="center">

<img src="./app/src/main/ic_launcher-playstore.png" alt="Acheron" height="110" />

# SeQ — Acheron — Mobile Vault

[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API 24+](https://img.shields.io/badge/Android-API%2024+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![BouncyCastle](https://img.shields.io/badge/Crypto-BouncyCastle-A91D22?style=flat-square)](https://www.bouncycastle.org)
[![Gradle 9](https://img.shields.io/badge/Gradle-9-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)

[Overview](#overview) · [Features](#features) · [Architecture](#architecture) · [Getting started](#getting-started) · [Tech stack](#tech-stack) · [Related projects](#related-projects)

---

</div>

## Overview

Acheron is the Android client of the **SeQ** suite: an encrypted vault for credentials, cards, identities, notes, and other sensitive data. It guards secrets locally with Argon2id-derived keys while syncing them against a SeQ backend.

> [!NOTE]
> The UI and in-app strings are written in Spanish. This README is in English for contributor accessibility.

## Features

- **Master password vault** — unlock with a single master password; change it at any time with full key and salt rotation (Argon2id).
- **Seven storable types** — Account, Credit Card, Secure Note, Identity, Bank Account, Wifi Network, and Software License, each with dedicated forms, icons, and field-level secret masking.
- **Strong cryptography** — Argon2id key derivation and AES encryption via BouncyCastle, with PBKDF2 as a fallback strategy.
- **Remote sync** — full-vault and granular per-item sync against the SeQ server over a Retrofit/OkHttp API.
- **Session security** — JWT-based auth with tokens stored in `EncryptedSharedPreferences`, plus explicit lock and logout flows.
- **Acheron design system** — a dark, Material 3 interface with a custom amethyst/laurel-gold brand identity and an animated river background.

## Architecture

The project is a two-module Gradle build:

```
SeQ-AcheronMobile/
├── app/            Android client — Kotlin + Jetpack Compose
│   └── src/main/java/com/seq/acheronmobile/
│       ├── data/         network, repositories, vault crypto bridge
│       ├── di/            simple service locator
│       ├── navigation/    Compose navigation graph
│       └── ui/            screens, view models, theme
└── AcheronCore/    Vault engine — pure Java library
    └── src/main/java/com/seq/acheron/
        ├── vault/         Vault, VaultFactory, User, storables
        ├── util/          cryptographic helpers
        └── exceptions/
```

`AcheronCore` is platform-agnostic and shared with the web counterpart of SeQ via crypto interop tests, so vault logic stays consistent across clients.

## Getting started

### Prerequisites

- Android Studio (or the Gradle/AGP toolchain it ships with)
- JDK 17+
- A running SeQ backend reachable from your device/emulator

### Configure the backend URL

The API base URL is set in [`app/build.gradle.kts`](app/build.gradle.kts):

```kotlin
buildConfigField("String", "SEQ_BASE_URL", "\"http://192.168.1.131:5000/\"")
```

> [!IMPORTANT]
> Point this at your own SeQ server instance before building. Cleartext HTTP traffic is enabled for local development only — use HTTPS for anything beyond a local network.

### Build & run

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified release APK
./gradlew test               # unit tests
```

Minimum supported SDK is Android 7.0 (API 24); target SDK is Android 15 (API 36).

## Tech stack

| Layer | Technology |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 |
| State | ViewModel, StateFlow, Navigation Compose |
| Networking | Retrofit 2, OkHttp 3, Kotlinx Serialization |
| Cryptography | BouncyCastle (Argon2id, AES, RSA/EC), PBKDF2 |
| Storage | Jetpack Security `EncryptedSharedPreferences` |
| Core engine | Java 17, shared `AcheronCore` library |
| Build | Gradle 9, Android Gradle Plugin 9, Kotlin 2.2 |

## Related projects

Acheron Mobile is one client of the broader **SeQ** vault platform, alongside a web client sharing the same crypto primitives and the **Iris** companion interface for handling `.eml` files.
