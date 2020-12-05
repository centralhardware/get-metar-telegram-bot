package ru.centralhardware.telegram.unbbGetMetaBot;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

public class TelegramBot extends TelegramLongPollingBot {

    static {
        ApiContextInitializer.init();
    }

    public static void start(){
        TelegramBot absSender = null;
        try {
            absSender = new TelegramBot();
            TelegramBotsApi botsApi = new TelegramBotsApi();
            botsApi.registerBot(absSender);
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()){
            try {
                switch (update.getMessage().getText()){
                    case "/metar" -> execute(new SendMessage().
                            setChatId(update.getMessage().getChatId()).
                            setText(Network.getMetar()));
                    case "/taf" -> execute(new SendMessage().
                            setChatId(update.getMessage().getChatId()).
                            setText(Network.getTaf()));
                    case "/start" -> execute(new SendMessage().
                            setChatId(update.getMessage().getChatId()).
                            setText("Получить погоду для UNBB в формате /metar или /taf"));
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "";
    }

    @Override
    public String getBotToken() {
        return "";
    }
}
