# Changelog

## 0.2.19-beta-mc1.21.1-leaf-tint-fog-fade-test — 2026-09-22

Mahogany LODs previously lost their colour because the custom leaf path queried a colour provider without biome/world context. Providers are now retrieved from their registration, and fully tinted custom leaf cubes retain the metadata needed for Voxy’s biome-colour lookup. Solid geometry, the existing leaf pattern and native model shading remain; mixed-tint models retain their previous fallback.

Added four fog/fade modes to the Sodium menu and rendering pipeline, with a horizontal fade near the LOD-distance edge. Retained the existing fog/cloud controls and corrected fractional fog-slider division. Existing disabled environmental-fog configurations migrate to Off; otherwise the new default is Fog & fade.

Ported the 13-bit fixed-point rasterizer, capped repeated in-flight-node warnings, removed redundant bakery exception logging, and included a server-only nested Sodium version provider. Ingest jobs now hold world references through completion and release queued references on shutdown. Existing loading/update optimizations and Iris shader-source registration are retained. Performance improvements have not been benchmarked.

Validation:

- Gradle build, access-widener validation and JAR remapping passed.
- Raster regression checks passed at 16, 32 and 64 pixels with exact colour and tint preservation.
- All 16 fog/fade and depth-mode fragment shader variants compiled in an OpenGL context.
- Final JAR bytecode and nested provider metadata were inspected.

Minecraft gameplay, resource-pack appearance, shader-pack integration and dedicated-server startup still need runtime testing.

Tested JAR SHA-256: `15025E14C4387E43525445641137B335262F63DDE6288A014E0965D94E659B48`.

The previous clean water-fix branch state is retained as `clean-water-mc1211-2026-09-22`.
