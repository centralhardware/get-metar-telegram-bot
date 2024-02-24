import io.github.mivek.model.*
import io.github.mivek.service.MetarService
import io.github.mivek.service.TAFService
import kotlin.math.roundToInt

class Formatter {

    val metarService = MetarService.getInstance()
    val tafService = TAFService.getInstance()

    fun getMetar(icao: String): String {
        log.info("get metar for $icao")
        val metar = metarService.retrieveFromAirport(icao)
        return getCommon(metar)
    }

    fun getTaf(icao: String): String {
        log.info("get taf for $icao")
        val taf = tafService.retrieveFromAirport(icao)
        return getCommon(taf)
    }

    fun getCommon(container: AbstractWeatherCode): String {
        val specific = when (container) {
            is Metar -> """
                ${container.day} ${container.time}
                temp: ${container.temperature}, dew point: ${container.dewPoint}, ${if (container.isNosig == true) "nosig" else ""}
                """.trimIndent().trimMargin()

            is TAF -> container.validity.let {
                "${it.startDay}d ${it.startHour}h - ${it.endDay}d ${it.endHour}"
            }

            else -> throw IllegalArgumentException()
        }

        val sb = StringBuilder()
        sb.append(getAirport(container.airport))
        sb.append("\n").append(specific)
        getWind(container.wind).ifNotEmpty { sb.append("\n").append(it) }
        sb.append("\n").append(getVisibility(container.visibility))
        getVerticalVisibility(container.verticalVisibility).ifNotEmpty { sb.append("\n").append(it) }
        getWeatherConditions(container.weatherConditions).ifNotEmpty { sb.append("\n").append(it) }
        getClouds(container.clouds).ifNotEmpty { sb.append("\n").append(it) }
        getRemark(container.remark).ifNotEmpty { sb.append("\n").append(it) }
        sb.append("\n\n").append(container.message)
        return sb.toString().replace("null", "")
    }

    fun convertSpeed(speed: Int, unit: String): Int =
        when (unit.lowercase()) {
            "kt" -> (speed * 1.852).roundToInt()
            "mps" -> (speed * 3.6).roundToInt()
            else -> throw IllegalArgumentException()
        }

    fun getAirport(airport: Airport): String =
        "${airport.name} ${airport.icao}(${airport.iata}) ${airport.altitude}"

    fun getWind(wind: Wind?): String =
        if (wind == null) "" else {
            "wind: ${convertSpeed(wind.speed, wind.unit)} km/h ${wind.directionDegrees}(${wind.direction})"
        }

    fun getVisibility(visibility: Visibility): String =
        "visibility: ${visibility.mainVisibility}"

    fun getVerticalVisibility(visibility: Int?): String =
        if (visibility == null) "" else {
            "vertical visibility: $visibility"
        }

    private fun getRemark(remark: String?): String =
        if (remark.isNullOrBlank()) "" else {
            "remark: $remark"
        }

    private fun getWeatherConditions(weatherCondition: List<WeatherCondition>): String =
        weatherCondition.map {
            "${it.intensity?.let { it.name.lowercase() }} ${it.descriptive} ${it.phenomenons.joinToString(",")}"
        }.joinToString(",").trim()

    private fun getClouds(clouds: List<Cloud>): String =
        clouds
            .sortedBy { it.height }
            .map {
                "${it.type} ${it.quantity} ${it.height}"
            }.joinToString(",").trim()

}