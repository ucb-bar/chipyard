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

# Allow user to override MAKE
[ -n "${MAKE:+x}" ] || MAKE=$(command -v gnumake || command -v gmake || command -v make)
readonly MAKE


module_prepare() ( # <submodule> [ignored-submodule..]
    set -e
    name=$1
    shift

    dir="${SRCDIR}/${name}"
    echo "=>  Starting ${name} build"
    echo "==>  Initializing ${name} submodule"
    if [ $# -gt 0 ] ; then
	(set -x; git submodule update --init "${dir}")
        while [ -n "$1" ] ; do
	    (set -x; git -C "${dir}" config submodule."${1}".update none)
            shift
        done
    fi
    (set -x; git submodule update --init --recursive "${dir}")
)

module_run() ( # <submodule> <command..>
    set -e
    echo "=> cd ${SRCDIR}/${1}"
    cd "${SRCDIR}/${1}"
    shift
    (set -x; "$@")
)

module_make() ( # <submodule> <target..>
    set -e -o pipefail
    build_dir="${SRCDIR}/${1}/build"
    shift
    (set -x; "${MAKE}" -C "$build_dir" "$@") | tee "build-${1:-make}.log"
    if [ -n "$CLEANAFTERINSTALL" ] ; then
        (set -x; "${MAKE}" -C "$build_dir" clean)  # get rid of intermediate files
    fi
)

module_build() ( # <submodule> [configure-arg..]
    set -e -o pipefail
    name=$1
    shift

    echo "==>  cd ${SRCDIR}/${name}"
    cd "${SRCDIR}/${name}"

    if [ -e build ] ; then
        echo "==>  Removing existing ${name}/build directory"
	(set -x; rm -rf build)
    fi
    if ! [ -e configure ] ; then
        echo "==>  Updating autoconf files for ${name}"
        find . -iname configure.ac -type f -print0 |
        while read -r -d '' file ; do
	    (set -x; mkdir -p -- "${file%/*}/m4")
        done
        (set -x; autoreconf -i)
    fi

    (set -x; mkdir -p build)
    {
        export PATH="${RISCV:+${RISCV}/bin:}${PATH}"
        echo "==>  Configuring ${name}"
        (set -x; cd build && ../configure "$@")
        echo "==>  Building ${name}"
        (set -x; "${MAKE}" -C build)
        echo "==>  Installing ${name}"
        (set -x; "${MAKE}" -C build install)
        if [ -n "$CLEANAFTERINSTALL" ] ; then
            (set -x; "${MAKE}" -C build clean)  # get rid of intermediate files
        fi
    } 2>&1 | tee build.log
)

module_all() { # <submodule> [configure-arg..]
    module_prepare "$1"
    module_build "$@"
}
