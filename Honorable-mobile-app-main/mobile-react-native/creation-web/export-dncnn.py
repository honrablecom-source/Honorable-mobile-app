#!/usr/bin/env python3
"""Reproduce the small licensed KAIR DnCNN25 graph; no download or training.

Source/weights: cszn/KAIR fc1732f4a4514e42ce15e5b3a1e18c828af47a1e.
Architecture/weights MIT, Copyright (c) 2019 Kai Zhang. Full notice ships
in THIRD_PARTY/DNCNN-MIT.txt. Uses published merged-BN 17-convolution graph.
Run in an isolated environment with torch2.6.0+cpu, onnx1.17.0,
onnxruntime1.20.1, numpy2.2.6. Original weights must match the hard-coded pin.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from torch import nn

WEIGHTS_SHA = "0451a70de9b672ae037270498fbb1c17a1c1c4403785df586ff65df5b858e5b0"


class DnCNN25(nn.Module):
    def __init__(self):
        super().__init__()
        layers = []
        for index in range(17):
            layers.append(nn.Conv2d(1 if index == 0 else 64, 1 if index == 16 else 64, 3, padding=1, bias=True))
            if index != 16:
                layers.append(nn.ReLU(inplace=True))
        self.model = nn.Sequential(*layers)

    def forward(self, value):
        return value - self.model(value)


def digest(file):
    return hashlib.sha256(Path(file).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    if digest(args.weights) != WEIGHTS_SHA:
        raise ValueError("The published MIT weights do not match the pin")
    if torch.__version__ != "2.6.0+cpu" or onnx.__version__ != "1.17.0" or ort.__version__ != "1.20.1" or np.__version__ != "2.2.6":
        raise ValueError("Use the exact documented isolated export toolchain")
    torch.set_num_threads(1)
    model = DnCNN25().eval()
    model.load_state_dict(torch.load(args.weights, weights_only=True, map_location="cpu"), strict=True)
    example = torch.zeros(1, 1, 128, 128)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, example, args.output, input_names=["noisy"], output_names=["clean"], opset_version=17,
                      export_params=True, do_constant_folding=True, dynamo=False)
    onnx.checker.check_model(onnx.load(args.output), full_check=True)
    session = ort.InferenceSession(args.output, providers=["CPUExecutionProvider"])
    x, y = np.meshgrid(np.arange(128), np.arange(128))
    clean = (0.2 + 0.5*x/127 + 0.1*np.sin(y/14)).astype(np.float32)
    clean[32:88, 40:75] = 0.85
    noise = np.random.default_rng(38219).normal(0, 25/255, clean.shape).astype(np.float32)
    noisy = np.clip(clean+noise, 0, 1)[None, None]
    with torch.inference_mode():
        expected = model(torch.from_numpy(noisy)).numpy()
    actual = session.run(["clean"], {"noisy": noisy})[0]
    parity = float(np.max(np.abs(expected-actual)))
    before = float(np.mean((noisy[0, 0]-clean)**2))
    after = float(np.mean((np.clip(actual[0, 0], 0, 1)-clean)**2))
    if parity > 0.00001 or not after < before/2:
        raise ValueError("Real inference parity/synthetic noise-removal gate failed")
    report = {"sourceRepository": "https://github.com/cszn/KAIR", "sourceCommit": "fc1732f4a4514e42ce15e5b3a1e18c828af47a1e",
              "license": "MIT", "weightsSha256": WEIGHTS_SHA, "onnxSha256": digest(args.output),
              "onnxBytes": Path(args.output).stat().st_size, "torch": torch.__version__, "onnx": onnx.__version__,
              "onnxruntime": ort.__version__, "numpy": np.__version__, "opset": 17, "shape": [1, 1, 128, 128],
              "maxAbsoluteTorchOrtDifference": parity, "generatedSigma25MseBefore": before,
              "generatedSigma25MseAfter": after, "note": "Generated grayscale fixture only; no general accuracy/device quality claim"}
    Path(args.report).write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
