import BuildKeys._
import Boilerplate._

// ---------------------------------------------------------------------------
// Commands

lazy val aggregatorIDs = Seq("core")

addCommandAlias("ci-jvm", ";" + aggregatorIDs.map(id => s"$id/clean ;$id/test:compile ;$id/test").mkString(";"))
addCommandAlias("ci-package", ";scalafmtCheckAll ;package")
addCommandAlias("ci-doc", ";unidoc ;site/makeMicrosite")
addCommandAlias("ci", ";project root ;reload ;+scalafmtCheckAll ;+ci-jvm ;+package ;ci-doc")
addCommandAlias("release", ";+clean ;ci-release ;unidoc ;site/publishMicrosite")

// ---------------------------------------------------------------------------
// Dependencies

val CatsVersion = "2.6.1"
val CatsEffectVersion = "3.1.1"
val FS2Version = "3.0.6"
val AwsSdkVersion = "1.11.816"
val CatsRetryVersion = "3.1.0"
val RefinedVersion = "0.9.14"
val MacroParadiseVersion = "2.1.1"
val KindProjectorVersion = "0.13.2"
val BetterMonadicForVersion = "0.3.1"

/**
  * Defines common plugins between all projects.
  */
def defaultPlugins: Project ⇒ Project = pr => {
  val withCoverage = sys.env.getOrElse("SBT_PROFILE", "") match {
    case "coverage" => pr
    case _          => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
  }
  withCoverage.enablePlugins(AutomateHeaderPlugin).enablePlugins(GitBranchPrompt)
}

lazy val sharedSettings = Seq(
  ThisBuild / includePluginResolvers := true,
  projectTitle := "fs2-kinesis-firehose",
  projectWebsiteRootURL := "https://zakolenko.github.io/",
  projectWebsiteBasePath := "/fs2-kinesis-firehose",
  githubOwnerID := "zakolenko",
  githubRelativeRepositoryID := "fs2-kinesis-firehose",
  organization := "io.github.zakolenko",
  scalaVersion := "2.13.3",
  crossScalaVersions := Seq("2.12.10", "2.13.3", "3.7.1"),
  // More version specific compiler options
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      Seq(
        "-Ypartial-unification"
      )
    case _ =>
      Seq(
        // Replaces macro-paradise in Scala 2.13
        "-Ymacro-annotations"
      )
  }),
  // Turning off fatal warnings for doc generation
  scalacOptions.in(Compile, doc) ~= filterConsoleScalacOptions,
  addCompilerPlugin(("org.typelevel" % "kind-projector" % KindProjectorVersion).cross(CrossVersion.full)),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % BetterMonadicForVersion),
  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath",
    file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),
  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),
  // ---------------------------------------------------------------------------
  // Options for testing
  logBuffered in Test := false,
  logBuffered in IntegrationTest := false,
  // Disables parallel execution
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  testForkedParallel in Test := false,
  testForkedParallel in IntegrationTest := false,
  testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a")),
  testFrameworks += new TestFramework("munit.Framework"),
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  // ---------------------------------------------------------------------------
  // Options meant for publishing on Maven Central
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  }, // removes optional dependencies
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url(projectWebsiteFullURL.value)),
  headerLicense := Some(HeaderLicense.Custom(s"""|Copyright (c) 2020 the ${projectTitle.value} contributors.
        |See the project homepage at: ${projectWebsiteFullURL.value}
        |
        |Licensed under the Apache License, Version 2.0 (the "License");
        |you may not use this file except in compliance with the License.
        |You may obtain a copy of the License at
        |
        |    http://www.apache.org/licenses/LICENSE-2.0
        |
        |Unless required by applicable law or agreed to in writing, software
        |distributed under the License is distributed on an "AS IS" BASIS,
        |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        |See the License for the specific language governing permissions and
        |limitations under the License.""".stripMargin)),
  scmInfo := Some(
    ScmInfo(
      url(s"https://github.com/${githubFullRepositoryID.value}"),
      s"scm:git@github.com:${githubFullRepositoryID.value}.git"
    )
  ),
  developers := List(
    Developer(
      id = "roman.zakolenko",
      name = "Roman Zakolenko",
      email = "zakolenkoroman@gmail.com",
      url = url("https://zakolenko.github.io")
    )
  ),
  // -- Settings meant for deployment on oss.sonatype.org
  sonatypeProfileName := organization.value
)

def defaultProjectConfiguration(pr: Project) = {
  pr.configure(defaultPlugins)
    .settings(sharedSettings)
    .settings(doctestTestSettings(DoctestTestFramework.Minitest))
    .settings(crossVersionSharedSources)
    .settings(requiredMacroCompatDeps(MacroParadiseVersion))
    .settings(
      filterOutMultipleDependenciesFromGeneratedPomXml(
        "groupId" -> "org.scoverage".r :: Nil,
        "groupId" -> "org.typelevel".r :: "artifactId" -> "simulacrum".r :: Nil
      )
    )
}

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(core)
  .configure(defaultPlugins)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .settings(unidocSettings(core))
  .settings(
    // Try really hard to not execute tasks in parallel ffs
    Global / concurrentRestrictions := Tags.limitAll(1) :: Nil
  )

lazy val site = project
  .in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
  .dependsOn(core)
  .settings {
    import microsites._
    Seq(
      micrositeName := projectTitle.value,
      micrositeDescription := "Amazon Kinesis Data Firehose bindings for fs2",
      micrositeAuthor := "Roman Zakolenko",
      micrositeGithubOwner := githubOwnerID.value,
      micrositeGithubRepo := githubRelativeRepositoryID.value,
      micrositeUrl := projectWebsiteRootURL.value.replaceAll("[/]+$", ""),
      micrositeBaseUrl := projectWebsiteBasePath.value.replaceAll("[/]+$", ""),
      micrositeDocumentationUrl := s"${projectWebsiteFullURL.value.replaceAll("[/]+$", "")}/${docsMappingsAPIDir.value}/",
      micrositeGitterChannelUrl := githubFullRepositoryID.value,
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      fork in mdoc := true,
      libraryDependencies += "com.47deg" %% "github4s" % "0.30.0",
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
        file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig(
          "CODE_OF_CONDUCT.md",
          "page",
          Map("title" -> "Code of Conduct", "section" -> "code of conduct", "position" -> "100")
        ),
        file("LICENSE.md") -> ExtraMdFileConfig(
          "LICENSE.md",
          "page",
          Map("title" -> "License", "section" -> "license", "position" -> "101")
        )
      ),
      docsMappingsAPIDir := s"api",
      addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc) in root, docsMappingsAPIDir),
      sourceDirectory in Compile := baseDirectory.value / "src",
      sourceDirectory in Test := baseDirectory.value / "test",
      mdocIn := (sourceDirectory in Compile).value / "mdoc",
      // Bug in sbt-microsites
      micrositeConfigYaml := microsites.ConfigYml(
        yamlCustomProperties = Map("exclude" -> List.empty[String])
      )
    )
  }

lazy val core = project
  .in(file("core"))
  .configure(defaultProjectConfiguration)
  .settings(
    name := "fs2-kinesis-firehose-core",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-kinesis" % AwsSdkVersion,
      "co.fs2" %% "fs2-core" % FS2Version,
      "com.github.cb372" %% "cats-retry" % CatsRetryVersion,
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsEffectVersion,
      "org.typelevel" %% "cats-effect-std" % CatsEffectVersion,
      // For testing
      "com.dimafeng" %% "testcontainers-scala-localstack" % "0.39.12" % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % "0.39.12" % Test,
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.0" % Test,
      "org.scalameta" %% "munit" % "0.7.29" % Test
    )
  )

// Reloads build.sbt changes whenever detected
Global / onChangedBuildSource := ReloadOnSourceChanges
