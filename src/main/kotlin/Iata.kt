import arrow.core.Either
import io.github.mivek.provider.airport.AirportProvider
import java.util.HashMap
import java.util.ServiceLoader

class Iata {

    val cache: MutableMap<String, String> = HashMap()
    val airportProvider = ServiceLoader.load(AirportProvider::class.java).iterator().next()

    fun getIcao(iata: String): Either<String, String> {
        return when {
            iata.length == 3 -> {
                if (cache.containsKey(iata)) {
                    log.info("get $iata from cache. size ${cache.size}")
                    return Either.Right(cache.getValue(iata))
                }

                val icao = getIcaoFromInternet(iata)
                if (icao != null) {
                    cache[iata] = icao
                    log.info("add ${iata}:${icao} to cache. size ${cache.size}")
                    return Either.Right(icao)
                } else {
                    return Either.Left("ICAO not found for IATA: $iata")
                }
            }
            iata.length == 4 -> Either.Right(iata)
            else -> return Either.Left("No IATA or ICAO found: $iata")
        }
    }

    fun getIcaoFromInternet(iata: String): String?{
        return airportProvider.airports
            .filter { it.value.iata.lowercase() == iata }
            .map { it.value.icao }
            .firstOrNull()
    }

}