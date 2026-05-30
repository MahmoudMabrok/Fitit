# AI super-resolution model

The video enhancer's **AI super-resolution** engine ([MlSuperResolution]) loads
`super_resolution.tflite` from this folder at runtime.

## Bundled model

`super_resolution.tflite` is the **ESRGAN-TF2** 4× super-resolution model exported to TensorFlow
Lite.

- Source: <https://tfhub.dev/captain-pool/esrgan-tf2> (mirror:
  `https://storage.googleapis.com/download.tensorflow.org/models/tflite/esrgan/ESRGAN.tflite`)
- License: Apache-2.0
- Input: `[1, 50, 50, 3]` float32, RGB in `[0, 255]`
- Output: `[1, 200, 200, 3]` float32, RGB in `[0, 255]` (4× scale)
- Size: ~4.8 MB, SHA-256 `1a380d3744103e11ef343534aaff54815cae40769dcd00c023652a7e5bc47f4b`

`MlSuperResolution` reads the tile size (50) and scale (4) from the tensor shapes automatically, so
swapping in a different RGB super-resolution model only requires matching the I/O conventions above
(adjust the float normalisation in `MlSuperResolution.writeInput` / `nextChannel` if your model uses
`[0, 1]` instead of `[0, 255]`).

## Notes

- The engine activates only on API 28+ (it uses `MediaMetadataRetriever.getFrameAtIndex`). On older
  devices, or if this file is removed, the enhancer transparently falls back to the OpenGL
  sharpen/upscale engine and shows a short notice in the UI.
- Because the model has a fixed 50×50 input, each frame is processed as many tiles; per-frame ML
  inference is **much** slower than the GL engine and is best suited to short clips.
- `.tflite` files are kept uncompressed in the APK (see `noCompress` in `app/build.gradle.kts`) so the
  interpreter can memory-map them.
