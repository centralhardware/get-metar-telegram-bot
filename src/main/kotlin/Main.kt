import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onAnyInlineQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import io.github.mivek.service.MetarService
import io.github.mivek.service.TAFService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

val metarService = MetarService.getInstance()
val tafService = TAFService.getInstance()

fun getMetar(icao: String): String{
    val metar = metarService.retrieveFromAirport(icao)
    val airport = metar.airport
    val wind = metar.wind
    return """
        ${metar.airport.name} ${airport.icao}(${airport.iata}) ${airport.altitude}
        ${metar.day} ${metar.time}
        temp: ${metar.temperature}, dew point: ${metar.dewPoint}, ${if (metar.isNosig == true) "nosig" else ""}
        wind: ${wind.speed} ${wind.unit} ${wind.directionDegrees}(${wind.direction})
        visibility: ${metar.visibility.mainVisibility}
         
        
        ${metar.message}
    """.trimIndent()
}

fun getTaf(icao: String): String{
    val taf = tafService.retrieveFromAirport(icao)
    return ""
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
        onCommandWithArgs("taf"){ message, args ->
            sendTextMessage(message.chat, getTaf(args.first()))
        }
        onAnyInlineQuery {
            answer(it,
                listOf(
                    InlineQueryResultArticle(
                        it.query,
                        "metar",
                        InputTextMessageContent(getMetar(it.query))
                    )
                ))
        }
    }.second.join()
}