# JT Stream Player

A lightweight Android video player built on [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer). Currently supports **RTSP** (including IP cameras) and **progressive (direct file) URLs** — HLS, DASH, and other adaptive formats are planned but not wired up yet (see [Roadmap](#roadmap)). It comes with a customizable buffering/playback pipeline and a distraction-free, fullscreen viewing experience.

## Features

- **Play RTSP and progressive URLs** — paste an `rtsp://` stream (auto-routed through `RtspMediaSource`) or a direct `http(s)://` file URL and go.
- **Saved streams, earned not guessed** — a URL is only added to your saved list once it actually plays successfully (confirmed via `onIsPlayingChanged`), not just because you typed it. Tap the history icon to pick from and replay any saved stream.
- **Auto-resume** — the last successfully played stream reopens automatically on next launch.
- **Customizable player settings** — tune min/max buffer, playback buffer thresholds, live-stream target offset, force-RTSP-over-TCP, repeat mode, and video resize mode (Fit/Zoom/Fill/Fixed width/Fixed height), all persisted across launches.
- **Immersive fullscreen UI** — the URL bar and system status/navigation bars hide together in sync with the player's control overlay, and reappear together on tap. Video is edge-to-edge with no cropping.
- **Rotates on its own terms** — sensor-based rotation works even if the device's system-wide auto-rotate lock is on.
- **Graceful error handling** — playback failures (bad URLs, unreachable streams, unsupported formats) route to a dedicated, non-fatal error screen with full diagnostic detail (error code, root cause, stack trace) instead of crashing. A known, currently-unresolved Media3 bug in RTSP SDP/SPS parsing ([androidx/media#2208](https://github.com/androidx/media/issues/2208)) is specifically detected and recovered from, since it throws outside the normal player error callback.

## Roadmap

- [ ] HLS support (`media3-exoplayer-hls`)
- [ ] DASH support (`media3-exoplayer-dash`)
- [ ] SmoothStreaming support (`media3-exoplayer-smoothstreaming`)

Trying to play an `.m3u8`/`.mpd` URL today will fail with an unsupported-format error, since `DefaultMediaSourceFactory` can't resolve those without the corresponding module on the classpath.

## Known limitations

- **RTSP + non-standard H.264 SPS**: some RTSP cameras send malformed or non-standard SPS/fmtp data that Media3's parser can't handle. This app detects and recovers from that specific crash, but the underlying stream still won't play until the library fixes it or the camera's encoding profile is changed (e.g. Baseline vs. Main/High profile).
- No support for DRM-protected streams.
- No picture-in-picture or background audio playback (foreground video playback only).

## License

MIT