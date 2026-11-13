# SoundScream notes

SoundScream is Myspoti v2.1.0 plus SoundScream's changes, with the playback engine from
Myspoti v2.4.0. The app ID is `app.myspoti.dev`, signed with the owner's debug key
(certificate SHA-256 `D2:20:45:25:74:A7:D4:78:47:8B:2E:34:6F:EE:12:27:64:83:FE:EE:2D:5E:81:7E:3F:BE:CD:54:62:57:F4:26`).
Releases are arm64 only and R8-shrunk (`-PoverDev -Pabis=arm64-v8a :app:assembleRelease`).

## Where things live (`base/`)

- `app/src/main/java/com/music/spotui/ui/theme/`: design tokens (`Color.kt`, `Type.kt`, `Theme.kt`).
- `ui/screens/`, `ui/components/`: the Classic screens.
- `di/SongPlayer.kt`: the player core (commands, queue, repeat, swipes, sleep timer).
- `di/AppShuffle.kt`, `di/ShuffleOrder.kt`, `di/LocalRepeat.kt`: shuffle and repeat.
- `api-spotify/`, `host-android/`, `api/`, `host-cdp/`: the playback engine, taken whole from
  Myspoti v2.4.0. `api-spotify/.../SpotifyPlayer.kt` drives the hidden Web Player page.
- `app/build.gradle.kts`: labels, version (`2.4.0-soundscream`, code 201), ABI filter.

## Changes from Myspoti

- Modernized Classic look, SoundScream name and green icon, all on-screen "Myspoti" renamed.
- Removed: the Immersive look, extension screens and startup check, music downloads, onboarding,
  look picker, donation prompts, crossfade, the app updater and its install permission.
- Ported from v2.4.0 on the app side: phone calls pause and resume, sign-in kept when a session
  check fails offline, resume in the list after a relaunch, service and speaker stream that hold,
  ads muted on Connect devices, skips stay on the chosen device, Myspoti shuffle waits for the
  next song.
- SoundScream change to the engine (`host-android/.../PlaybackService.kt`): closing the app from
  the recent apps sets `closed_by_user`, makes the media session inactive and returns no
  resumption root, so media widgets drop the last song. Opening the app or playing clears it.
  Re-check this when porting upstream changes to that file.
- Swipe back on the mini player or the full player's cover always goes to the song before.

## Porting from upstream

Take playback fixes only. Commits that touch removed files no longer apply; port the parts for
files that still exist, check for misplaced hunks and leftover conflict markers, and strip
references to removed features (Cast, downloads, extensions UI, Immersive).

## Known issues and tips

- Maven Central may rate-limit sandboxes (HTTP 429); `scripts/mirror.init.gradle.kts` uses
  Google's mirror.
- Build with one Gradle worker on machines with about 8 GB of RAM: R8 and lint ran out of
  memory with two.
- `PlaybackFollowUpTest` "the repeat mode of an earlier run…" is timing-sensitive and once
  timed out in a full run.
- Escape apostrophes in Android string resources (`\'`).
- A screen that crashes only in the release build is likely an R8 shrinking problem; try a debug
  build to tell them apart.
