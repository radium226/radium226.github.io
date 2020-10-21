package blog
package file

import java.nio.file.{Files, Path}
import java.util.stream.Collectors

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import fs2.{Pipe, Stream, text}
import fs2.io.file

import scala.jdk.CollectionConverters._
import cats.implicits._
import cats.effect.implicits._

case class MimeType(`type`: String, subtype: String)

object MimeType {

  def parse(mimeTypeAsString: String): Option[MimeType] = {
    val mimeTypeSegments = mimeTypeAsString.split("/")
    if (mimeTypeSegments.length == 2) {
       MimeType(mimeTypeSegments(0), mimeTypeSegments(1)).some
    } else {
      none[MimeType]
    }
  }

}


trait FileAlgebra[F[_]] {

  def listFiles(folderPath: Path): Stream[F, Path]

  def mimeType(filePath: Path): F[Option[MimeType]]

  def readFile(filePath: Path): Stream[F, String]

  def writeFile(filePath: Path): Pipe[F, String, Unit]

}

object FileAlgebra {

  def resource[F[_]](implicit F: Sync[F], contextShift: ContextShift[F]): Resource[F, FileAlgebra[F]] = {
    Blocker[F]
      .map({ blocker =>
        new FileAlgebra[F] {

          override def listFiles(folderPath: Path): Stream[F, Path] = {
            Stream
              .evals(F.delay(Files.walk(folderPath).collect(Collectors.toList[Path]()).asScala.toList))
              .evalFilter(fileOrFolderPath => F.delay(!Files.isDirectory(fileOrFolderPath)))
              /*.map({ filePath =>
                folderPath.relativize(filePath)
              })*/
          }

          override def mimeType(filePath: Path): F[Option[MimeType]] = {
            blocker
              .delay(Files.probeContentType(filePath))
              .map({ mimeTypeAsString =>
                MimeType.parse(mimeTypeAsString)
              })
              .recover({ _ =>
                none[MimeType]
              })
          }

          override def readFile(filePath: Path): Stream[F, String] = {
            file
              .readAll[F](filePath, blocker, 1024)
              .through(text.utf8Decode[F])
              .through(text.lines[F])
          }

          override def writeFile(filePath: Path): Pipe[F, String, Unit] = { lines =>
            lines
              .map({ line => s"${line}\n"})
              .through(text.utf8Encode)
              .through(file.writeAll(filePath, blocker))
          }
        }
      })

  }

}