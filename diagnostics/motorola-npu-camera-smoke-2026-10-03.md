# Motorola Edge 60 Pro — NPU camera smoke test

Date: 2026-10-03  
Acquisition: `EXP_1791036437950_f606aa06`

## Configuration

- Model: YOLO26n PPE dataset v2, input 640
- Runtime: LiteRT/TFLite 2.1.5
- Requested accelerator: NPU
- Execution strategy: always local
- Camera configuration: 1080p, requested 30 FPS
- Frames recorded: 33

Every frame reported:

```text
requested=NPU
configuredBackend=NPU
runtimeApi=CompiledModel 2.1.5
fallbackReason=null
runtimeAccelerators=[GPU, CPU, NPU]
```

No out-of-order, discarded or remotely processed frames were recorded.

## Latency

The first frame includes MediaTek on-device compilation and warm-up:

- reported initialization and warm-up: 19,366.8 ms
- first-frame inference: 19,674 ms
- first-frame end-to-end: 19,676 ms

The exported aggregate average of 728.4 ms includes this first frame and is
therefore not representative of steady-state execution.

After excluding only the first frame:

| Metric | Inference | End-to-end |
| --- | ---: | ---: |
| Frames | 32 | 32 |
| Mean | 136.4 ms | 139.7 ms |
| Median | 144 ms | 147 ms |
| p95 | 175 ms | 177 ms |
| p99 (nearest-rank sample) | 182 ms | 183 ms |
| Minimum | 85 ms | 86 ms |
| Maximum | 255 ms | 303 ms |

Thirty-one of 32 steady-state frames (96.9%) met the 200 ms deadline. The only
violation was `frame_000016` at 303 ms end-to-end. The observed steady-state
throughput was approximately 6.07 processed frames/s; requested camera FPS is not
equivalent to inference throughput because the pipeline processes one frame at a
time.

## Detection sanity check

Fourteen frames contained one post-NMS person detection and 19 contained none,
which is plausible for this short framing-dependent smoke test. The detected
person confidence reached approximately 0.81 in the sampled opening frames. The
test was not designed to evaluate PPE accuracy.

## Interpretation

The acquisition confirms that the application uses the configured NPU backend
without runtime fallback and that steady-state camera-to-overlay latency is
generally below 200 ms. Performance appears comparable to the earlier GPU smoke
test, but this short 5.3-second steady-state interval is insufficient for a formal
CPU/GPU/NPU comparison or thermal analysis. A controlled, longer benchmark with
the same scene and model is still required.

The main engineering issue exposed by this run is the 19-second cold-start cost.
The UI should complete NPU initialization before acquisition, and future work
should investigate MediaTek compiled-artifact caching.
