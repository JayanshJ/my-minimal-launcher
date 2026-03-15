# Minimal Launcher

A native Android launcher built in Kotlin. Pure black background, white/gray text, no icons anywhere.

---

## Features

### Home screen
- **Clock** — large, tap to toggle 12h/24h, long-press to open Settings
- **Date** — day, date, month below the clock
- **Widget row** — weather · battery % on one line, screen time on the next, then today's calendar events
- **Pomodoro timer** — 25 min focus / 5 min break / 15 min long break; tap to start/pause, long-press to reset; vibrates on session end
- **Media controls** — shows when audio is playing (requires Notification Access); prev · play/pause · next
- **Pinned apps** — long-press any app in the drawer to add it to the home screen; long-press on the home screen to remove or view app info
- **Empty-state hint** — shown when no apps are pinned

### App drawer
- Swipe up or tap **↑** to open; swipe down or back to close
- Slides up with animation
- Alphabetical list with A–Z fast-scroll strip on the right edge
- Section headers (A, B, C…)
- Real-time search — filters as you type; falls back to a web search button
- Keyboard auto-shows on open (toggleable in Settings)
- Grid / list view toggle (set in Settings)
- Long-press any app for: pin to home, app info, hide from list

### Gestures (home screen)
| Gesture | Action |
|---|---|
| Swipe up | Open app drawer |
| Swipe down | Expand notification shade |
| Swipe left | Launch configured app |
| Swipe right | Launch configured app |

### Settings (long-press clock to open)
| Section | Options |
|---|---|
| Clock format | 24h · 12h |
| App list | list · grid |
| Font size | small · medium · large |
| Pomodoro timer | show · hide |
| Drawer keyboard | on · off |
| Temperature | °C · °F |
| Gestures | swipe left app · swipe right app |
| Hidden apps | hide / unhide apps from the drawer |

---

## Permissions

| Permission | Why | Grant method |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Show all installed apps | Auto at install |
| `READ_CALENDAR` | Calendar events in widget row | Runtime prompt |
| `INTERNET` | Weather fetch | Auto at install |
| `ACCESS_COARSE_LOCATION` | Weather for your location | Runtime prompt |
| `ACCESS_NETWORK_STATE` | Check connectivity before weather fetch | Auto at install |
| `VIBRATE` | Pomodoro session-end buzz | Auto at install |
| `EXPAND_STATUS_BAR` | Swipe-down opens notification shade | Auto at install |
| `PACKAGE_USAGE_STATS` | Screen time in widget row | **Manual** — Settings → Apps → Special app access → Usage access |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Media controls (track info, play/pause) | **Manual** — Settings → Apps → Special app access → Notification access |

---

## Setup

1. Build and install the APK
2. Press Home — Android will ask which launcher to use; pick **Minimal Launcher** and choose **Always**
3. Grant optional permissions for full functionality:
   - **Notification Access** → media controls appear
   - **Usage Access** → screen time appears in the widget row

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | View system (no Compose), ViewBinding |
| Min SDK | 29 (Android 10) |
| Target SDK | 34 (Android 14) |
| AGP | 8.2.2 |
| Gradle | 8.4 |
| Dependencies | `kotlinx-coroutines-android:1.7.3` (weather fetch) |

### Key files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Home screen — clock, widget row, pinned apps, gestures, media, Pomodoro |
| `AppDrawerActivity.kt` | Full app list — search, alphabet index, grid/list, section headers |
| `SettingsActivity.kt` | All settings options |
| `AppListAdapter.kt` | Multi-type RecyclerView adapter (Header / App), DiffUtil, grid span |
| `PrefsManager.kt` | Single source of truth for all persisted state |
| `WeatherManager.kt` | Open-Meteo fetch (no API key needed) |
| `MediaNotificationListener.kt` | NotificationListenerService — enables `MediaSessionManager.getActiveSessions()` |
| `AlphabetIndexView.kt` | Custom A–Z fast-scroll strip drawn on Canvas |
| `PackageReceiver.kt` | BroadcastReceiver for app installs / removals |
| `MinimalLauncherApp.kt` | Application class — forces `MODE_NIGHT_YES` for dark dialogs |

### Architecture notes
- Root layout is `LinearLayout` (not ConstraintLayout) so `GONE` collapses space correctly
- Weather + battery + screen time + calendar all live in one `tv_widget` TextView, assembled in `updateWidgetText()`
- Gestures use `dispatchTouchEvent` (not `onTouchEvent`) so they fire even when child views consume touches
- Media panel polls every 2 s in `onResume`, paused in `onPause`
- Clock ticks every second aligned to the next second boundary
- Weather refreshes on resume + every 15 min via Handler
- Grid mode uses `GridLayoutManager(2)` with `SpanSizeLookup` — headers span 2, apps span 1
