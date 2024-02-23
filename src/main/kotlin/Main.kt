import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.send.withAction
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onAnyInlineQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.actions.TypingAction
import dev.inmo.tgbotapi.types.chat.User
import io.github.mivek.model.Airport
import io.github.mivek.model.Visibility
import io.github.mivek.model.WeatherCondition
import io.github.mivek.model.Wind
import io.github.mivek.service.MetarService
import io.github.mivek.service.TAFService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

val metarService = MetarService.getInstance()
val tafService = TAFService.getInstance()
val log = LoggerFactory.getLogger("root")

fun getMetar(icao: String): String{
    val metar = metarService.retrieveFromAirport(icao)
    return """
        ${getAirport(metar.airport)}
        ${metar.day} ${metar.time}
        temp: ${metar.temperature}, dew point: ${metar.dewPoint}, ${if (metar.isNosig == true) "nosig" else ""}
        ${getWind(metar.wind)}
        ${getVisibility(metar.visibility)}
        ${getVerticalVisibility(metar.verticalVisibility)}
        ${getWeatherConditions(metar.weatherConditions)}
        
        ${metar.message}
    """.trimIndent().replace("null", "")
}

fun getTaf(icao: String): String{
    val taf = tafService.retrieveFromAirport(icao)
    val validity = taf.validity
    return """
        ${getAirport(taf.airport)}
        ${validity.startDay}d ${validity.startHour}h - ${validity.endDay}d ${validity.endHour}h
        ${getWind(taf.wind)}
        ${getVisibility(taf.visibility)}
        ${getVerticalVisibility(taf.verticalVisibility)}
        ${getWeatherConditions(taf.weatherConditions)}
        
        ${taf.message}
    """.trimIndent().replace("null", "")
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

fun getVerticalVisibility(visibility: Int?): String =
    "vertical visibility: ${visibility.toString()}"

fun getWeatherConditions(weatherCondition: List<WeatherCondition>): String{
    return weatherCondition.map {
        "${it.intensity?.let { it.name }} ${it.descriptive} ${it.phenomenons.joinToString(",")}"
    }.joinToString(",")
}

suspend fun main(){
    val iata: Iata = Iata()
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"),
        CoroutineScope(Dispatchers.IO),
        defaultExceptionsHandler = {it -> log.warn("", it)}){
        setMyCommands(
            BotCommand("metar", "Get metar. Usage: /w <icao>"),
            BotCommand("taf", "Get taf. Usage: /taf <icao>")
        )
        onCommandWithArgs(Regex("metar|m")){ message, args ->
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction){
                iata.getIcao(args.first().lowercase()).fold(
                    {error -> sendTextMessage(message.chat, error)},
                    {value -> sendTextMessage(message.chat, getMetar(value))}
                )
            }
        }
        onCommandWithArgs(Regex("taf|t")){ message, args ->
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction){
                iata.getIcao(args.first().lowercase()).fold(
                    {error -> sendTextMessage(message.chat, error)},
                    {value -> sendTextMessage(message.chat, getTaf(value))}
                )
            }
        }
        onAnyInlineQuery {
            log("inline " + it.query, it.from)
            iata.getIcao(it.query.lowercase()).map {value -> answer(it,
                listOf(
                    InlineQueryResultArticle(
                        it.query + "metar",
                        "metar",
                        InputTextMessageContent(getMetar(value))
                    ),
                    InlineQueryResultArticle(
                        it.query + "taf",
                        "taf",
                        InputTextMessageContent(getTaf(value))
                    )
                ),
                cachedTime = 0)}
        }
    }.second.join()
}

fun log(text: String?, from: User?){
    from?.let{
        log.info("$text from ${it.id.chatId} ${it.firstName} ${it.lastName}")
    }
}