# Voxy 0.2.19 beta backport for Minecraft 1.21.1

This branch backports Voxy 0.2.19 beta to Minecraft 1.21.1 with Fabric and Sodium 0.8. It also fixes a visible height seam between water surfaces rendered at different distant LOD levels.

The water correction marks fluid block models and adjusts the top-face depth offset according to the active LOD scale. No custom leaf rendering, leaf textures, or leaf opacity changes are included.

This is an unofficial source-only backport. Do not request support for it from the original Voxy developers.

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

## Credits and license

Voxy was created by MCRcortex. This repository is based on the [Minecraft 1.21.1/Sodium 0.8 backport](https://github.com/m3t4f1v3/voxy/tree/mc_1211-sodium0.8.12). See [LICENSE.md](LICENSE.md) for the project's license notice.
