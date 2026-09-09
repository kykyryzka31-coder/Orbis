# Testing Orbis

## CI

`Android CI` runs on every push and pull request and performs:

1. JVM unit tests.
2. Android lint.
3. Debug APK compilation.
4. APK and lint report upload as workflow artifacts.

`Android Emulator Smoke` boots an API 35 x86_64 emulator and launches `MainActivity` through an instrumentation test to catch startup/runtime failures that compilation cannot detect.

## Local environment

Recommended baseline:

- JDK 17
- Gradle 9.6.0
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0

For physical-device renderer testing, test both Vulkan-capable hardware and at least one OpenGL fallback device. Emulator testing is primarily a correctness smoke test and is not a GPU performance benchmark.
