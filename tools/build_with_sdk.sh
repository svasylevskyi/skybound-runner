#!/usr/bin/env bash
set -euo pipefail

# Dependency-free alternate build for environments without Gradle.
# Example: ANDROID_HOME=/path/to/android-sdk ./tools/build_with_sdk.sh
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
    echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an SDK with platform 35 and build-tools 35.0.0.' >&2
    exit 1
fi
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build_dir="$project_root/build/manual"
tools_dir="$sdk_root/build-tools/35.0.0"
platform_jar="$sdk_root/platforms/android-35/android.jar"
for needed in "$tools_dir/aapt2" "$tools_dir/d8" "$tools_dir/zipalign" "$tools_dir/apksigner" "$platform_jar"; do
    if [[ ! -e "$needed" ]]; then
        echo "Missing SDK component: $needed" >&2
        exit 1
    fi
done
rm -rf "$build_dir"
mkdir -p "$build_dir/classes" "$build_dir/dex"

# The Android Gradle plugin supplies the package name when merging manifests.
# Give the standalone aapt2 invocation an equivalent temporary manifest.
python3 - "$project_root/app/src/main/AndroidManifest.xml" "$build_dir/AndroidManifest.xml" <<'PY'
import sys
from xml.etree import ElementTree as ET
tree = ET.parse(sys.argv[1])
tree.getroot().set('package', 'com.stepan.skyboundrunner')
tree.write(sys.argv[2], encoding='utf-8', xml_declaration=True)
PY

sources=("$project_root"/app/src/main/java/com/stepan/skyboundrunner/*.java)
if command -v javac >/dev/null; then
    javac -source 8 -target 8 -classpath "$platform_jar" \
        -d "$build_dir/classes" "${sources[@]}"
else
    java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 \
        -classpath "$platform_jar" -d "$build_dir/classes" "${sources[@]}"
fi

(cd "$build_dir/classes" && zip -q -r "$build_dir/classes.jar" .)
"$tools_dir/d8" --min-api 23 --lib "$platform_jar" \
    --output "$build_dir/dex" "$build_dir/classes.jar"
"$tools_dir/aapt2" link -o "$build_dir/resources.apk" \
    -I "$platform_jar" --manifest "$build_dir/AndroidManifest.xml" \
    --min-sdk-version 23 --target-sdk-version 35 \
    --version-code 1 --version-name 0.1.0
cp "$build_dir/resources.apk" "$build_dir/unsigned.apk"
(cd "$build_dir/dex" && zip -q -u "$build_dir/unsigned.apk" classes.dex)
"$tools_dir/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"

key_store="$build_dir/debug.keystore"
if [[ ! -e "$key_store" ]]; then
    keytool -genkeypair -noprompt -keystore "$key_store" \
        -alias androiddebugkey -storepass android -keypass android \
        -dname 'CN=Android Debug,O=Android,C=US' \
        -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1
fi
output="$project_root/SkyboundRunner-debug.apk"
"$tools_dir/apksigner" sign --ks "$key_store" --ks-pass pass:android \
    --key-pass pass:android --out "$output" "$build_dir/aligned.apk"
"$tools_dir/apksigner" verify --verbose "$output"
echo "Built $output"
