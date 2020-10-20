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
      for {
        snippets <- fileAlgebra
          .listFiles(Paths.get("."))
          .evalTap({ filePath =>
            IO(println(s"filePath=${filePath}"))
          })
          .through(sourceAlgebra.sourceFilesOnly)
          .through(snippetAlgebra.extractSnippets)
          .evalTap({ snippet =>
            IO(println(s"snippet=${snippet}"))
          })
          .compile
          .toList

        _ = println(snippets)

        _ <- fileAlgebra
          .listFiles(Paths.get("."))
          .through(sourceAlgebra.sourceFilesOnly)
          .through(snippetAlgebra.replaceSnippets(snippets))
          .compile
          .drain
      } yield ExitCode.Success

    })
  }

}