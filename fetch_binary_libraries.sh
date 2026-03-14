#!/bin/bash

# Configuration
SDK_NAME="VSBarcodeReader-Android-EVAL"
VERSION="7.3.1"
DOWNLOAD_URL="https://sdk.visionsmarts.com/downloads/${SDK_NAME}-v${VERSION}.tar.gz"
EXPECTED_SHA256="4175f65ce3d9140110ee9475e9ebe6f58c899faa1d9fa8446013c0bb365b52ac"

# Colors
BOLD_GREEN="\033[1;32m"
BOLD_RED="\033[1;31m"
RESET="\033[0m"

echo "--------------------------------------------------------"
echo "Installing $SDK_NAME (Evaluation Version)"
echo "--------------------------------------------------------"
echo "By proceeding, you agree to the Evaluation License terms:"
echo "https://github.com/VisionSmarts/VSBarcodeReader-Android/blob/main/LICENSE"
echo ""
echo "NOTE: This SDK transmits anonymous telemetry to our servers."
echo "--------------------------------------------------------"

# 1. License Check
read -p "Do you accept these terms? (y/n): " confirm
if [[ $confirm != [yY] ]]; then
    echo "Installation cancelled."
    exit 1
fi

# 2. Check for required tools
if ! command -v tar &> /dev/null; then
    echo "Error: 'tar' is not installed. Please install it to continue."
    exit 1
fi
if command -v sha256sum &> /dev/null; then
    SHA_CMD="sha256sum"
elif command -v shasum &> /dev/null; then
    SHA_CMD="shasum -a 256"
else
    echo "Error: No SHA-256 tool found (sha256sum or shasum). Please install one."
    exit 1
fi

# 3. Download
TMP_FILE="$(mktemp /tmp/${SDK_NAME}-XXXXXX.tar.gz)"

cleanup() {
    rm -f "$TMP_FILE"
}
trap cleanup EXIT

echo "Downloading $SDK_NAME..."
if ! curl -fL --progress-bar "$DOWNLOAD_URL" -o "$TMP_FILE"; then
    echo "Error: Download failed. Check your connection or rate limits."
    exit 1
fi

# 4. Verify SHA-256
echo "Verifying SHA-256 checksum..."
ACTUAL_HASH="$($SHA_CMD "$TMP_FILE" | awk '{print $1}')"

if [[ "$ACTUAL_HASH" != "$EXPECTED_SHA256" ]]; then
    rm -f "$TMP_FILE"
    echo -e "${BOLD_RED}--------------------------------------------------------${RESET}"
    echo -e "${BOLD_RED}ALERT: Checksum mismatch! The downloaded file has been deleted.${RESET}"
    echo -e "${BOLD_RED}  Expected: $EXPECTED_SHA256${RESET}"
    echo -e "${BOLD_RED}  Got:      $ACTUAL_HASH${RESET}"
    echo -e "${BOLD_RED}Installation aborted. Please contact support@visionsmarts.com${RESET}"
    echo -e "${BOLD_RED}--------------------------------------------------------${RESET}"
    exit 1
fi
echo "SHA-256 verified: $ACTUAL_HASH"

# 5. Extract
echo "Extracting $SDK_NAME..."
mkdir -p barcode/src/main/jniLibs
if tar -xzf "$TMP_FILE" -C barcode/src/main/jniLibs/; then
    echo -e "${BOLD_GREEN}--------------------------------------------------------${RESET}"
    echo -e "${BOLD_GREEN}SUCCESS: $SDK_NAME has been installed.${RESET}"
    echo -e "${BOLD_GREEN}Reminder: This is for EVALUATION ONLY. No production/pilot use.${RESET}"
    echo -e "${BOLD_GREEN}--------------------------------------------------------${RESET}"
else
    echo -e "${BOLD_RED}Error: Extraction failed.${RESET}"
    exit 1
fi
