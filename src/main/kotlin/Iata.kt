import arrow.core.Either
import io.github.mivek.provider.airport.AirportProvider
import java.util.ServiceLoader

class Iata {

    private val airportProvider = ServiceLoader.load(AirportProvider::class.java).iterator().next()

    suspend fun icao(code: String): Either<String, String> = when {
        isIataCode(code) -> toIcao(code)
        isIcaoCode(code) -> Either.Right(code)
        else -> Either.Left("No IATA or ICAO found: $code")
    }

    private fun isIataCode(code: String) = code.length == 3

    private fun isIcaoCode(code: String) = code.length == 4

    private suspend fun toIcao(iata: String): Either<String, String> {
        if (redisClient.hexists("iata2icao", iata) == 1L) {
            log.info("get $iata from redis")
            return Either.Right(redisClient.hget("iata2icao", iata)!!)
        }
        return loadIcao(iata)?.let { icao ->
            redisClient.hset("iata2icao", Pair(iata, icao))
            log.info("add $iata:$icao to cache")
            Either.Right(icao)
        } ?: Either.Left("ICAO not found for IATA: $iata")
    }

    private fun loadIcao(iata: String): String? {
        return airportProvider.airports
            .filter { it.value.iata.lowercase() == iata }
            .map { it.value.icao }
            .firstOrNull()
    }
}