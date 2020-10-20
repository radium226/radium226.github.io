package blog
package file

import java.nio.file.Path

import cats.effect.{Blocker, ContextShift, Sync}
import fs2.Stream
import fs2.io.file
import fs2.text


trait FileAlgebra[F[_]] {

  type MimeType = String

  def listFiles(folderPath: Path): Stream[F, Path]

  def mimeType(filePath: Path): F[MimeType]

  def fileContent(filePath: Path)(blocker: Blocker): Stream[F, String]

}

object FileAlgebra {

  def apply[F[_]: FileAlgebra]: FileAlgebra[F] = implicitly

}

trait FileAlgebraInstances {

  implicit def syncFileAlgebra[F[_]: Sync: ContextShift]: FileAlgebra[F] = new FileAlgebra[F] {

    override def listFiles(folderPath: Path): Stream[F, Path] = ???

    override def mimeType(filePath: Path): F[MimeType] = F.pure("text/caca")

    override def fileContent(filePath: Path)(blocker: Blocker): Stream[F, String] = {
      file
        .readAll[F](filePath, blocker, 1024)
        .through(text.utf8Decode[F])
        .through(text.lines[F])
    }
  }

}
