# Video Enhancer Feature — Design Spec (Proof of Concept)
**Date:** 2026-05-29
**Status:** POC

---

## Overview

A new tool that improves the visual quality of a user-picked video **entirely on-device**
(no upload, matching the app's privacy-first stance). The user picks a clip (max 5 minutes),
chooses an enhancement level, and gets back a sharpened, optionally upscaled, higher-bitrate
re-encode that can be previewed, saved to the gallery, or shared.

This is a proof of concept: a single synchronous transcode pass, AVC output, H.264 only.

---

## What "enhancement" means here

Three things, controlled by the chosen `EnhancementLevel` (Light / Standard / Max):

1. **Sharpen** — an unsharp-mask (3×3 cross kernel) applied in a fragment shader.
2. **Colour grading** — mild contrast + saturation boost.
3. **Upscale + re-encode** — the shorter edge is scaled toward a target resolution
   (480p / 720p / 1080p), capped per level so tiny clips aren't blown up absurdly, and
   re-encoded at a bitrate derived from output pixels × fps × a bits-per-pixel factor.

It is **not** ML super-resolution — that would need a bundled model and a much heavier
dependency. This shader pipeline is dependency-free and runs on any GLES 2.0 device.

---

## Pipeline

```
MediaExtractor ──► MediaCodec (decoder) ──► SurfaceTexture (OES texture)
                                                    │
                                          FrameRenderer (sharpen + grade shader)
                                                    │
                                          EGL input surface
                                                    │
                              MediaCodec (encoder, higher res/bitrate)
                                                    │
                                              MediaMuxer ──► enhanced.mp4
Audio track: copied verbatim (no re-encode) via a second MediaExtractor.
```

The encoder's input `Surface` owns the shared EGL context (`InputSurface`); the decoder
renders into a `SurfaceTexture` (`OutputSurface`) created in that same context, and each
frame is drawn through the shader onto the encoder surface. Based on the well-known Grafika
`DecodeEditEncode` pattern.

---

## Output spec math (pure, unit-tested)

`computeOutputSpec(srcW, srcH, fps, level)`:
- `scale = clamp(targetShortSide / min(srcW, srcH), 1.0, level.maxScale)` — never downscales.
- dimensions rounded to even (H.264 requirement).
- bitrate = `w * h * fps * level.bitsPerPixel`, clamped to [1 Mbps, 40 Mbps].

| Level | Target short side | Max scale | bpp | Sharpen | Saturation | Contrast |
|-------|-------------------|-----------|-----|---------|-----------|----------|
| Light | 480 | 1.0× | 0.10 | 0.30 | 1.05 | 1.03 |
| Standard | 720 | 1.5× | 0.14 | 0.60 | 1.12 | 1.06 |
| Max | 1080 | 2.0× | 0.20 | 1.00 | 1.20 | 1.10 |

---

## Files

```
ui/videoenhancer/
  EnhancementPreset.kt        // pure levels + output-spec math (JVM-testable)
  VideoEnhancerGl.kt          // InputSurface / OutputSurface / FrameRenderer (shader)
  VideoEnhancer.kt            // transcode engine (decode → GL → encode + audio copy)
  VideoEnhancerViewModel.kt   // state, pick/enhance/save/share/preview
  VideoEnhancerScreen.kt      // Compose UI
test/.../videoenhancer/
  EnhancementPresetTest.kt
```

Follows the existing ViewModel + Composable screen pattern.

### Navigation
- Route: `video_enhancer`, wired from `HomeScreen` (new tool card, AutoFixHigh icon).

### Output
- Cached at `cacheDir/enhanced_videos/`, exposed via the existing FileProvider
  (`file_paths.xml` cache-path added). Saved to `Movies/Zaki` via MediaStore.

---

## Known limitations / next steps

- Synchronous single pass on a background dispatcher; very large clips will be slow.
- AVC encoder only; could detect HEVC support for smaller files.
- Capture frame rate is read from metadata and falls back to 30 fps when absent.
- Audio is written after the full video pass (functionally correct, not perfectly
  interleaved); a production version should interleave by PTS.
- Could not be compiled in the web container (no Android SDK + restricted network);
  the pure math is covered by JVM unit tests.
