import  dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.send.withAction
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onAnyInlineQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.actions.TypingAction
import dev.inmo.tgbotapi.types.chat.User
import io.github.crackthecodeabhi.kreds.args.LeftRightOption
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import io.github.mivek.model.AbstractWeatherCode
import io.github.mivek.model.Airport
import io.github.mivek.model.Cloud
import io.github.mivek.model.Visibility
import io.github.mivek.model.WeatherCondition
import io.github.mivek.model.Wind
import io.github.mivek.service.MetarService
import io.github.mivek.service.TAFService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

val metarService = MetarService.getInstance()
val tafService = TAFService.getInstance()
val log = LoggerFactory.getLogger("root")
val redisClient = newClient(Endpoint.from(System.getenv("REDIS_URL")))

fun getMetar(icao: String): String{
    log.info("get metar for $icao")
    val metar = metarService.retrieveFromAirport(icao)
    var inline =  """
        ${metar.day} ${metar.time}
        temp: ${metar.temperature}, dew point: ${metar.dewPoint}, ${if (metar.isNosig == true) "nosig" else ""}
    """.trimIndent().trimMargin();
    return getCommon(metar, inline).replace("null", "")
}

fun getTaf(icao: String): String{
    log.info("get taf for $icao")
    val taf = tafService.retrieveFromAirport(icao)
    val validity = taf.validity
    var inline = "${validity.startDay}d ${validity.startHour}h - ${validity.endDay}d ${validity.endHour}"
    return getCommon(taf, inline).replace("null", "")
}

fun getCommon(container: AbstractWeatherCode, inline: String): String{
    val sb = StringBuilder()
    sb.append(getAirport(container.airport))
    sb.append("\n").append(inline)
    getWind(container.wind).ifNotEmpty { sb.append("\n").append(it) }
    sb.append("\n").append(getVisibility(container.visibility))
    getVerticalVisibility(container.verticalVisibility).ifNotEmpty { sb.append("\n").append(it) }
    getWeatherConditions(container.weatherConditions).ifNotEmpty { sb.append("\n").append(it) }
    getClouds(container.clouds).ifNotEmpty { sb.append("\n").append(it) }
    getRemark(container.remark).ifNotEmpty { sb.append("\n").append(it) }
    sb.append("\n\n").append(container.message)
    return sb.toString()
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

fun getWind(wind: Wind?): String {
    return if (wind == null){
        ""
    } else{
        "wind: ${convertSpeed(wind.speed, wind.unit)} km/h ${wind.directionDegrees}(${wind.direction})"
    }
}

fun getVisibility(visibility: Visibility): String =
    "visibility: ${visibility.mainVisibility}"

fun getVerticalVisibility(visibility: Int?): String {
    return if (visibility == null){
        ""
    } else{
        "vertical visibility: ${visibility.toString()}"
    }
}

fun getRemark(remark: String?): String{
    return if (remark.isNullOrBlank()){
        ""
    } else{
        "remark: $remark"
    }
}

fun getWeatherConditions(weatherCondition: List<WeatherCondition>): String{
    return weatherCondition.map {
        "${it.intensity?.let { it.name.lowercase() }} ${it.descriptive} ${it.phenomenons.joinToString(",")}"
    }.joinToString(",").trim()
}

fun getClouds(clouds: List<Cloud>): String{
    return clouds
        .sortedBy { it.height }
        .map {
        "${it.type} ${it.quantity} ${it.height}"
    }.joinToString(",").trim()
}

suspend fun main(){
    val iata: Iata = Iata()
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"),
        CoroutineScope(Dispatchers.IO),
        defaultExceptionsHandler = {it -> log.warn("", it)}){
        setMyCommands(
            BotCommand("metar", "Get metar. Usage: /w <icao>"),
            BotCommand("taf", "Get taf. Usage: /taf <icao>"),
            BotCommand("r", "repeat last command")
        )
        onCommandWithArgs(Regex("metar|m")){ message, args ->
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction){
                iata.getIcao(args.first().lowercase()).fold(
                    {error -> sendTextMessage(message.chat, error)},
                    {value ->
                        pushCommand(message.from!!, "m", value)
                        sendTextMessage(message.chat, getMetar(value))}
                )
            }
        }
        onCommandWithArgs(Regex("taf|t")){ message, args ->
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction){
                iata.getIcao(args.first().lowercase()).fold(
                    {error -> sendTextMessage(message.chat, error)},
                    {value ->
                        pushCommand(message.from!!, "t", value)
                        sendTextMessage(message.chat, getTaf(value))}
                )
            }
        }
        onCommand("r"){
            withAction(it.chat.id, TypingAction){
                val key = "${it.from!!.id.chatId}@history"
                val command = redisClient.lmove(key, key, LeftRightOption.LEFT, LeftRightOption.LEFT)!!
                val type = command.split(" ")[0]
                val icao = command.split(" ")[1]
                val res = when(type){
                    "m" -> getMetar(icao)
                    "t" -> getTaf(icao)
                    else -> "Error occurred"
                }
                sendTextMessage(it.chat, res)
            }
        }
        onAnyInlineQuery {
            log("inline " + it.query, it.from)
            iata.getIcao(it.query.lowercase()).map {value ->
                val res = awaitAll(
                    async { getMetar(value) },
                    async { getTaf(value) }
                )
                answer(it,
                listOf(
                    InlineQueryResultArticle(
                        it.query + "metar",
                        "metar",
                        InputTextMessageContent(res[0])
                    ),
                    InlineQueryResultArticle(
                        it.query + "taf",
                        "taf",
                        InputTextMessageContent(res[1])
                    )
                ),
                cachedTime = 0)}
        }
    }.second.join()
}

suspend fun pushCommand(from: User, command: String, icao: String){
    redisClient.lpush( "${from.id.chatId}@history", "$command $icao")
    redisClient.ltrim("${from.id.chatId}@history", 0, 6)
}

fun log(text: String?, from: User?){
    from?.let{
        log.info("$text from ${it.id.chatId} ${it.firstName} ${it.lastName}")
    }
}