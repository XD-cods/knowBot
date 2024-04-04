package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Document;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.Services.QuizService;
import org.example.Services.UsersService;
import org.example.model.PermanentUserInfo;
import org.example.model.Quiz;
import org.example.model.TempUserInfo;
import org.example.model.UserInfo;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminBot implements UpdatesListener {
  private final TelegramBot bot;
  private final QuizService quizService;
  private final UsersService usersService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Logger logger = LogManager.getLogger();
  private final Map<Long, UserInfo> users = new HashMap<>();

  public AdminBot(TelegramBot bot, QuizService quizService, UsersService usersService) {
    this.bot = bot;
    this.quizService = quizService;
    this.usersService = usersService;
  }

  @Override
  public int process(List<Update> updates) {
    Update update = updates.get(updates.size() - 1);
    try {
      if (update.message() == null) {
        return UpdatesListener.CONFIRMED_UPDATES_NONE;
      }

      Message message = update.message();
      Long userId = message.chat().id();
      String messageText = message.text();
      TempUserInfo tempUserInfo = null;

      if (isRegisterUser(userId)) {
        tempUserInfo = users.get(userId).getTempUserInfo();

        if (isDocument(message)) {
          DocumentHandler(userId, message, tempUserInfo);
        }
        if (tempUserInfo.isUpdateChoiceTopic() && messageText.matches("[0-9]+$")) {
          sendFile(messageText, userId, tempUserInfo);
        }
      }

      if (!isRegisterUser(userId) && !isStartMessage(messageText)) {
        sendMessage(userId, "input /start");
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
      } else if (messageText.equals(AdminBotConstants.START_BOT_COMMAND)) {
        PermanentUserInfo permanentUserInfo = usersService.findByUserName(message.chat().username());
        permanentUserInfo.setUserId(userId);
        users.put(userId, new UserInfo(permanentUserInfo, new TempUserInfo()));
        sendMessage(userId, AdminBotConstants.START_BOT_MESSAGE);
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
      }

      

      if (!isAdmin(userId)) {
        sendMessage(userId, "You are not admin");
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
      }


      switch (messageText) {
        case AdminBotConstants.CLEAR_DB_COMMAND -> {
          if (tempUserInfo.isAddQuizMode()) {
            sendMessage(userId, "Input /add for finish add files or input /cancel");
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
          }
          sendMessage(userId, "Database cleared");
          quizService.deleteAllQuiz();
        }
        case AdminBotConstants.ADD_NEW_QUIZ_COMMAND -> {
          if (!tempUserInfo.isAddQuizMode()) {
            sendMessage(userId, "Input your .json files and input /add" +
                    " for finish add files or input /cancel");
          }
          if(tempUserInfo.isAddQuizMode()){
            sendMessage(userId,"You are canceled add quiz");
          }
          tempUserInfo.setAddQuizMode(!tempUserInfo.isAddQuizMode());
        }
        case AdminBotConstants.UPDATE_QUIZ_COMMAND -> {
          if (tempUserInfo.isAddQuizMode()) {
            sendMessage(userId, "Input /add for finish add files or input /cancel");
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
          }
          if (tempUserInfo.isUpdateChoiceTopic() || tempUserInfo.isUpdateInputFIle()) {
            sendMessage(userId, "Please finish update quiz or input /cancel");
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
          }
          sendMessage(userId, "Input number of quiz");
          tempUserInfo.setUpdateChoiceTopic(true);
          sendAllQuiz(userId);
        }
        case AdminBotConstants.CANCEL_COMMAND -> {
          sendMessage(userId, "Commands is canceled");
          users.get(userId).setTempUserInfo(new TempUserInfo());
        }
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
    } finally {
      return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }
  }

  private void sendMessage(Long userId, String s) {
    bot.execute(new SendMessage(userId, s));
  }

  private void sendFile(String messageText, Long userId, TempUserInfo tempUserInfo) throws IOException {
    int topicIndex = Integer.parseInt(messageText) - 1;
    List<String> allTopicsName = quizService.findAllTopicName();
    String topicName = allTopicsName.get(topicIndex);
    java.io.File tempFile = Files.createTempFile(topicName, ".json").toFile();
    Quiz quiz = quizService.findByTopicName(topicName);
    try (FileWriter fileWriter = new FileWriter(tempFile)) {
      fileWriter.write(objectMapper.writeValueAsString(quiz));
    }
    sendMessage(userId, "Topic is " + topicName);
    bot.execute(new SendDocument(userId, tempFile));
    tempUserInfo.setUpdateChoiceTopic(false);
    tempUserInfo.setUpdateInputFIle(true);
    tempFile.delete();
  }

  private void DocumentHandler(Long userId, Message message, TempUserInfo tempUserInfo) {
    Quiz quiz = getQuizFromFile(userId, message.document());
    if (tempUserInfo.isAddQuizMode()) {
      quizService.insertNewQuiz(quiz);
    }
    if (tempUserInfo.isUpdateInputFIle()) {
      quizService.updateQuizByTopicName(quiz.getTopicName(), quiz);
      tempUserInfo.setUpdateInputFIle(false);
    }
  }

  private void sendAllQuiz(Long userId) {
    List<String> allTopicsName = quizService.findAllTopicName();
    StringBuilder choiceTopicText = new StringBuilder("Choose your topic! Input number of quiz");
    for (int i = 0; i < allTopicsName.size(); i++) {
      int pagination = i + 1;
      choiceTopicText.append("\n").append(pagination).append(". ").append(allTopicsName.get(i));
    }
    SendMessage topics = new SendMessage(userId, choiceTopicText.toString());
    bot.execute(topics);
  }

  private boolean isAdmin(Long userId) {
    return users.get(userId).getPermanentUserInfo().isAdmin();
  }

  private static boolean isStartMessage(String messageText) {
    return messageText.equals(UserBotConstants.START_BOT_COMMAND);
  }

  private static boolean isDocument(Message message) {
    return message.document() != null;
  }

  private boolean isRegisterUser(Long userId) {
    return users.containsKey(userId);
  }


  private Quiz getQuizFromFile(Long userId, Document document) {
    if (document != null && "application/json".equals(document.mimeType())) {
      String fileId = document.fileId();
      GetFile getFile = new GetFile(fileId);
      File file = bot.execute(getFile).file();
      if (file != null) {
        String content;
        try {
          content = new String(bot.getFileContent(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        try {
          return objectMapper.readValue(content, Quiz.class);
        } catch (JsonProcessingException e) {
          sendMessage(userId, "Please send valid json " + e.getMessage());
          throw new RuntimeException(e);
        }
      } else {
        logger.error("No file found for document ID in admin bot: " + document.fileId());
        throw new RuntimeException("No file found in adminBot");
      }
    }
    sendMessage(userId, "Please send .json file");
    return null;
  }

  public JsonSchema getJsonSchema() {
    JsonSchemaGenerator jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);
    JsonSchema jsonSchema;
    try {
      jsonSchema = jsonSchemaGenerator.generateSchema(Quiz.class);
      ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
      System.out.println(objectWriter.writeValueAsString(jsonSchema));
    } catch (JsonMappingException e) {
      throw new RuntimeException(e);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return jsonSchema;
  }
}
