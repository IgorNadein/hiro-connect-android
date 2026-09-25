#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
baresip_dir="$project_dir/libbaresip-android/baresip"
patch_file="$project_dir/patches/baresip-opensles-voice-path.patch"

if [[ ! -d "$baresip_dir" ]]; then
  echo "baresip sources are missing. Run: make -C libbaresip-android download-sources" >&2
  exit 1
fi

if git -C "$baresip_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
  echo "HI-RO OpenSL ES voice patch is already applied."
  exit 0
fi

git -C "$baresip_dir" apply --check "$patch_file"
git -C "$baresip_dir" apply "$patch_file"
echo "Applied HI-RO OpenSL ES voice patch."
