# JOSM Block Plugins

Two standalone JOSM plugins for OpenStreetMap / OpenGeofiction map editors:

| Plugin | Jar | Function |
|--------|-----|----------|
| **BlockFill** (街区填充) | `josm-blockfill.jar` | Generate land-use polygons from road-enclosed blocks, classify by Chinese national land-use codes, and generate fictional place names for OGF. |
| **BlockLayout** (建筑排布) | `josm-block-layout.jar` | Place residential buildings, community shops and industrial factories inside selected parcels with sun-spacing compliance, sample-footprint tiling and one-undo batch creation. |

Both plugins are independent: they can be installed separately or together without conflict.

## Requirements

- JOSM 19555 or later
- Java 17+ (built with JDK 21, targeting `--release 17`)

## Installation

1. Download the latest release jar(s) from [Releases](../../releases).
2. Close JOSM completely.
3. Copy the jar(s) to your JOSM plugins directory:
   - **Windows:** `%APPDATA%\JOSM\plugins\`
   - **Linux:** `~/.config/JOSM/plugins/`
   - **macOS:** `~/Library/Application Support/JOSM/plugins/`
4. Restart JOSM.
5. Enable the plugin(s) in *Edit → Preferences → Plugins* if not auto-detected.

## Usage

### BlockFill (Alt+Shift+L)

1. Open an editable OSM data layer with existing roads.
2. Activate via the toolbar button or *Tools → 街区填充…*
3. Hover over a road-enclosed area to preview the generated block.
4. Left-click to create the polygon with the selected land-use tag.
5. Ctrl+Z to undo, Esc to exit.

Land-use categories follow Chinese national classification codes (CN-2023 style). The optional OGF naming panel generates residential, commercial and business-office names from an editable vocabulary.

### BlockLayout (Alt+Shift+B)

1. Select one or more closed parcel ways (`landuse=residential` or similar).
2. Open the *建筑排布* side panel.
3. Choose a mode: rectangular housing, sample-footprint tiling, community shops, or industrial factories.
4. Adjust parameters (dimensions, setbacks, sun-spacing factor, etc.).
5. Click *开始排布预览* to preview, left-click to confirm, Ctrl+Z to undo.

Three housing presets are built in:
- **多层住宅小区** (5–6F): 12×48 m, 18 m height, 1.5× sun factor.
- **中高层住宅** (9–11F): 14×52 m, 36 m height, staggered.
- **高层住宅** (18–24F): 16×60 m, 72 m height.

Industrial mode generates rectangular `building=industrial` footprints with random dimensions, optional grouped rows, and a developer-mode road generation toggle.

### Developer Mode

Road generation and street-tree features are hidden by default. To enable:

```
java -Dblocklayout.dev=true -jar josm-custom.jar
```

Or set the system property `blocklayout.dev` in your JOSM startup script.

## Building from Source

```powershell
# BlockFill
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1

# BlockLayout
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-layout.ps1
```

The build uses a bundled Zulu JDK 21 (in `tools/`). Output jars are placed in `dist/`.

## Project Structure

```
src/org/openstreetmap/josm/plugins/
  blockfill/     — BlockFill plugin (land-use)
  blocklayout/   — BlockLayout plugin (buildings)
test/            — Unit and integration tests
resources/       — Land-use catalog, mapcss style, name vocabulary
docs/            — Design notes and research
```

## License

MIT License. See [LICENSE](LICENSE).

Name generation vocabulary is derived from public OpenStreetMap data (ODbL 1.0). See [docs/NAMING-RESEARCH.md](docs/NAMING-RESEARCH.md) for methodology.
