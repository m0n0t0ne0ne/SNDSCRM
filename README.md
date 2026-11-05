# SoundScream

SoundScream (SNDSCR on the home screen) is a personal, restyled build of
[Myspoti](https://github.com/kota-zo/myspoti) by kota-zo for Android. It plays Spotify through
Spotify's own web player and draws its own screens on top. A Spotify account is required.

It is based on Myspoti 2.1.0, with the playback engine and selected playback fixes from Myspoti 2.4.0.

## Changes from Myspoti

- A modernized Classic look, and the SoundScream name and icon
- Removed: the Immersive look, extension screens, music downloads, crossfade, donation prompts,
  onboarding pages and the app updater

## Install

Download the APK from [Releases](../../releases). It is built for arm64 phones. To update, install
the new APK over the old one.

## Build

You need JDK 17+ and an Android SDK with platform 37 and build tools 37.0.0.

```bash
scripts/build-apk.sh
```

The signing key is not in this repository, so your build gets a different signature and won't
install over a release APK.

## License

GPL-3.0, the same as Myspoti. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
This project is not affiliated with Spotify or Myspoti.
