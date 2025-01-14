package io.sherpair.w4s.geo.app

import cats.effect.{ConcurrentEffect, Resource}
import cats.syntax.applicative._
import io.sherpair.w4s.domain.Logger
import io.sherpair.w4s.engine.Engine
import io.sherpair.w4s.geo.cache.CacheRef
import io.sherpair.w4s.geo.config.GeoConfig
import io.sherpair.w4s.geo.engine.EngineOps
import org.http4s.HttpRoutes
import org.http4s.client.Client

object Routes {

  def apply[F[_]: ConcurrentEffect](
      cacheRef: CacheRef[F], client: Client[F], engineOps: EngineOps[F])(
      implicit C: GeoConfig, E: Engine[F], L: Logger[F]
  ): Resource[F, Seq[HttpRoutes[F]]] =
    Resource.liftF(
      Seq(
        new CountryApp[F](cacheRef, client).routes,
        new Monitoring[F].routes,
        new SuggestApp[F](cacheRef, engineOps).routes,
      ).pure[F]
    )
}
