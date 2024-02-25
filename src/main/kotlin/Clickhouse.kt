import com.clickhouse.jdbc.ClickHouseDataSource
import dev.inmo.tgbotapi.types.chat.CommonUser
import kotliquery.queryOf
import kotliquery.sessionOf
import java.sql.SQLException
import java.time.LocalDateTime
import javax.sql.DataSource

class Clickhouse {

    val dataSource: DataSource = try {
        ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
    } catch (e: SQLException) {
        throw RuntimeException(e)
    }

    fun log(text: String, isInline: Boolean, user: CommonUser?, userId: Long) {
        user?.let {
            sessionOf(dataSource).execute(
                //language=GenericSQL
                queryOf(
                    """
           INSERT INTO bot_log (
                date_time,
                bot_name,
                user_id,
                usernames,
                first_name,
                last_name,
                is_premium,
                is_inline,
                lang,
                text
           ) VALUES (
                now(),
                'metarBot',
                :user_id,
                :usernames,
                :first_name,
                :last_name,
                :is_premium,
                :is_inline,
                :lang,
                :text
           ) 
        """, mapOf(
                        "user_id" to userId,
                        "usernames" to if (it.username != null) it.username!!.full else null,
                        "first_name" to it.firstName,
                        "last_name" to it.lastName,
                        "is_premium" to it.isPremium,
                        "is_inline" to isInline,
                        "lang" to it.languageCode,
                        "text" to text
                    )
                )
            )
        }
    }

}