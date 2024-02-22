import arrow.core.Either
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.google.common.io.Resources
import java.util.HashMap

class Iata {

    val cache: MutableMap<String, String> = HashMap()

    fun getIcao(iata: String): Either<String, String> {
        return when {
            iata.length == 3 -> {
                if (cache.containsKey(iata)) return Either.Right(cache.getValue(iata))

                val icao = parseCsv()[iata]
                if (icao != null) {
                    cache[iata] = icao
                    println("add ${iata}:${icao} to cache. size ${cache.size}")
                    return Either.Right(icao)
                } else {
                    return Either.Left("ICAO not found for IATA: $iata")
                }
            }
            iata.length == 4 -> Either.Left(iata)
            else -> return Either.Left("No IATA or ICAO found: $iata")
        }
    }

    fun parseCsv(): Map<String, String>{
        val csv = Resources.getResource("iata-icao.csv").readText()
        val raws = csvReader().readAllWithHeader(csv)
        return raws.map { row -> row["iata"]!!.lowercase() to row["icao"]!!.lowercase() }.toMap()
    }

}