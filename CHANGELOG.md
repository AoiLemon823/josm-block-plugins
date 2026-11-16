# Changelog

All notable changes to the JOSM BlockFill and BlockLayout plugins.

## [1.0.1] — 2026-11-16

### BlockFill (街区填充) 1.0.1

- Optional highway-intersection corner chamfer: toggle plus configurable cut distance.
  Only convex block corners on a highway junction are cut; reflex and nearly straight corners are left alone.
- Chamfer distance is measured on the generated (inset) block boundary and is clamped to the adjacent road length.
- Corner geometry references [Columbina](https://github.com/Daredemodaisuki/Columbina) (`chamfer/ChamferGenerator`).
- Both plugin panels now collapse/expand from the JOSM title bar (the content is mounted with `ToggleDialog.createLayout`, which registers the title-bar handler); dock and minimized state are stored per plugin.
- BlockFill replaces the long help paragraph with per-parameter tooltips; the mode shortcut is shown in JOSM's main status bar, and the panel button toggles the fill mode using the shortcut resolved by JOSM.
- Deleting the last layer (closing the map view) no longer throws: plugin cleanup releases modes and listeners instead of removing dialogs from the already destroyed map frame.

## [1.0.0] — 2026-11-09

### BlockFill (街区填充) 1.0.0

First public release.

- Road-enclosed block detection with hover preview and left-click generation.
- Chinese national land-use classification (29 categories, CN-2023 style codes).
- Configurable inset (0–100 m, default 5 m).
- Optional inclusion of service roads and pedestrian streets.
- OGF fictional naming: residential (5 styles), commercial (4 styles), business-office (4 styles).
- Editable name vocabulary (`name-vocabulary.tsv`).
- One-undo creation (Ctrl+Z removes the entire batch).
- Custom map paint style for land-use preview.

### BlockLayout (建筑排布) 1.0.0

First public release.

- **Residential housing**: rectangular layout, sample-footprint tiling, direction pick from parcel edge.
- **Three presets**: multi-storey (5–6F), mid-rise (9–11F), high-rise (18–24F), each with independent parameter memory.
- **Sun-spacing compliance**: configurable height × sun factor (default 1.5× per GB 50180).
- **Community shops**: inward storefront polygons with chamfered corners; multipolygon output with holes for building/road obstacles.
- **Industrial factories**: random-dimension rectangular footprints, grouped rows, optional service roads (developer mode).
- **Street trees** (developer mode): place trees along service roads or selected highways per CJJ/T 75-2023 spacing (min 4 m, default 6 m).
- One-undo batch creation; preview before commit.
- Fixed: duplicate closing node defect — generated ways no longer carry a redundant trailing node.
- Developer mode (`-Dblocklayout.dev=true`) hides road generation and street trees from normal users.

### Infrastructure

- Both plugins compiled against JOSM 19555, Java 17 target.
- Unit tests: geometry, undo integration, duplicate-node regression, UI startup.
- Custom programmatic icons for both plugins (no external image dependencies).
- BlockLayout no longer adds a duplicate *Tools* menu entry; entry points are the side panel and toolbar button only.
