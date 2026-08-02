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

### KEEP_JAVA_VENDOR_OR_GENERATED

| File | Reason |
|------|--------|
| `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Third-party Apache Commons Lang 3 patch for `MemberUtils`; not project code, should not be migrated. |

### KEEP_JAVA_FRAMEWORK_ENTRY

| File | Reason |
|------|--------|
| `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | The libxposed `XposedModule` entry point. The framework discovers and instantiates this class by class name; converting it to Kotlin would change the compile-time class/signature and require a matching Xposed manifest update. Keep as Java. |

### MIGRATE_TO_KOTLIN

| File | JVM/ABI Notes | Migration Risk |
|------|---------------|----------------|
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | Many `public static` helper methods called from Kotlin and used in hooks. Methods are referenced directly, not by reflection, but the class name is well-known in the project. | Medium: large file with overloads; should be migrated in one focused batch with tests. |

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

## Migration Plan (P9.2)

1. Convert `XposedHelpers.java` in one focused batch. Preserve all public static method JVM signatures (`@JvmStatic`, `@JvmOverloads` where needed) because hook call sites and tests rely on them.
2. After migration, this allowlist should only contain `MainModule.java` and `MemberUtilsX.java`.
