# Various utilities
Various helper utilities to help me

## depdiff
Used to calculate diff of maven projects dependency tree. 

### Usage
`  depdiff --ours /abs-path/to/project/v1 --theirs /abs-path/to/project/v2 [--settings /abs-path/to/maven-settings.xml]`

### Assemble
With nix shell.
`nix-shell` and then `sbt --java-home $JAVA_HOME nativeImage`

Without nix shell.
- Set $JAVA_HOME variable
- execute `sbt nativeImage`