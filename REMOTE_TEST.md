# Android-to-server smoke test

This procedure validates the `Always Offload` path against the stateless API v1.

## 1. Server

Pull the latest `PPERemoteInference` revision and rebuild the YOLO26n 640 service:

```bash
cd ~/PPERemoteInference/deploy/compose
sudo docker compose -p ppe-yolo26n-640 \
  --env-file yolo26n-640.env \
  -f compose.yaml \
  -f compose.gpu.yaml \
  up -d --build
curl http://192.168.2.126:8081/v1/status
```

The status response should be `ready` and include `warmUp.completed: true`.

## 2. Phone connectivity

Before starting an acquisition, make the phone able to reach
`http://192.168.2.126:8081`. When testing outside the server LAN, activate the VPN
and verify that the laptop's VPN sharing/tethering configuration routes phone
traffic into that tunnel.

In the app:

1. open **Settings**;
2. enter host `192.168.2.126` and port `8081`;
3. select **Save and test server**;
4. verify that the result reports `Ready`, `yolo26n-ppe-v2-640`, and the server GPU.

## 3. Remote acquisition

Create a new acquisition with:

- model: **YOLO26n · Dataset v2 · 640**;
- runtime: **Remote Server**;
- strategy: **Always Offload**;
- JPEG quality: initially **80**;
- analysis resolution: initially **720p** to limit uplink traffic.

Only one request is kept in flight. CameraX retains the most recent frame while the
current request is processed, preventing an unbounded client queue.

## 4. Expected export

Every remotely processed `frames.jsonl` record has `processingSegment: REMOTE` and
contains a `remoteInference` object with:

- JPEG and response byte counts;
- encode and request round-trip latency;
- transport/API overhead;
- server queue, decode, preprocess, inference, postprocess and total time;
- deployed model/device identity and server timestamps.

The ordinary detection structure is unchanged, so local and remote results remain
ordered in the same application-owned JSONL schema.

## Interpretation

This smoke test covers camera capture, JPEG encoding, VPN/network transport, remote
GPU inference, JSON decoding, overlay rendering and export. It does not yet exercise
an adaptive policy: every accepted frame is remotely processed.
