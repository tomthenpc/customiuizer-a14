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
| `app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.java` | `public static void install(PackageReadyParam, PrefMap)` called from Kotlin `MainModule`. | Low: convert to `object` with `@JvmStatic`. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java` | `public static void installPostAttach(...)` called from Kotlin `MainModule`. | Low: small, one hook. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.java` | `public static void install(...)` called from Kotlin `MainModule`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.java` | `public static void install(...)` called from Kotlin `SystemUiBootstrapCoordinator`. | Low. |
| `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | Many `public static` helper methods called from Kotlin and used in hooks. Methods are referenced directly, not by reflection, but the class name is well-known in the project. | Medium: large file with overloads; should be migrated in one focused batch with tests. |

## Migration Plan (P9.2)

1. Convert the 13 installer files first: each is a thin registration loop and can become a Kotlin `object` with `@JvmStatic` to preserve `MainModule` call sites.
2. Convert `XposedHelpers.java` in one focused batch. Preserve all public static method JVM signatures (`@JvmStatic`, `@JvmOverloads` where needed) because hook call sites and tests rely on them.
3. After migration, this allowlist should only contain `MainModule.java` and `MemberUtilsX.java`.
