package io.sherpair.w4s.loader.config

import io.sherpair.w4s.config.{Configuration, Engine, Http, Service}
import pureconfig.ConfigSource
// Needed.
import pureconfig.generic.auto._

case class LoaderConfig(
  countryDownloadUrl: String,
  engine: Engine,
  httpLoader: Http,
  maxEnqueuedCountries: Int,
  service: Service
) extends Configuration

object LoaderConfig {
  def apply(): LoaderConfig = ConfigSource.default.loadOrThrow[LoaderConfig]
}
