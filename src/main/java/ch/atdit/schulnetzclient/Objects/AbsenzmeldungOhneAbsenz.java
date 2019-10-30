package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class AbsenzmeldungOhneAbsenz {
    private String date;
    private String startTime;
    private String endTime;
    private long startTimestamp;
    private long endTimestamp;
    private String output;
}
