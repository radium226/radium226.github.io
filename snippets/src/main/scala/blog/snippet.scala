package blog
package snippet

import java.nio.file.Path

import blog.file.FileAlgebra
import blog.source.SourceAlgebra
import cats.Applicative
import cats.effect.{Blocker, Resource, Sync}
import fs2._

import cats.implicits._
import cats.effect.implicits._

import scala.collection.immutable.NumericRange

case class Snippet(name: String, filePath: Path, lineNoRange: NumericRange.Inclusive[Long])

object SnippetAlgebra {

  def resource[F[_]: Sync](fileAlgebra: FileAlgebra[F]): Resource[F, SnippetAlgebra[F]] = {
    Resource.pure[F, SnippetAlgebra[F]](new SnippetAlgebra[F] {

      override def extractSnippets: Pipe[F, Path, Snippet] = { filePaths =>

        def go(lineAndNoStream: Stream[F, (String, Long)], sourceFilePath: Path, partialSnippet: Option[(String, Long)]): Pull[F, Snippet, Unit] = partialSnippet match {
          case None =>
            lineAndNoStream.pull.uncons1.flatMap({
              // Looking for start
              case Some(((line, lineNo), remainingLineAndNoStream)) if line.startsWith("//<<") =>
                println("We are here! ")
                go(remainingLineAndNoStream, sourceFilePath, ("name", lineNo).some)

              // Looking for end
              case Some(((line, no), remainingLineAndNoStream)) =>
                go(remainingLineAndNoStream, sourceFilePath, none[(String, Long)])

              case None =>
                Pull.done
            })

          case Some((snippetName, snippetStartLineNo)) =>
            lineAndNoStream.pull.uncons1.flatMap({
              // Looking for start
              case Some(((line, snippetEndLineNo), remainingLineAndNoStream)) if line.startsWith("//>>") =>
                println("We also are here! ")
                Pull.output1(Snippet(snippetName, sourceFilePath, snippetStartLineNo to snippetEndLineNo)) >> go(remainingLineAndNoStream, sourceFilePath, none[(String, Long)])

              // Looking for end
              case Some((_, remainingLineAndNoStream)) =>
                go(remainingLineAndNoStream, sourceFilePath, (snippetName, snippetStartLineNo).some)

              case None =>
                Pull.done
            })

        }

        filePaths
          .flatMap({ filePath =>
            go(fileAlgebra.fileContent(filePath).evalTap({ line => F.delay(println(s"line=${line}")) }).zipWithIndex, filePath, none[(String, Long)]).stream
          })

      }

      override def replaceSnippets(snippets: List[Snippet]): Pipe[F, Path, Unit] = { sourceFilePath =>
        sourceFilePath.drain.as(())
      }

    })
  }

}

trait SnippetAlgebra[F[_]] {

  def extractSnippets: Pipe[F, Path, Snippet]

  def replaceSnippets(snippets: List[Snippet]): Pipe[F, Path, Unit]

}