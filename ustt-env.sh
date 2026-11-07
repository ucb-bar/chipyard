# ustt-env.sh: environment setup for boom-ustt (conda + Chipyard, optional Xilinx).
#
# Most people should use the ./ustt wrapper instead, which applies this env to
# a single command without touching your shell:
#     ./ustt make -C sims/verilator CONFIG=SmallBoomV3Config
#     ./ustt --vivado make -C fpga SUB_PROJECT=vcu118 bitstream
#
# To set up your current interactive shell instead, SOURCE it:
#     source ustt-env.sh [--vivado]
#
# Machine-specific settings (conda location, cache dirs, Xilinx version) go in
# ustt-env.local.sh next to this file (git-ignored; see ustt-env.local.sh.example).

if [ -n "${BASH_VERSION:-}" ] && [ "${BASH_SOURCE[0]}" = "$0" ]; then
    echo "ustt-env.sh must be sourced (source $0), or use ./ustt <command>" >&2
    exit 1
fi

_ustt_main() {
    local want_vivado=0 quiet=0 arg
    for arg in "$@"; do
        case "$arg" in
            --vivado) want_vivado=1 ;;
            --quiet)  quiet=1 ;;
            -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; return 0 ;;
            *) echo "ustt-env: unknown option '$arg'" >&2; return 1 ;;
        esac
    done

    local repo
    repo="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || return 1

    # 1. Per-machine overrides (CONDA_ROOT, caches, XILINX_MODULE, VIVADO_SETTINGS)
    [ -f "$repo/ustt-env.local.sh" ] && source "$repo/ustt-env.local.sh"

    # 2. Xilinx FIRST, so Chipyard's RISC-V toolchain (step 4) lands ahead of the
    #    riscv64-unknown-elf that the Vitis install puts on PATH.
    #    UT ECE machines (see ounuvar/coralnpu#6): the xilinx/2023 module sets the
    #    license server; settings64.sh sets the full Vivado env. Use Vivado 2023.2
    #    only, because the ECE license is capped at version 2023.10.
    if [ "$want_vivado" = 1 ]; then
        local xmod="${XILINX_MODULE-xilinx/2023}"
        local vs="${VIVADO_SETTINGS:-/usr/local/packages/xilinx_2023.2/Vivado/2023.2/settings64.sh}"
        if [ -n "$xmod" ]; then
            if ! type module >/dev/null 2>&1 && [ -f /usr/share/Modules/init/bash ]; then
                source /usr/share/Modules/init/bash 2>/dev/null
            fi
            type module >/dev/null 2>&1 && module load "$xmod" 2>/dev/null
        fi
        # Fallback if the module system is unavailable or failed
        case "${XILINXD_LICENSE_FILE:-}" in
            *harrison*) ;;
            *) export XILINXD_LICENSE_FILE="${USTT_XILINX_LICENSE:-2100@harrison.ece.utexas.edu}${XILINXD_LICENSE_FILE:+:$XILINXD_LICENSE_FILE}" ;;
        esac
        if [ -f "$vs" ]; then
            source "$vs"
        else
            echo "ustt-env: Vivado settings not found at $vs (set VIVADO_SETTINGS)" >&2
            return 1
        fi
    fi

    # 3. Find conda: $CONDA_ROOT, then conda already on PATH, then common spots
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

    # 4. Chipyard's generated env (conda activate + RISCV, PATH)
    if ! grep -q "conda activate" "$repo/env.sh" 2>/dev/null; then
        echo "ustt-env: $repo/env.sh is not set up yet; run ./build-setup.sh riscv-tools first" >&2
        return 1
    fi
    source "$repo/env.sh" || return 1

    # 5. Optional local TeX Live for building project-docs/*-report (if installed)
    local tl
    for tl in "${TEXLIVE_ROOT:-}" "/misc/scratch/$USER/texlive/2026"; do
        if [ -n "$tl" ] && [ -x "$tl/bin/x86_64-linux/pdflatex" ]; then
            export PATH="$tl/bin/x86_64-linux:$PATH"
            break
        fi
    done

    export USTT_ROOT="$repo"
    if [ "$quiet" = 0 ]; then
        echo "boom-ustt env ready: $repo"
        echo "  conda env: ${CONDA_PREFIX:-?}"
        [ "$want_vivado" = 1 ] && echo "  vivado:    $(command -v vivado)"
    fi
    return 0
}

_ustt_main "$@"
_ustt_rc=$?
unset -f _ustt_main
return $_ustt_rc
