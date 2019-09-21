package io.sherpair.w4s.geo.engine

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.sherpair.w4s.domain.{epochAsLong, toIsoDate, Meta}
import io.sherpair.w4s.domain.Meta.{id, indexName}
import io.sherpair.w4s.engine.{Engine, EngineIndex}

private[engine] class EngineOpsMeta[F[_]: Sync](implicit E: Engine[F], L: Logger[F]) {

  private[engine] val engineMeta: EngineIndex[F, Meta] = E.engineIndex[Meta](indexName, _ => id)

  def count: F[Long] = engineMeta.count

  def createIndexIfNotExists: F[Meta] =
    E.indexExists(indexName).ifM(firstMetaLoad, initialiseMeta)

  def loadMeta: F[Option[Meta]] = engineMeta.getById(id)

  def upsert(meta: Meta): F[String] = engineMeta.upsert(meta)

  private def extractMetaAndLogLastEngineUpdate(maybeMeta: Option[Meta]): F[Meta] = {
    require(maybeMeta.isDefined, Meta.requirement)  // Fatal Error!!
    Sync[F].delay(maybeMeta.get)
  }

  private[engine] def firstMetaLoad: F[Meta] =
    for {
      _ <- logIndexStatus("already exists")
      maybeMeta <- loadMeta
      meta <- extractMetaAndLogLastEngineUpdate(maybeMeta)
      _ <- L.info(s"Last Engine update at(${toIsoDate(meta.lastEngineUpdate)})")
    } yield meta

  private[engine] def initialiseMeta: F[Meta] =
    for {
      _ <- E.createIndex(indexName)
      _ <- logIndexStatus("was created")
      meta = Meta(epochAsLong)
      _ <- engineMeta.upsert(meta)
    } yield meta

  private def logIndexStatus(status: String): F[Unit] =
    L.info(s"Index(${indexName}) ${status}")
}

object EngineOpsMeta {
  def apply[F[_]: Logger: Sync](implicit E: Engine[F]): EngineOpsMeta[F] = new EngineOpsMeta[F]
}
