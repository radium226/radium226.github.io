package blog
package source

import java.nio.file.Path

import blog.file.{FileAlgebra, MimeType}
import cats.Applicative
import cats.effect.{Resource, Sync}
import fs2.Pipe
import cats.implicits._
import cats.effect.implicits._


import sys.process._

trait SourceAlgebra[F[_]] {

  def sourceFilesOnly: Pipe[F, Path, Path]

}

object SourceAlgebra {

  def resource[F[_]: Sync](fileAlgebra: FileAlgebra[F]): Resource[F, SourceAlgebra[F]] = Resource.pure[F, SourceAlgebra[F]](new SourceAlgebra[F] {

    def includeTextFiles: Pipe[F, Path, Path] = { filePaths =>
      filePaths
        .evalMap({ filePath =>
          fileAlgebra
            .mimeType(filePath)
            .map({ mimeTypeOption =>
              System.err.println(s"${filePath} is ${mimeTypeOption}")
              mimeTypeOption.map({ mimeType =>
                (filePath, mimeType)
              })
            })
        })
        .unNone
        .collect({
          case (filePath, MimeType("text", _)) =>
            filePath

          case (filePath, MimeType("application", "sql")) =>
            filePath
        })
    }

    def excludeIgnoredFiles: Pipe[F, Path, Path] = { filePaths =>
      filePaths
        .evalMap({ filePath =>
          F.delay(Process(Seq("git", "check-ignore", "--no-index", "--quiet", s"${filePath.getFileName}"), filePath.getParent.toFile) !).map({ exitCode => (filePath, exitCode == 0) })
        })
        .collect({
          case (filePath, false) =>
            filePath
        })

    }

    override def sourceFilesOnly: Pipe[F, Path, Path] = { filePaths =>
      filePaths
        .through(includeTextFiles)
        .through(excludeIgnoredFiles)
    }

  })

}