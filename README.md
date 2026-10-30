# Local Player

An offline music player for Android. It plays the MP3s already on your phone, with playlists, a persistent queue, and background playback with lock-screen and Bluetooth controls. No account, no ads, no internet access required.

Built with Kotlin, Jetpack Compose (Material 3) and Media3/ExoPlayer.

## Features

- **Local library**: import MP3s through Android's file picker. Files are referenced in place and never copied, so there is no storage permission and no duplicated data.
- **Browse by** Songs, Albums, Artists, Playlists and Liked songs, plus search across your library. Albums and artists are built from the files' tags, and embedded cover art is shown when present.
- **Playlists**: create them, import files straight into one, add from the library, copy or move songs between playlists, and multi-select to act on several songs at once.
- **Reordering that doesn't fight scrolling**: press and hold the ≡ handle at the right of a song, then drag. Swiping anywhere else scrolls normally. There are also Move up / Move down actions for screen readers.
- **Imports keep file-name order**: files named `1. …`, `2. …`, `10. …` are inserted in natural numeric order. Playlists also have "Sort by title".
- **Queue**: editable, separate from saved playlists (changing the queue never changes a playlist), restored after the app or phone restarts (paused, never auto-playing).
- **Repeat / autoplay**: one tap loops the playlist, two taps loop the song, three taps return to autoplay. With autoplay on, random songs from your library keep playing when the playlist ends.
- **Playback controls**: mini-player and full Now Playing screen with seek bar, shuffle, repeat and queue; notification, lock-screen, headset and Bluetooth controls.
- **Light and dark themes** that follow the system setting.
- **Accessibility**: labelled controls, visible focus, and animations that respect the system "remove animations" setting.

## Requirements

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- An Android device or emulator running Android 8.0 (API 26) or newer

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/VictorhpXavier/GooseWave.git
   ```
2. Open the `GooseWave` folder in Android Studio and let Gradle sync.
3. Run the `app` configuration on a device or emulator.

From the command line instead:

```bash
./gradlew assembleDebug        # or `gradle wrapper` first if the wrapper jar is missing
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Using the app

1. Open **Library → Songs** and tap the import button to pick your MP3 files. Android asks you to select files; no folders are scanned.
2. Create a playlist under **Library → Playlists**, or import files directly into an existing one with **Add → Import MP3s from files**.
3. Tap a song to play it. Tap the current song again to pause or resume.
4. To reorder a playlist, **press and hold** the ≡ handle on the right of a song and drag it.
5. Use the repeat button on the player to choose looping or autoplay.

## Project structure

```
app/src/main/java/com/example/GooseWave/
├── App.kt, MainActivity.kt        entry points
├── data/                          Room database, file/tag reading, cover art cache, natural sort
├── playback/
│   ├── PlaybackService.kt         MediaSessionService + ExoPlayer: owns playback, queue restore, autoplay
│   └── PlayerController.kt        MediaController wrapper the UI uses
└── ui/
    ├── Theme.kt                   colours, typography, spacing
    ├── Components.kt              TrackRow/SongRow, ReorderHandle, buttons, dialogs
    ├── Home.kt, Browse.kt         home screen, library tabs, search
    ├── Details.kt                 album, artist, playlist and queue screens
    ├── NowPlaying.kt              full player, mini-player, seek bar
    ├── Root.kt                    navigation and bottom bar
    ├── MainViewModel.kt           library and playlist operations
    └── OnlineSearch.kt            data model for a future online-search feature (not wired in)
```

## How it works

- **Playback lives in a service.** `PlaybackService` (a `MediaSessionService` with ExoPlayer) owns the player so music continues with the screen off. The UI only talks to it through `PlayerController`.
- **Queue vs playlists.** The active queue is the ExoPlayer timeline, saved to the Room table `queue_entries`. Saved playlists are the tables `playlists` and `playlist_entries(position)`. Playing a playlist copies its songs, in order, into the queue.
- **Song identity.** Each song has an auto-generated Room id, used as the media id. Files are referenced by their Storage Access Framework URI with a persisted read grant.
- **Small state** (queue position, playback position, repeat, shuffle, liked songs, recently played) is kept in DataStore.
- **Autoplay.** When the queue ends and repeat is off, the service adds up to 10 random available library songs, preferring ones that weren't just played, and trims the queue to about 50 entries.

## Known limitations

- Android limits persisted file grants per app (128 on Android 10 and below, 512 on 11 and above), which caps how many individual files can be imported. Folder-based import would remove the cap and isn't implemented.
- With shuffle on, "Play next" goes where ExoPlayer's shuffle order puts it, which can be a random position. Shuffle order isn't kept across process restarts.
- Notification artwork isn't set; cover art shows only inside the app.
- Files that can't be read are marked "unavailable" and skipped; use "Locate file…" to fix them or remove them.
- MP3 is the tested format.

## Roadmap

- Online search for music from sources that permit downloading. `ui/OnlineSearch.kt` defines the states (found online, downloading, downloaded, in library, failed, unavailable). Online results are kept separate from the library until a file has actually been downloaded and added.
- In-app theme toggle (system / light / dark).

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Media3 (ExoPlayer, MediaSession) · Room · DataStore · Coil · [Reorderable](https://github.com/Calvin-LL/Reorderable) · Palette

## License

Add a license before publishing (for example MIT) and put it in a `LICENSE` file.
