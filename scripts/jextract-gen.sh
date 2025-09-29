#!/usr/bin/env bash
set -euo pipefail

JX="/opt/jextract/bin/jextract"
ORT="/opt/ort"
GENAI="/opt/genai"
# Where outputs should appear on your HOST (mounted as /workspace in the container)
OUT="/workspace/panama-src"
SHIM_DIR="$OUT/genai/shims"

# Help jextract find libclang on Jammy (LLVM 14)
export LIBCLANG_PATH="${LIBCLANG_PATH:-/usr/lib/llvm-14/lib}"

mkdir -p "$OUT/ort" "$OUT/genai" "$SHIM_DIR"

cp "$GENAI/include/ort_genai_c.h" "$SHIM_DIR"/ort_genai_c.h

sed -i 's/typedef struct OgaRequest OgqRequest;/typedef struct OgaRequest OgaRequest;/' "$SHIM_DIR/ort_genai_c.h"
grep -n 'OgaRequest' "$SHIM_DIR/ort_genai_c.h" | head

ORT_HDR="$ORT/include/onnxruntime_c_api.h"
[ -f "$ORT_HDR" ] || { echo "ORT header missing"; exit 1; }

GENAI_HDR="$GENAI/include/ort_genai_c.h"
[ -f "$GENAI_HDR" ] || { echo "Missing GenAI headers"; exit 1; }

echo "Using ORT header   : $ORT_HDR"
echo "Using GenAI shim   : $SHIM_DIR/ort_genai_c.h"

# ONNX Runtime C API
"$JX" \
  -I "$ORT/include" \
  -l onnxruntime \
  --target-package ffi.ort \
  --output "$OUT/ort" \
  "$ORT_HDR"

# GenAI C API (use the discovered header!)

"$JX" \
  -I "$SHIM_DIR" -I "$GENAI/include" -I "$ORT/include" \
  -l onnxruntime-genai -l onnxruntime \
  --target-package ffi.genai \
  --output "$OUT/genai" \
  "$SHIM_DIR/ort_genai_c.h"

echo "jextract done → $OUT/{ort,genai}"

