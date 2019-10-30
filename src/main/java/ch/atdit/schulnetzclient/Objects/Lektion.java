package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Lektion {
    private String subject;
    private String room;
    private String startTime;
    private String endTime;
    private boolean red;
    private boolean yellow;
    private boolean lined;
    private long startTimestamp;
    private long endTimestamp;
    private String output;
}
