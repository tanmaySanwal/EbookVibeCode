# TV PDF Reader

A PDF reader for Android TV / Google TV projectors such as the **XGIMI MoGo**. It is built for a
1920×1080 landscape screen and is driven entirely by the **D-pad remote**: no touchscreen, no
mouse.

- Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), single activity, ViewModel + StateFlow
- PDF rendering with the platform `android.graphics.pdf.PdfRenderer`, with no third-party PDF library
- minSdk 26 (Android 8.0), targetSdk 34 (Android 14)

## Features

| | |
|---|---|
| **Library** | Grid of recent books with a page-1 cover, title and last-read page. Hold OK on a book to remove it. |
| **Built-in file browser** | Internal storage, Downloads, Documents and USB drives (refreshes automatically when a stick is plugged in). The system picker is offered as a fallback. PDFs opened from another file manager (VIEW intent) also work. |
| **Resume** | The last-read page and zoom are saved per book in DataStore. |
| **Page themes** | Light / Dark / Sepia. Dark uses a "smart invert" ColorMatrix: it inverts the colours and then rotates the hue 180°, so photos keep their colours. The app UI is dark throughout. |
| **Zoom** | Fit page, Fit width, 125 %, 150 %, 200 %, 300 % (percentages are relative to Fit page). Each page is re-rendered at the zoomed resolution, so text stays sharp. |
| **Navigation** | On-screen Prev / Next, D-pad, Page Up/Down, Channel ±, media ⏮ ⏭. *Go to page* opens an on-screen keypad (the remote's digit keys also work). Shows "Page 12 / 240". The previous and next pages are pre-rendered. |
| **Sleep timer** | 5 / 10 / 15 / 30 min, Custom or Off. A countdown shows in the corner. One minute before the end a *"Still reading? +5 min"* prompt appears. When the timer runs out the app saves your page, fades the screen to black and closes the reader. |
| **Projector-friendly** | Large amber focus highlight with a white ring, text 18 sp or larger, buttons 48 dp or taller, and a 48 dp overscan-safe margin. The screen stays awake while reading and is released when the timer ends. |

## Remote control

**Library and file browser:** use the D-pad to move and OK to open. BACK goes up one folder, then back to the library.

**While reading:**

| Key | Action |
|---|---|
| ◀ / ▶ | Previous / next page. When zoomed in *Pan* mode, they pan left / right instead. |
| ▲ / ▼ | Scroll a zoomed page. At the top or bottom edge they move to the previous / next page. When the page fits the screen, they turn pages. |
| OK | Show the toolbar and focus it |
| Hold OK | Switch between *Pan* and *Turn* mode |
| MENU | Show the toolbar / settings |
| 1–9 | Open *Go to page* with that digit already entered |
| BACK | Close the dialog → hide the toolbar → leave the reader → exit the app |

Any key shows the toolbar. It hides itself after 4 seconds without input.

**Toolbar:** `◀ Prev` · `Page x / n` (go to page) · `Next ▶` · `Sleep` · `Close`, plus
`A−` · `Zoom` · `A+` · `Mode` · `Page theme`.

## Building

You need JDK 17 and the Android SDK (platform 34). Point `ANDROID_HOME` at the SDK, or create
`local.properties` containing `sdk.dir=...`.

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Every push is also built by GitHub Actions (`.github/workflows/build.yml`). You can download the APK
from the run's **Artifacts** section (`tv-pdf-reader-debug`).

## Testing

**Unit tests** cover the pure logic: zoom ordering and scales, the dark/sepia colour matrices
(for example, that red stays red after the smart invert), and sleep-timer formatting. CI runs them
on every push:

```bash
./gradlew testDebugUnitTest
```

**Remote-control smoke test.** `scripts/remote-test.sh` sends D-pad, OK (short and long press),
MENU, BACK, digit and page keys over ADB, so you can watch every interaction on the projector
without picking up the remote:

```bash
adb connect 192.168.1.50:5555
scripts/remote-test.sh            # PAUSE=2 scripts/remote-test.sh for a slower run
```

It needs at least one book in the library. Which button a D-pad press lands on depends on the
layout, so it is a guided walkthrough, not an assertion-based test. Check that:

- every focused element has the amber fill and white ring
- the toolbar appears on any key and hides 4 s later
- BACK unwinds in the order dialog → toolbar → reader → app
- the page you were on is shown on the book's card afterwards

## Sideloading onto the projector

### Option A: ADB over Wi-Fi

1. On the projector, open **Settings → Device Preferences → About** and select **Build** seven
   times to unlock *Developer options*.
2. In **Settings → Device Preferences → Developer options**, turn on **USB debugging**. On some
   firmware there is also an **ADB debugging / Network debugging** switch; turn that on too.
3. Find the projector's IP address in **Settings → Network & Internet → (your Wi-Fi)**.
4. On a computer on the same network, with
   [platform-tools](https://developer.android.com/tools/releases/platform-tools) installed, run:

   ```bash
   adb connect 192.168.1.50:5555      # use your projector's IP
   # accept the "Allow USB debugging?" prompt on the projector screen
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   On Android 11+ firmware that only offers *Wireless debugging*, pair first with
   `adb pair IP:PAIR_PORT` using the code shown on screen. Then run
   `adb connect IP:PORT` with the port shown on the Wireless debugging screen.

5. The app appears in the launcher's **Apps** row as **TV PDF Reader**, with its banner.

Optional: grant storage access from the computer instead of on the TV:

```bash
# Android 11+ (all-files access)
adb shell appops set --uid com.ebookvibe.tvreader MANAGE_EXTERNAL_STORAGE allow
# Android 8–10
adb shell pm grant com.ebookvibe.tvreader android.permission.READ_EXTERNAL_STORAGE
```

### Option B: USB stick and a file manager

1. Copy `app-debug.apk` (and your PDFs) to a USB stick, then plug it into the projector.
2. Install a file manager from the Play Store, for example *File Commander*, *X-plore* or
   *FX File Explorer*. Some XGIMI firmware already includes one.
3. Allow installs from it: **Settings → Device Preferences → Security & restrictions → Unknown
   sources**, and turn on the file manager. On Android 8+ this permission is set for each app; the
   file manager will prompt you the first time.
4. Browse to the USB stick in the file manager, select the APK and choose **Install**.

## Storage permissions explained

| Android version | What the app asks for |
|---|---|
| 8.0 – 10 (API 26–29) | `READ_EXTERNAL_STORAGE` runtime prompt. `requestLegacyExternalStorage` is set for Android 10. |
| 11 – 14 (API 30–34) | **All files access** (`MANAGE_EXTERNAL_STORAGE`). `READ_EXTERNAL_STORAGE` and `READ_MEDIA_*` only expose images, video and audio, never PDFs, so a real file browser needs all-files access. The app opens the system settings page for it. |

Some TV firmware leaves out the *All files access* settings screen. If the button can't open it,
the app shows the `adb` command above. You can also use **System picker** (where the device has
one), or open a PDF from another file manager. Those routes use content URIs and need no storage
permission.

## Performance on a 2 GB device

- Pages are rendered on one background thread and drawn 1:1 (rasterised at screen resolution, not
  scaled).
- The page cache holds at most **3 pages** (previous, current, next). It also has a byte budget
  based on the device's memory class, which is halved on low-RAM devices. Neighbouring pages are
  skipped when they would not fit.
- A bitmap is recycled when it leaves the cache, but only after it is no longer on screen. Each
  page bitmap is capped at about 12 MP, and the app retries at a lower resolution if it runs out of
  memory.
- Dark and Sepia are colour filters applied while drawing, so switching themes needs no re-render.

## Project layout

```
app/src/main/java/com/ebookvibe/tvreader/
├── MainActivity.kt          single activity, Navigation-Compose host, VIEW intents
├── ReaderApp.kt             Application: repository + app-wide coroutine scope
├── data/                    Book model, DataStore repository, storage roots & permissions
├── pdf/                     PdfRenderer wrapper (single render thread), 3-page LRU cache
└── ui/
    ├── home/                recent-books grid
    ├── browser/             built-in file browser
    ├── reader/              reader screen, ViewModel, zoom/theme models, colour matrices, sleep timer
    ├── components/          focus-ring button & card, number pad, option dialog, system picker
    └── theme/               dark TV theme, typography (≥ 18 sp), safe margins
```
