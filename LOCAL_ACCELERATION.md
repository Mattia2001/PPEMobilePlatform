# Local acceleration and Motorola benchmark

The APK supports the existing LiteRT 2.1.5 CPU Interpreter baseline plus the
CompiledModel GPU/NPU API. Select **Local accelerator** in acquisition configuration.
CPU remains the default, including when loading older saved configurations.

| Selection | Attempts, in order |
| --- | --- |
| CPU | CPU Interpreter |
| GPU preferred | GPU CompiledModel, CPU Interpreter |
| NPU preferred | NPU CompiledModel, CPU Interpreter |
| Auto | NPU CompiledModel, GPU CompiledModel, CPU Interpreter |

Auto is an availability/compatibility policy, not a fastest-backend benchmark.
All model creation, invocation and destruction use the same dedicated thread.
Initialization includes a synthetic warm-up and finite-output/size validation.
Model input remains RGB FP32 NCHW; preprocessing, thresholds and NMS are unchanged.
CompiledModel copies input/output arrays; those costs are included in acquisition
timing. A failed accelerated initialization or invocation falls back to CPU and
records the reason. A CPU failure remains an acquisition error.

## What hardware reporting means

Android reports SoC manufacturer/model (API 31+), processor count and the OpenGL
renderer, obtained from a temporary EGL context. LiteRT reports accelerators
available to its environment. This is not an inventory of all physical chips.
In particular, "NPU runtime unavailable" does not mean the phone has no NPU.

CompiledModel's Kotlin API in 2.1.5 does not report operator partition placement.
An accelerated configuration passing warm-up therefore says **configured**, not
"100% GPU/NPU". JSONL explicitly marks acceleratorPlacementVerified=false for
these paths. Proving placement needs runtime profiling on the physical device.

## MediaTek limitation in this APK

Update 2026-09-23: application-level diagnostics successfully loaded the public
Neuron libraries and executed a quantized ADD graph through the explicitly selected
MediaTek MDLA NNAPI driver, with framework CPU fallback disabled. See
[NPU_DEVICE_DIAGNOSTICS.md](NPU_DEVICE_DIAGNOSTICS.md). This proves a usable driver
access path, **not** YOLO support or working LiteRT CompiledModel NPU integration.

The official v2.1.5 and v2.2.0 NPU JIT release archives inspected on 2026-09-16 do
not contain a MediaTek runtime directory. No unverified vendor binary is bundled.
This APK can probe the standard LiteRT environment and attempt its NPU API if it
reports availability, but it does **not** claim working MediaTek NPU support.
Vendor compiler/dispatch/runtime libraries and model compatibility still need
verification. Neither silicon specifications nor a successful GPU run proves NPU use.

Official references:
- https://developers.google.com/edge/litert/next/mediatek
- https://developers.google.com/edge/litert/next/npu
- https://github.com/google-ai-edge/LiteRT/releases/tag/v2.1.5

## Export and first physical-device test

Metadata includes configuration.localAcceleration. Each local frame includes
detectionResult.localExecution: requested policy, configured backend, runtime API,
fallback reason, initializationAndWarmUpMs, SoC, GPU renderer, runtime accelerator
list, and acceleratorPlacementVerified. Remote results retain their own metrics.

1. Install the APK on the Motorola and press Refresh on Home. Record Hardware
   and Runtime details. The runtime check uses the saved model/policy; it is a
   separate initialization from acquisition.
2. Select YOLO26n v2 640, Always Local, CPU. Keep camera resolution, lighting,
   FPS limit and scene fixed; run for 2–3 minutes and export the ZIP.
3. Cool the phone to a comparable starting temperature. Repeat with GPU preferred.
   Check the configured backend and fallback reason, not just the requested policy.
4. Inspect boxes on the same scene/video, including class identity and coordinate
   alignment, before trusting accelerated latency. Synthetic warm-up is not an
   accuracy equivalence test.
5. Compare end-to-end p50/p95, deadline miss fraction above 200 ms, throughput and
   temperature evolution. Repeat runs with alternating order. Test 960 afterward.

NPU/Auto tests are useful diagnostics but a fallback is not an NPU benchmark.
No physical-device performance or accuracy claims are made by a successful build.
