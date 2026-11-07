# ustt-env.sh: set up a shell for boom-ustt development.
#
# Usage (must be SOURCED, not executed):
#     source /path/to/boom-ustt/ustt-env.sh            # conda + Chipyard env
#     source /path/to/boom-ustt/ustt-env.sh --vivado   # ...plus Xilinx Vivado
#
# Machine-specific settings (conda location, cache dirs, Vivado path) go in
# ustt-env.local.sh next to this file. It is git-ignored; copy
# ustt-env.local.sh.example to get started.

# --- refuse to run as a subprocess (env changes would be lost) -------------
if [ -n "${BASH_VERSION:-}" ] && [ "${BASH_SOURCE[0]}" = "$0" ]; then
    echo "ustt-env.sh must be sourced:  source $0" >&2
    exit 1
fi

_ustt_main() {
    local want_vivado=0 arg
    for arg in "$@"; do
        case "$arg" in
            --vivado) want_vivado=1 ;;
            -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}"; return 0 ;;
            *) echo "ustt-env: unknown option '$arg'" >&2; return 1 ;;
        esac
    done

    local repo
    repo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || return 1

    # 1. Per-machine overrides (CONDA_ROOT, caches, VIVADO_SETTINGS, ...)
    [ -f "$repo/ustt-env.local.sh" ] && source "$repo/ustt-env.local.sh"

    # 2. Find conda: $CONDA_ROOT, then conda already on PATH, then common spots
    if ! type conda >/dev/null 2>&1 || [ -n "${CONDA_ROOT:-}" ]; then
        local c
        for c in "${CONDA_ROOT:-}" "$HOME/miniforge3" "/misc/scratch/$USER/miniforge3" \
                 "$HOME/miniconda3" "$HOME/anaconda3"; do
            if [ -n "$c" ] && [ -f "$c/etc/profile.d/conda.sh" ]; then
                source "$c/etc/profile.d/conda.sh"
                break
            fi
        done
    fi
    if ! type conda >/dev/null 2>&1; then
        echo "ustt-env: conda not found. Set CONDA_ROOT in $repo/ustt-env.local.sh" >&2
        return 1
    fi

    # 3. Chipyard's generated env (conda activate + PATH/RISCV setup)
    if ! grep -q "conda activate" "$repo/env.sh" 2>/dev/null; then
        echo "ustt-env: $repo/env.sh is not set up yet; run ./build-setup.sh riscv-tools first" >&2
        return 1
    fi
    source "$repo/env.sh" || return 1

    # 4. Optional Vivado
    if [ "$want_vivado" = 1 ]; then
        local vs="${VIVADO_SETTINGS:-/usr/local/packages/xilinx_2023.2/Vivado/2023.2/settings64.sh}"
        if [ -f "$vs" ]; then
            source "$vs"
        else
            echo "ustt-env: Vivado settings not found at $vs (set VIVADO_SETTINGS)" >&2
            return 1
        fi
    fi

    export USTT_ROOT="$repo"
    echo "boom-ustt env ready: $repo"
    echo "  conda env: ${CONDA_PREFIX:-?}"
    [ "$want_vivado" = 1 ] && echo "  vivado:    $(command -v vivado)"
    return 0
}

_ustt_main "$@"
_ustt_rc=$?
unset -f _ustt_main
return $_ustt_rc
