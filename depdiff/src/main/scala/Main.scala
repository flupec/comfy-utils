import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters
import scala.util.CommandLineParser

type ProgramErr = String

given CommandLineParser.FromString[Path] with
  def fromString(s: String): Path = Path.of(s)

given DependenciesFileReader = (p: Path) =>
  import scala.collection.convert.AsScalaConverters
  CollectionConverters.ListHasAsScala(Files.readAllLines(p)).asScala.toSeq

given CommandExecutor = executeCommand

val HELP = """
|Help: tool to view Maven project dependency tree diff
|Usage:
|  depdiff --ours /abs-path/to/project/v1 --theirs /abs-path/to/project/v2 [--settings /abs-path/to/settings.xml]
""".stripMargin

@main def main(args: String*) =
  val overriddenSettingsXml: Option[Path] = getOptionalCliArg("settings", args)

  val programResult = for
    ours <- getMandatoryCliArg("ours", args)
    theirs <- getMandatoryCliArg("theirs", args)
    oursExecResult <- callMvnDepTree(ours, overriddenSettingsXml)
    theirsExecResult <- callMvnDepTree(theirs, overriddenSettingsXml)
    oursDeps <- readDependencies(oursExecResult)
    theirsDeps <- readDependencies(theirsExecResult)
    compareResult = compareWithoutVersion(oursDeps, theirsDeps)
    _ = printCompareResult(compareResult)
  yield ()

  programResult match
    case Left(err) =>
      println(s"Error occurred: $err")
      print(HELP)
    case Right(_) => ()
end main

private def getMandatoryCliArg[A](
    argName: String,
    args: Seq[String]
)(using argParser: CommandLineParser.FromString[A]): Either[ProgramErr, A] =
  getOptionalCliArg(argName, args) match
    case Some(argValue) => Right(argValue)
    case None           => Left(s"Argument '$argName' is mandatory")
end getMandatoryCliArg

private def getOptionalCliArg[A](
    argName: String,
    args: Seq[String]
)(using argParser: CommandLineParser.FromString[A]): Option[A] =
  val argKey = s"--$argName"
  args.indexWhere(_ == argKey) match
    case -1                          => None
    case idx if args.size - 1 == idx => None
    case idx                         =>
      val argValue = args(idx + 1)
      if argValue.startsWith("--") then None else argParser.fromStringOption(argValue)
end getOptionalCliArg

private def callMvnDepTree(
    projectPath: Path,
    overriddenSettingsXml: Option[Path]
): Either[ProgramErr, MvnDepTreeExecResult] =
  overriddenSettingsXml match
    case Some(settingsXml) => executeMvnDepTree(projectPath, settingsXml)
    case None              => executeMvnDepTree(projectPath)
