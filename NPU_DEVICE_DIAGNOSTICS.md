# Motorola Edge 60 Pro — NPU diagnostics

## Success update: MediaTek LiteRT execution verified on 2026-10-03

The blocker documented below has been resolved for the debug application. The
official MediaTek compiler and dispatch plugins were built against LiteRT 2.1.5,
packaged for `arm64-v8a`, and registered through
`BuiltinNpuAcceleratorProvider`. The existing FP32 YOLO26n 640 model now reports
`configuredBackend=NPU`, `fallbackReason=null`; runtime logs show the MediaTek
compiler plugin and `DispatchDelegate`, with no XNNPACK fallback.

This proves that the NPU path is accessible to the ordinary application and that
INT8 is not a prerequisite for this model/runtime combination. A single warmed
synthetic YOLO invocation took 7.557 ms, while first initialization and on-device
compilation took about 12.7 s. Neither number is camera-to-overlay latency.

Full evidence, limitations and hashes are in
`diagnostics/motorola-npu-success-2026-10-03.md` and
`diagnostics/motorola-npu-access-2026-10-03.json`. The plugins remain debug-only
until redistribution and release packaging are reviewed.

## Follow-up: application execution verified on 2026-09-23

**The MediaTek accelerator drivers are accessible to our ordinary debug APK.**
This supersedes any interpretation that the NPU is simply reserved for Motorola
or Google applications. It does not establish that YOLO is running on the NPU.

Current firmware: `motorola/cybert_g_syse/cybert:16/W1VVS36H.7-21-5-2/411f42-1b697:user/release-keys`;
Android 16/API 36, MT6897. The firmware differs from the September 16 inspection,
so the two sessions are not a controlled before/after comparison.

### What was actually tested

Built and installed a debug APK and a separate instrumentation APK; no root,
platform signature, firmware modification, camera capture or experiment deletion.
The test runs in the application's UID. JNA is an **androidTest-only** dependency
used to call the documented native Android NNAPI functions. Optional vendor native
library declarations are restricted to the debug manifest.

All five `System.loadLibrary` attempts succeeded in the instrumented application:
`libneuron_runtime.so`, `libneuron_runtime.7.so`,
`libneuron_graph_delegate.mtk.so`, `libneuronusdk_adapter.mtk.so`,
`libtflite_mtk.mtk.so`. Loading a library alone does not validate its delegate ABI.

The test constructs a 64-element ADD graph and checks every output against a
known answer. Each compilation explicitly selects **one** NNAPI device through
`ANeuralNetworksCompilation_createForDevices`; framework CPU fallback is disabled.

| Explicit driver | Driver version | FP32 ADD | Unsigned quantized 8-bit ADD |
| --- | --- | --- | --- |
| mtk-dsp_shim | 7.2.4 | Correct output | Correct output |
| mtk-mdla_shim | 7.2.4 | Reported unsupported | Correct output |
| mtk-neuron_shim | 7.2.4 | Correct output | Correct output |
| nnapi-reference (CPU control) | Firmware version | Correct output | Correct output |

The three MediaTek devices report type ACCELERATOR (4). This establishes successful
execution through their selected driver interfaces; internal silicon placement
still needs vendor profiling. The tiny ADD timings are **not YOLO latency estimates**.
MDLA's rejection of this FP32 graph is not proof that every floating-point YOLO
conversion is impossible. It is a reason to test precision and conversion carefully.

### Why the normal app can still report NPU unavailable

NNAPI driver accessibility and LiteRT `CompiledModel` availability are different
layers. The application has not bundled/configured MediaTek's LiteRT compiler and
dispatch plugins. Merely having Neuron libraries in the firmware, or selecting
`Accelerator.NPU`, does not supply that integration. The CPU/GPU acquisition paths
were not changed by this diagnostic and YOLO NPU support is not enabled.

The updated probe reports `GPU, CPU` for the default LiteRT environment, but
`NPU, GPU, CPU` when using `Environment.create(BuiltinNpuAcceleratorProvider(context))`, and
`builtinProviderDeviceSupported=true`. The built-in provider also returns
`isLibraryReady=true`, but inspection of the exact 2.1.5 class bytecode shows that
this method unconditionally returns true: it is **not a library-presence check**.
The provider assumes the necessary libraries are already packaged in the app's
native-library directory. Do not use this flag as proof of NPU readiness.
Likewise, successful NPU registration with the explicit provider is not proof
that model compilation or dispatch will succeed. The production code currently
uses the default environment; this distinction must be addressed when integrating
an actual vendor-backed execution path.

Saved machine-readable evidence:
`diagnostics/motorola-npu-access-2026-09-23.json`.

### Actual YOLO26n 640 attempt through the explicit provider

The diagnostic also requested `Accelerator.NPU` with our existing FP32 TFLite
model. `CompiledModel.create` returned successfully and one synthetic invocation
produced 75,600 finite outputs. **This is not a successful NPU inference.** Logs
from that same test process explicitly show:

```text
Failed to apply compiler plugins: No compiler plugin found
No dispatch library found in .../lib/arm64
Failed to initialize Dispatch API
Created TensorFlow Lite XNNPACK delegate for CPU.
```

See `diagnostics/motorola-litert-yolo-2026-09-23.log`. XNNPACK replaced 412 of 414
nodes: the execution used CPU, not a working NPU dispatch. The requested NPU policy
and successful return from CompiledModel do not prohibit CPU execution. The recorded
single synthetic run (~201 ms) is neither an NPU benchmark nor end-to-end latency.
This confirms the concrete current blocker: LiteRT MediaTek compiler/dispatch
integration, not a blanket ban on third-party access to the accelerator drivers.

### NeuroPilot/LiteRT route and remaining work

Google documents MediaTek AOT and on-device compilation and explicitly lists MT6897
(under Dimensity 8300). Our phone reports MT6897; this is favorable evidence but
not certification of this particular Motorola firmware/model combination.

The official LiteRT v2.1.5 source also publishes a MediaTek SDK download procedure:
`ci/tools/python/vendor_sdk/mediatek/setup.py`, distributed as
`ai-edge-litert-sdk-mediatek`. Thus absence of MediaTek files in the previously
inspected JIT release ZIPs is **not** evidence that no SDK is available. The SDK
download attempted in this session timed out; partial archives under
`diagnostics/vendor-downloads/` are ignored by git, not validated or integrated.
The packaging script targets Linux host libraries for AOT compilation; that alone
does not establish availability of ready-to-package Android dispatch binaries.

Recommended next experiment, separate from production CPU/GPU support:

1. Obtain/build the matching MediaTek LiteRT compiler/dispatch components, check
   their licenses and compatibility with the firmware's Neuron runtime.
2. Run a minimal model through that **modern LiteRT** integration, inspecting
   partitioning/fallback. NNAPI here is a diagnostic bridge, not the proposed
   long-term runtime (deprecated starting with Android 15).
3. Compile/test YOLO26n 640; record supported operations, precision, layout,
   delegated partitions, initialization failures and output equivalence.
4. If quantization is required, use representative calibration data and re-evaluate
   aggregate/per-class accuracy. Keep existing FP32 models unchanged.
5. Benchmark sustained end-to-end latency only after correctness and placement
   checks. No NPU speedup or 200 ms deadline guarantee follows from this probe.

Motorola/MediaTek assistance is now best framed as a request for the **supported
SDK/plugin version, sample APK, profiling tools and distribution terms**, rather
than a request to unlock hardware that appears universally inaccessible.

### Reproduce

Source: `app/src/androidTest/java/it/polito/ppemobile/NpuAccessDiagnosticsTest.kt`.
From the project directory with the normal JDK/Android SDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -r -e class it.polito.ppemobile.NpuAccessDiagnosticsTest it.polito.ppemobile.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell run-as it.polito.ppemobile cat files/npu-diagnostic.json
```

JUnit completion alone is not a success criterion: inspect `supported`,
`outputCorrect`, and every `error` in the JSON. Unsupported devices are reported,
not converted into failing assertions. The test's native size_t bindings require
an arm64 process. It does not change the saved acquisition configuration.

References:

- [MediaTek with LiteRT](https://developers.google.com/edge/litert/next/mediatek)
- [LiteRT NPU integration](https://developers.google.com/edge/litert/next/npu)
- [NNAPI explicit device selection and fallback](https://developer.android.com/ndk/guides/neuralnetworks)
- [Official MediaTek SDK packaging source](https://github.com/google-ai-edge/LiteRT/blob/v2.1.5/ci/tools/python/vendor_sdk/mediatek/setup.py)
- [LiteRT MediaTek adapter loading implementation](https://github.com/google-ai-edge/LiteRT/blob/v2.1.5/litert/vendors/mediatek/neuron_adapter_api.cc)
- [NeuroPilot portal](https://neuropilot.mediatek.com/)
- [MediaTek NeuroPilot introduction](https://developer.mediatek.com/ai/6423c8b6c612745b3a4baa2c.html)

## Historical inspection: 2026-09-16

Read-only ADB inspection performed on 2026-09-16. No root or firmware changes.

## Device

- Android 16, SDK 36; ABI arm64-v8a.
- SoC reported by Android: MediaTek MT6897.
- GPU previously probed through EGL: Mali-G615 MC6.
- Build fingerprint: motorola/cybert_g_syse/cybert:16/W1VV36H.7-21-5/bdc1c-bb36b3:user/release-keys.

## Firmware evidence

Service manager lists:

- android.hardware.neuralnetworks.IDevice/mtk-dsp_shim
- android.hardware.neuralnetworks.IDevice/mtk-mdla_shim
- android.hardware.neuralnetworks.IDevice/mtk-neuron_shim
- vendor.mediatek.hardware.apuware.apusys.INeuronApusys/default
- vendor.mediatek.hardware.apuware.utils.IApuwareUtils/default

Vendor libraries include libneuron_runtime.so, libneuron_runtime.7.so,
libneuron_graph_delegate.mtk.so, libneuron_adapter_mgvi.so,
libneuron_wrapper.so, libneuron_platform.so and libapusys.so.

/vendor/etc/public.libraries.txt explicitly includes libneuron_runtime.so and
libneuron_runtime.7.so, as well as libOpenCL.so. This is favorable evidence for
application access, not proof that all dependencies or APIs are accessible.
Library presence does not establish license rights to copy or redistribute it.

`dumpsys neuralnetworks` reports no service under that short name. This does not
negate the separately registered AIDL IDevice services above.

## Current APK

LiteRT 2.1.5 Environment reports GPU and CPU. Previous startup logs show NPU
registration failing with kLiteRtStatusErrorInvalidArgument, before YOLO NPU
compilation. The app has not integrated a MediaTek LiteRT dispatch/compiler plugin.
Neither a device access prohibition nor YOLO NPU incompatibility is established.

## Next diagnostic stages

1. In a small app diagnostic, test loading the publicly exposed Neuron library
   from the application namespace, with the required optional native-library
   declaration. Record load/dependency errors. A successful dlopen is not an
   inference test; avoid calling undocumented entry points.
2. Obtain official compatible MediaTek SDK/LiteRT dispatch and compiler plugins,
   sample code and distribution terms. Check the supported firmware/API version.
3. Run a small official model through that integration. Verify operator placement
   and absence of CPU fallback using vendor/runtime profiling.
4. Test YOLO FP32 NCHW only after the runtime works. If conversion, AOT compilation
   or quantization is required, validate accuracy against the original checkpoint.
5. Benchmark sustained end-to-end latency, initialization, temperature and energy
   under the same protocol used for CPU/GPU. NNAPI can be a separate legacy
   diagnostic route; do not infer modern LiteRT availability from NNAPI services.

## Questions for Motorola/Lenovo or MediaTek

- Which SDK and LiteRT plugin/runtime versions support this MT6897 firmware?
- Is Neuron usable by ordinary third-party APKs without platform signing/root?
- How should public Neuron libraries be loaded and initialized on Android 16?
- Are compiler/dispatch binaries available for standalone APK distribution?
- Is there a working classical-CV sample and a profiler showing NPU placement?
- Which precisions/layouts/operators are supported for YOLO-like models?

Provide build fingerprint and these findings with the request. Do not include
personal experiment videos or unrelated device/account identifiers.
