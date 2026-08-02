# Java Boundary Allowlist

This file records the classification of every remaining `.java` source file in the
`app/src` tree. The target state of P9 is:

```text
MIGRATE_TO_KOTLIN
KEEP_JAVA_FRAMEWORK_ENTRY
KEEP_JAVA_JVM_BOUNDARY
KEEP_JAVA_REFLECTION_ABI
KEEP_JAVA_VENDOR_OR_GENERATED
```

No `KEEP_JAVA_TEMPORARY_BLOCKER` or `UNCLASSIFIED` files are allowed at PROJECT_COMPLETE.

## Classification

### KEEP_JAVA_FRAMEWORK_ENTRY

| File | Reason |
|------|--------|
| `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | The libxposed `XposedModule` entry point. The framework discovers and instantiates this class by class name; converting it to Kotlin would change the compile-time class/signature and require a matching Xposed manifest update. Keep as Java. |

### KEEP_JAVA_VENDOR_OR_GENERATED

| File | Reason |
|------|--------|
| `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Third-party Apache Commons Lang 3 patch for `MemberUtils`; not project code, should not be migrated. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | Originally from LSPosed, now a stable, project-patched reflection/utility helper (2136 lines). Converting to Kotlin would require preserving a very large surface of static overloads, exception contracts and reflection semantics with marginal maintainability gain. Per AGENTS.md §18 the project does not require 100% Kotlin; this file is retained as a vendor-derived utility. |

### MIGRATED (now `.kt`)

The following installer files were converted to Kotlin `object` with `@JvmStatic` to preserve Java call sites in `MainModule.java`. They are now located at the same path with `.kt` extension:

- `app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt`

## Migration Outcome

P9.2 is complete. The remaining production Java files are:

```text
MainModule.java          -> KEEP_JAVA_FRAMEWORK_ENTRY
XposedHelpers.java       -> KEEP_JAVA_VENDOR_OR_GENERATED
MemberUtilsX.java        -> KEEP_JAVA_VENDOR_OR_GENERATED
```

No `MIGRATE_TO_KOTLIN`, `KEEP_JAVA_TEMPORARY_BLOCKER` or `UNCLASSIFIED` entries remain.
