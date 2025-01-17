package io.sherpair.w4s.loader.engine

import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import io.sherpair.w4s.domain.{now, unit, BulkErrors, Country, Localities, Locality, Logger, Meta}
import io.sherpair.w4s.domain.Country.countryUnderLoadOrUpdate
import io.sherpair.w4s.domain.Meta.id
import io.sherpair.w4s.engine.Engine
import io.sherpair.w4s.loader.domain.LoaderAccums

class EngineOps[F[_]: Sync] (
  clusterName: String,
  engineOpsCountries: EngineOpsCountries[F],
  engineOpsLocality: EngineOpsLocality[F],
  engineOpsMeta: EngineOpsMeta[F]
)(implicit E: Engine[F], L: Logger[F]) {

  val init: F[Unit] =
    E.healthCheck.flatMap[Unit](res => logEngineStatus(res._1, res._2))

  val close: F[Unit] =
    L.info(s"Closing connection with ES cluster(${clusterName})") *> E.close

  def countLocalities(country: Country): F[Long] = engineOpsLocality.count(country)

  def findCountry(country: Country): F[Option[Country]] = engineOpsCountries.find(country)

  def prepareEngineFor(country: Country): F[Unit] =
    E.indexExists(country.code)
      .ifM(deleteIndexFor(country), unit.pure[F]) *> E.createIndex(country.code, Locality.mapping.some)

  def saveAllLocalities(country: Country, localities: Localities): F[BulkErrors] =
    engineOpsLocality.saveAll(country, localities)

  def updateEngineFor(country: Country, loaderAccums: LoaderAccums): F[Unit] =
    E.refreshIndex(country.code).handleErrorWith(
      // Once country's localities are saved, do not stop the "notify clients" process if refreshing fail
      L.error(_)(s"While refreshing localities for ${country}") *> false.pure[F]
    ) *>
      notifyClients(country.copy(localities = loaderAccums.localities), now)
        .handleErrorWith(L.error(_)(s"Final client notification failure for ${country}")) *>
      logEngineUpdateValues(loaderAccums, country)

  def upsertCountry(country: Country): F[String] = engineOpsCountries.upsert(country)

  def upsertMeta(meta: Meta): F[String] = engineOpsMeta.upsert(meta)

  private def deleteIndexFor(country: Country): F[Unit] = {
    engineOpsLocality.delete(country)

    // Once the index is deleted, do not stop the "load country" process if notifyClients fails
    notifyClients(country, countryUnderLoadOrUpdate)
      .handleErrorWith(L.error(_)(s"Client notification failure after Index(${country}) delete"))
  }

  private def logEngineStatus(attempts: Int, status: String): F[Unit] = {
    val color = status.toLowerCase match {
      case "red" => "**** RED!! ****"
      case "yellow" => "** YELLOW **"
      case _ => status
    }

    val healthCheck = s"Health check successful after ${attempts} ${if (attempts == 1) "attempt" else "attempts"}"
    L.info(s"\n${healthCheck}\nStatus of ES cluster(${clusterName}) is ${color}")
  }

  private def logEngineUpdateValues(loaderAccums: LoaderAccums, country: Country): F[Unit] = {
    val s = s" errors while streaming ${country} to engine"
    L.info(s"${loaderAccums.localities} localities saved for ${country}") >>
      Sync[F].delay(loaderAccums.bulkErrors == 0).ifM(L.info(s"No${s}"), L.error(s"${loaderAccums.bulkErrors}${s}"))
  }

  private def notifyClients(country: Country, updated: Long): F[Unit] =
    for {
      _ <- engineOpsCountries.upsert(country.copy(updated = updated))
      _ <- engineOpsMeta.upsert(Meta(now))
    }
    yield unit
}

object EngineOps {

  def apply[F[_]: Sync](clusterName: String)(implicit E: Engine[F], L: Logger[F]): F[EngineOps[F]] =
    for {
      countryIndex <- E.engineIndex[Country](Country.indexName, _.code)
      engineOpsCountries <- EngineOpsCountries[F](countryIndex)

      localityIndex <- E.localityIndex
      engineOpsLocality <- EngineOpsLocality[F](localityIndex)

      metaIndex <- E.engineIndex[Meta](Meta.indexName, _ => id)
      engineOpsMeta <- EngineOpsMeta[F](metaIndex)
    }
      yield new EngineOps[F](clusterName, engineOpsCountries, engineOpsLocality, engineOpsMeta)
}
