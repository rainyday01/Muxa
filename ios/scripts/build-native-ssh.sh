#!/bin/zsh

set -euo pipefail

readonly SCRIPT_DIR="${0:A:h}"
readonly IOS_ROOT="${SCRIPT_DIR:h}"
readonly WORK_ROOT="$IOS_ROOT/.build/NativeBuild"
readonly DOWNLOAD_ROOT="$IOS_ROOT/.build/NativeDownloads"
readonly OUTPUT_ROOT="$IOS_ROOT/Vendor/Native"
readonly DEPLOYMENT_TARGET="17.0"

readonly LIBSSH2_VERSION="1.11.1"
readonly LIBSSH2_ARCHIVE="libssh2-$LIBSSH2_VERSION.tar.gz"
readonly LIBSSH2_URL="https://www.libssh2.org/download/$LIBSSH2_ARCHIVE"
readonly LIBSSH2_SHA256="d9ec76cbe34db98eec3539fe2c899d26b0c837cb3eb466a56b0f109cabf658f7"

readonly OPENSSL_VERSION="3.5.7"
readonly OPENSSL_ARCHIVE="openssl-$OPENSSL_VERSION.tar.gz"
readonly OPENSSL_URL="https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/$OPENSSL_ARCHIVE"
readonly OPENSSL_SHA256="a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8"

function verify_archive() {
  local archive="$1"
  local expected="$2"
  local actual
  actual="$(shasum -a 256 "$archive" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    print -u2 "Checksum mismatch for ${archive:t}: expected $expected, got $actual"
    exit 1
  fi
}

function download_archive() {
  local url="$1"
  local archive="$2"
  local checksum="$3"
  if [[ ! -f "$archive" ]]; then
    curl -fL --retry 3 --output "$archive" "$url"
  fi
  verify_archive "$archive" "$checksum"
}

function prepare_source() {
  local archive="$1"
  local destination="$2"
  mkdir -p "$destination"
  tar -xzf "$archive" -C "$destination" --strip-components=1
}

function build_openssl() {
  local platform="$1"
  local configure_target="$2"
  local minimum_flag="$3"
  local source="$WORK_ROOT/src/openssl-$platform"
  local prefix="$WORK_ROOT/install/openssl-$platform"

  prepare_source "$DOWNLOAD_ROOT/$OPENSSL_ARCHIVE" "$source"
  (
    cd "$source"
    ./Configure "$configure_target" \
      no-shared no-tests no-apps no-docs no-module \
      --prefix="$prefix" \
      --openssldir="$prefix/ssl" \
      "$minimum_flag"
    make -j"$(sysctl -n hw.logicalcpu)"
    make install_sw
  )

  xcrun libtool -static \
    -o "$prefix/lib/libOpenSSL.a" \
    "$prefix/lib/libssl.a" \
    "$prefix/lib/libcrypto.a"

  mkdir -p "$prefix/include/module"
  cp -R "$prefix/include/openssl" "$prefix/include/module/"
  cp "$IOS_ROOT/scripts/native-modules/OpenSSL.modulemap" "$prefix/include/module/module.modulemap"
}

function build_libssh2() {
  local platform="$1"
  local sdk="$2"
  local source="$WORK_ROOT/src/libssh2-$platform"
  local build="$WORK_ROOT/cmake/libssh2-$platform"
  local prefix="$WORK_ROOT/install/libssh2-$platform"
  local openssl="$WORK_ROOT/install/openssl-$platform"

  prepare_source "$DOWNLOAD_ROOT/$LIBSSH2_ARCHIVE" "$source"
  cmake -S "$source" -B "$build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$sdk" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY \
    -DCMAKE_INSTALL_PREFIX="$prefix" \
    -DBUILD_STATIC_LIBS=ON \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_EXAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    -DCRYPTO_BACKEND=OpenSSL \
    -DOPENSSL_ROOT_DIR="$openssl" \
    -DOPENSSL_INCLUDE_DIR="$openssl/include" \
    -DOPENSSL_SSL_LIBRARY="$openssl/lib/libssl.a" \
    -DOPENSSL_CRYPTO_LIBRARY="$openssl/lib/libcrypto.a" \
    -DOPENSSL_USE_STATIC_LIBS=TRUE
  cmake --build "$build" --parallel
  cmake --install "$build"

  mkdir -p "$prefix/include/module"
  cp "$prefix/include/libssh2.h" "$prefix/include/module/"
  cp "$prefix/include/libssh2_publickey.h" "$prefix/include/module/"
  cp "$prefix/include/libssh2_sftp.h" "$prefix/include/module/"
  cp "$IOS_ROOT/scripts/native-modules/CLibSSH2.modulemap" "$prefix/include/module/module.modulemap"
}

function create_xcframeworks() {
  mkdir -p "$OUTPUT_ROOT"
  xcodebuild -create-xcframework \
    -library "$WORK_ROOT/install/openssl-device/lib/libOpenSSL.a" \
    -library "$WORK_ROOT/install/openssl-simulator/lib/libOpenSSL.a" \
    -output "$OUTPUT_ROOT/OpenSSL.xcframework"

  xcodebuild -create-xcframework \
    -library "$WORK_ROOT/install/libssh2-device/lib/libssh2.a" \
    -headers "$WORK_ROOT/install/libssh2-device/include/module" \
    -library "$WORK_ROOT/install/libssh2-simulator/lib/libssh2.a" \
    -headers "$WORK_ROOT/install/libssh2-simulator/include/module" \
    -output "$OUTPUT_ROOT/LibSSH2.xcframework"
}

function write_provenance() {
  local openssl_device openssl_simulator libssh2_device libssh2_simulator
  openssl_device="$(shasum -a 256 "$WORK_ROOT/install/openssl-device/lib/libOpenSSL.a" | awk '{print $1}')"
  openssl_simulator="$(shasum -a 256 "$WORK_ROOT/install/openssl-simulator/lib/libOpenSSL.a" | awk '{print $1}')"
  libssh2_device="$(shasum -a 256 "$WORK_ROOT/install/libssh2-device/lib/libssh2.a" | awk '{print $1}')"
  libssh2_simulator="$(shasum -a 256 "$WORK_ROOT/install/libssh2-simulator/lib/libssh2.a" | awk '{print $1}')"

  sed \
    -e "s/@OPENSSL_VERSION@/$OPENSSL_VERSION/g" \
    -e "s/@OPENSSL_SOURCE_SHA256@/$OPENSSL_SHA256/g" \
    -e "s/@OPENSSL_DEVICE_SHA256@/$openssl_device/g" \
    -e "s/@OPENSSL_SIMULATOR_SHA256@/$openssl_simulator/g" \
    -e "s/@LIBSSH2_VERSION@/$LIBSSH2_VERSION/g" \
    -e "s/@LIBSSH2_SOURCE_SHA256@/$LIBSSH2_SHA256/g" \
    -e "s/@LIBSSH2_DEVICE_SHA256@/$libssh2_device/g" \
    -e "s/@LIBSSH2_SIMULATOR_SHA256@/$libssh2_simulator/g" \
    "$IOS_ROOT/scripts/native-provenance.json.in" > "$OUTPUT_ROOT/Provenance.json"

  mkdir -p "$OUTPUT_ROOT/Licenses"
  cp "$WORK_ROOT/src/openssl-device/LICENSE.txt" "$OUTPUT_ROOT/Licenses/OpenSSL.txt"
  cp "$WORK_ROOT/src/libssh2-device/COPYING" "$OUTPUT_ROOT/Licenses/LibSSH2.txt"
}

for tool in cmake curl make perl shasum xcodebuild xcrun; do
  command -v "$tool" >/dev/null
done

mkdir -p "$DOWNLOAD_ROOT"
download_archive "$LIBSSH2_URL" "$DOWNLOAD_ROOT/$LIBSSH2_ARCHIVE" "$LIBSSH2_SHA256"
download_archive "$OPENSSL_URL" "$DOWNLOAD_ROOT/$OPENSSL_ARCHIVE" "$OPENSSL_SHA256"

if [[ -d "$WORK_ROOT" ]]; then
  find "$WORK_ROOT" -mindepth 1 -delete
fi
if [[ -d "$OUTPUT_ROOT" ]]; then
  find "$OUTPUT_ROOT" -mindepth 1 -delete
fi

build_openssl device ios64-xcrun "-miphoneos-version-min=$DEPLOYMENT_TARGET"
build_openssl simulator iossimulator-arm64-xcrun "-mios-simulator-version-min=$DEPLOYMENT_TARGET"
build_libssh2 device iphoneos
build_libssh2 simulator iphonesimulator
create_xcframeworks
write_provenance

print "Built libssh2 $LIBSSH2_VERSION and OpenSSL $OPENSSL_VERSION XCFrameworks in $OUTPUT_ROOT"
