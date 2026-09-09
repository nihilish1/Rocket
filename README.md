# Rocket Live Wallpaper

An Android live wallpaper: your rocket flies between home-screen icons in
straight legs, alternating horizontal and vertical, turning at grid
intersections.

## Why it needs manual setup (important)

Android wallpapers run **behind** the launcher and have no API to read
where the launcher placed its icons or widgets — that data belongs to the
launcher app and isn't shared for privacy reasons. So instead of
auto-detecting your layout, the rocket flies along a **virtual grid** that
you configure once to match your real home screen, and you mark any
widget cells as "blocked" so the rocket routes around them.

## How to set it up — no Android Studio needed

You can get this onto your phone using only a web browser, via a free
GitHub account that builds the app for you in the cloud. See the numbered
steps below (also in the chat). Skip to "Configuring the grid" if you'd
rather use Android Studio.

## How to set it up (Android Studio route)

1. Open the project in Android Studio (File > Open > select the
   `RocketWallpaper` folder). Let Gradle sync.
2. Open `app/src/main/java/com/example/rocketwallpaper/RocketWallpaperService.kt`
   and edit the `GridConfig` object at the top:
   - `COLUMNS` / `ROWS` — match your launcher's home screen grid
     (Launcher settings > Home screen > Grid size, e.g. 4x6, 5x6).
   - `TOP_MARGIN_FRACTION` / `BOTTOM_MARGIN_FRACTION` — how much of the
     screen height to leave clear at the top (status/search bar) and
     bottom (dock). Defaults are reasonable starting points; nudge them
     if the rocket clips the dock or status bar on your phone.
   - `BLOCKED_CELLS` — list every `(col, row)` cell (0-indexed, top-left
     is `(0,0)`) that's covered by a widget or anything off-grid. A
     4-wide by 2-tall widget in the top-left corner would block
     `(0,0)…(3,1)`. Update this whenever you rearrange your home screen.
3. Build > Run on your device, or Build > Generate Signed Bundle/APK if
   you want to sideload it manually.
4. On the phone: long-press the home screen > Wallpapers > pick
   "Rocket Wallpaper" from Live Wallpapers.

## Tuning the flight

In the same file:
- `speedPxPerSec` — how fast the rocket travels.
- `spriteBaseHeadingDeg` — the artwork's resting orientation (currently
  assumes the nose points up-and-right, ~-45°). If the rocket looks
  rotated wrong once running, adjust this value.

## Notes

- The rocket's flame is baked into the artwork and rotates with the
  sprite, so it automatically trails behind the direction of travel.
- If every cell on one axis is blocked, the rocket automatically tries
  the other axis; if it's fully boxed in, it teleports to a free cell
  rather than getting stuck.
- Multiple screen sizes/orientations are handled — the grid recomputes
  in `onSurfaceChanged`.
