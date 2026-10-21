#!/usr/bin/env bash
# Bootstrap the version required by pixi-reqs/pixi.toml (also used in CI).
set -euo pipefail
PIXI_VERSION=0.82.1
PIXI_INSTALL_PREFIX=${PIXI_HOME:-$HOME/.pixi}
if [ "${1:-}" = --prefix ] && [ $# = 2 ]; then
    PIXI_INSTALL_PREFIX=$2
elif [ $# != 0 ]; then
    echo "Usage: $0 [--prefix PATH]" >&2
    exit 2
fi
case $(uname -sm) in
    'Linux x86_64') target=x86_64-unknown-linux-musl ;;
    'Linux aarch64'|'Linux arm64') target=aarch64-unknown-linux-musl ;;
    *) echo 'Chipyard requires Linux x86_64 or aarch64.' >&2; exit 1 ;;
esac
if [ -x "$PIXI_INSTALL_PREFIX/bin/pixi" ] && [ "$("$PIXI_INSTALL_PREFIX/bin/pixi" --version)" = "pixi $PIXI_VERSION" ]; then
    exit 0
fi
install_dir=$(mktemp -d)
trap 'rm -rf "$install_dir"' EXIT
url="https://github.com/prefix-dev/pixi/releases/download/v$PIXI_VERSION/pixi-$target.tar.gz"
if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "$url" -o "$install_dir/pixi.tar.gz"
else
    wget -O "$install_dir/pixi.tar.gz" "$url"
fi
tar -xzf "$install_dir/pixi.tar.gz" -C "$install_dir"
mkdir -p "$PIXI_INSTALL_PREFIX/bin"
install -m 755 "$install_dir/pixi" "$PIXI_INSTALL_PREFIX/bin/pixi"
echo "Installed Pixi $PIXI_VERSION to $PIXI_INSTALL_PREFIX/bin; add that directory to PATH."
