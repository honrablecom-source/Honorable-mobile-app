# npm security audit

Audit date: 2026-08-25. Command: `npm audit --json`. No forced audit fix was used.

The initial report contained 9 high-severity package entries. The unused direct dependency `@react-native/new-app-screen` was safely removed, reducing the report to 8. The remaining entries form one React Native/Metro dependency chain rather than eight independent application vulnerabilities.

| Package | Direct/runtime classification | Advisory/path | Safe path and breaking risk |
|---|---|---|---|
| `image-size` | Transitive build-time Metro parser | GHSA-w3rx-r6r6-pgpr and GHSA-5p2g-fcmc-qvqq: crafted ICNS/JXL/HEIF may cause an infinite-loop denial of service | No compatible patched version is selected by the current RN 0.87 Metro tree. Do not feed untrusted image assets to Metro. npm proposes moving the stack to RN/Metro 0.86.3, which is a framework downgrade and is not a safe automated fix. |
| `metro` | Transitive development/bundling tool | Includes vulnerable `image-size`, `metro-config`, and `metro-transform-worker` paths | Wait for the supported RN 0.87 patch line or deliberately test a framework migration. Forced downgrade has native/template compatibility risk. |
| `metro-config` | Transitive development/bundling tool | Via `metro` | Same as above. |
| `metro-transform-worker` | Transitive development/bundling tool | Via `metro` | Same as above. |
| `@react-native/metro-config` | Direct development configuration | Via `metro-config` | Keep aligned exactly with React Native 0.87; npm's 0.86.3 suggestion is a breaking cross-version mismatch. |
| `@react-native/community-cli-plugin` | Transitive development CLI | Via Metro chain | Update only with a supported React Native patch set. |
| `@react-native/virtualized-lists` | Transitive runtime library | npm links its finding through `react-native`; no separate advisory detail is emitted | Update with a supported React Native patch, not independently. |
| `react-native` | Direct runtime/framework | Aggregate path through CLI plugin and virtualized lists | npm proposes 0.86.3 and marks it semver-major. Downgrading would undo the reviewed 0.87 native foundation, so it remains pending upstream/supported patch review. |

Exposure is primarily at development/bundle time; the concrete published advisories in this report concern Metro's parsing of crafted local build assets. Repository-controlled assets should remain trusted, and CI dependency updates must be reviewed. This does not make the findings harmless, but it makes `npm audit fix --force` an unjustified migration risk.

Remaining status: 8 high, 0 critical. Re-audit when a supported React Native 0.87 patch updates Metro/image-size or before release.
