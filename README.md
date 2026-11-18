# VoxForge — React Studio Voiceover Recorder & Real-Time DSP Workbench

**VoxForge** is a React + TypeScript + Web Audio studio voiceover recorder featuring a real-time 48 kHz mono DSP processing chain, TD-PSOLA + formant-preserving voice changer, Acoustic Echo Cancellation (AEC) with an adjustable decibel Noise Gate and background noise calibration, live headphone monitoring with delay control, multi-band parametric EQ, and browser-based WAV recording & export.

## Key Features

- **48 kHz Mono Studio Recording**: Captures microphone audio via Web Audio (`48,000 Hz`, 16-bit mono PCM WAV, `768 kbps`) with linear resampling when the input device runs at another rate (e.g., 44.1 kHz).
- **Pre-DSP Live Input Metering & Only-Active Processing Policy**: 30-segment dBFS bar (`-60 dB` to `0 dB`), RMS/peak readout, peak-hold marker, threshold overlay, and resettable clip counter measured from raw microphone audio. Audio capture and DSP run strictly while recording, Live Voice Preview, ambient noise calibration, or explicit **Monitor Input** is active, freeing CPU and memory at idle.
- **Acoustic Echo Cancellation (AEC) + Adjustable Decibel Noise Gate**:
  - Separate DSP stages: **Stage 1 (AEC)**: browser/hardware track `echoCancellation` verification + `120 Hz` high-pass filter + Packet Loss Concealment (PLC); **Stage 2 (Noise Gate)**: dedicated soft-knee noise gate (`-60` to `-20 dBFS`, default `-45 dBFS`, `1 dB` step) with `[-]` / `[+]` precision controls, smooth attack/hold/release, and hysteresis.
  - Applied consistently across **live recording**, **Live Voice Preview**, **imported audio live playback**, and **offline WAV export**.
  - **Noise Floor Detection & Calibration**: real-time background noise floor estimation, live `Gate Open` / `Gate Closed` / `Noise Floor Detected` indicators, and 1.5 s **Calibrate Background Noise** room sampler with safe threshold recommendation.
- **Bluetooth Audio Compatibility & Honest Reporting**: Inspects active `MediaStreamTrack.getSettings()` & capabilities, classifies Bluetooth `HFP/HSP` vs `A2DP` routes, supports output sink routing (`setSinkId`) where available, recovers gracefully on route changes, and reports honestly whether hardware AEC + DSP or DSP-only protection is active.
- **Voice Changer**: ON/OFF switch (default OFF) with 5 vocal profiles (`Normal`, `Female`, `Child`, `Girl`, `Woman`), real-time YIN pitch detection + TD-PSOLA + Catmull-Rom formant shifting, plus `Pitch Level`, `Tone Level`, and `Clarity` sliders.
- **Live Voice Preview & Monitoring**: Real-time headphone monitoring branch with `Faster` / `Default` / `More Delay` buffer-delay adjustment (`-1.0` to `+1.0`, up to `+200 ms`), `0%`–`500%` preview volume boost, and wired-headphone feedback warning.
- **10 Studio Effects + 3 Rack Extensions**:
  - Synchronized across the **Recorder** (`AUDIO EFFECTS & STUDIO CONTROLS`) and **Effects** tabs: `Pitch Correction`, `Pitch & Formant`, `Equalizer (EQ)` with live SVG log-frequency curve (`20 Hz`–`20 kHz`, `±15 dB`), `Compression`, `Saturation`, `Reverb` (`Small Room`, `Studio`, `Hall`, `Plate`), `Delay / Echo` (with BPM tempo sync & Ping-Pong), `Noise Gate`, `De-Esser`, `Limiter`, `Low-pass`, `High-pass`, and `Hall`.
  - Built-in & custom user effect presets persisted to `localStorage`.
- **Responsive Multi-Device UI & Decoupled Telemetry**: Adapts cleanly across phones (`< 768px`), tablets (`768px–1279px`), and large desktop/TV screens (`>= 1280px`) with keyboard/D-pad arrow navigation and high-contrast focus rings. 20 Hz telemetry updates are isolated to meter/timer components so heavy effect and recording lists stay smooth while scrolling.
- **Import Audio & Video Audio Extraction**: Import audio files or extract the audio track from video files into a `48 kHz` buffer, audition changes live without resetting playback position, and render & download/share the processed `.wav`.
- **Recorded Voiceovers Library**: Stored in browser IndexedDB with play/pause, scrubbing, rename, Web Share / WAV download, and delete.

## Development & Build

```bash
npm install
npm run dev      # Starts Vite dev server on http://0.0.0.0:3000
npm run lint     # Runs TypeScript type checking (tsc --noEmit)
npm run build    # Builds production bundle to dist/
```
