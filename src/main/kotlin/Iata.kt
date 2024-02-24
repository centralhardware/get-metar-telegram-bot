import arrow.core.Either
import io.github.mivek.provider.airport.AirportProvider
import java.util.HashMap
import java.util.ServiceLoader

class Iata {

    val airportProvider = ServiceLoader.load(AirportProvider::class.java).iterator().next()

    suspend fun getIcao(code: String): Either<String, String> = when {
        isIataCode(code) -> getIcaoForIataCode(code)
        isIcaoCode(code) -> Either.Right(code)
        else -> Either.Left("No IATA or ICAO found: $code")
    }

    private fun isIataCode(code: String) = code.length == 3

    private fun isIcaoCode(code: String) = code.length == 4

    private suspend fun getIcaoForIataCode(iata: String): Either<String, String> {
        val redisKey = "$iata@iata"
        if (redisClient.exists(redisKey) == 1L) {
            log.info("get $iata from redis")
            return Either.Right(redisClient.get(redisKey)!!)
        }
        return getIcaoFromInternet(iata)?.let { icao ->
            redisClient.set(redisKey, icao)
            log.info("add $iata:$icao to cache")
            Either.Right(icao)
        } ?: Either.Left("ICAO not found for IATA: $iata")
    }

    private fun getIcaoFromInternet(iata: String): String? {
        return airportProvider.airports
            .filter { it.value.iata.lowercase() == iata }
            .map { it.value.icao }
            .firstOrNull()
    }
}