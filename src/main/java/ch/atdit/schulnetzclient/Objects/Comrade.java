package ch.atdit.schulnetzclient.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Comrade {
    private String Vorname;
    private String Nachname;
    private String Strasse;
    private int PLZ;
    private String Ort;
    private String Telefon;
    private String EMail;
    private String Geburtstag;
}
