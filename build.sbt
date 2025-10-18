val scala3Version = "3.7.3"

lazy val depdiff = project
  .in(file("depdiff"))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "depdiff",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test,
    
    compile / mainClass := Some("main"),
    nativeImageGraalHome := file("/nix/store/vlf7dd304277jw2xp8lfay3lxw58jk1d-graalvm-ce-24.0.1").toPath,
    nativeImageInstalled := true
  )