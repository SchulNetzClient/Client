package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Event {
    private EventType type;
    private String text;
    private String description;
    private long startTimestamp;
    private long endTimestamp;
    private boolean active;

    public String getOutput() {
        return type.toString() + ": (" + (active ? "active" : "inactive") + ") " + text + " - " + description + " - " + startTimestamp;
    }
}
