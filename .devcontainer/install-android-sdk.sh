#!/usr/bin/env bash
set -e

ANDROID_SDK_ROOT="/opt/android-sdk"
CMDLINE_TOOLS_VERSION="11076708"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

echo ">>> Installing dependencies..."
sudo apt-get update -qq
sudo apt-get install -y -qq unzip wget

echo ">>> Downloading Android command-line tools..."
sudo mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
sudo chown -R "$(whoami)" "$ANDROID_SDK_ROOT"

cd /tmp
curl -sSL "$CMDLINE_TOOLS_URL" -o cmdline-tools.zip
unzip -q cmdline-tools.zip
mv cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"
rm cmdline-tools.zip

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"

echo ">>> Accepting licenses and installing SDK packages..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "export ANDROID_HOME=$ANDROID_SDK_ROOT" >> ~/.bashrc
echo "export PATH=\$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools" >> ~/.bashrc

echo ">>> Android SDK installed successfully."
