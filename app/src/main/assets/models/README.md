# AI super-resolution model

The video enhancer's **AI super-resolution** engine ([MlSuperResolution]) loads a TensorFlow Lite
model from this folder at runtime. The binary is intentionally **not** committed (it is several MB and
device-specific), so the engine is inert until you drop one in.

## How to enable it

1. Obtain or train an RGB super-resolution `.tflite` model (e.g. an ESRGAN / Real-ESRGAN export, or
   the TF Hub ESRGAN model). It must:
   - take an input tensor shaped `[1, H, W, 3]` (fixed or dynamic),
   - return an output tensor shaped `[1, H * scale, W * scale, 3]`,
   - use `FLOAT32` (RGB normalised to `[0, 1]`) or `UINT8` tensors.
2. Save it here as **`super_resolution.tflite`** (see `MlSuperResolution.MODEL_ASSET`).
3. Rebuild. The "AI super-resolution" toggle in the enhancer becomes active on API 28+ devices.

When the file is absent — or on API < 28 — the enhancer transparently falls back to the OpenGL
sharpen/upscale engine and shows a short notice in the UI.

> Note: per-frame ML inference is much slower than the GL engine and is best suited to short clips.
> `.tflite` files are kept uncompressed in the APK (see `noCompress` in `app/build.gradle.kts`) so the
> interpreter can memory-map them.
