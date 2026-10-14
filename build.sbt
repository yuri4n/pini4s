ThisBuild / organization := "io.github.yuri4n"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / licenses := List(License.Apache2)

lazy val root = project.in(file(".")).settings(
  name := "pini4s",
  description := "Small Pinia-inspired shared state stores for Scala and Kyo.",
  libraryDependencies ++= Seq(
    "io.getkyo" %% "kyo-core" % "1.0.0-RC7",
    "io.getkyo" %% "kyo-test-runner" % "1.0.0-RC7" % Test,
  ),
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
  Test / testFrameworks += new TestFramework("kyo.test.runner.SbtFramework"),
  Test / fork := true,
  Test / javaOptions ++= Seq(
    "-Xmx1g",
    "-Xss16m",
    "--sun-misc-unsafe-memory-access=allow",
  ),
)
