## Highlights

### Cross-platform multiplayer: play online with PC players

**Android players can now play GeneralsOnline matches together with PC (Windows) players** -- join a PC player's
lobby, or host one from the phone and let PC players join it.

- The phone speaks the PC client's network protocol and matches its game checksums, so PC lobbies accept it.
- The PC client's executable checksum is computed on the phone from the GeneralsOnline data patch, so a new PC
  release needs no new APK.
- Many simulation paths were aligned with the PC client's own source so that both sides compute the same game.
- **The PC player has to turn their anti-cheat off** (GeneralsOnline AntiCheat / Easy Anti-Cheat in the
  GeneralsOnline launcher): the mobile client has none, and the server only matches clients with the same setting.
- Setup: sign in with your GeneralsOnline account in the launcher, install **Online game data** there (community data
  patch + maps, straight from GeneralsOnline) and turn on **Play with PC players**.

Cross-play is new; if a match against a PC goes out of sync, please send **the logs** from the launcher (log button)
**and the replay** of that match (the phone's and, if you can get it, the PC player's `.rep`) -- that is how the
remaining differences are being found.

### The game in 12 languages besides English

Ten new game-text language packs, each a complete translation of the English original:
**German, French, Spanish, Brazilian Portuguese, Polish, Interslavic, Simplified Chinese, Korean, Arabic and Persian.**
The **Russian** and **Ukrainian** packs from 1.2.2 were completed against the English original and edited
(GLA is "ГЛА" again, unit names follow the original translation). Every pack is downloadable from the launcher,
and the launcher now names each language the way the pack itself writes it.

Text rendering was extended to carry them:

- **Right-to-left layout and Arabic/Persian letter shaping** -- joined letters, right-aligned text and tooltips,
  numbers kept intact inside RTL text.
- **Chinese, Korean and Arabic glyphs** are taken from the phone's own system fonts when the game's fonts lack them,
  instead of empty boxes. Symbol fonts too, so the GeneralsOnline lobby's shield icon now draws.
- One-line labels whose translation is longer than the English shrink to fit instead of being cut in half.

### Controls and interface

- **Second page on builder command bars.** Dozers and workers with more structures than the bar has slots get an
  arrow button (bottom right) that flips to the rest.
- **Force-attack and waypoint buttons** on the command bar (#25). Force attack is only offered to units with a weapon.
- **Scroll any list with your finger** -- game lists, player lists, chat, map lists.
- **Corner HUD, messages and the watermark stay inside the screen's safe area** on phones with rounded corners and
  cut-outs (#20).
- **Enter on the Android keyboard submits a text field** -- e.g. the password of a locked online game, which could not
  be sent at all before.
- The Steam release's **Custom Mission** button works.
- **Android 10 storage access** fixed (#22).
- One home-screen icon, the game's, opening the launcher.

### Online (GeneralsOnline)

- **Sign-in fixed**: the launcher asks the server for the login code instead of inventing one, and says what went wrong
  when something does.
- **Online game data from the launcher.** The GeneralsOnline community data patch and community maps download
  straight from GeneralsOnline -- no PC needed -- and can be switched off or removed. The launcher checks for a newer
  version on its own and installs it on Wi-Fi.
- **30 Hz or 60 Hz simulation**, chosen in the launcher. The APK carries both engines.
- Lobby fixes: the reason a join was refused is shown instead of blaming the host; map names in any language reach
  other players intact (a Russian map name used to arrive garbled); games hosted from a phone are marked
  **[Android]** and shown in green in this client's list.
- PC replays load on the phone, and a **Replay check** screen runs a replay through to compare it frame by frame.

### Updates without a new APK

- The launcher takes **signed updates from this repository**: newer engine builds and network settings (servers, PC
  compatibility) arrive without reinstalling the APK. Anything without a valid signature is ignored; an updated engine
  that fails to start twice is dropped automatically. Home → Updates.
- The launcher and the game work fully **offline**; the last verified update stays in use.
- **Collect logs** switch: turn it off and nothing is logged in the background.

### Fixes

- A missing control in a menu layout names itself in the log instead of closing the game.
- Unit bone names were garbage under the Android C library (`itoa` wrote nothing) -- docking and attachment points are right again.
- Upgrades are no longer queued on buildings still under construction.
- Base-game INI tokens (`FLESHY_SNIPER`, `AIRFIELD`) read as their Zero Hour equivalents (#2).

**Full Changelog**: https://github.com/MYSOREZ/GeneralsZH-Android-Port/compare/v1.2.2...v1.3.0

## P.S. **Your language is missing? Send it in ;)**

Anyone can add a language: a pack is one plain UTF-8 text file (`generals.str`) that can be edited in any text
editor. Send it to this repository as a pull request (or attach it to an issue), and once it is merged it appears in
the launcher's language list for every player -- **no app update needed**. Corrections to the existing packs are
just as welcome. How to start, including converting an existing PC translation (`.csf`): see
[`languages/README.md`](https://github.com/MYSOREZ/GeneralsZH-Android-Port/blob/main/languages/README.md).
