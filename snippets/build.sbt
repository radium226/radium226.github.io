ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.github.radium226"
ThisBuild / organizationName := "Radium226"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:_",
  "-unchecked",
  "-Wvalue-discard",
  "-Xfatal-warnings",
  "-Ymacro-annotations"
)

lazy val fs2Dependency = for {
  fs2  <- Dependencies.fs2
  cats <- Dependencies.cats
} yield fs2 exclude(cats.organization, cats.name)

lazy val root = (project in file("."))
  .settings(
    addCompilerPlugin(Dependencies.contextApplied),
    name := "include-snippets",
    // cats
    libraryDependencies ++= Dependencies.cats,
    // fs2
    libraryDependencies ++= (for {
      fs2  <- Dependencies.fs2
      cats <- Dependencies.cats
    } yield fs2 exclude(cats.organization, cats.name)),
    // scopt
    libraryDependencies ++= Dependencies.scopt,
    // logback
    //libraryDependencies ++= Dependencies.logback,
    // scala-test
    libraryDependencies ++= Dependencies.scalaTest map { _ % Test },
    // http4s
    libraryDependencies ++= Dependencies.http4s,
    // slf4j
    libraryDependencies ++= Dependencies.slf4j,

    libraryDependencies += "org.scalameta" %% "svm-subs" % "20.2.0",

    Compile / mainClass := Some("blog.IncludeSnippets"),

    /*assembly / mainClass := Some("pr0n.Main"),
    assembly / assemblyJarName := "pr0n.jar",*/

    graalVMNativeImageCommand := "/usr/lib/jvm/java-11-graalvm/bin/native-image",
    graalVMNativeImageOptions := List(
      "-H:+ReportUnsupportedElementsAtRuntime",
      "--initialize-at-build-time",
      "--no-fallback",
      "--allow-incomplete-classpath",
      "-H:+AddAllCharsets",
      "-H:ResourceConfigurationFiles=../../src/main/graal/resource-config.json",
      "-H:ReflectionConfigurationFiles=../../src/main/graal/reflect-config.json",
      "-H:DynamicProxyConfigurationFiles=../../src/main/graal/proxy-config.json",
      "-H:JNIConfigurationFiles=../../src/main/graal/jni-config.json",
      "-H:Log=registerResource"
      /*"--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "--initialize-at-build-time",
      "-H:+AddAllCharsets",
      "-H:+JNI",
      "--no-fallback",
      "--allow-incomplete-classpath",
      "--report-unsupported-elements-at-runtime"*/
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin)