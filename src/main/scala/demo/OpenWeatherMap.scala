package demo

import cats.*
import cats.effect.*
import cats.implicits.*
import demo.MeasUnits.AngularMeasureUnit
import demo.MeasUnits.AngularMeasureUnits.DegreesOfArc
import demo.MeasUnits.Dimensionless
import demo.MeasUnits.Dimensionless.Percent
import demo.MeasUnits.LengthUnit
import demo.MeasUnits.LengthUnits.Meter
import demo.MeasUnits.LengthUnits.Millimeter
import demo.MeasUnits.RelativeHumidityUnit
import demo.MeasUnits.RelativeHumidityUnits.PercentRH
import demo.MeasUnits.SpeedUnit
import demo.MeasUnits.SpeedUnits.MilesPerHour
import demo.MeasUnits.TemperatureUnit
import demo.MeasUnits.TemperatureUnits.Fahrenheit
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Network
import io.circe.*
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.Client
import org.http4s.ember.client.*
import org.http4s.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerName
import org.typelevel.log4cats.syntax.*

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** OpenWeatherMap client configuration.
  *
  * @param apiId
  *   OpenWeatherMap API ID
  * @param latitude
  *   target location latitude
  * @param longitude
  *   target location longitude
  */
case class OpenWeatherMap(apiId: String, latitude: Double, longitude: Double)

object OpenWeatherMap {

  private val baseUri: Uri =
    uri"https://api.openweathermap.org/data/2.5/weather"

  /** Type of measurements units supported by this sensor.
    */
  type Units =
    TemperatureUnit | RelativeHumidityUnit | LengthUnit | SpeedUnit |
      AngularMeasureUnit | Dimensionless

  /** Weather data container.
    *
    * @param temperature
    *   current temperature (degrees Fahrenheit)
    * @param humidity
    *   current relative humidity (percent RH)
    * @param visibility
    *   current visibility (meters)
    * @param cloudCover
    *   current cloud cover (percent)
    * @param windSpeed
    *   current wind speed (miles/hour)
    * @param windDirection
    *   current wind direction (degrees or arc w.r.t. north)
    * @param trailingHourRain
    *   rain precipitation over the preceding hour (millimeters)
    * @param trailingHourSnow
    *   snow precipitation over the preceding hour (millimeters)
    */
  case class WeatherData(
      timestamp: Instant,
      temperature: Meas[Fahrenheit],
      humidity: Meas[PercentRH],
      visibility: Option[Meas[Meter]],
      cloudCover: Meas[Percent],
      windSpeed: Meas[MilesPerHour],
      windDirection: Meas[DegreesOfArc],
      trailingHourRain: Option[Meas[Millimeter]],
      trailingHourSnow: Option[Meas[Millimeter]]
  )

  object WeatherData {
    given Decoder[WeatherData] = Decoder { c =>
      (
        c.downField("dt").as[Long].map(Instant.ofEpochSecond),
        c.downField("main")
          .downField("temp")
          .as[Double]
          .map(Meas(_, Fahrenheit)),
        c.downField("main")
          .downField("humidity")
          .as[Double]
          .map(Meas(_, PercentRH)),
        c.downField("visibility").as[Option[Double]].map(_.map(Meas(_, Meter))),
        c.downField("clouds").downField("all").as[Double].map(Meas(_, Percent)),
        c.downField("wind")
          .downField("speed")
          .as[Double]
          .map(Meas(_, MilesPerHour)),
        c.downField("wind")
          .downField("deg")
          .as[Double]
          .map(Meas(_, DegreesOfArc)),
        c.downField("rain")
          .downField("1h")
          .as[Option[Double]]
          .map(_.map(Meas(_, Millimeter))),
        c.downField("snow")
          .downField("1h")
          .as[Option[Double]]
          .map(_.map(Meas(_, Millimeter)))
      ).mapN(WeatherData.apply)
    }
  }

  /** Sample weather data using a one-off client.
    *
    * @param state
    *   OpenWeatherMap config
    * @return
    *   sampled weather
    */
  def sampleOne[F[_]: Async: Network: LoggerFactory](
      state: OpenWeatherMap
  ): F[WeatherData] = {
    EmberClientBuilder.default[F].build.use(sample(state))
  }

  /** Create a stream of weather samples.
    *
    * @param state
    *   OpenWeatherMap configuration
    * @param sampleRate
    *   sample rate
    * @return
    *   Stream of weather samples in the specified evaluation context
    */
  def stream[F[_]: Async: Network: Temporal: LoggerFactory](
      state: OpenWeatherMap,
      sampleRate: FiniteDuration
  )(using LoggerName): Stream[F, WeatherData] = {
    val logger = LoggerFactory[F].getLogger
    Stream
      .resource(EmberClientBuilder.default[F].build)
      .flatMap { client =>
        val sampleStream =
          Stream
            .evalSeq(
              sample(state)(client)
                .map(_ :: Nil)
                .recoverWith { e =>
                  logger
                    .error(e)("Error while sampling")
                    .map(_ => Nil)
                }
            )

        sampleStream ++
          (Stream.awakeEvery(sampleRate) >> sampleStream)
      }
      .filterWithPrevious {
        _.timestamp.toEpochMilli / 1000 != _.timestamp.toEpochMilli / 1000
      }
  }

  /** Create a stream of weather samples.
    *
    * @param state
    *   OpenWeatherMap configuration
    * @param sampleRate
    *   sample rate
    * @return
    *   Stream of weather samples in the specified evaluation context
    */
  def streamGeneric[F[_]: Async: Network: Temporal: LoggerFactory](
      state: OpenWeatherMap,
      sampleRate: FiniteDuration
  )(using LoggerName): Stream[F, Sample[Units]] = {
    stream(state, sampleRate)
      .flatMap { data =>
        Stream(
          Sample(data.timestamp, TaggedMeas("temperature", data.temperature)),
          Sample(data.timestamp, TaggedMeas("humidity", data.humidity)),
          // Sample(data.timestamp, TaggedMeas("visibility", data.visibility)),
          Sample(data.timestamp, TaggedMeas("cloud_cover", data.cloudCover)),
          Sample(data.timestamp, TaggedMeas("wind_speed", data.windSpeed)),
          Sample(
            data.timestamp,
            TaggedMeas("wind_direction", data.windDirection)
          )
        ) ++
          Stream(
            Seq(
              data.visibility.map(m =>
                Sample(data.timestamp, TaggedMeas("visibility", m))
              ),
              data.trailingHourRain.map(m =>
                Sample(data.timestamp, TaggedMeas("rain_1h", m))
              ),
              data.trailingHourSnow.map(m =>
                Sample(data.timestamp, TaggedMeas("snow_1h", m))
              )
            ).flatten*
          )
      }
  }

  /** Sample weather data using the specified client.
    *
    * @param state
    *   OpenWeatherMap config
    * @param client
    *   HTTP client
    * @return
    *   sampled weather
    */
  private def sample[F[_]: Async](
      state: OpenWeatherMap
  )(client: Client[F]): F[WeatherData] = {
    val requestUri = baseUri
      .withQueryParam("appid", state.apiId)
      .withQueryParam("lat", state.latitude)
      .withQueryParam("lon", state.longitude)
      .withQueryParam("units", "imperial")
    client.expect[WeatherData](requestUri)
  }

}
