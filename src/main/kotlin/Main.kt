import dev.inmo.tgbotapi.extensions.api.answers.answer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.slf4j.LoggerFactory


val log = LoggerFactory.getLogger("root")
val redisClient = newClient(Endpoint.from(System.getenv("REDIS_URL")))


suspend fun main(){
    val iata = Iata()
    val formatter = Formatter()
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
                        sendTextMessage(message.chat, formatter.getMetar(value))}
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
                        sendTextMessage(message.chat, formatter.getTaf(value))}
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
                    "m" -> formatter.getMetar(icao)
                    "t" -> formatter.getTaf(icao)
                    else -> "Error occurred"
                }
                sendTextMessage(it.chat, res)
            }
        }
        onAnyInlineQuery {
            log("inline " + it.query, it.from)
            iata.getIcao(it.query.lowercase()).map {value ->
                val res = awaitAll(
                    async { formatter.getMetar(value) },
                    async { formatter.getTaf(value) }
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