import io.github.mivek.provider.airport.AirportProvider
import java.util.*

sealed class AirportCode(val code: String) {
    override fun toString(): String = code
}

class Iata(code: String) : AirportCode(code) {

    init {
        require(isValid(code))
    }

    companion object {
        fun isValid(value: String): Boolean {
            return value.length == 3
        }
    }

}

class Icao(code: String) : AirportCode(code) {

    init {
        require(isValid(code))
    }

    companion object {
        fun isValid(value: String): Boolean {
            return value.length == 4
        }
    }

}

private val airportProvider = ServiceLoader.load(AirportProvider::class.java).iterator().next()
fun Iata.asIcao(): Icao? {
    return airportProvider.airports
        .filter { it.value.iata.lowercase() == this.code }
        .map { it.value.icao }
        .map { Icao(it) }
        .firstOrNull()
}