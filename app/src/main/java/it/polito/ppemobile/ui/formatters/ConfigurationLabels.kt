package it.polito.ppemobile.ui.formatters

import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.Runtime

fun CVModel.displayName(): String = when (this) {
    CVModel.YOLO11N -> "YOLO11n · SH17"
    CVModel.YOLO11N_SH17_PICTOR -> "YOLO11n · SH17 + Pictor"
    CVModel.YOLO11N_V2 -> "YOLO11n · Dataset v2 · 640"
    CVModel.YOLO11S -> "YOLO11s"
    CVModel.YOLO26N -> "YOLO26n · SH17 + Pictor"
    CVModel.YOLO26N_V2 -> "YOLO26n · Dataset v2 · 640"
    CVModel.YOLO26N_V2_960 -> "YOLO26n · Dataset v2 · 960"
}

fun CVModel.supportingText(): String = when (this) {
    CVModel.YOLO11N -> "FP32 · SH17 baseline · best checkpoint (100 epochs)"
    CVModel.YOLO11N_SH17_PICTOR ->
        "FP32 · SH17 + Pictor-PPE · best checkpoint (100 epochs)"
    CVModel.YOLO11N_V2 ->
        "FP32 · 640 × 640 · SH17 + Pictor-PPE + Construction-PPE · best checkpoint"
    CVModel.YOLO11S -> "Model artifact not available"
    CVModel.YOLO26N ->
        "FP32 · SH17 + Pictor-PPE · best checkpoint (100 epochs)"
    CVModel.YOLO26N_V2 ->
        "FP32 · 640 × 640 · SH17 + Pictor-PPE + Construction-PPE · best checkpoint"
    CVModel.YOLO26N_V2_960 ->
        "FP32 · 960 × 960 · same frozen PPE Dataset v2 · best checkpoint"
}

fun Runtime.displayName(): String = when (this) {
    Runtime.TFLITE -> "TensorFlow Lite"
    Runtime.ONNX_RUNTIME -> "ONNX Runtime"
    Runtime.REMOTE_SERVER -> "Remote Server"
}

fun OffloadingStrategy.displayName(): String = when (this) {
    OffloadingStrategy.ALWAYS_LOCAL -> "Always Local"
    OffloadingStrategy.ALWAYS_OFFLOAD -> "Always Offload"
    OffloadingStrategy.GREEDY -> "Greedy"
    OffloadingStrategy.REINFORCEMENT_LEARNING -> "Reinforcement Learning"
}
