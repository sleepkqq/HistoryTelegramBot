package io.project.CJMTelegramBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.project.CJMTelegramBot.config.BotConfig;
import io.project.CJMTelegramBot.model.HistoryDate;
import io.project.CJMTelegramBot.repository.HistoryDateRepository;
import io.project.CJMTelegramBot.model.User;
import io.project.CJMTelegramBot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component

public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private HistoryDateRepository historyDateRepository;
    @Autowired
    private UserRepository userRepository;
    private final BotConfig CONFIG;
    private int random;
    static final String START_TEXT = EmojiParser.parseToUnicode("""
            Этот бот создан для того, чтобы помочь тебе подготовиться к ЕГЭ по истории!

            Тут собраны ключевые даты начиная с V заканчивая XXI веком!
            
            Для того чтобы начать подготовку к экзамену по истории напиши /history

            Чтобы получить список команд данного бота напиши /help или используй кнопку menu
    
            Enjoy!:smiley:""");
    static final String HELP_TEXT = """
            /start - начать работу с ботом

            /help - список всех доступных команд

            /history - начать подготовку!
            
            /feedback - оставить отзыв
            
            /cooperation - контакт разработчика бота
            
            /history_text_yes - включить вводное сообщение для теста
            
            /history_text_no - отключить вводное сообщение для теста""";
    static final String HISTORY_TEXT = EmojiParser.parseToUnicode("""
            Под этим сообщением начнется тест по самым известным датам в истории.

            :bangbang:ОСНОВНОЙ ФУНКЦИОНАЛ БОТА:bangbang:
            Тест состоит из вопросов. К каждому вопросу прикреплено 4 разных вариантов ответа. Среди них только 1 правильный.

            Тест можно проходить неограниченное количество раз. Рекомендую нажимать кнопку 'завершить' после того, как вы окончите сессию. И если захотите пройти тест ещё раз достаточно ввести команду /history

            Если вы не хотите, чтобы при начале нового теста этот текст каждый раз отображался, используйте команду /history_text_no. Если пожелаете вернуть текст - /history_text_yes""");


    public TelegramBot(BotConfig config) {

        CONFIG = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "начать работу с ботом"));
        listOfCommands.add(new BotCommand("/help", "список всех доступных команд"));
        listOfCommands.add(new BotCommand("/history", "начать подготовку!"));
        listOfCommands.add(new BotCommand("/feedback", "оставить отзыв"));
        listOfCommands.add(new BotCommand("/cooperation", "контакт разработчика бота"));
        listOfCommands.add(new BotCommand("/history_text_yes", "включить вводное сообщение для теста"));
        listOfCommands.add(new BotCommand("/history_text_no", "отключить вводное сообщение для теста"));
        try{
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }


    @Override
    public String getBotUsername() {
        return CONFIG.getBotName();
    }

    @Override
    public String getBotToken() {
        return CONFIG.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start" -> {
                    registerUser(update.getMessage());
                    sendMessage(chatId, START_TEXT);
                }
                case "/history" -> {
                    if (getHistoryTextYN(chatId))
                        sendMessage(chatId, HISTORY_TEXT);
                    sendHistoryDateAndAnswers(chatId);
                }
                case "/help" -> sendMessage(chatId, HELP_TEXT);
                case "/feedback" -> sendMessage(chatId, EmojiParser.parseToUnicode("Чтобы оставить отзыв, просто напиши сообщение, но в начале него должно быть \"отзыв:\"\n" +
                        "Иначе я не получу твой отзыв :sob:"));
                case "/cooperation" -> sendMessage(chatId, "Telegram: @renegade6");
                case "/history_text_no" -> {
                    sendMessage(chatId, "Вводное сообщение отключено. Чтобы включить - /history_text_yes");
                    setHistoryTextYN(chatId, false);
                }
                case "/history_text_yes" -> {
                    sendMessage(chatId, "Вводное сообщение включено. Чтобы отключить - /history_text_no");
                    setHistoryTextYN(chatId, true);
                }
            }
            if (messageText.toLowerCase().contains("отзыв:")) {
                sendMessage(chatId, EmojiParser.parseToUnicode("Спасибо за отзыв! Ты очень помог мне :smiley:"));
                setUserFeedback(chatId, messageText);
            }
        } else if (update.hasCallbackQuery()) {
            callbackData(update);
        }
    }

    private void callbackData(Update update) {

        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        EditMessageText message = new EditMessageText();

        String text = "";

        if (callbackData.equals("correct")) {
            text = EmojiParser.parseToUnicode("Правильный ответ :white_check_mark:\n\nВопрос: " + getHistoryDate(random) + "\n\nОтвет: " + getHistoryYear(random));
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(getInlineKeyboardForNextOrStop());
            message.setReplyMarkup(markup);

            setCountOfAnswers(chatId, getCountOfCorrectAnswers(chatId) + 1, "correctAnswers");
            setCountOfAnswers(chatId, getCountOfAnswers(chatId) + 1, "allAnswers");
        }
        if (callbackData.equals("1") || callbackData.equals("2") || callbackData.equals("3") || callbackData.equals("4")) {
            text = EmojiParser.parseToUnicode("Неправильный ответ :x:\n\nВопрос: " + getHistoryDate(random) + "\n\nПравильный ответ был: " + getHistoryYear(random));
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(getInlineKeyboardForNextOrStop());
            message.setReplyMarkup(markup);

            setCountOfAnswers(chatId, getCountOfAnswers(chatId) + 1, "allAnswers");
        }
        if (callbackData.equals("next")) {
            sendHistoryDateAndAnswers(chatId);
            text = update.getCallbackQuery().getMessage().getText();
        }
        if (callbackData.equals("stop")) {
            sendOverMessage(chatId);
            text = update.getCallbackQuery().getMessage().getText();

            setCountOfAnswers(chatId, 0, "allAnswers");
            setCountOfAnswers(chatId, 0, "correctAnswers");
        }

        message.setChatId(String.valueOf(chatId));
        message.setMessageId((int) messageId);
        message.setText(text);
        executeMessage(message);
    }

    private void registerUser(Message msg) {

        if (userRepository.findById(msg.getChatId()).isEmpty()) {

            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setCountOfAnswers(0);
            user.setCountOfCorrectAnswers(0);
            user.setHistoryTextYN(true);

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    private List<List<InlineKeyboardButton>> getInlineKeyboardForAnswers(List<String> listOfAnswers, String correctAnswer) {
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        for (int i = 0, number = 1; i < 2; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int j = 0; j < 2; j++, number++) {
                var button = new InlineKeyboardButton();
                button.setText(listOfAnswers.get(number - 1));
                button.setCallbackData(button.getText().equals(correctAnswer) ? "correct" : "" + number);
                row.add(button);
            }
            rowsInline.add(row);
        }
        return rowsInline;
    }

    private List<List<InlineKeyboardButton>> getInlineKeyboardForNextOrStop() {
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            var button = new InlineKeyboardButton();
            button.setText(i == 0 ? "следующий вопрос" : "завершить");
            button.setCallbackData(button.getText().equals("завершить") ? "stop" : "next");
            row.add(button);
            rowsInline.add(row);
        }
        return rowsInline;
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId("" + chatId);
        message.setText(textToSend);

        executeSendMessage(message);
    }

    private void sendOverMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        int countAll = getCountOfAnswers(chatId), countCorrect = getCountOfCorrectAnswers(chatId);

        String text = EmojiParser.parseToUnicode(":tada: Опрос окончен :tada:\n\nВсего вопросов было: " + countAll + randomEmoji() + "\n\nПравильных ответов: "
                + countCorrect + " :white_check_mark:\n\nНеправильных ответов: "
                + (countAll - countCorrect) + " :x:\n\nПроцент правильных ответов: "
                + percentOfCorrectAnswers(countAll, countCorrect) + " :fire:\n\n:sparkles:Обязательно поделись своим результатом с друзьями!:sparkles:\n\n@PreparationForHistoryBot");

        sendMessage(chatId, text);
    }

    private void sendHistoryDateAndAnswers(long chatId) {
        int random = generateRandomIntNumber(157) - 1;
        this.random = random;

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(getHistoryDate(random));

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();

        markupInline.setKeyboard(getInlineKeyboardForAnswers(getShuffleListFromYear(getNumbersFromString(getHistoryYear(random))), getNumbersFromString(getHistoryYear(random))));

        message.setReplyMarkup(markupInline);

        executeSendMessage(message);
    }

    private void setCountOfAnswers(long id, int count, String answers) {
        var optionalUser = userRepository.findById(id);
        var userNow = optionalUser.get();
        if (answers.equals("allAnswers")) userNow.setCountOfAnswers(count);
        if (answers.equals("correctAnswers")) userNow.setCountOfCorrectAnswers(count);
        userRepository.save(userNow);
    }

    private void setUserFeedback(long id, String feedback) {
        feedback = feedback.substring(6);
        var optionalUser = userRepository.findById(id);
        var userNow = optionalUser.get();
        userNow.setFeedback(feedback);
        userRepository.save(userNow);
    }

    private void setHistoryTextYN(long id, boolean statement) {
        var optionalUser = userRepository.findById(id);
        var userNow = optionalUser.get();
        userNow.setHistoryTextYN(statement);
        userRepository.save(userNow);
    }

    private boolean getHistoryTextYN(long id) {
        var user = userRepository.findById(id);
        return user.get().isHistoryTextYN();
    }

    private int getCountOfCorrectAnswers(long id) {
        var user = userRepository.findById(id);
        return user.get().getCountOfCorrectAnswers();
    }

    private int getCountOfAnswers(long id) {
        var user = userRepository.findById(id);
        return user.get().getCountOfAnswers();
    }

    private String getHistoryYear(int random) {
        var historyDate = historyDateRepository.findById((long) random);
        return historyDate.get().getYear();
    }

    private String getHistoryDate(int random) {
        var historyDate = historyDateRepository.findById((long) random);
        return historyDate.get().getDateName();
    }

    private List<String> getShuffleListFromYear(String year) {
        String[] arr = year.split("\\D+");
        year = String.join("", arr);
        List<String> list;
        int random1to5 = generateRandomIntNumber(5);
        int random6to10 = generateRandomIntNumber(5) + 5;

        switch (year.length()) {
            case 3, 4 -> list = getShuffleListFromYearOption(year, 1, 0, random1to5, random6to10);
            case 8 -> list = getShuffleListFromYearOption(year, 2, 4, random1to5, random6to10);
            default -> list = getShuffleListFromYearOption(year, 2, 3, random1to5, random6to10);
        }
        Collections.shuffle(list);
        return list;
    }

    private List<String> getShuffleListFromYearOption(String year, int countOfNumbers, int beginEndIndex, int random1to5, int random6to10) {
        List<String> list = new ArrayList<>();
        if (countOfNumbers == 1) {
            list.add(year);
            list.add(String.valueOf(Integer.parseInt(year) + random6to10));
            list.add(String.valueOf(Integer.parseInt(year) + random1to5));
            list.add(String.valueOf(Integer.parseInt(year) - random6to10));
        } else {
            int firstYear = Integer.parseInt(year.substring(0, beginEndIndex));
            int secondYear = Integer.parseInt(year.substring(beginEndIndex));
            list.add(firstYear + "-" + secondYear);
            list.add((firstYear + random1to5) + "-" + (secondYear + random1to5));
            list.add((firstYear - random1to5) + "-" + (secondYear - random1to5));
            list.add((firstYear + random6to10) + "-" + (secondYear + random6to10));
        }
        return list;
    }

    private String getNumbersFromString(String s) {
        String[] arr1 = s.split("");
        s = "";
        for (String value : arr1) {
            if (value.equals("г"))
                break;
            s += value;
        }
        String[] arr2 = s.split("\\D+");
        s = String.join("", arr2);
        switch (s.length()) {
            case 8 -> {
                int firstYear = Integer.parseInt(s.substring(0, 4));
                int secondYear = Integer.parseInt(s.substring(4));
                s = firstYear + "-" + secondYear;
            }
            case 6, 7 -> {
                int firstYear = Integer.parseInt(s.substring(0, 3));
                int secondYear = Integer.parseInt(s.substring(3));
                s = firstYear + "-" + secondYear;
            }
        }
        return s;
    }

    private String percentOfCorrectAnswers(int answers, int correctAnswers) {
        Double value = (correctAnswers * 1.0 / answers) * 100;
        String resultString = String.format("%.2f", value);
        Double result = Double.parseDouble(resultString.replace(',','.'));
        return result + "%";
    }

    private int generateRandomIntNumber(int to) {
        return (int)(Math.random() * to + 1);
    }

    private String randomEmoji() {
        String[] arrayOfEmoji = {":eyes:", ":exploding_head:", ":scream:", ":sleeping:", ":cold_face:", ":woozy_face:", ":sneezing_face:",
                                ":confounded:", ":yum:", ":pray:", ":rage:", ":see_no_evil:", ":sunglasses:"};
        return " " + arrayOfEmoji[generateRandomIntNumber(11) - 1];
    }

    private void executeSendMessage(SendMessage message) {
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private void executeMessage(EditMessageText message) {
        try {
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private void addHistoryTable() throws IOException {
        var document = Jsoup.connect("https://barabook.ru/6394249963542517/Osnovnye_daty_Vsemirnojj_istorii").get();
        Elements table = document.select("div[class=txtcontainer]");
        List<String> listOfDates = new ArrayList<>();
        for (int i = 1; i <= 314; i++) {
            listOfDates.add(table.get(i).text());
        }
        for (int i = 0, index = 1; i < listOfDates.size(); i += 2, index++) {
            HistoryDate date = new HistoryDate();
            date.setDateName(listOfDates.get(i + 1));
            date.setId((long) index);
            date.setYear(listOfDates.get(i));
            historyDateRepository.save(date);
            log.info("date saved: " + date);
        }
    }


}
