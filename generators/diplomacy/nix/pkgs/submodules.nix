{ pkgs, makeSetupHook, writeText, lib }:

let
  submodules = lib.filterAttrs (_: v: v ? src) (pkgs.callPackage ./_sources/generated.nix { });
  makeRemote = module: "git@github.com:${module.src.owner}/${module.src.repo}.git";
in
{
  setupHook = makeSetupHook { name = "submodules-setup.sh"; } (writeText "submodules-setup.sh" (''
    _setupOneSubmodule() {
      src="$1"
      name="$2"

      echo "[nix-shell] linking '$src' to 'dependencies/$name'"
      ln -sfT "$src" "dependencies/$name"
    }

    _setupOneSubmoduleEditable() {
      name="$1"; shift
      remote="$1"; shift
      rev="$1"; shift
      depDir="dependencies/$name"

      if [ -d "$depDir" -a ! -L "$depDir" ]; then
        echo "[nix-shell] ignored existing submodule directory '$depDir', remove them if you want a update"
      else 
        if [ -L "$depDir" ]; then
          echo "[nix-shell] replacing symlink '$depDir' with full git worktree"
          rm "$depDir"
        else
          echo "[nix-shell] fetching submodule $name"
        fi

        git clone $remote $depDir
        git -C $depDir -c advice.detachedHead=false checkout $rev
      fi
    }

    setupSubmodules() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmodule '${v.src}' '${k}'") submodules) + ''
    }

    # for use of shellHook
    setupSubmodulesEditable() {
      mkdir -p dependencies
  '' + lib.concatLines (lib.mapAttrsToList (k: v: "_setupOneSubmoduleEditable '${k}' '${makeRemote v}' '${v.src.rev}'") submodules) + ''
    }
    prePatchHooks+=(setupSubmodules)
  ''));
  sources = submodules;
}
