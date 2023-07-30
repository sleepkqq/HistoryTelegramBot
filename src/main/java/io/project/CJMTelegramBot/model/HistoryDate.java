package io.project.CJMTelegramBot.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;

@Data
@Entity(name = "historyDatesTable")
public class HistoryDate {

    @Id
    private Long id;
    private String year;
    private String dateName;

    @Override
    public String toString() {
        return "HistoryDate{" +
                "id=" + id +
                ", year='" + year + '\'' +
                ", date='" + dateName + '\'' +
                '}';
    }
}
