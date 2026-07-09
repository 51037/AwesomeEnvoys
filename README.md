# AwesomeEnvoys

A fork of [CrazyEnvoys](https://github.com/Crazy-Crew/CrazyEnvoys) that turns envoy events into Rust-style supply drops for vanilla survival servers. Instead of scattering crates around spawn, every event announces a random drop zone somewhere in the wilderness and sends the whole server racing to it.

## What this fork adds

- **Random drop zones** — each event picks a random spot within a configurable radius of your center (default ±1000 blocks x/z, at least 100 from center) and clusters every crate within a tight spread (default 64 blocks) of that point. Open water is avoided when possible and the zone always fits inside the world border.
- **Coordinates in every announcement** — the zone is chosen when the event is scheduled, so warnings tell players exactly where to go: *"Supply drop incoming in 30m near 640, -872. Fight for it!"*
- **Boss bar navigation** — a per-player boss bar appears ahead of the event (default 30 minutes) with the zone coordinates and a countdown. Once crates land, it switches to a live directional arrow (↑ ↗ → ↘ ↓ ↙ ← ↖) and the distance to the nearest unclaimed crate, re-targeting as crates get looted. The progress bar fills as the drop approaches, then tracks how close you are.
- **Safer generation** — the random-location search is capped, so an unlucky zone (e.g. mostly water) spawns what it can instead of hanging the server.

Everything is configurable: cluster sizes under `envoys.generation.random-locations.cluster` and the boss bar under `envoys.boss-bar` in `config.yml`; all announcement and boss bar text in `messages.yml`.

## Installation

1) Build the jar (see below) or grab it from this repo's releases.
2) You must be running a [Paper](https://papermc.io) server.
3) Drop the jar in the `plugins` folder and restart the server.
4) Set the drop origin with `/crazyenvoys center` (defaults to world spawn).
5) Configure `config.yml`, `messages.yml`, and the files in the `tiers` folder.
6) Execute `/crazyenvoys reload`, then `/crazyenvoys start` to test an event.

Documentation for the base plugin lives at https://docs.crazycrew.us/mods/crazyenvoys/

## Building from source

Requires JDK 17+ to launch Gradle (the Java 25 compile toolchain downloads automatically):

```
./gradlew :paper:build
```

The plugin jar lands in `paper/build/libs/paper.jar`.

## Credits & license

All the crate, tier, flare, and scheduling infrastructure comes from [CrazyEnvoys](https://github.com/Crazy-Crew/CrazyEnvoys) by the Crazy-Crew team. Like the original, this fork is licensed under the [MIT License](LICENSE).

The plugin reports anonymous usage statistics to [bStats](https://bstats.org/plugin/bukkit/CrazyEnvoy/4537); set `enabled: false` in the `bStats` folder's config to opt out.
