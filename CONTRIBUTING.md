# Contributing

Bug reports, code, docs and device testing are welcome. This is an early port:
clear reports from a real Thor help more than broad claims that everything works.

## Keep game data and keys out

Don't commit retail archives, extracted game assets, player saves, signing keys,
account tokens or generated build folders. A few screenshots demonstrate the
interface; the art used by it is read from each player's installation at runtime.
Keep the inherited license, copyright notices and credits intact.

## Making a change

1. Follow [the build guide](docs/BUILD/THOR.md) and make a branch.
2. Keep controller and Android changes in the device/app layers. Use the original
   engine paths for orders, production and placement; don't change game rules to
   make a controller action easier.
3. Run `bash scripts/qa/test-thor.sh` and build the APK. For interface changes,
   try the result on the Thor with buttons and touch, on both screens.
4. Open a pull request. Say what changed and how you checked it; distinguish a
   screenshot or code check from something you actually did in the game.

## Reporting a bug

Include the app version, Android version, your game edition, what you selected,
the button sequence, and what happened. For a layout problem, show both screens.
For a crash, include the app's log if you have it, with account details removed.
Don't upload your game data to an issue.

## Writing

Use plain words and useful examples. Explain what a player needs to do. Keep
tested behavior separate from plans and assumptions. Credit the earlier ports
when describing work inherited from them.
