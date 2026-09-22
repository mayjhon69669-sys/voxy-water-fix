# Voxy personal backport for Minecraft 1.21.1

Voxy 0.2.19 beta for Minecraft 1.21.1, Fabric and Sodium 0.8.12, a distant-water height correction and personal fast-leaf rendering changes.


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
