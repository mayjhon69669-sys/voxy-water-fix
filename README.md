# Voxy personal backport for Minecraft 1.21.1

Voxy 0.2.19 beta for Minecraft 1.21.1, Fabric and Sodium 0.8.12, with the distant-water height correction and personal fast-leaf rendering changes.

## Current test build

The `mc1211` branch now includes:

- Solid fast-leaf LOD geometry with native model shading and biome tint handling, including a repair for grey Nature’s Spirit mahogany leaves.
- A **Fog and LOD fade** setting with **Fog & fade**, **Fog**, **Fade**, and **Off**. Edge fade covers the outer 10% of the LOD render distance. Existing fog and cloud controls remain.
- The fixed-point software rasterizer with 13 integer bits.
- Ingest world-reference cleanup and retained loading/update optimizations.
- Repeated-node warning suppression and reduced redundant bakery logging.
- A server-only Sodium version provider and the existing Iris shader-source compatibility hooks.

The water correction marks fluid block models and adjusts the top-face depth offset according to the active LOD scale.

Download the JAR from [Releases](https://github.com/mayjhon69669-sys/voxy-water-fix/releases). Install only one Voxy JAR and restart Minecraft after replacing it. These are experimental personal builds; in-game validation is still needed. With shader packs enabled, the pack controls its own fog/fade effects.

The previous clean water-fix source is preserved at the `clean-water-mc1211-2026-09-22` tag. See [CHANGELOG.md](CHANGELOG.md) for this test build and its validation.

This is an unofficial backport. Do not request support for it from the original Voxy developers.

## Building

Java 21 or newer is required.

On Windows:

```powershell
.\gradlew build
```

On Linux or macOS:

```sh
./gradlew build
```

Local build output is written to `build/libs/` and is excluded from version control.

For the additional rasterizer and shader checks, run `./gradlew featureRegression` (Windows: `.\gradlew featureRegression`). This requires an OpenGL 4.5 desktop environment and creates a hidden verification window.

## Credits and license

Voxy was created by MCRcortex. This repository is based on the [Minecraft 1.21.1/Sodium 0.8 backport](https://github.com/m3t4f1v3/voxy/tree/mc_1211-sodium0.8.12). Fog/fade was adapted from the supplied newer development source; this is not a wholesale update to the newer renderer. See [LICENSE.md](LICENSE.md) for the project's license notice.
