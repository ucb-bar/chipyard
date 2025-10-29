{ lib
, stdenv
, fetchMillDeps
, makeWrapper
, jre

  # chisel deps
, mill
, git
, espresso
, strip-nondeterminism

, submodules
}:

let
  self = stdenv.mkDerivation rec {
    name = "diplomacy";

    src = (with lib.fileset; toSource {
      root = ./../..;
      fileset = unions [
        ./../../build.sc
        ./../../common.sc
        ./../../diplomacy
      ];
    }).outPath;

    passthru.millDeps = fetchMillDeps {
      inherit name;
      src = (with lib.fileset; toSource {
        root = ./../..;
        fileset = unions [
          ./../../build.sc
          ./../../common.sc
        ];
      }).outPath;
      millDepsHash = "sha256-5CagEixOPT5AlGInTqCBhts2hko1FBqKFkMRYYpma2o=";
      nativeBuildInputs = [ submodules.setupHook ];
    };

    passthru.editable = self.overrideAttrs (_: {
      shellHook = ''
        setupSubmodulesEditable
        mill mill.bsp.BSP/install 0
      '';
    });

    shellHook = ''
      setupSubmodules
    '';

    nativeBuildInputs = [
      mill
      git

      strip-nondeterminism

      makeWrapper
      passthru.millDeps.setupHook

      submodules.setupHook
    ];

    outputs = [ "out" ];

    buildPhase = ''
      mill -i '__.assembly'
    '';

    installPhase = ''
      mkdir -p $out/share/java

      strip-nondeterminism out/diplomacy/source/assembly.dest/out.jar

      mv out/diplomacy/source/assembly.dest/out.jar $out/share/java/diplomacy.jar

      mkdir -p $out/bin
      makeWrapper ${jre}/bin/java $out/bin/diplomacy --add-flags "-jar $out/share/java/diplomacy.jar"
    '';
  };
in
self
