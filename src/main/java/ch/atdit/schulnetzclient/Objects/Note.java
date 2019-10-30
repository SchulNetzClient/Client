package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Note {
    private String date;
    private long dateTimestamp;
    private long registeredTimestamp;
    private String subject;
    private String topic;
    private String grade;
    private String output;
}
