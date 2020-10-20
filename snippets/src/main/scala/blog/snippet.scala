package blog
package snippet

import java.nio.file.Path

import blog.file.FileAlgebra
import blog.source.SourceAlgebra
import cats.effect.{Blocker, Sync}
import fs2._


case class Snippet(name: String, filePath: Path, lineRange: Range)

object SnippetAlgebra {

  def apply[F[_]: SnippetAlgebra]: SnippetAlgebra[F] = implicitly

}

trait SnippetAlgebra[F[_]] {

  def extractSnippets(blocker: Blocker): Pipe[F, Path, Snippet]

  def replaceSnippets(snippets: List[Snippet]): Pipe[F, Path, Unit]

}

trait SnippetAlgebraInstances {

  implicit def syncSnippetAlgebra[F[_]: Sync: FileAlgebra]: SnippetAlgebra[F] = new SnippetAlgebra[F] {

    override def extractSnippets(blocker: Blocker): Pipe[F, Path, Snippet] = { filePaths =>
      def go(lineAndNoStream: Stream[F, (String, Long)], startLineNo: Long): Pull[F, Snippet, Nothing] = {
        lineAndNoStream.pull.uncons1.flatMap({
          case _ =>
            Pull.done

          case None =>
            Pull.done
        })
      }

      filePaths
        .flatMap({ filePath =>
          go(FileAlgebra[F].fileContent(_)(blocker).zipWithIndex, -1l).stream
        })

    }

    override def replaceSnippets(snippets: List[Snippet]): Pipe[F, Path, Unit] = ???

  }

}