import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.send.withAction
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onAnyInlineQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.utils.asCommonUser
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.actions.TypingAction
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.utils.row
import io.github.crackthecodeabhi.kreds.args.LeftRightOption
import io.github.crackthecodeabhi.kreds.args.SetOption
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import me.centralhardware.telegram.bot.common.ClickhouseKt
import me.centralhardware.telegram.bot.common.MessageType
import org.slf4j.LoggerFactory
import java.util.*


val log = LoggerFactory.getLogger("root")
val redisClient = newClient(Endpoint.from(System.getenv("REDIS_URL")))


suspend fun main() {
    val clickhouse = ClickhouseKt()
    telegramBotWithBehaviourAndLongPolling(System.getenv("BOT_TOKEN"),
        CoroutineScope(Dispatchers.IO),
        defaultExceptionsHandler = { log.warn("", it) }) {
        setMyCommands(
            BotCommand("metar", "Get metar. Usage: /w <icao>"),
            BotCommand("taf", "Get taf. Usage: /taf <icao>"),
            BotCommand("r", "repeat last command")
        )
        onCommandWithArgs(Regex("metar|m")) { message, args ->
            async { clickhouse.log(message.text!!,message.from!!.asCommonUser(), "metarBot", MessageType.TEXT) }
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction) {
                IcaoStorage.get(args.first().lowercase()).fold(
                    { error -> sendTextMessage(message.chat, error) },
                    { value ->
                        val metar = Formatter.getMetar(value)
                        pushCommand(message.from!!, "m", value)
                        sendTextMessage(message.chat, metar.first,
                            replyMarkup = inlineKeyboard {
                                row { dataButton("raw message", saveRawMessage(metar.second)) }
                            })
                    }
                )
            }
        }
        onCommandWithArgs(Regex("taf|t")) { message, args ->
            async { clickhouse.log(message.text!!,message.from!!.asCommonUser(), "metarBot", MessageType.TEXT) }
            log(message.text, message.from)
            withAction(message.chat.id, TypingAction) {
                IcaoStorage.get(args.first().lowercase()).fold(
                    { error -> sendTextMessage(message.chat, error) },
                    { value ->
                        val taf = Formatter.getTaf(value)
                        pushCommand(message.from!!, "t", value)
                        sendTextMessage(message.chat, taf.first,
                            replyMarkup = inlineKeyboard {
                                row { dataButton("raw message", saveRawMessage(taf.second)) }
                            })
                    }
                )
            }
        }
        onCommand("r") {
            async { clickhouse.log(it.text!!, it.from!!.asCommonUser(), "metarBot", MessageType.TEXT) }
            withAction(it.chat.id, TypingAction) {
                val key = "${it.from!!.id.chatId}@history"
                val command = redisClient.lmove(key, key, LeftRightOption.LEFT, LeftRightOption.LEFT)!!
                val type = command.split(" ")[0]
                val icao = command.split(" ")[1].asIcao()
                val res = when (type) {
                    "m" -> Formatter.getMetar(icao)
                    "t" -> Formatter.getTaf(icao)
                    else -> throw IllegalArgumentException()
                }
                sendTextMessage(it.chat, res.first,
                    replyMarkup = inlineKeyboard {
                        row { dataButton("raw message", saveRawMessage(res.second)) }
                    })
            }
        }
        onAnyInlineQuery {
            async { clickhouse.log(it.query!!, it.from.asCommonUser(), "metarBot", MessageType.INLINE) }
            log("inline " + it.query, it.from)
            IcaoStorage.get(it.query.lowercase()).map { value ->
                val res = awaitAll(
                    async { Formatter.getMetar(value).first },
                    async { Formatter.getTaf(value).first }
                )
                answer(
                    it,
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
                    cachedTime = 0
                )
            }
        }
        onDataCallbackQuery {
            async { clickhouse.log(it.data, it.from.asCommonUser(), "metarBot", MessageType.CALLBACK) }
            answerCallbackQuery(it, redisClient.get("raw_messages_${it.data}"), showAlert = true)
        }

    }.second.join()
}

suspend fun pushCommand(from: User, command: String, icao: Icao) {
    redisClient.lpush("${from.id.chatId}@history", "$command $icao")
    redisClient.ltrim("${from.id.chatId}@history", 0, 6)
}

suspend fun saveRawMessage(raw: String): String{
    val id = UUID.randomUUID().toString()
    redisClient.set("raw_messages_$id", raw, SetOption.Builder().exSeconds(604800u).build())
    return id
}

fun log(text: String?, from: User?) {
    from?.let {
        log.info("$text from ${it.id.chatId} ${it.firstName} ${it.lastName}")
    }
}