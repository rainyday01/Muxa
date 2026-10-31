#!/bin/zsh

set -euo pipefail

readonly resolved="Muxa.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
readonly inventory="THIRD_PARTY_LICENSES.md"
readonly native_root="Vendor/Native"

if [[ ! -f "$resolved" || ! -f "$inventory" ]]; then
  print -u2 "Package lockfile or license inventory is missing"
  exit 1
fi

typeset -a package_identities
package_identities=("${(@f)$(sed -n 's/^[[:space:]]*"identity" : "\([^"]*\)",$/\1/p' "$resolved")}")

if (( ${#package_identities} == 0 )); then
  print -u2 "No package identities found in $resolved"
  exit 1
fi

for identity in "${package_identities[@]}"; do
  if ! grep -Fq "| \`$identity\` |" "$inventory"; then
    print -u2 "Missing license inventory entry for package: $identity"
    exit 1
  fi
done

typeset -A native_versions
native_versions=(
  libssh2 "1.11.1"
  openssl "3.5.7"
)

for identity version in ${(kv)native_versions}; do
  if ! grep -Fq "| \`$identity\` | $version |" "$inventory"; then
    print -u2 "Missing native license inventory entry: $identity $version"
    exit 1
  fi
done

typeset -a native_files
native_files=(
  "$native_root/Provenance.json"
  "$native_root/Licenses/LibSSH2.txt"
  "$native_root/Licenses/OpenSSL.txt"
  "$native_root/LibSSH2.xcframework/Info.plist"
  "$native_root/OpenSSL.xcframework/Info.plist"
)

for native_file in "${native_files[@]}"; do
  if [[ ! -s "$native_file" ]]; then
    print -u2 "Missing native dependency artifact: $native_file"
    exit 1
  fi
done

if [[ "$(plutil -extract dependencies.libssh2.version raw "$native_root/Provenance.json")" != "1.11.1" ]] ||
  [[ "$(plutil -extract dependencies.OpenSSL.version raw "$native_root/Provenance.json")" != "3.5.7" ]]; then
  print -u2 "Native provenance versions do not match the license inventory"
  exit 1
fi

print "License inventory covers ${#package_identities} Swift packages and ${#native_versions} native libraries"
