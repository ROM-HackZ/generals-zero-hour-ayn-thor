# Where this port comes from

Thor RTS is a modified Android build of the released Generals/Zero Hour engine.
The Thor work adds controller input, the second-screen deck, radial menus, and
the presentation and lifecycle changes those interfaces need.

The starting point was [MYSOREZ's Android port](https://github.com/MYSOREZ/GeneralsZH-Android-Port),
version 1.4.0, commit `576e2265bf271176028d1cef4f2b3fe8caa20b34`. This repository
imports that source and the Thor changes as a new source history. Upstream APKs,
Windows release bundles, signing keys and generated files are excluded.
Original copyright notices and the full license remain in the source.

The work behind it includes:

- [Electronic Arts' source release](https://github.com/electronicarts/CnC_Generals_Zero_Hour).
- [TheSuperHackers' GeneralsGameCode](https://github.com/TheSuperHackers/GeneralsGameCode).
- [Fighter19's Unix port](https://github.com/Fighter19/CnC_Generals_Zero_Hour).
- [Ammaar Reshi's Mac, iOS and iPad port](https://github.com/ammaarreshi/Generals-Mac-iOS-iPad).
- [fbraz3's GeneralsX](https://github.com/fbraz3/GeneralsX) and the Android fork above.
- SDL, DXVK, OpenAL, FFmpeg, ANGLE, libadrenotools, and the other dependencies
  named by the CMake and vcpkg files. Their source/license notices remain with them.

The Thor changes were developed for ROM-HackZ with Codex assistance, and checked
on a real AYN Thor. Controller direction, speed, wheel spacing and the new deck
were adjusted from hands-on feedback.

This project does not claim to have written the engine or those earlier ports.
The inherited engine is under GPL v3 with EA's additional terms, preserved in
[LICENSE.md](../../LICENSE.md). Retail game data is supplied by each player.
