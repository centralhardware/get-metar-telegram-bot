import arrow.core.Either
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.google.common.io.Resources
import java.io.File
import java.util.HashMap

class Iata {

    val cache: MutableMap<String, String> = HashMap()

    fun getIcao(iata: String): Either<String, String> {
        return when {
            iata.length == 3 -> {
                val icao = parseCsv()[iata]
                if (icao != null) {
                    println("add ${iata}:${icao} to cache. size ${cache.size}")
                    cache[iata] = icao
                    return Either.Right(icao)
                } else {
                    return Either.Left("ICAO not found for IATA: $iata")
                }
            }
            iata.length == 4 -> Either.Left(iata)
            else -> return Either.Right("No IATA or ICAO found: $iata")
        }
    }

    fun parseCsv(): Map<String, String>{
        val csv = File(Resources.getResource("iata-icao.csv").file).readText()
        val raws = csvReader().readAllWithHeader(csv)
        return raws.map { row -> row["iata"]!!.lowercase() to row["icao"]!!.lowercase() }.toMap()
    }

}