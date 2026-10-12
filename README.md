# LemonDS

LemonDS is a slimmed-down fork of [melonDS-android](https://github.com/rafaelvcaetano/melonDS-android) (an Android port of the [melonDS](https://melonds.kuribo64.net/) Nintendo DS emulator) built around easy same-Wi-Fi multiplayer.

**LemonDS does not include any games, BIOS or firmware files.** You must supply your own, legally obtained.

## What is different from melonDS-android

- **One-tap LAN multiplayer.** Both devices join the same Wi-Fi network. One device taps **HOST** (Player 1) and the other taps **JOIN** (Player 2). There are no names or IP addresses to type.
- **Slimmed down.** RetroAchievements, DSi mode, GBA slot support and microphone support have been removed.
- **Optional frameskip.** Settings > Video > Frameskip. (Added recently and not widely tested.)
- **LAN timing and Wi-Fi channel fixes** in the emulator core, kept in the [melonDS-android-lib fork](https://github.com/Mormot123/melonDS-android-lib/tree/lemonds) (branch `lemonds`).

## Tested devices and honest limits

- Developed and tested on the **Retroid Pocket 5** and **Retroid Pocket Flip2**. Two-device LAN play with **Tetris DS** worked.
- Slow devices such as the **Anbernic RG DS / RG DS Plus** are too slow for demanding games like *New Super Mario Bros.*, with or without LAN.
- Other games have not been tested. *Pokemon* and *Mario Kart DS* did not connect in my testing.

## Known issues

- After a failed or finished LAN session, you may need to fully close and reopen the app before hosting or joining again.
- LAN speed is limited by the slower of the two devices and by Wi-Fi quality.

## Building

You need Android Studio, the Android NDK and CMake. The project uses git submodules, so clone with them:

```
git clone --recurse-submodules https://github.com/Mormot123/LemonDS.git
```

Then open the folder in Android Studio and build the `app` module.

## Credits and license

LemonDS is built on the work of others:

- **melonDS** by Arisotura and contributors.
- **melonDS-android** by Rafael Caetano and contributors.
- Third-party libraries as included in this repository (ENet, Oboe, FAAD2 and others), under their own licenses.

LemonDS is licensed under the **GNU General Public License v3.0**, the same as the projects it is based on. See [LICENSE](LICENSE). The complete source for every release is in this repository.

LemonDS is not affiliated with or endorsed by Nintendo, the melonDS team, or the melonDS-android author.
