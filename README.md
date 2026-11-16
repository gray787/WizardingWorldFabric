# Wizarding World: Hogwarts Legacy — Fabric 26.3

## Build
This is a Fabric mod source project, not a precompiled JAR. It targets Minecraft Java 26.3, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Loom 1.19-SNAPSHOT and Java 25.

1. Install a Java 25 JDK.
2. Install Gradle 9.6.0 or use a compatible Gradle wrapper.
3. From this folder run `gradle build`.
4. The built mod JAR will be in `build/libs/`.
5. Install Fabric Loader 0.20.1 and Fabric API 0.166.0+26.3, then put the JAR in `.minecraft/mods`.

## Implemented design in this source
- `/hogwarts spawn`, `/hogwarts diagon spawn`, `/hogwarts platform spawn`
- Five-answer Sorting Hat quiz: `/hogwarts sorting`, then `/hogwarts answer 1..4`
- 50 wand item registrations and a separate Elder Wand
- 2,000 spell archive records; scroll item learns a spell (currently defaults to Lumos unless spell data is attached)
- 20 named spell definitions and spell effects in the cast engine
- Z key opens a basic spellbook screen
- Broom item toggles player flight
- Quaffle, Bludger and Snitch throwable ball items
- Named vendor villagers
- Currency denominations and exchange-rate display

## Work still needed for a complete polished release
This first Fabric source build is a functional foundation, not a finished commercial-quality mod. The shop inventory/trading UI, durable per-player spellbook persistence, scroll selection/tooltip generation for all 2,000 entries, broom mount model/entity, Quidditch goal scoring and arena boundary enforcement, custom wand/ball renderers, and automated galleon/sickle/knut transactions still need completion. The Java source should be compiled and tested against the exact 26.3 toolchain before use; this environment did not have Java 25, Gradle, or network access to download dependencies, so no verified installable JAR can honestly be supplied here.
