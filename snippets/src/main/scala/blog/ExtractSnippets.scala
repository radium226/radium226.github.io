package blog

import java.nio.file.Paths

import file.FileAlgebra
import snippet.SnippetAlgebra
import source.SourceAlgebra

import instances._

import cats.effect._


object ExtractSnippets extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val fileAlgebra = FileAlgebra[IO]
    implicit val sourceAlgebra = SourceAlgebra[IO]
    implicit val snippetAlgebra = SnippetAlgebra[IO]

    for {
      snippets <- fileAlgebra
        .listFiles(Paths.get(""))
        .through(SourceAlgebra[IO].keepSourceFile)
        .through(SnippetAlgebra[IO].extractSnippets)
        .compile
        .toList

      _ <- fileAlgebra
        .listFiles(Paths.get(""))
        .through(SourceAlgebra[IO].keepSourceFile)
        .through(SnippetAlgebra[IO].replaceSnippets(snippets))
        .compile
        .drain
    } yield ExitCode.Success
  }

}