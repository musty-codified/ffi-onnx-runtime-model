#!/usr/bin/env bash
# Script to set up and download Phi-3 Mini ONNX model

# 1) Create & activate a venv (safe; won’t touch Homebrew’s Python)
python3 -m venv .venv
source .venv/bin/activate

# 2) Install the Hugging Face CLI inside the venv
python -m pip install -U pip "huggingface_hub[cli]"

# 3) Make the model folder and download a small ONNX GenAI model
mkdir -p models/deps

hf auth login

hf download microsoft/Phi-3-mini-4k-instruct-onnx \
  --include "cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/*" \
  --local-dir ./models/deps
