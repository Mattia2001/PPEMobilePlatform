# Motorola Edge 60 Pro — LiteRT MediaTek NPU success

Date: 2026-10-03

## Result

The application successfully initialized and executed the existing **FP32
YOLO26n 640 TFLite model through the MediaTek LiteRT compiler/dispatch path** on
the Motorola Edge 60 Pro. The application reported:

```text
requested=NPU
configuredBackend=NPU
runtimeApi=CompiledModel 2.1.5
fallbackReason=null
runtimeAccelerators=[GPU, CPU, NPU]
```

This supersedes the 2026-09-23 result, in which `CompiledModel` silently used
XNNPACK because no compiler or dispatch plugin was packaged.

## Components built and packaged

- LiteRT source tag: `v2.1.5` (commit `9d26e89`)
- Bazel: 7.4.1
- Android NDK: r28b, Clang 19
- Official package: `ai-edge-litert-sdk-mediatek`
- Target ABI: `arm64-v8a`
- Compiler plugin SHA-256:
  `DA86EA73378BDA9D0208BA0BD72CDE44C67162ABE44E58D4C1A992238BB5A708`
- Dispatch plugin SHA-256:
  `D62FB58D7D0140524D7A72B8F5633201D86DE63A1BE57FF8F2D7EAC4C69BE53C`

The native plugins are currently packaged only in the debug variant under
`app/src/debug/jniLibs/arm64-v8a/`. Native libraries use legacy/extracted
packaging because LiteRT discovers compiler and dispatch plugins by scanning the
application native-library directory.

## Evidence of accelerator dispatch

The decisive runtime messages were:

```text
1 compiler plugins were applied successfully: MediaTek compiler plugin (ver 0.1.0)
Replacing 1 out of 1 node(s) with delegate (DispatchDelegate)
```

The MediaTek compiler legalized all 414 YOLO operations, compiled for a runtime
compatible with NeuroPilot 8.2.26 and created the DispatchDelegate. Unlike the
previous attempt, no XNNPACK fallback was observed. The public Java API does not
expose per-operator silicon placement, so the JSON field
`placementVerifiedByJavaApi` remains false; placement is established here from
the successful MediaTek compilation and DispatchDelegate logs.

## Measurements

| Test | Result |
| --- | ---: |
| Minimal ADD model, one synthetic invocation | 6.733 ms |
| YOLO26n 640 FP32, one synthetic invocation | 7.557 ms |
| YOLO output elements | 75,600, all finite |
| First application initialization and warm-up | 12,718 ms |

The 7.557 ms value measures a single already-initialized synthetic model
invocation. It is **not** camera-to-overlay latency. The first initialization is
expensive because it includes on-device MediaTek compilation; the application
must warm the model before acquisition and future work should evaluate compiled
artifact caching.

## Precision finding

INT8 is **not mandatory** for this MediaTek LiteRT path: the existing FP32 YOLO
model compiled and ran successfully. A direct NNAPI diagnostic had shown that the
`mtk-mdla` device rejected a minimal FP32 graph while accepting quantized 8-bit,
but the higher-level MediaTek compiler/dispatch integration can target the
available NeuroPilot/Neuron stack differently. INT8 therefore remains an
optimization candidate whose accuracy and latency must be benchmarked, not a
prerequisite for NPU access.

## Validation status and next test

The Android debug app, androidTest APK and JVM unit tests build successfully; the
instrumentation test completed with `OK (1 test)`. The debug app installed on the
connected phone contains the working NPU backend.

The remaining promotion test is a sustained camera acquisition with the same
protocol used for CPU and GPU, recording end-to-end latency, warm-up, output
equivalence, temperature and energy. Release packaging should follow only after
checking plugin redistribution terms and cross-device fallback on the Samsung.

Machine-readable evidence is stored in
`diagnostics/motorola-npu-access-2026-10-03.json`.
