package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class OffeneAbsenz {
    private String startDate;
    private String endDate;
    private String untilDate;
    private long startTimestamp;
    private long endTimestamp;
    private long untilTimestamp;
    private String output;
}
