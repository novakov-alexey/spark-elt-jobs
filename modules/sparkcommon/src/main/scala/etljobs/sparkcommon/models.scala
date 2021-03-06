package etljobs.sparkcommon

import etljobs.common.MainArgsUtil.UriRead
import etljobs.common.{FileCopyCfg, SparkOption}
import mainargs._

import java.net.URI

sealed trait DataFormat {
  def toSparkFormat: String =
    getClass.getSimpleName.toLowerCase.stripSuffix("$")
}

object DataFormat {
  implicit object DataFormatRead
      extends TokensReader[DataFormat](
        "input file or output file/table format",
        strs =>
          strs.head match {
            case "csv"     => Right(CSV)
            case "json"    => Right(JSON)
            case "parquet" => Right(Parquet)
            case "delta"   => Right(Delta)
            case "hudi"    => Right(Hudi)
            case _         => Left("Unknown file format")
          }
      )

  case object CSV extends DataFormat
  case object JSON extends DataFormat
  case object Parquet extends DataFormat
  case object Delta extends DataFormat
  case object Hudi extends DataFormat
}

@main
case class SparkCopyCfg(
    @arg(
      name = "input-format",
      doc = "Data input format to be used by Spark Datasource API on read"
    )
    inputFormat: DataFormat,
    @arg(
      name = "output-format",
      doc = "Data output format to be used by Spark Datasource API on write"
    )
    saveFormat: DataFormat,
    @arg(
      name = "move-files",
      doc =
        "Whether to move files to processed directory inside the job context. If 'stream-move-file' is set, then 'move-files' flag is ignored"
    )
    moveFiles: Flag,
    @arg(
      name = "reader-options",
      doc = "<name>:<value> list of options to be passed to Spark reader"
    )
    readerOptions: List[SparkOption],
    @arg(
      short = 's',
      name = "schema-path",
      doc = "A path to schema directory for all entities as per entityPatterns"
    )
    schemaPath: Option[URI],
    @arg(
      name = "partition-by",
      doc = "Dataframe column list to partition by. Options: [year, month, day]"
    )
    partitionBy: List[String],
    fileCopy: FileCopyCfg,
    @arg(
      name = "stream-move-files",
      doc =
        "Whether to move source files using Spark streaming 'cleanSource' feature. If set, then 'move-files' flag is ignored"
    )
    streamMoveFiles: Flag,
    @arg(
      name = "hudi-sync-to-hive",
      doc =
        "If set, Spark will sync Hudi table to Hive"
    )
    syncToHive: Flag,
    @arg(
      name = "sync-database",
      doc =
        "If set, Spark will sync Hudi table to Hive sync-database"
    )
    syncDatabase: Option[String]
)

object SparkCopyCfg {
  implicit def copyParamsParser: ParserForClass[SparkCopyCfg] =
    ParserForClass[SparkCopyCfg]
}
@main
case class SparkStreamingCopyCfg(
    sparkCopy: SparkCopyCfg,
    @arg(
      name = "trigger-interval",
      doc =
        "Number of milliseconds for Spark streaming ProcessingTime trigger. Negative value sets Trigger.Once"
    )
    triggerInterval: Long
)

object SparkStreamingCopyCfg {
  implicit def copyParamsParser: ParserForClass[SparkStreamingCopyCfg] =
    ParserForClass[SparkStreamingCopyCfg]
}
