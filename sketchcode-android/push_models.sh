#!/usr/bin/env bash
#
# Push Whisper-Large-V3-Turbo models to the phone for SketchCode.
#
# Usage:
#   ./push_models.sh <encoder.onnx> <encoder.bin> <decoder.onnx> <decoder.bin>
#
# The app must be installed and launched at least once before running this script,
# so the app-owned directories exist on the device.
#
# Example:
#   ./push_models.sh \
#     ~/downloads/WhisperEncoder.onnx ~/downloads/WhisperEncoder.bin \
#     ~/downloads/WhisperDecoder.onnx ~/downloads/WhisperDecoder.bin

set -euo pipefail

APP_ID="com.sketchcode.app"
BASE_DIR="/sdcard/Android/data/${APP_ID}/files/models"
ENCODER_DIR="${BASE_DIR}/encoder"
DECODER_DIR="${BASE_DIR}/decoder"

if [ $# -ne 4 ]; then
    echo "Usage: $0 <encoder.onnx> <encoder.bin> <decoder.onnx> <decoder.bin>"
    echo ""
    echo "Files from Qualcomm AI Hub precompiled_qnn_onnx export."
    echo "The app must be installed and launched once first."
    exit 1
fi

ENCODER_ONNX="$1"
ENCODER_BIN="$2"
DECODER_ONNX="$3"
DECODER_BIN="$4"

# Validate files exist
for f in "$ENCODER_ONNX" "$ENCODER_BIN" "$DECODER_ONNX" "$DECODER_BIN"; do
    if [ ! -f "$f" ]; then
        echo "Error: file not found: $f"
        exit 1
    fi
done

# Check adb is connected
if ! adb get-state &>/dev/null; then
    echo "Error: no device connected. Connect your phone via USB and enable USB debugging."
    exit 1
fi

# Check app is installed
if ! adb shell pm list packages | grep -q "$APP_ID"; then
    echo "Error: $APP_ID is not installed on the device."
    echo "Build and install the app from Android Studio first."
    exit 1
fi

echo "Pushing Whisper models to device..."
echo ""

# Launch app briefly to create directories (ModelManager.init creates them)
echo "[1/6] Ensuring app directories exist..."
adb shell am start -n "${APP_ID}/.MainActivity" > /dev/null 2>&1 || true
sleep 2
adb shell am force-stop "$APP_ID" > /dev/null 2>&1 || true

# Verify directories exist
if ! adb shell "[ -d '${ENCODER_DIR}' ]"; then
    echo "Warning: encoder dir not created by app, creating manually..."
    adb shell mkdir -p "$ENCODER_DIR"
fi
if ! adb shell "[ -d '${DECODER_DIR}' ]"; then
    echo "Warning: decoder dir not created by app, creating manually..."
    adb shell mkdir -p "$DECODER_DIR"
fi

# Push encoder
ENCODER_ONNX_SIZE=$(stat -f%z "$ENCODER_ONNX" 2>/dev/null || stat -c%s "$ENCODER_ONNX")
ENCODER_BIN_SIZE=$(stat -f%z "$ENCODER_BIN" 2>/dev/null || stat -c%s "$ENCODER_BIN")
echo "[2/6] Pushing encoder ONNX ($(numfmt --to=iec "$ENCODER_ONNX_SIZE" 2>/dev/null || echo "${ENCODER_ONNX_SIZE} bytes"))..."
adb push "$ENCODER_ONNX" "${ENCODER_DIR}/model.onnx"

echo "[3/6] Pushing encoder bin ($(numfmt --to=iec "$ENCODER_BIN_SIZE" 2>/dev/null || echo "${ENCODER_BIN_SIZE} bytes"))... (this may take a while)"
adb push "$ENCODER_BIN" "${ENCODER_DIR}/model.bin"

# Push decoder
DECODER_ONNX_SIZE=$(stat -f%z "$DECODER_ONNX" 2>/dev/null || stat -c%s "$DECODER_ONNX")
DECODER_BIN_SIZE=$(stat -f%z "$DECODER_BIN" 2>/dev/null || stat -c%s "$DECODER_BIN")
echo "[4/6] Pushing decoder ONNX ($(numfmt --to=iec "$DECODER_ONNX_SIZE" 2>/dev/null || echo "${DECODER_ONNX_SIZE} bytes"))..."
adb push "$DECODER_ONNX" "${DECODER_DIR}/model.onnx"

echo "[5/6] Pushing decoder bin ($(numfmt --to=iec "$DECODER_BIN_SIZE" 2>/dev/null || echo "${DECODER_BIN_SIZE} bytes"))... (this may take a while)"
adb push "$DECODER_BIN" "${DECODER_DIR}/model.bin"

# Verify
echo "[6/6] Verifying..."
echo ""
adb shell "ls -lh ${ENCODER_DIR}/ && echo '---' && ls -lh ${DECODER_DIR}/"

echo ""
echo "Done! Models pushed successfully."
echo "Launch the app — Whisper should initialize on the NPU."
