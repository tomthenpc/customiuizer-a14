# A14 Risk Gate Matrix

| Tier | Examples | Mandatory gates |
|---|---|---|
| R0 | documentation and generated report | parser/checker, diff check |
| R1 | pure internal refactor with no behavior change | compile, unit, main/release differential |
| R2 | FeatureSpec, installer, preference route, Hook contract | R1 + contract parity + real callback/behavior test + mutation + Full |
| R3 | system_server, SystemUI, security Hook, reflection, ClassLoader, lifecycle | R2 + fatal tests + args/overload mutations + owner cleanup + exact qualifying-commit CI |
| R4 | R8, signing, release, core JVM ABI | R3 + clean debug/develop builds + artifact semantic diff + signed RC + device evidence |

## R3 minimum mutation set

```text
short args
wrong arg type
wrong exact overload
missing AnyOf candidate
duplicate target
hard/optional mismatch
ordinary partial-install failure
fatal failure
same-loader retry
old callback after cleanup
```

## Evidence rule

A passing test suite is necessary but not sufficient. At least one plausible wrong implementation must be proven to fail the gate.
