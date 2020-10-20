package blog
package source

import java.nio.file.Path

import blog.file.FileAlgebra
import cats.effect.Sync
import fs2.Pipe

import cats.implicits._
import cats.effect.implicits._


trait SourceAlgebra[F[_]] {

  def keepSourceFile: Pipe[F, Path, Path]

}

object SourceAlgebra {

  def apply[F[_]: SourceAlgebra]: SourceAlgebra[F] = implicitly

}

trait SourceAlgebraInstances {

  implicit def syncSourceAlgebra[F[_]: Sync: FileAlgebra](implicit fileAlgebra: FileAlgebra[F]): SourceAlgebra[F] = new SourceAlgebra[F] {

    override def keepSourceFile: Pipe[F, Path, Path] = { filePath =>
      filePath
        .evalFilter({ filePath =>
          FileAlgebra[F]
            .mimeType(filePath)
            .map({ mimeType =>

            })

        })

    }

  }

}
