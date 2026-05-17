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
    nativeImageInstalled := true
  )
