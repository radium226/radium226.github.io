package blog
package snippet

import java.nio.file.Path
import java.util.regex.Pattern

import blog.file.{FileAlgebra, MimeType}
import fs2._
import cats._
import cats.effect._

import scala.collection.immutable.NumericRange
import scala.util.matching.Regex
import cats.implicits._
import cats.effect.implicits._

case class CommentSyntax(begin: String, end: String)

case class Snippet(name: String, sourceFilePath: Path, lineNoRange: NumericRange.Inclusive[Long])

case class SnippetRef(snippet: Snippet, sourceFilePath: Path, lineNo: Long)

object SnippetAlgebra {

  def commentSyntaxFor(mimeType: MimeType): Option[CommentSyntax] = CommentSyntax("//", "//").some

  def resource[F[_]: Sync](fileAlgebra: FileAlgebra[F]): Resource[F, SnippetAlgebra[F]] = {
    Resource.pure[F, SnippetAlgebra[F]](new SnippetAlgebra[F] {

      def lookUpCommentSyntax: Pipe[F, Path, (Path, CommentSyntax)] = { filePaths =>
        filePaths
          .evalMap({ sourceFilePath =>
            fileAlgebra
              .mimeType(sourceFilePath)
              .map(_.map({ sourceFileMimeType =>
                (sourceFilePath, sourceFileMimeType)
              }))
          })
          .unNone
          .map({ case (sourceFilePath, sourceFileMimeType) =>
            commentSyntaxFor(sourceFileMimeType)
              .map({ commentSyntax =>
                (sourceFilePath, commentSyntax)
              })
          })
          .unNone
      }

      override def parseSnippets: Pipe[F, Path, Snippet] = { sourceFilePaths =>

        def go(lineAndNoStream: Stream[F, (String, Long)], sourceFilePath: Path, commentSyntax: CommentSyntax, partialSnippetOption: Option[(String, Long)]): Pull[F, Snippet, Unit] = partialSnippetOption match {
          case None =>
            lineAndNoStream.pull.uncons1.flatMap({
              // Looking for start
              case Some(((line, lineNo), remainingLineAndNoStream)) =>
                val linePattern = s"${Pattern.quote(commentSyntax.begin)}<<< ([a-z0-9-]+)".r("snippetName")
                linePattern.findFirstMatchIn(line) match {
                  // If the line matches...
                  case Some(lineMatch) =>
                    go(remainingLineAndNoStream, sourceFilePath, commentSyntax, (lineMatch.group("snippetName"), lineNo).some)

                  // If not...
                  case None =>
                    go(remainingLineAndNoStream, sourceFilePath, commentSyntax, none[(String, Long)])
                }

              case None =>
                Pull.done
            })

          case Some((snippetName, snippetStartLineNo)) =>
            lineAndNoStream.pull.uncons1.flatMap({
              // Looking for end
              case Some(((line, snippetEndLineNo), remainingLineAndNoStream)) =>
                val linePattern = s"^${Pattern.quote(commentSyntax.end)}>>>".r
                linePattern.findFirstMatchIn(line) match {
                  case Some(_) =>
                    Pull.output1(Snippet(snippetName, sourceFilePath, snippetStartLineNo to snippetEndLineNo)) >> go(remainingLineAndNoStream, sourceFilePath, commentSyntax, none[(String, Long)])

                  case None =>
                    go(remainingLineAndNoStream, sourceFilePath, commentSyntax, (snippetName, snippetStartLineNo).some)
                }

              case None =>
                Pull.done
            })

        }

        sourceFilePaths
          .through(lookUpCommentSyntax)
          .flatMap({ case (sourceFilePath, commentSyntax) =>
            go(
              fileAlgebra
                .readFile(sourceFilePath)
                .zipWithIndex,
              sourceFilePath,
              commentSyntax,
              none[(String, Long)]
            ).stream
          })

      }

      override def includeSnippets(inputFolderPath: Path, outputFolderPath: Path): Pipe[F, SnippetRef, Unit] = { snippetRefs =>
        snippetRefs
          .flatMap({ snippetRef =>
            fileAlgebra
              .readFile(snippetRef.sourceFilePath)
              .zipWithIndex
              .flatMap({
                case (_, lineNo) if lineNo == snippetRef.lineNo =>
                  readSnippet(snippetRef.snippet)
                case (line, _) =>
                  Stream.emit(line)
              })
              .through(fileAlgebra.writeFile(outputFolderPath.resolve("test.md")))
          })
      }

      def parseSnippetRefs(snippets: List[Snippet]): Pipe[F, Path, SnippetRef] = { sourceFilePaths =>
        val snippetsByName = snippets
          .map({ snippet =>
            (snippet.name, snippet)
          })
          .toMap

        sourceFilePaths
          .through(lookUpCommentSyntax)
          .flatMap({ case (sourceFilePath, commentSyntax) =>
            val linePattern = s"^${Pattern.quote(commentSyntax.begin)}<<< ([a-z0-9-]+) >>>".r("snippetName")

            fileAlgebra
              .readFile(sourceFilePath)
              .zipWithIndex
              .map({ case (line, lineNo) =>
                for {
                  lineMatch     <- linePattern.findFirstMatchIn(line)
                  snippetName = lineMatch.group("snippetName")
                  snippet       <- snippetsByName.get(snippetName)
                } yield SnippetRef(snippet, sourceFilePath, lineNo)
              })
              .unNone

          })
      }

      def readSnippet(snippet: Snippet): Stream[F, String] = {
        fileAlgebra
          .readFile(snippet.sourceFilePath)
          .zipWithIndex
          .collect({
            case (line, lineNo) if snippet.lineNoRange.contains(lineNo) =>
              line
          })
      }
    })

  }

}

trait SnippetAlgebra[F[_]] {

  def parseSnippets: Pipe[F, Path, Snippet]

  def parseSnippetRefs(snippets: List[Snippet]): Pipe[F, Path, SnippetRef]

  def includeSnippets(inputFolderPath: Path, outputFolderPath: Path): Pipe[F, SnippetRef, Unit]

  def readSnippet(snippet: Snippet): Stream[F, String]

}