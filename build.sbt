ThisBuild / organization := "io.github.yuri4n"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / licenses := List(License.Apache2)

lazy val check = taskKey[Unit]("Run the library tests and compile the packaged demo.")

lazy val strictSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Werror",
    "-Wvalue-discard",
    "-Wnonunit-statement",
    "-Wunused:all",
    "-Yexplicit-nulls",
    "-Wconf:msg=@nowarn annotation does not suppress any warnings:s",
  ),
)

lazy val root = project.in(file(".")).settings(strictSettings).settings(
  name := "pini4s",
  description := "Small Pinia-inspired shared state stores for Scala and Kyo.",
  libraryDependencies ++= Seq(
    "io.getkyo" %% "kyo-core" % "1.0.0-RC7",
    "io.getkyo" %% "kyo-test-runner" % "1.0.0-RC7" % Test,
  ),
  Test / testFrameworks += new TestFramework("kyo.test.runner.SbtFramework"),
  Test / fork := true,
  Test / javaOptions ++= Seq(
    "-Xmx1g",
    "-Xss16m",
    "--sun-misc-unsafe-memory-access=allow",
  ),
  check := {
    (Test / test).value
    val _ = (LocalProject("demo") / Compile / compile).value
  },
)

lazy val demo = project.in(file("demo"))
  .dependsOn(root)
  .enablePlugins(JavaAppPackaging)
  .settings(strictSettings)
  .settings(
    name := "pini4s-demo",
    description := "Console demonstration of shared and isolated pini4s state.",
    maintainer := "yuri4n",
    publish / skip := true,
    Compile / mainClass := Some("pini4s.demo.Pini4sDemo"),
    executableScriptName := "pini4s-demo",
    Universal / javaOptions ++= Seq(
      "-J-Xss16m",
      "-J--sun-misc-unsafe-memory-access=allow",
    ),
  )
