package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@AllArgsConstructor
@Getter
@Setter
public class Person {
    private long timestamp;
    private String date;
    private String time;
    private String source;
    private String sourceSplit;
    private ArrayList<Note> noten;
    private ArrayList<OffeneAbsenz> offeneAbsenzen;
    private ArrayList<AbsenzmeldungOhneAbsenz> absenzmeldungenOhneAbsenz;
    private ArrayList<Lektion> lektionen;
    //private boolean eventsUpdate;

    public boolean containsNote(Note otherNote) {
        for (Note note : noten) {
            if (note.getOutput().equals(otherNote.getOutput())) return true;
        }

        return false;
    }

    public boolean containsOffeneAbsenz(OffeneAbsenz otherOffeneAbsenz) {
        for (OffeneAbsenz offeneAbsenz : offeneAbsenzen) {
            if (offeneAbsenz.getOutput().equals(otherOffeneAbsenz.getOutput())) return true;
        }

        return false;
    }

    public boolean containsAbsenzeldungOhneAbsenz(AbsenzmeldungOhneAbsenz otherAbsenzmeldungOhneAbsenz) {
        for (AbsenzmeldungOhneAbsenz absenzmeldungOhneAbsenz : absenzmeldungenOhneAbsenz) {
            if (absenzmeldungOhneAbsenz.getOutput().equals(otherAbsenzmeldungOhneAbsenz.getOutput())) return true;
        }

        return false;
    }

    public boolean containsLesson(Lektion otherLektion) {
        for (Lektion lektion : lektionen) {
            if (lektion.getOutput().equals(otherLektion.getOutput())) return true;
        }

        return false;
    }
}
