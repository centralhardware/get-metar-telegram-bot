import arrow.core.Either
import io.github.mivek.provider.airport.AirportProvider
import java.util.HashMap
import java.util.ServiceLoader

class Iata {

    val airportProvider = ServiceLoader.load(AirportProvider::class.java).iterator().next()

    suspend fun getIcao(iata: String): Either<String, String> {
        return when {
            iata.length == 3 -> {
                if (redisClient.exists(iata.redisKey()) == 1L) {
                    log.info("get $iata from redis")
                    return Either.Right(redisClient.get(iata.redisKey())!!)
                }

                val icao = getIcaoFromInternet(iata)
                if (icao != null) {
                    redisClient.set(iata.redisKey(), icao)
                    log.info("add ${iata}:${icao} to cache")
                    return Either.Right(icao)
                } else {
                    return Either.Left("ICAO not found for IATA: $iata")
                }
            }
            iata.length == 4 -> Either.Right(iata)
            else -> return Either.Left("No IATA or ICAO found: $iata")
        }
    }

    fun String.redisKey(): String{
        return "$this@iata"
    }

    fun getIcaoFromInternet(iata: String): String?{
        return airportProvider.airports
            .filter { it.value.iata.lowercase() == iata }
            .map { it.value.icao }
            .firstOrNull()
    }

}