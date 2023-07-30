package io.project.CJMTelegramBot.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
@Data
@Entity(name = "usersTable")
public class User {

    @Id
    private Long chatId;
    private String firstName;
    private String lastName;
    private String userName;
    private Integer countOfCorrectAnswers;
    private Integer countOfAnswers;
    private String feedback;
    private boolean historyTextYN;

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userName='" + userName + '\'' +
                ", countOfCorrectAnswers=" + countOfCorrectAnswers +
                ", countOfAnswers=" + countOfAnswers +
                ", feedback='" + feedback + '\'' +
                ", historyTextYN=" + historyTextYN +
                '}';
    }
}
