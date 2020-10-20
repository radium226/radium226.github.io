package blog
package file

import java.nio.file.{Files, Path}
import java.util.stream.Collectors

import cats.effect.{Blocker, ContextShift, Resource, Sync}
import fs2.Stream
import fs2.io.file
import fs2.text

import scala.jdk.CollectionConverters._

import cats.implicits._

trait FileAlgebra[F[_]] {

  type MimeType = String

  def listFiles(folderPath: Path): Stream[F, Path]

  def mimeType(filePath: Path): F[MimeType]

  def fileContent(filePath: Path): Stream[F, String]

}

object FileAlgebra {

  def resource[F[_]](implicit F: Sync[F], contextShift: ContextShift[F]): Resource[F, FileAlgebra[F]] = {
    Blocker[F]
      .map({ blocker =>
        new FileAlgebra[F] {

          override def listFiles(folderPath: Path): Stream[F, Path] = {
            Stream
              .evals(F.delay(Files.list(folderPath).collect(Collectors.toList[Path]()).asScala.toList))
              .evalFilter(fileOrFolderPath => F.delay(!Files.isDirectory(fileOrFolderPath)))
          }

          override def mimeType(filePath: Path): F[MimeType] = F.pure("text/caca")

          override def fileContent(filePath: Path): Stream[F, String] = {
            file
              .readAll[F](filePath, blocker, 1024)
              .through(text.utf8Decode[F])
              .through(text.lines[F])
          }
        }
      })

  }

}