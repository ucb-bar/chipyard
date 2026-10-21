# Source this file from env.sh; supports Bash and Zsh without starting a shell.
if [ -z "${BASH_VERSION-}${ZSH_VERSION-}" ]; then
    echo 'Chipyard activation requires Bash or Zsh.' >&2
    return 1
fi

if [ -n "${BASH_VERSION-}" ]; then
    _cy_pixi_script=${BASH_SOURCE[0]}
else
    _cy_pixi_script=${(%):-%x}
fi
# Capture the real executable and any pre-existing function before installing ours.
if [ -z "${_CY_PIXI_ROOT-}" ]; then
    _CY_PIXI_PREVIOUS_FUNCTION=$(typeset -f pixi 2>/dev/null) || _CY_PIXI_PREVIOUS_FUNCTION=
fi
_CY_PIXI_ROOT=$(cd -- "$(dirname -- "$_cy_pixi_script")/.." && pwd)
unset _cy_pixi_script

chipyard_deactivate() {
    if [ -n "${_CY_PIXI_RESTORE-}" ]; then
        eval "$_CY_PIXI_RESTORE"
        unset _CY_PIXI_RESTORE
        unset -f pixi
        if [ -n "${_CY_PIXI_PREVIOUS_FUNCTION-}" ]; then
            eval "$_CY_PIXI_PREVIOUS_FUNCTION"
        fi
        hash -r 2>/dev/null || true
    fi
}

chipyard_activate() {
    local _cy_env=${1:-${_CY_PIXI_DEFAULT_ENV:-default}} _cy_code
    command -v pixi >/dev/null 2>&1 || {
        echo 'Pixi >=0.82.1 is required; install it or run pixi self-update.' >&2
        return 1
    }
    command -v python3 >/dev/null 2>&1 || {
        echo 'python3 is required to activate Chipyard.' >&2
        return 1
    }
    # Generate against the original environment in a subprocess. Failed switches
    # leave the caller active; successful switches never stack paths.
    _cy_code=$(
        if [ -n "${_CY_PIXI_RESTORE-}" ]; then eval "$_CY_PIXI_RESTORE"; fi
        _cy_json=$(command pixi shell-hook --json --as-is --no-completions \
            --manifest-path "$_CY_PIXI_ROOT/pixi-reqs/pixi.toml" \
            --environment "$_cy_env") || exit $?
        printf '%s\n' "$_cy_json" | python3 "$_CY_PIXI_ROOT/scripts/pixi-activation.py" "$_CY_PIXI_ROOT"
    ) || return $?
    chipyard_deactivate
    eval "$_cy_code" || return $?
    pixi() { _chipyard_pixi "$@"; }
    hash -r 2>/dev/null || true
}

_chipyard_pixi() {
    local _cy_cmd=${1-} _cy_arg _cy_manifest= _cy_no_install=false _cy_explicit_env=false
    local _cy_env=${PIXI_ENVIRONMENT_NAME:-default}
    local -a _cy_options
    case $_cy_cmd in
        activate)
            chipyard_activate "${2:-${_CY_PIXI_DEFAULT_ENV:-default}}"
            return $? ;;
        deactivate)
            chipyard_deactivate
            return $? ;;
    esac
    # Respect explicit workspaces and no-install flags. Ordinary additions go to
    # the shared default feature, inherited by default/full/lean and FireSim.
    for _cy_arg in "$@"; do
        case $_cy_arg in
            -m|--manifest-path|--manifest-path=*|-m?*|-w|--workspace|--workspace=*) _cy_manifest=yes ;;
            -s|--script|--script=*|-s?*) _cy_manifest=yes ;;
            -e|--environment|--environment=*|-e?*|-a|--all) _cy_explicit_env=true ;;
            --no-install|--no-lockfile-update|--help|-h) _cy_no_install=true ;;
        esac
    done
    if [ -n "$_cy_manifest" ]; then
        command pixi "$@"
        return $?
    fi
    case $_cy_cmd in
        add|a|remove|rm|update|upgrade)
            _cy_options=()
            if [ "$_cy_no_install" = false ]; then _cy_options=(--no-install); fi
            command pixi "$@" --manifest-path "$_CY_PIXI_ROOT/pixi-reqs/pixi.toml" "${_cy_options[@]}" || return $?
            if [ "$_cy_no_install" = false ]; then
                command pixi install --locked --manifest-path "$_CY_PIXI_ROOT/pixi-reqs/pixi.toml" \
                    --environment "$_cy_env" || return $?
                chipyard_activate "$_cy_env" || return $?
            fi ;;
        install|i)
            _cy_options=()
            if [ "$_cy_explicit_env" = false ]; then _cy_options=(--environment "$_cy_env"); fi
            command pixi "$@" --manifest-path "$_CY_PIXI_ROOT/pixi-reqs/pixi.toml" \
                "${_cy_options[@]}" || return $?
            if [ "$_cy_no_install" = false ]; then chipyard_activate "$_cy_env"; fi ;;
        *) command pixi "$@" ;;
    esac
}

_CY_PIXI_DEFAULT_ENV=${1:-default}
chipyard_activate "$_CY_PIXI_DEFAULT_ENV"
