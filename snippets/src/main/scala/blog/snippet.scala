package blog
package snippet

import java.nio.file.Path
import java.util.regex.Pattern

import blog.file.{FileAlgebra, MimeType}
import fs2._
import cats._
import cats.data.OptionT
import cats.effect._

import scala.collection.immutable.NumericRange
import scala.util.matching.Regex
import cats.implicits._
import cats.effect.implicits._

case class CommentSyntax(begin: String, end: String)

case class Snippet(name: String, sourceFilePath: Path, lineNoRange: NumericRange.Inclusive[Long])

case class SnippetRef(snippet: Snippet, sourceFilePath: Path, lineNo: Long)

object SnippetAlgebra {

  val CommentSyntaxesByMimeType = Map(
    MimeType("application", "sql")      -> CommentSyntax("--", "--"),
    MimeType("text", "markdown") -> CommentSyntax("[//]: # ", "[//]: # "),
    MimeType("text", "scala") -> CommentSyntax("//", "//"), 
    MimeType("text", "java") -> CommentSyntax("//", "//"), 
    MimeType("text", "makefile") -> CommentSyntax("#", "#")
  )

  def commentSyntaxFor(mimeType: MimeType): Option[CommentSyntax] = CommentSyntaxesByMimeType.get(mimeType)

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
                    Pull.output1(Snippet(snippetName, sourceFilePath, (snippetStartLineNo + 1) to (snippetEndLineNo - 1))) >> go(remainingLineAndNoStream, sourceFilePath, commentSyntax, none[(String, Long)])

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

      override def includeSnippets(sourceFilePath: Path, snippetRefs: List[SnippetRef]): Stream[F, String] = {
        fileAlgebra
          .readFile(sourceFilePath)
          .zipWithIndex
          .map({ case (line, lineNo) =>
            (line, snippetRefs
              .find({ snippetRef =>
                snippetRef.lineNo == lineNo && sourceFilePath == snippetRef.sourceFilePath
              }))
          })
          .flatMap({
            case (_, Some(snippetRef)) =>
              readSnippet(snippetRef.snippet)

            case (line, None) =>
              Stream.emit(line)
          })
      }

      def parseSnippetRefs(sourceFilePath: Path, snippets: List[Snippet]): Stream[F, SnippetRef] = {
        (for {
          mimeType      <- OptionT(Stream.eval(fileAlgebra.mimeType(sourceFilePath)))
          commentSyntax <- OptionT(Stream.emit(commentSyntaxFor(mimeType)).covary[F])
          linePattern    = s"^${Pattern.quote(commentSyntax.begin)}<<< ([a-z0-9-]+) >>>".r("snippetName")
          snippetsByName = snippets
            .map({ snippet =>
              (snippet.name, snippet)
            })
            .toMap

          snippetRef    <- OptionT(fileAlgebra
            .readFile(sourceFilePath)
            .zipWithIndex
            .map({ case (line, lineNo) =>
              for {
                lineMatch     <- linePattern.findFirstMatchIn(line)
                snippetName = lineMatch.group("snippetName")
                snippet       <- snippetsByName.get(snippetName)
              } yield SnippetRef(snippet, sourceFilePath, lineNo)
            }))
        } yield snippetRef)
          .value
          .unNone
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

  def parseSnippetRefs(sourceFilePath: Path, snippets: List[Snippet]): Stream[F, SnippetRef]

  def includeSnippets(sourceFilePath: Path, snippetRefs: List[SnippetRef]): Stream[F, String]

  def readSnippet(snippet: Snippet): Stream[F, String]

}