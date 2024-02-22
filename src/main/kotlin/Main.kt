import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onAnyInlineQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import io.github.mivek.model.Airport
import io.github.mivek.model.Visibility
import io.github.mivek.model.WeatherCondition
import io.github.mivek.model.Wind
import io.github.mivek.service.MetarService
import io.github.mivek.service.TAFService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

val metarService = MetarService.getInstance()
val tafService = TAFService.getInstance()

fun getMetar(icao: String): String{
    val metar = metarService.retrieveFromAirport(icao)
    return """
        ${getAirport(metar.airport)}
        ${metar.day} ${metar.time}
        temp: ${metar.temperature}, dew point: ${metar.dewPoint}, ${if (metar.isNosig == true) "nosig" else ""}
        ${getWind(metar.wind)}
        ${getVisibility(metar.visibility)}
        ${getWeatherConditions(metar.weatherConditions)}
        
        ${metar.message}
    """.trimIndent()
}

fun getTaf(icao: String): String{
    val taf = tafService.retrieveFromAirport(icao)
    val validity = taf.validity
    return """
        ${getAirport(taf.airport)}
        ${validity.startDay}d ${validity.startHour}h - ${validity.endDay}d ${validity.endHour}h
        ${getWind(taf.wind)}
        ${getVisibility(taf.visibility)}
        ${getWeatherConditions(taf.weatherConditions)}
        
        ${taf.message}
    """.trimIndent()
}

fun convertSpeed(speed: Int, unit: String): Int{
    return when(unit.lowercase()){
        "kt" -> (speed * 1.852).roundToInt()
        "mps" -> (speed * 3.6).roundToInt()
        else -> throw IllegalArgumentException()
    }
}

fun getAirport(airport: Airport): String =
    "${airport.name} ${airport.icao}(${airport.iata}) ${airport.altitude}"

fun getWind(wind: Wind): String =
    "wind: ${convertSpeed(wind.speed, wind.unit)} km/h ${wind.directionDegrees}(${wind.direction})"

fun getVisibility(visibility: Visibility): String =
    "visibility: ${visibility.mainVisibility}"

fun getWeatherConditions(weatherCondition: List<WeatherCondition>): String{
    return weatherCondition.map {
        "${it.intensity.name} ${it.descriptive} ${it.phenomenons.joinToString(",")}"
    }.joinToString(",")
}

suspend fun main(){
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"),
        CoroutineScope(Dispatchers.IO),
        defaultExceptionsHandler = {it -> println(it)}){
        setMyCommands(
            BotCommand("metar", "Get metar. Usage: /w <icao>"),
            BotCommand("taf", "Get taf. Usage: /taf <icao>")
        )
        onCommandWithArgs("metar"){ message, args ->
            sendTextMessage(message.chat, getMetar(args.first()))
        }
        onCommandWithArgs("m"){ message, args ->
            sendTextMessage(message.chat, getMetar(args.first()))
        }
        onCommandWithArgs("taf"){ message, args ->
            sendTextMessage(message.chat, getTaf(args.first()))
        }
        onCommandWithArgs("t"){ message, args ->
            sendTextMessage(message.chat, getTaf(args.first()))
        }
        onAnyInlineQuery {
            answer(it,
                listOf(
                    InlineQueryResultArticle(
                        it.query + "metar",
                        "metar",
                        InputTextMessageContent(getMetar(it.query))
                    ),
                    InlineQueryResultArticle(
                        it.query + "taf",
                        "taf",
                        InputTextMessageContent(getTaf(it.query))
                    )
                ),
                cachedTime = 0)
        }
    }.second.join()
}