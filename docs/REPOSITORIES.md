# Repository Configuration

`settings.gradle.kts` selects repositories based on the Gradle property `useChinaMirrors`.

## Default (CI and non-China builds)

Without `-PuseChinaMirrors=true` the build uses only the official, upstream repositories:

- `pluginManagement`: `google()`, `mavenCentral()`, `gradlePluginPortal()`
- `dependencyResolutionManagement`:
  - `mavenLocal` (only for `io.github.libxposed`)
  - `maven("https://jitpack.io")` (only for `com.github.*` / `io.github.*`)
  - `google()`
  - `mavenCentral()`

This is the configuration used by GitHub Actions CI.

## China mirrors (opt-in)

Pass the property to use Aliyun and HuaweiCloud mirrors:

```bash
./gradlew assembleDebug -PuseChinaMirrors=true --no-daemon
```

When enabled:

- `pluginManagement` uses Aliyun Gradle plugin mirror and HuaweiCloud Maven mirror.
- `dependencyResolutionManagement` uses Aliyun Google mirror and HuaweiCloud Maven mirror.
- Each mirror has a `content` filter so artifacts are only fetched from the repository that is supposed to host them.
- `io.github.libxposed` is excluded from the Huawei mirror and resolves from `mavenLocal` only.

If the China mirrors return 502 or become unavailable, remove `-PuseChinaMirrors=true` to fall back to the official repositories. No repository list or dependency version is changed.
