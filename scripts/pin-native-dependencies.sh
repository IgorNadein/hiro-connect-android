#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$project_dir/libbaresip-android"

# These are the exact native revisions used by the tested Android build.
# libbaresip-android's upstream Makefile follows several moving branches, so a
# release build must pin them before applying the project's patches.
dependencies=(
  "amr 7dba8c32238418ce0b316a852b2224df586ca896"
  "baresip f48c14bc35b55eb4443c627ef77f578a91e2a9ff"
  "bcg729 7743da51c0bc5ff366da97179e1003b8ef056a1c"
  "codec2 06d4c11e699b0351765f10398abb4f663a984f36"
  "g722 2ec8a2dbe0cd1786e0525de94fe0d1d5fb8034ac"
  "g7221 7d35574323585fa5ad2c90daf4f88784c518dec4"
  "ilbc 5533286ac556fc6cc027701242a3e8832f9910df"
  "openssl 03e826d14122b939a1515683e2534d99b16cc71a"
  "opus 82ac57d9f1aaf575800cf17373348e45b7ce6c0d"
  "re fd6c71be2a981f2e961c59c7f1b39b5cbd30494e"
  "sndfile 4d5c8600023c82874eb2c1c108172a8f0e6f8f1f"
  "vo-amrwbenc 884dd247dbeb6b2bd2cd3291c4872de95700291f"
  "zrtpcpp 11c86eda399875368a124c0b018cb86700dc11d0"
)

for dependency in "${dependencies[@]}"; do
  read -r directory revision <<< "$dependency"
  repository="$source_dir/$directory"
  if [[ ! -d "$repository/.git" ]]; then
    echo "Native source is missing: $directory" >&2
    exit 1
  fi
  git -C "$repository" reset --hard --quiet
  git -C "$repository" checkout --detach --quiet "$revision"
  actual="$(git -C "$repository" rev-parse HEAD)"
  [[ "$actual" == "$revision" ]] || {
    echo "Unable to pin $directory to $revision" >&2
    exit 1
  }
  printf '%-14s %s\n' "$directory" "$revision"
done

# Reapply libbaresip-android's own compatibility patches after resetting the
# cloned repositories to their locked revisions.
patch -d "$source_dir/g7221" -p1 < "$source_dir/g7221-patch"
patch -d "$source_dir/re" -p1 < "$source_dir/re-patch"
