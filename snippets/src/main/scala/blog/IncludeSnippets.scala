package blog

import java.nio.file.{Path, Paths}

import file.FileAlgebra
import snippet.SnippetAlgebra
import source.SourceAlgebra
import cats.effect._
import cats.implicits._
import scopt._
import java.nio.file.{ Path => FilePath }



object IncludeSnippets extends IOApp {

  implicit def readForPath: Read[FilePath] = Read.reads(Paths.get(_))

  case class Config(sourceFolderPath: Path = null, sourceFilePath: Path = null)

  def includeSnippets[F[_]: Sync: ContextShift](sourceFolderPath: Path, inputFilePath: Path): F[Unit] = {
    (for {
      fileAlgebra <- FileAlgebra.resource[F]
      sourceAlgebra <- SourceAlgebra.resource[F](fileAlgebra)
      snippetAlgebra <- SnippetAlgebra.resource[F](fileAlgebra)
    } yield (fileAlgebra, sourceAlgebra, snippetAlgebra)).use({ case (fileAlgebra, sourceAlgebra, snippetAlgebra) =>
      for {
        snippets <- fileAlgebra
          .listFiles(sourceFolderPath)
          .through(sourceAlgebra.sourceFilesOnly)
          .through(snippetAlgebra.parseSnippets)
          .compile
          .toList

        snippetRefs <- snippetAlgebra.parseSnippetRefs(inputFilePath, snippets).compile.toList

        _ <- snippetAlgebra
          .includeSnippets(inputFilePath, snippetRefs)
          .showLines(System.out)
          .compile
          .drain
      } yield ()
    })
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val builder = OParser.builder[Config]
    val parser = {
      import builder._
      OParser.sequence(
        programName("snippets"),
        head("Snippets! ", "0.1.0"),
        help('h', "help"),
        opt[Path]('s', "source-folder")
          .action({ (sourceFolderPath, config) =>
            config.copy(sourceFolderPath = sourceFolderPath)
          })
          .text("Source folder"),
        arg[FilePath]("<source file>")
          .action({ (sourceFilePath, config) =>
            config.copy(sourceFilePath = sourceFilePath)
          })
          .text("Source file")
      )
    }

    OParser
      .parse(parser, args, Config())
      .fold(new Exception("Invalid arguments! ").raiseError[IO, Config])(_.pure[IO])
      .flatMap({ config =>
        includeSnippets[IO](config.sourceFolderPath, config.sourceFilePath).as(ExitCode.Success)
      })
  }

}