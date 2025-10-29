{ self }:

final: prev: {
  espresso = final.callPackage ./pkgs/espresso.nix { };
  fetchMillDeps = final.callPackage ./pkgs/mill-builder.nix { };
  mill = let jre = final.jdk21; in
    (prev.mill.override { inherit jre; }).overrideAttrs (_: {
      passthru = { inherit jre; };
    });
  submodules = final.callPackage ./pkgs/submodules.nix { };
  diplomacy = final.callPackage ./pkgs/diplomacy.nix { };
}
