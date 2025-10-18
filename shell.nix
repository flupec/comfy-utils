let
  nixpkgs = fetchTarball "https://github.com/NixOS/nixpkgs/tarball/nixos-25.05";
  pkgs = import nixpkgs { config = {}; overlays = []; };
in

pkgs.mkShellNoCC {
  packages = [
    pkgs.glibc
    pkgs.zlib
    pkgs.libgcc
    pkgs.gcc
    pkgs.graalvmPackages.graalvm-ce
  ];
}