package blog
package source

import java.nio.file.Path

import blog.file.FileAlgebra
import cats.Applicative
import cats.effect.{Resource, Sync}
import fs2.Pipe
import cats.implicits._
import cats.effect.implicits._


trait SourceAlgebra[F[_]] {

  def sourceFilesOnly: Pipe[F, Path, Path]

}

object SourceAlgebra {

  def resource[F[_]: Applicative](fileAlgebra: FileAlgebra[F]): Resource[F, SourceAlgebra[F]] = Resource.pure[F, SourceAlgebra[F]](new SourceAlgebra[F] {

    override def sourceFilesOnly: Pipe[F, Path, Path] = { filePath => filePath }

  })

}