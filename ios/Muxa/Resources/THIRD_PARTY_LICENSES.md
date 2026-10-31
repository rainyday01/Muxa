# Muxa iOS Third-Party Software

This inventory covers every package currently pinned by Swift Package Manager
and every native library shipped with the app. Full native license texts are
stored under `Vendor/Native/Licenses` and must be included in the in-app
acknowledgements before release.

| Package identity | Locked version | License | Upstream |
| --- | --- | --- | --- |
| `grdb.swift` | 7.11.1 | MIT | https://github.com/groue/GRDB.swift |
| `swift-argument-parser` | 1.8.2 | Apache-2.0 | https://github.com/apple/swift-argument-parser |
| `swiftterm` | 1.15.0 | MIT | https://github.com/migueldeicaza/SwiftTerm |
| `valet` | 5.1.0 | Apache-2.0 | https://github.com/square/Valet |
| `libssh2` | 1.11.1 | BSD-3-Clause | https://www.libssh2.org |
| `openssl` | 3.5.7 | Apache-2.0 | https://www.openssl.org |

Native source and binary SHA-256 values are recorded in
`Vendor/Native/Provenance.json`.

`swift-argument-parser` is a transitive SwiftTerm package dependency declared
for SwiftTerm's Termcast executable target. Muxa links the SwiftTerm library
target only; the ArgumentParser product is not linked into the app target.
