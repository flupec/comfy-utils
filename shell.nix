# If you have predownloaded nix-pkgs in tar gz format, then call it `nix-shell --arg tar-src file://abs-path-to-nixpkgs.tar.gz`
# Example `nix-shell --arg tar-src file:///home/flupec/Downloads/NixOS-nixpkgs-25.05-9268-gc8aa8cc.tar.gz`
{ tar-src ? "https://github.com/NixOS/nixpkgs/tarball/nixos-25.05" }:

let
  nixpkgs = fetchTarball tar-src;
  pkgs = import nixpkgs { config = {}; overlays = []; };
in
  pkgs.mkShellNoCC {
    packages = [
      pkgs.glibc
      pkgs.zlib
      pkgs.libgcc
      pkgs.gcc
      pkgs.graalvmPackages.graalvm-ce
      pkgs.nushell
    ];

    shellHook = ''
      export JAVA_HOME="${pkgs.graalvmPackages.graalvm-ce}"
    '';
}