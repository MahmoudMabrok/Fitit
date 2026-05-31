# AI noise-removal models (DTLN)

The audio enhancer's **AI noise removal** engine ([MlAudioDenoiser]) loads
`model_1.tflite` and `model_2.tflite` from this folder at runtime.

## Bundled models

These are the two stateful TensorFlow Lite models of **DTLN** (Dual-signal
Transformation LSTM Network), a real-time speech-denoising network.

- Source: <https://github.com/breizhn/DTLN> — `pretrained_model/model_1.tflite`
  and `pretrained_model/model_2.tflite`
  - `https://github.com/breizhn/DTLN/raw/master/pretrained_model/model_1.tflite`
  - `https://github.com/breizhn/DTLN/raw/master/pretrained_model/model_2.tflite`
- License: **MIT** (see the DTLN repository `LICENSE`).
- Reference: N. L. Westhausen and B. T. Meyer, *"Dual-Signal Transformation LSTM
  Network for Real-Time Noise Suppression"*, Interspeech 2020.
- Sizes / SHA-256:
  - `model_1.tflite` — 1,459,944 bytes,
    `91281a38e80fe9fd330e28eda7e16fe4e483ee5199a3e687a099939013c25de0`
  - `model_2.tflite` — 2,515,804 bytes,
    `7ae37ec802862d8a65b5cdabfbcbbe22caaf7cd39e79adf574d15837d1520830`

## How DTLN runs

DTLN operates on **16 kHz mono** audio in overlapping blocks of **512 samples**
with a **128-sample hop** (75 % overlap):

1. The 512-sample input block is transformed with an rFFT; its **magnitude**
   `[1, 1, 257]` is fed to `model_1` together with that model's LSTM state.
2. `model_1` returns a spectral **mask** `[1, 1, 257]` and its updated state. The
   mask is applied to the magnitude and combined with the **original phase**, then
   an inverse rFFT yields a 512-sample time-domain block.
3. That block `[1, 1, 512]` is fed to `model_2` (a learned 1-D convolutional
   transform + LSTM) together with `model_2`'s state, producing the cleaned block
   and its updated state.
4. Cleaned blocks are recombined with overlap-add.

Both models carry two LSTM layers of 128 units; their states (shape
`[1, 2, 128, 2]`) are **carried across blocks** for real-time, causal operation.

## Notes

- `MlAudioDenoiser.isAvailable(context)` checks that both files are present (and
  the OS can load them). When either is missing — or the model fails to load —
  the enhancer transparently falls back to the pure-Kotlin spectral-gating
  denoiser ([AudioDsp.reduceNoise]) and shows a short notice in the UI, mirroring
  the video enhancer's GL/ML fallback.
- Inference runs on the TensorFlow Lite **GPU delegate** when the device supports
  it, otherwise on a multi-threaded CPU interpreter.
- `.tflite` files are kept uncompressed in the APK (see `noCompress` in
  `app/build.gradle.kts`) so the interpreter can memory-map them.
</content>
</invoke>
