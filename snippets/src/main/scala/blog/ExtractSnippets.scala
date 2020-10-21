package blog

import java.nio.file.Paths

import file.FileAlgebra
import snippet.SnippetAlgebra
import source.SourceAlgebra

import cats.effect._


object ExtractSnippets extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      fileAlgebra <- FileAlgebra.resource[IO]
      sourceAlgebra <- SourceAlgebra.resource[IO](fileAlgebra)
      snippetAlgebra <- SnippetAlgebra.resource[IO](fileAlgebra)
    } yield (fileAlgebra, sourceAlgebra, snippetAlgebra)).use({ case (fileAlgebra, sourceAlgebra, snippetAlgebra) =>
      val inputFolderPath = Paths.get(".")
      val outputFolderPath = Paths.get("output")
      for {
        snippets <- fileAlgebra
          .listFiles(inputFolderPath)
          .evalTap({ filePath =>
            IO(println(s"filePath=${filePath}"))
          })
          .through(sourceAlgebra.sourceFilesOnly)
          .through(snippetAlgebra.parseSnippets)
          .evalTap({ snippet =>
            IO(println(s"snippet=${snippet}"))
          })
          .compile
          .toList

        _ <- fileAlgebra
          .listFiles(inputFolderPath)
          .through(sourceAlgebra.sourceFilesOnly)
          .through(snippetAlgebra.parseSnippetRefs(snippets))
          //.through(snippetAlgebra.includeSnippets(inputFolderPath, outputFolderPath))
          .compile
          .drain

      } yield ExitCode.Success

    })
  }

}