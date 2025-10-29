# Diplomacy

Diplomacy is a parameter negotiation framework for Chisel. More documentation will be added in the future.

## Development Guide
### Setup Nix
We use nix flake as our primary build system. If you have not installed nix, install it following the [guide](https://nixos.org/manual/nix/stable/installation/installing-binary.html), and enable flake following the [wiki](https://nixos.wiki/wiki/Flakes#Enable_flakes). Or you can try the [installer](https://github.com/DeterminateSystems/nix-installer) provided by Determinate Systems, which enables flake by default.

### Setup Dependencies and IDE
Run command below will help you setup dependencies(including ivy and source dependencies):
```shell
nix develop .#diplomacy -c mill mill.bsp.BSP/install
```