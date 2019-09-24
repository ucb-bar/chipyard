# Derived from
# https://github.com/riscv/riscv-tools/blob/master/build.common

[ -n "${SRCDIR}" ] || exit 1

# Scale number of parallel make jobs by hardware thread count
ncpu="${NPROC:-$(getconf _NPROCESSORS_ONLN || # GNU
    getconf NPROCESSORS_ONLN || # *BSD, Solaris
    nproc --all || # Linux
    sysctl -n hw.ncpu || # *BSD, OS X
    :)}" 2>/dev/null
case ${ncpu} in
''|*[!0-9]*) ;; # Ignore non-integer values
*) export MAKEFLAGS="-j ${ncpu} ${MAKEFLAGS}" ;;
esac

MAKE=$(command -v gmake || command -v make)
readonly MAKE


module_prepare() ( # <submodule> [ignored-submodule..]
    set -e
    name=$1
    shift

    dir="${SRCDIR}/${name}"
    echo "=>  Starting ${name} build"
    echo "==>  Initializing ${name} submodule"
    if [ $# -gt 0 ] ; then
        git submodule update --init "${dir}"
        while [ -n "$1" ] ; do
            git -C "${dir}" config submodule."${1}".update none
            shift
        done
    fi
    git submodule update --init --recursive "${dir}"
)

module_run() ( # <submodule> <command..>
    set -e
    cd "${SRCDIR}/${1}"
    shift
    "$@"
)

module_make() ( # <submodule> <target..>
    set -e -o pipefail
    cd "${SRCDIR}/${1}/build"
    shift
    "${MAKE}" "$@" | tee "build-${1:-make}.log"
)

module_build() ( # <submodule> [configure-arg..]
    set -e -o pipefail
    name=$1
    shift

    cd "${SRCDIR}/${name}"

    if [ -e build ] ; then
        echo "==>  Removing existing ${name}/build directory"
        rm -rf build
    fi
    if ! [ -e configure ] ; then
        echo "==>  Updating autoconf files for ${name}"
        find . -iname configure.ac -type f -print0 |
        while read -r -d '' file ; do
            mkdir -p -- "${file%/*}/m4"
        done
        autoreconf -i
    fi

    mkdir -p build
    cd build
    {
        export PATH="${RISCV:+${RISCV}/bin:}${PATH}"
        echo "==>  Configuring ${name}"
        ../configure "$@"
        echo "==>  Building ${name}"
        "${MAKE}"
        echo "==>  Installing ${name}"
        "${MAKE}" install
    } 2>&1 | tee build.log
)

module_all() { # <submodule> [configure-arg..]
    module_prepare "$1"
    module_build "$@"
}
