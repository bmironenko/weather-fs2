package demo

import cats.effect.*
import cats.effect.kernel.Temporal
import cats.effect.std.Console
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.implicits.javasql.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.transactor.Transactor
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerName
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

import MeasUnits.RelativeHumidityUnit
import MeasUnits.TemperatureUnit
import OpenWeatherMap.WeatherData

object Main extends IOApp {

  private val defaultZoneId = ZoneId.of("America/New_York")
  private val sampleRate = 2.minutes
  private val owmApiKey = ""
  private val config = List(
    ("Baltimore, MD", 39.289444, -76.615278),
    ("Frederick, MD", 39.431111, -77.397222)
  )

  given logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/weather",
    "",
    "",
    None
  )

  given instantPut: Put[Instant] = Put[Timestamp].contramap { instant =>
    Option(instant).map(inst => new Timestamp(inst.toEpochMilli)).orNull
  }

  /** Set up a weather stream.
    *
    * @param label
    *   stream label
    * @param latitude
    *   geographic latitude
    * @param longitude
    *   geographic longitude
    * @return
    *   `Stream` emitting label -> measurement tuples
    */
  private def createWeatherStream(
      label: String,
      latitude: Double,
      longitude: Double
  ): Stream[IO, (String, Sample[OpenWeatherMap.Units])] = {
    given LoggerName = LoggerName("OpenWeatherMap")
    OpenWeatherMap
      .streamGeneric[IO](
        state = OpenWeatherMap(
          apiId = owmApiKey,
          latitude = latitude,
          longitude = longitude
        ),
        sampleRate = sampleRate
      )
      .map(s => (label, s))
  }

  /** Write samples to persistent storage.
    *
    * @param label
    *   label/tag
    * @param measurements
    *   measurements to write
    * @return
    *   persistence `IO`
    */
  private def writeSamples(samples: (String, Sample[?])*): IO[Int] = {
    if (samples.nonEmpty) {
      val valuesFrag = samples
        .map((label, s) =>
          fr"(${s.time}, $label, ${s.tagged.name}, ${s.tagged.meas.unit.toString}, ${s.tagged.meas.value})"
        )
        .reduce(_ ++ fr", " ++ _)
      (sql"insert into meas (time, label, name, unit, value) values " ++ valuesFrag ++
        fr" on conflict do nothing").update.run.transact(xa)
    } else {
      IO.pure(0)
    }
  }

  /** Log a single sample.
    *
    * @param label
    *   sample label
    * @param meas
    *   sample measurement
    * @return
    *   `IO` with the opaque result of the log operation
    */
  private def logSample(
      label: String,
      sample: Sample[OpenWeatherMap.Units]
  ): IO[Unit] = {
    logging
      .getLogger(LoggerName(label))
      .info(
        s"${sample.tagged.name}: ${sample.tagged.meas.value} ${sample.tagged.meas.unit}"
      )
  }

  /** Run the application.
    *
    * @param args
    *   program arguments
    * @return
    *   `IO` containing the exit code
    */
  def run(args: List[String]): IO[ExitCode] = {
    config
      .map(createWeatherStream) // for each of the locations, construct a stream
      .fold(Stream.empty)(_ merge _) // merge the streams together
      .evalTap(logSample) // log each sample
      .groupWithin(100, 30.seconds) // batch samples for writing
      .flatMap { chunk =>
        // write each batch as a single operation
        // this can also be done as a pipe along with the grouping
        Stream.eval(writeSamples(chunk.toList*))
      }
      .compile
      .drain
      .map(_ => ExitCode.Success)
  }
}
