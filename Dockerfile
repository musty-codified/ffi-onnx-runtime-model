
FROM eclipse-temurin:22-jdk-jammy

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y \
    curl unzip tar ca-certificates build-essential cmake git \
    llvm clang libclang-dev maven jq grep sed \
 && rm -rf /var/lib/apt/lists/*

# ONNX Runtime (base)
# https://github.com/microsoft/onnxruntime/releases/download/v1.22.0/onnxruntime-linux-x64-1.22.0.tgz
ARG ORT_VERSION=1.22.0
RUN set -eux; \
  mkdir -p /opt/ort; \
  curl -fL -o /tmp/ort.tgz \
    "https://github.com/microsoft/onnxruntime/releases/download/v${ORT_VERSION}/onnxruntime-linux-x64-${ORT_VERSION}.tgz"; \
  tar -tzf /tmp/ort.tgz > /dev/null; \
  tar -xzf /tmp/ort.tgz -C /opt/ort --strip-components=1; \
  rm -f /tmp/ort.tgz

# ONNX Runtime GenAI (C API + shared lib)
# https://github.com/microsoft/onnxruntime-genai/releases/download/v0.9.2/onnxruntime-genai-0.9.2-linux-x64.tar.gz
ARG GENAI_VERSION=0.9.2
RUN set -eux; \
  mkdir -p /opt/genai; \
  GENAI_NAME_A="onnxruntime-genai-${GENAI_VERSION}-linux-x64.tar.gz"; \
  GENAI_URL_A="https://github.com/microsoft/onnxruntime-genai/releases/download/v${GENAI_VERSION}/${GENAI_NAME_A}"; \
  GENAI_NAME_B="onnxruntime-genai-linux-x64-${GENAI_VERSION}.tgz"; \
  GENAI_URL_B="https://github.com/microsoft/onnxruntime-genai/releases/download/v${GENAI_VERSION}/${GENAI_NAME_B}"; \
  if curl -fLI "$GENAI_URL_A" >/dev/null 2>&1; then \
    curl -fL -o /tmp/genai.tar.gz "$GENAI_URL_A"; \
  elif curl -fLI "$GENAI_URL_B" >/dev/null 2>&1; then \
    curl -fL -o /tmp/genai.tar.gz "$GENAI_URL_B"; \
  else \
    GENAI_API="https://api.github.com/repos/microsoft/onnxruntime-genai/releases/tags/v${GENAI_VERSION}"; \
    GENAI_URL=$(curl -s "$GENAI_API" \
      | jq -r '.assets[] | select((.name|test("linux")) and (.name|test("x64")) and (.name|test("\\.(tgz|tar\\.gz|zip)$"))) | .browser_download_url' \
      | head -n1); \
    [ -n "$GENAI_URL" ] || { echo "No Linux x64 asset found for GenAI v${GENAI_VERSION}"; exit 1; }; \
    curl -fL -o /tmp/genai.tar.gz "$GENAI_URL"; \
  fi; \
  tar -tzf /tmp/genai.tar.gz > /dev/null; \
  tar -xzf /tmp/genai.tar.gz -C /opt/genai --strip-components=1; \
  rm -f /tmp/genai.tar.gz


ENV LD_LIBRARY_PATH=/opt/ort/lib:/opt/genai/lib:${LD_LIBRARY_PATH}
ENV LIBCLANG_PATH=/usr/lib/llvm-14/lib

# --- jextract (EA) ---
# Pull the current linux-x64 tarball from the official EA page and install.
RUN set -eux; \
  curl -fsSL https://jdk.java.net/jextract/ -o /tmp/jextract.html; \
  JX_URL=$(grep -oE 'https://download\.java\.net/java/early_access/jextract/[0-9]+/[0-9]+/openjdk-[^"]+_linux-x64_bin\.tar\.gz' /tmp/jextract.html | head -n1); \
  [ -n "$JX_URL" ] || { echo "Could not resolve jextract linux-x64 URL"; exit 1; }; \
  curl -fL -o /tmp/jextract.tar.gz "$JX_URL"; \
  mkdir -p /opt/jextract; \
  tar -xzf /tmp/jextract.tar.gz -C /opt/jextract --strip-components=1; \
  rm -f /tmp/jextract.tar.gz /tmp/jextract.html; \
  printf '%s\n' '#!/usr/bin/env bash' 'exec /opt/jextract/bin/jextract "$@"' > /usr/local/bin/jextract; \
  ln -sf /opt/jextract/bin/jextract /usr/local/bin/jextract;\
  chmod +x /usr/local/bin/jextract

WORKDIR /workspace
