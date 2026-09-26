#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
submodule_dir="$project_dir/libbaresip-android"
patch_file="$project_dir/patches/libbaresip-android-hi-ro.patch"

if git -C "$submodule_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
  echo "HI-RO native build patch is already applied."
  exit 0
fi

git -C "$submodule_dir" apply --check "$patch_file"
git -C "$submodule_dir" apply "$patch_file"
echo "Applied HI-RO native build patch."
