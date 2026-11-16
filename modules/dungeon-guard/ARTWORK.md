# Transparent area banner

File: `art/dungeon-area-banner.png` (also embedded in the mod as `assets/dungeonguard/textures/gui/area_banner.png`).

- Dimensions: 2172 × 724, a 3:1 aspect ratio.
- Format: RGBA PNG with real transparent pixels; not a painted checkerboard.
- Design: cute original orange fox shinobi, plain forehead protector, parchment scroll, gold border, red cord and kunai; Minecraft-inspired pixel art.
- Text: deliberately blank in the PNG. The optional client HUD draws the server's configured element, dungeon/boss-area title, and boss name.
- Safe text rectangle: horizontal17–83%, vertical52–82% of the canvas. The fox extends above the scroll.
- Default in-game size: up to330×110 scaled GUI pixels. Placement starts near the top, below any rendered boss bars, and avoids known occupied HUD rectangles. Text fits the parchment and uses Minecraft's font.
- Crowded screens: artwork can shrink to260pixels wide, then falls back to a compact text card if the image cannot fit. Combat controls and boss bars keep their reserved space.

Keep the PNG unchanged if using the included renderer. Custom replacements should keep the same dimensions, alpha channel and text area. The art was generated for this addon; no Minecraft or Naruto mod assets are bundled.
