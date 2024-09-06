package demo

import java.time.Instant

/** A measurement.
  *
  * @param value
  *   measurement value
  * @param unit
  *   measurement unit
  * @tparam U
  *   measurement unit type
  */
case class Meas[+U <: MeasUnit](value: Double, unit: U)

/** Represents an annotated measurement.
  *
  * While a measurement, on its own, has information about its unit and value,
  * the semantics are defined by the sensor by assigning a text label to it.
  *
  * @param name
  *   measurement name
  * @param meas
  *   measurement
  * @tparam U
  *   measurement unit
  */
case class TaggedMeas[U <: MeasUnit](name: String, meas: Meas[U])

/** Abstract measurement unit.
  */
trait MeasUnit

/** Define usable measurement units.
  */
object MeasUnits {
  trait Dimensionless extends MeasUnit
  object Dimensionless {
    sealed trait Percent extends Dimensionless
    case object Percent extends Percent
  }

  trait LengthUnit extends MeasUnit
  object LengthUnits {
    sealed trait Meter extends LengthUnit
    case object Meter extends Meter
    sealed trait Millimeter extends LengthUnit
    case object Millimeter extends Millimeter
  }

  trait SpeedUnit extends MeasUnit
  object SpeedUnits {
    sealed trait MilesPerHour extends SpeedUnit
    case object MilesPerHour extends MilesPerHour
  }

  trait AngularMeasureUnit extends MeasUnit
  object AngularMeasureUnits {
    sealed trait DegreesOfArc extends AngularMeasureUnit
    case object DegreesOfArc extends DegreesOfArc
  }

  /** Pressure units.
    */
  trait PressureUnit extends MeasUnit

  object PressureUnits {
    sealed trait Pascal extends PressureUnit

    case object Pascal extends Pascal

    /** Convert pressure value from one unit to Pascals.
      *
      * @tparam P
      *   source measurement unit type
      */
    abstract class ToPascal[P <: PressureUnit] extends (Meas[P] => Meas[Pascal])
  }

  /** Temperature units.
    */
  trait TemperatureUnit extends MeasUnit

  object TemperatureUnits {
    sealed trait Kelvin extends TemperatureUnit

    case object Kelvin extends Kelvin

    sealed trait Celsius extends TemperatureUnit

    case object Celsius extends Celsius

    sealed trait Fahrenheit extends TemperatureUnit

    case object Fahrenheit extends Fahrenheit

    /** Convert temperature value from one unit to Kelvin.
      *
      * @tparam T
      *   source measurement unit type
      */
    abstract class ToKelvin[T <: TemperatureUnit]
        extends (Meas[T] => Meas[Kelvin])
  }

  /** Relative humidity units.
    */
  trait RelativeHumidityUnit extends MeasUnit

  object RelativeHumidityUnits {
    sealed trait PercentRH
        extends RelativeHumidityUnit
        with Dimensionless.Percent

    case object PercentRH extends PercentRH

    /** Convert relative humidity value from one unit to percent RH.
      *
      * @tparam RH
      *   source measurement unit type
      */
    abstract class ToPercentRH[RH <: RelativeHumidityUnit]
        extends (Meas[RH] => Meas[PercentRH])
  }

}

/** Represents a sample - an annotated measurement taken at a particular time
  *
  * @param time
  *   sample timestamp
  * @param tagged
  *   tagged measurement
  */
case class Sample[U <: MeasUnit](time: Instant, tagged: TaggedMeas[U])
