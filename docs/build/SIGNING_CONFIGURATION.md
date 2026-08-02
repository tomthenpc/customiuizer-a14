# A14 Signing Configuration

```text
DocumentKind: CURRENT
Product: customiuizer-a14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: pending
EvidenceState: STATIC
GeneratedBy: A14_OWNER_SIGNING_INSTRUCTION
SourceOfTruth: A13_A14_SIGNING_SOURCE_OF_TRUTH_V1/A14_SIGNING_MEMORY_PROMPT.md
```

## Owner Policy

- Signing configuration must not depend on chat memory or filesystem search.
- Keystore secrets must never be committed to the repository.
- The only authorized source of the properties file path is the Gradle property `customiuizerA14KeystoreProperties` or the environment variable `CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES`.
- The actual keystore file is defined by the `storeFile` entry inside that properties file.

## Gradle Configuration

`app/build.gradle.kts` resolves signing as follows:

```kotlin
val officialRelease = (project.findProperty("officialRelease")?.toString()?.toBoolean() ?: false)

val keystorePropertiesPath =
    providers.gradleProperty("customiuizerA14KeystoreProperties").orNull
        ?: providers.environmentVariable("CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES").orNull

val keystorePropertiesFile = keystorePropertiesPath?.let(::file)
```

- When `officialRelease=false` (debug/develop or normal verification), no signing properties are required.
- When `officialRelease=true`, `keystorePropertiesFile` must exist and contain `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
- `storeFile` must point to an existing keystore.

## Check Script

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-signing-config.ps1
```

The script only reports:

- properties path source (Gradle property or environment variable)
- whether the properties file exists
- whether `storeFile` is defined
- whether the keystore file exists
- whether required fields are present
- whether signing is enabled

It never prints passwords or key content.

## Building with Official Signing

```powershell
.\gradlew.bat :app:assembleDevelop -PofficialRelease=true
```

Only run this after `check-signing-config.ps1` passes.
