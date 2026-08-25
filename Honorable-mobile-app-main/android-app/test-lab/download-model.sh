#!/usr/bin/env bash
set -euo pipefail
model_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/models/tinyclip"
revision=9463a9c508a344c837ffefe9d724f3827bf2dc79
base="https://huggingface.co/onnx-community/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M-ONNX/resolve/$revision"
mkdir -p "$model_dir"
download() { local remote=$1 local_name=$2 expected=$3; local target="$model_dir/$local_name"; if [[ ! -f "$target" ]] || ! echo "$expected  $target" | sha256sum -c --status; then curl --fail --location --retry 3 "$base/$remote" -o "$target.tmp"; echo "$expected  $target.tmp" | sha256sum -c --status; mv "$target.tmp" "$target"; fi; }
download onnx/model_quantized.onnx model_int8.onnx 10921310ddef06557ec1598d1260470a0a4db53f70ffe0deb60b946dcad6d27a
download tokenizer.json tokenizer.json 6d9109cc838977f3ca94a379eec36aecc7c807e1785cd729660ca2fc0171fb35
download preprocessor_config.json preprocessor_config.json 5df7e578c37e907a431daf47fd592fc49fa50d23ed4c41285a0a34a58a9d2e06
echo "Validated TinyCLIP assets ready at $model_dir"
