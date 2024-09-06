# weather-fs2

This is a _very basic_ technology demo for libraries in the Typelevel ecosystem, primarily [fs2](https://fs2.io/) and [doobie](https://typelevel.org/doobie/). It uses the [cats-effect](https://typelevel.org/cats-effect/) runtime.

The utility uses the [OpenWeatherMap API](https://openweathermap.org/current) to stream weather data for configured locations, perform some transformations, and record it in a Postgres database.

There is also a much more developed [ZIO-based version](https://github.com/bmironenko/weather-zio/) of this demo.

## Usage

### Database

By default, a `postgres` database is needed to run the demo. The schema can be found [here](src/main/resources/schema.sql). 

Alternatively, to simply log data to console, one could comment out the database stage of the stream in `Main.run()`:

```scala3 
// .flatMap { chunk =>
//   // write each batch as a single operation
//   // this can also be done as a pipe along with the grouping
//   Stream.eval(writeSamples(chunk.toList*))
// }
```

### Configuration

There is currently no configuration file. See `Main.scala` to set `owmApiKey` constant and to configure the database `Transactor`.

### Run 

You can compile code with `sbt compile`, run it with `sbt run`.
