# Build and verification

This Windows build intentionally targets JOSM 19555 and uses the JOSM jar from the user's selected local install. Run `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1` in the project root.

The local prototype build was verified with JOSM 19555 (`AppData\Local\JOSM\app\josm-custom.jar`), Azul Zulu JDK 21.0.13 and Java 17 bytecode. The local user-level Java on this machine is only Java 8; do not compile this project with that old `java` command. A portable JDK 21 was downloaded solely into the project `tools\` directory for this build. For another machine, set `$JavaHome` in `build.ps1` to a JDK 21 installation and `$JOSM` to the target JOSM installation jar.

## Tests

`BlockGeometryTest` covers a closed ring, open spur pruning, inset/edge safety, unsupported inner rings, adjacent faces and shared edges. `BlockFillModeTest` verifies road filtering and non-shared-node topology using local JOSM data primitives. `CatalogSmokeTest` verifies the UTF-8 class catalog and checks it includes broad urban classes and only explicit OSM mappings.

The JOSM installation version was checked from the local jar manifest (19555). The plugin version is explicitly limited to JOSM 19555 rather than claiming compatibility with untested JOSM releases.
