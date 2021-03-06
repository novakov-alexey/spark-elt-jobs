import Dependencies._

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / resolvers += "aws-glue-etl-artifacts" at "https://aws-glue-etl-artifacts.s3.amazonaws.com/release/"
ThisBuild / scalaVersion := "2.12.14"
ThisBuild / organization := "io.github.novakov-alexey"
ThisBuild / organizationName := "novakov-alexey"

lazy val root = (project in file("."))
  .aggregate(
    sparkJobs,
    hadoopJobs,
    common,
    awsLambda,
    glueJobs,
    glueScripts,
    emrJobs,
    sparkCommon
  )
  .settings(
    assembleArtifact := false
  )

lazy val sparkLocalCmd = s"""
    import org.apache.spark.sql.SparkSession
    val session =
      SparkSession.builder
        .appName("sbt-shell")
        .master("local[*]")
        .getOrCreate()
    """.stripMargin

lazy val sparkCommon = (project in file("./modules/sparkcommon"))
  .dependsOn(common)
  .settings(
    name := "etl-spark-common",
    libraryDependencies ++= Seq(
      sparkSql % Provided
    ),
    assembleArtifact := false
  )

lazy val sparkJobs = (project in file("./modules/sparkjobs"))
  .dependsOn(sparkCommon)
  .settings(
    name := "etl-spark-jobs",
    libraryDependencies ++= Seq(
      sparkSql % Provided,
      delta
    ) ++ sparkHadoopS3Dependencies,
    assemblyPackageScala / assembleArtifact := false,
    console / initialCommands := sparkLocalCmd,
    // uses compile classpath for the run task, including "provided" jar (cf http://stackoverflow.com/a/21803413/3827)
    Compile / run := Defaults
      .runTask(
        Compile / fullClasspath,
        Compile / run / mainClass,
        Compile / run / runner
      )
      .evaluated,
    Compile / runMain := Defaults
      .runMainTask(Compile / fullClasspath, Compile / run / runner)
      .evaluated
  )

lazy val hadoopJobs = (project in file("./modules/hadoopjobs"))
  .dependsOn(common)
  .settings(
    name := "etl-hadoop-jobs",
    assemblyMergeStrategy := {
      case PathList("scala", "annotation", xs @ _*)
          if xs.headOption.exists(_.startsWith("nowarn")) =>
        MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "public-suffix-list.txt" =>
        MergeStrategy.concat
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      hadoopCommon
    ) ++ sparkHadoopS3Dependencies
  )

lazy val common = (project in file("./modules/common")).settings(
  name := "common",
  assembleArtifact := false,
  libraryDependencies ++= Seq(
    sparkSql % Provided,
    hadoopCommon % Provided,
    mainargs
  )
)

lazy val awsLambda = (project in file("./modules/lambda"))
  .dependsOn(hadoopJobs)
  .settings(
    name := "lambda",
    assemblyMergeStrategy := {
      case PathList("scala", "annotation", xs @ _*)
          if xs.headOption.exists(_.startsWith("nowarn")) =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      awsLambdaCore,
      circeCore,
      circeParser,
      circeGeneric
    )    
  )

lazy val glueJobs = (project in file("./modules/gluejobs"))
  .settings(
    scalaVersion := "2.12.14",
    name := "etl-glue-jobs",
    assemblyPackageScala / assembleArtifact := false,
    assemblyMergeStrategy := {
      case "org/apache/spark/unused/UnusedStubClass.class" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    console / initialCommands := sparkLocalCmd,
    console / cleanupCommands := "spark.close",
    s3Upload / mappings := Seq(
      (
        target.value / "scala-2.12" / (assembly / assemblyJarName).value,
        (assembly / assemblyJarName).value
      )
    ),
    s3Upload / s3Progress := true,
    s3Upload / s3Host := "glue-jars-etljobs",
    libraryDependencies ++= Seq(
      hudiSparkBundle % Provided,
      awsGlue % Provided,
      hadoopCommon % Provided
    ) ++ sparkHadoopS3Dependencies.map(_ % Provided)
  )
  .enablePlugins(S3Plugin)

lazy val upload = taskKey[Unit]("compile and then upload to S3")

lazy val glueScripts = (project in file("./modules/gluescripts"))
  .dependsOn(glueJobs)
  .settings(
    name := "etl-glue-scripts",
    assembleArtifact := false,
    s3Upload / s3Host := "glue-scripts-etljobs",
    s3Upload / mappings := Seq(
      (
        new java.io.File(
          "modules/gluescripts/src/main/scala/etljobs/scripts/FileToFile.scala"
        ),
        "FileToFile.scala"
      ),
      (
        new java.io.File(
          "modules/gluescripts/src/main/scala/etljobs/scripts/HudiScript.scala"
        ),
        "HudiScript.scala"
      )
    ),
    upload := Def
      .sequential(
        Compile / compile,
        s3Upload.toTask
      )
      .value,
    libraryDependencies ++= Seq(
      awsGlue % Provided
    )
  )
  .enablePlugins(S3Plugin)

lazy val emrJobs = (project in file("./modules/emrjobs"))
  .dependsOn(sparkCommon)
  .settings(
    name := "etl-emr-jobs",
    assemblyPackageScala / assembleArtifact := false,
    assemblyMergeStrategy := {
      case PathList("org", "apache", xs @ _*)
          if xs.headOption.exists(_.startsWith("http")) =>
        MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith "public-suffix-list.txt" =>
        MergeStrategy.concat
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    libraryDependencies ++= Seq(
      sparkSql % Provided,
      emrHudiSparkBundle // % Provided
    ),
    s3Upload / mappings := Seq(
      (
        target.value / "scala-2.12" / "etl-emr-jobs-assembly-0.1.0-SNAPSHOT.jar",
        "etl-emr-jobs-assembly-0.1.0-SNAPSHOT.jar"
      )
    ),
    s3Upload / s3Progress := true,
    s3Upload / s3Host := "emr-extra-jars-etljobs"
  )
  .enablePlugins(S3Plugin)
