import CompareKey.NoVersion
import CompareKey.WithVersion

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.regex.Pattern
import scala.collection.JavaConverters
import scala.compiletime.ops.double
import scala.jdk.CollectionConverters
import scala.sys.Prop
import scala.sys.SystemProperties
import scala.sys.process.*
import scala.util.CommandLineParser
import scala.util.Properties
import scala.util.Try
import scala.util.matching.Regex

type CommandExecutor = (workDir: Path, cmd: String) => Either[ProgramErr, Int]
type DependenciesFileReader = (mvnDepTreeOutputFile: Path) => Seq[String]

val TEMP_DIR = sys.props.get("java.io.tmpdir").get

case class MvnDepTreeExecResult(
    projectPath: Path,
    treeOutput: Path,
    exitCode: Int
)

def executeMvnDepTree(projectPath: Path)(using
    commandExecutor: CommandExecutor
): Either[ProgramErr, MvnDepTreeExecResult] = executeMvnDepTree(projectPath, None)

def executeMvnDepTree(projectPath: Path, overriddenSettingsXml: Path)(using
    commandExecutor: CommandExecutor
): Either[ProgramErr, MvnDepTreeExecResult] = executeMvnDepTree(projectPath, Some(overriddenSettingsXml))

private def executeMvnDepTree(projectPath: Path, overriddenSettingsXml: Option[Path])(using
    commandExecutor: CommandExecutor
): Either[ProgramErr, MvnDepTreeExecResult] =
  val treeOutFilename = s"${projectPath.getFileName}-${UUID.randomUUID}"
  val treeOutPath = Path.of(TEMP_DIR, treeOutFilename)
  val cmd = overriddenSettingsXml match
    case Some(settingsXml) =>
      s"mvn -s ${settingsXml.toAbsolutePath} dependency:tree -DoutputFile=${treeOutPath.toAbsolutePath} -DoutputType=tgf"
    case None => s"mvn dependency:tree -DoutputFile=${treeOutPath.toAbsolutePath} -DoutputType=tgf"
  return commandExecutor(projectPath, cmd)
    .map(exitCode => MvnDepTreeExecResult(projectPath, treeOutPath, exitCode))
end executeMvnDepTree

private def executeCommand(workDir: Path, cmd: String): Either[ProgramErr, Int] =
  println(s">>>>>> Executing command='$cmd' at $workDir <<<<<<")
  val process = Process(cmd, Some(workDir.toFile))
  val execResult = Try(process.!).toEither.left.map(t => s"Cannot execute command, reason=${t.getMessage}")
  println()
  return execResult

case class GAV(
    group: String,
    artifact: String,
    version: String
)

val DEPENDENCY_ELEM_LINE_REGEX: Regex = "^(?<nodeId>\\d+) (?<gav>\\S+)$".r
val GAV_REGEX: Regex =
  "^(?<group>[\\p{ASCII}&&[^:]]+):(?<artifact>[\\p{ASCII}&&[^:]]+):(?<packaging>[\\p{ASCII}&&[^:]]+):(?<version>[\\p{ASCII}&&[^:]]+):?\\p{ASCII}*$".r
val NODES_EDGES_SEPARATOR_REGEX: Regex = "^.*#.*$".r
val EDGES_LINE_REGEX: Regex = "^(?<out>\\d+) (?<in>\\d+) (?<scope>\\S+)$".r

def readDependencies(from: MvnDepTreeExecResult)(using
    depFileReader: DependenciesFileReader
): Either[ProgramErr, Seq[GAV]] =
  import scala.collection.convert.AsScalaConverters
  try
    val seqLines = depFileReader(from.treeOutput).map(treeLineToGAV)
    return sequenceRight(seqLines).map(gavs => gavs.flatMap(_.toSeq))
  catch case e: Throwable => Left(s"Cannot read dependencies from file=${from.treeOutput}, err=${e.getMessage}")
end readDependencies

private def treeLineToGAV(line: String): Either[ProgramErr, Option[GAV]] = line match
  case EDGES_LINE_REGEX(_, _, _)          => Right(None)
  case "#"                                => Right(None)
  case DEPENDENCY_ELEM_LINE_REGEX(_, gav) => toGAV(gav).map(Some.apply)
  case x                                  => Left(s"Cannot parse tgf tree output from line=$line")

private def toGAV(gavSeparatedByColon: String): Either[ProgramErr, GAV] = gavSeparatedByColon match
  case GAV_REGEX(group, artifact, _, version) => Right(GAV(group, artifact, version))
  case _                                      => Left(s"Cannot find GAV coords in value=$gavSeparatedByColon")

private def sequenceRight[A, B](e: Seq[Either[A, B]]): Either[A, Seq[B]] =
  val (errs, goods) = e.partitionMap(identity)
  if errs.nonEmpty then Left(errs.head) else Right(goods)

enum CompareKey:
  case NoVersion(group: String, artifact: String)
  case WithVersion(group: String, artifact: String, version: String)

  override def toString: String = this match
    case NoVersion(group, artifact)            => s"$group:$artifact"
    case WithVersion(group, artifact, version) => s"$group:$artifact$version"
end CompareKey

case class CompareResult(
    // Dependencies that present in ours, but lacks in theirs
    removed: Seq[CompareKey],

    // Dependencies that present in theirs, but lacks in ours
    added: Seq[CompareKey]
)

def compareWithoutVersion(ours: Seq[GAV], theirs: Seq[GAV]): CompareResult =
  val compare = (ours: Seq[GAV], theirs: Seq[GAV]) =>
    val oursSet = ours.map(gav => NoVersion(gav.group, gav.artifact)).toSet
    val theirsSet = theirs.map(gav => NoVersion(gav.group, gav.artifact)).toSet
    val removed = oursSet.diff(theirsSet)
    val added = theirsSet.diff(oursSet)
    CompareResult(removed.toSeq, added.toSeq)
  val (oursWithoutSelf, theirsWithoutSelf) =
    (ours.tail, theirs.tail) // First line always contains target Maven project
  return compare(oursWithoutSelf, theirsWithoutSelf)
end compareWithoutVersion

def printCompareResult(result: CompareResult): Unit =
  println(s">>>>>> Compare results <<<<<<")
  if result.removed.isEmpty && result.added.isEmpty then
    println("No differences")
    return
  if result.added.nonEmpty then
    for added <- result.added.map(_.toString) do println(s"+++ $added")
    println()
  if result.removed.nonEmpty then for removed <- result.removed.map(_.toString) do println(s"--- $removed")
end printCompareResult
