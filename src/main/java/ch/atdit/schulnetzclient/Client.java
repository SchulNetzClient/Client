package ch.atdit.schulnetzclient;

import ch.atdit.schulnetzclient.Objects.*;
import ch.atdit.schulnetzclient.Objects.Event;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.toedter.calendar.JDateChooser;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Map.Entry.comparingByValue;

@SuppressWarnings("Duplicates")
public class Client {

    private static NumberFormat twoDecimalFormatter = new DecimalFormat("#0.00");
    private static SimpleDateFormat ddMMyyyy = new SimpleDateFormat("dd.MM.yyyy");
    private static long lastRequest = 0;
    private static int failures = 0;

    @Getter
    @Setter
    private static ArrayList<Comrade> comrades = new ArrayList<>();

    @Getter
    @Setter
    private static ArrayList<Lektion> lessons = new ArrayList<>();

    @Getter
    @Setter
    private static HashMap<Integer, Integer> rowsToColor = new HashMap<>();

    @Getter
    @Setter
    private static HashMap<String, String> subjectAbbreviations = new HashMap<>();

    @Getter
    @Setter
    private static Frame frame = null;

    @Getter
    @Setter
    private static JFrame jFrame = null;

    @Getter
    @Setter
    private static Person person = null;

    @Getter
    @Setter
    private static ArrayList<Event> events = new ArrayList<>();

    @Getter
    @Setter
    private static ArrayList<String> lastEvents = new ArrayList<>();

    @Getter
    @Setter
    private static ArrayList<String> lastNoten = new ArrayList<>();

    @Getter
    @Setter
    private static boolean eventsTypeUpdated = false;

    @Getter
    @Setter
    private static EventType eventUpdateType = EventType.ALL;

    @Getter
    @Setter
    private static int page = 1;

    @Getter
    @Setter
    private static int maxPage = 1;

    @Getter
    @Setter
    private static HashMap<EventType, ArrayList<String>> defaultEventMessages = new HashMap<>();

    private static void print(Object object) {
        System.out.println(object);
    }

    private static boolean preinitialize() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            print(Charset.defaultCharset());
            System.setProperty("file.encoding", "UTF-8");
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null, null);
            print(Charset.defaultCharset());

            updatePeople();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static boolean postinitialize() {
        try {
            person = convertJsonToPerson(Objects.requireNonNull(getPersonJSON()));
            Person newPerson = convertJsonToPerson(downloadData().getObject());
            comparePersonObjects(newPerson);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Refused connection to server", "Error", JOptionPane.ERROR_MESSAGE);
            Runtime.getRuntime().exit(1);
        }

        JSONObject jsonConfig = new JSONObject(getString("config.json"));

        JSONObject jsonSubjectAbbreviations = (JSONObject) jsonConfig.get("subject_abbreviations");

        for (Object object : jsonSubjectAbbreviations.keySet()) {
            String key = object.toString();
            String value = jsonSubjectAbbreviations.get(key).toString();

            subjectAbbreviations.put(key, value);
        }

        JSONObject jsonEventTextAbbreviations = (JSONObject) jsonConfig.get("event_text_abbreviations");

        for (Object object : jsonEventTextAbbreviations.keySet()) {
            String key = object.toString();
            JSONArray eventType = (JSONArray) jsonEventTextAbbreviations.get(key);

            ArrayList<String> eventMessage = new ArrayList<>();
            eventMessage.add(eventType.get(0).toString());
            eventMessage.add(eventType.get(1).toString());
            eventMessage.add(eventType.get(2).toString());
            eventMessage.add(eventType.get(3).toString());

            defaultEventMessages.put(EventType.valueOf(key), eventMessage);
        }

        //TODO Remove when events are server sided only

        JSONArray jsonEvents = new JSONArray(getString("events.json"));
        loadEvents(jsonEvents);
        updateBirthdayEvents();

        jFrame.setVisible(true);
        return true;
    }

    private static void loadEvents(org.json.JSONArray jsonEvents) {
        events = new ArrayList<>();

        for (Object jsonEvent : jsonEvents) {
            JSONObject jsonObject = (JSONObject) jsonEvent;

            Event event = new Event(
                    EventType.valueOf(jsonObject.getString("type")),
                    jsonObject.getString("text"),
                    jsonObject.has("description") ? jsonObject.getString("description") : "",
                    (long) jsonObject.get("startTimestamp"),
                    (long) jsonObject.get("endTimestamp"),
                    (boolean) jsonObject.get("active")
            );

            if (event.getType() != EventType.BIRTHDAY && !events.contains(event)) events.add(event);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static String readFile(String file) {
        try {
            return FileUtils.readFileToString(new File(file), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void saveFile(String string, String file) {
        try {
            FileUtils.writeStringToFile(new File(file), string, "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveEvents() {
        JSONArray jsonArray = new JSONArray();

        for (Event event : events) {
            JSONObject jsonEvent = new JSONObject();

            jsonEvent.put("type", event.getType().toString());
            jsonEvent.put("text", event.getText());
            jsonEvent.put("description", event.getDescription());
            jsonEvent.put("startTimestamp", event.getStartTimestamp());
            jsonEvent.put("endTimestamp", event.getEndTimestamp());
            jsonEvent.put("active", event.isActive());

            jsonArray.put(jsonEvent);
        }

        saveFile(jsonArray.toString(4), "events.json");
    }

    private static JsonNode downloadData() throws UnirestException, IOException {
        HttpResponse<JsonNode> response = Unirest.post(Reference.SERVER)
                .queryString("token", Reference.TOKEN)
                .asJson();
        return response.getBody();
    }

    @SuppressWarnings("unused")
    private static JsonNode downloadEvents() throws UnirestException {
        HttpResponse<JsonNode> response = Unirest.post(Reference.SERVER + "/events")
                .queryString("token", Reference.TOKEN)
                .asJson();
        return response.getBody();
    }

    private static String getString(String file) {
        try {
            return FileUtils.readFileToString(new File(file), "utf-8");
        } catch (Exception e) {
            e.printStackTrace();
            Runtime.getRuntime().exit(1);
            return "";
        }
    }

    private static org.json.JSONObject getPersonJSON() {
        try {
            return new org.json.JSONObject(Objects.requireNonNull(readFile("person.json")));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void updatePeople() {
        JSONArray jsonArray = new JSONArray(getString("schueler.json"));
        ArrayList<Comrade> comrades = new ArrayList<>();

        for (Object object : jsonArray) {
            JSONObject jsonPerson = (JSONObject) object;
            Comrade comrade = new Comrade(
                    jsonPerson.getString("Vorname"),
                    jsonPerson.getString("Name"),
                    jsonPerson.getString("Strasse"),
                    jsonPerson.getInt("PLZ"),
                    jsonPerson.getString("Ort"),
                    jsonPerson.getString("Telefon"),
                    jsonPerson.getString("Email"),
                    jsonPerson.getString("GebDatum")
            );

            comrades.add(comrade);
        }

        Client.comrades = comrades;
    }

    private static Date getBirthDistance(String birthday, int yearsToAdd) throws ParseException {
        Date birth = ddMMyyyy.parse(birthday.substring(0, birthday.lastIndexOf(".") + 1) + (Calendar.getInstance().get(Calendar.YEAR) + yearsToAdd));
        long differenceMilliseconds = birth.getTime() - new Date().getTime();
        if (differenceMilliseconds < 0) return getBirthDistance(birthday, 1);
        return birth;
    }

    private static void updateBirthdayEvents() {
        for (Comrade comrade : comrades) {
            String vorname = comrade.getVorname();
            String birthday = comrade.getGeburtstag();

            Date startDate = new Date();

            try {
                startDate = getBirthDistance(birthday, 0);
            } catch (ParseException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Impossible Error occured:\nCaught ParseException while trying to parse:\n" + birthday, "Error", JOptionPane.ERROR_MESSAGE);
                Runtime.getRuntime().exit(1);
            }

            Event birthdayEvent = new Event(
                    EventType.BIRTHDAY,
                    vorname,
                    "",
                    startDate.getTime(),
                    startDate.getTime() + 1000 * 60 * 60 * 24,
                    true
            );

            if (!events.contains(birthdayEvent)) events.add(birthdayEvent);
        }
    }

    private static void addEvent(EventType eventType, String text, String description, Long startTimestamp) {
        Event event = new Event(
                eventType,
                text,
                description,
                startTimestamp,
                startTimestamp + 1000 * 60 * 60 * 24,
                true
        );

        events.add(event);
        saveEvents();
        update();
    }

    private static void setUpdateType(EventType eventType) {
        if (eventUpdateType == eventType) {
            eventUpdateType = EventType.ALL;
        } else {
            eventUpdateType = eventType;
        }
        eventsTypeUpdated = true;

        page = 1;

        frame.getPreviousButton().setEnabled(false);

        updateEvents();
    }

    private static void updateEvents() {
        long now = System.currentTimeMillis();

        StringBuilder text = new StringBuilder();

        HashMap<Event, Long> eventDistances = new HashMap<>();
        HashMap<Event, Long> blackboardEventDistances = new HashMap<>();

        Event blackboardEvent = null;

        for (Event event : events) {
            if (event.getEndTimestamp() < now) continue; // Event already ended

            if (event.getType() == EventType.BLACKBOARD) {
                blackboardEventDistances.put(event, event.getStartTimestamp());
            }

            if ((eventUpdateType != EventType.ALL && eventUpdateType != event.getType()) || !event.isActive()) continue;

            eventDistances.put(event, Math.floorDiv(event.getStartTimestamp() - now, 1000 * 60 * 60 * 24) + 1); // TODO this in new update method
        }

        eventDistances = eventDistances // Sort by ascending order
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));


        /*

        long lastEvent = 0;

        print("");
        print("");
        print("");

        for (Event event : eventDistances.keySet()) {
            print("Event: " + event.getText() + " before previous: " + (lastEvent <= event.getStartTimestamp()));
            lastEvent = event.getStartTimestamp();
        }

        print("");
        print("");
        print("");

        */

        blackboardEventDistances = blackboardEventDistances
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        for (Event event : blackboardEventDistances.keySet()) {
            if (blackboardEvent == null || event.getStartTimestamp() < now) {
                blackboardEvent = event;
            }
        }

        HashMap<Integer, HashMap<Event, Long>> eventIndex = new HashMap<>();
        HashMap<Event, Long> currentEvents = new HashMap<>();

        int eventsCounter = 0;

        print("Size: " + eventDistances.size());

        for (Event event : eventDistances.keySet()) {
            eventsCounter++;
            //print(event.getText() + ": " + event.getStartTimestamp());

            if (currentEvents.size() == 7) {
                eventIndex.put(eventIndex.size() + 1, currentEvents);
                currentEvents = new HashMap<>();
                currentEvents.put(event, eventDistances.get(event));
            } else {
                currentEvents.put(event, eventDistances.get(event));

                if (eventsCounter == eventDistances.size()) {
                    eventIndex.put(eventIndex.size() + 1, currentEvents);
                }
            }
        }

        maxPage = eventIndex.size();

        frame.getNextButton().setEnabled(maxPage != 1);

        if (maxPage == page) {
            frame.getNextButton().setEnabled(false);
        }

        String title;
        String pageIndicator = page + "/" + maxPage;

        if (blackboardEvent != null) { // TODO Fix this mess
            title = "Events (" + eventUpdateType + "), WT: " + blackboardEvent.getText();
        } else {
            title = "Events (" + eventUpdateType + "), " + page + "/" + maxPage;
        }

        Border border = BorderFactory.createTitledBorder(title);
        frame.getEventsField().setBorder(border);

        if (eventDistances.size() == 0) {
            frame.getEventsText().setText(text.toString() + "Keine Events.");
            return;
        }

        ArrayList<String> shownEvents = new ArrayList<>();

        HashMap<Event, Long> finalEvents = eventIndex.get(page);

        finalEvents = finalEvents // Sort by ascending order
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        for (Event event : finalEvents.keySet()) {
            long daysRemaining = finalEvents.get(event);

            shownEvents.add(event.getOutput());

            if (daysRemaining > 0) {
                text.append(formatCountdown(daysRemaining));
                text.append(" bis ");
                text.append(defaultEventMessages.get(event.getType()).get(0));
                text.append(event.getText());
                text.append(defaultEventMessages.get(event.getType()).get(1));
            } else {
                text.append("Heute");
                text.append(defaultEventMessages.get(event.getType()).get(2));
                text.append(event.getText());
                text.append(defaultEventMessages.get(event.getType()).get(3));
            }

            text.append("\n");

            if (!event.getDescription().equals("")) {
                text.append("    (");

                boolean newLine = false;
                for (int i = 0; i < event.getDescription().length(); i++) {
                    String character = event.getDescription().substring(i, i + 1);
                    if (character.equals(" ") && newLine) {
                        newLine = false;
                        text.append("\n    ");
                    } else {
                        text.append(character);
                    }

                    if ((i + 1) % 80 == 0) newLine = true;
                }

                text.append(")\n");
            }
        }

        boolean same = true;

        if (lastEvents.size() > 0) {
            for (String shownEvent : shownEvents) {
                if (!lastEvents.contains(shownEvent)) {
                    print("Found other event, not similar. Updating...");
                    print(shownEvent);
                    same = false;
                    break;
                }
            }
        }

        if (!same || lastEvents.size() == 0 || eventsTypeUpdated) {
            eventsTypeUpdated = false;
            frame.getEventsText().setText(text.toString());
            lastEvents = shownEvents;
        }
    }

    private static void oldUpdateEvents() { // TODO HIGH IMPORTANCE: Clean this code and remove unnecessary stuff
        long now = System.currentTimeMillis();

        StringBuilder text = new StringBuilder();

        HashMap<Event, Long> eventDistances = new HashMap<>();
        HashMap<Event, Long> blackboardEvents = new HashMap<>();

        for (Event event : events) {
            if (now - event.getEndTimestamp() < 0) continue;

            if (event.getType() == EventType.BLACKBOARD) {
                blackboardEvents.put(event, event.getStartTimestamp());
            }

            if ((eventUpdateType != EventType.ALL && eventUpdateType != event.getType()) || !event.isActive())
                continue; // If a certain update type is selected, continue the for loop
            eventDistances.put(event, Math.floorDiv(event.getStartTimestamp() - now, 1000 * 60 * 60 * 24) + 1); // TODO this in new update method
        }

        Event currentBlackboardEvent = null;

        blackboardEvents = blackboardEvents
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        for (Event event : blackboardEvents.keySet()) {
            if (currentBlackboardEvent == null || event.getStartTimestamp() < now) {
                currentBlackboardEvent = event;
            }
        }

        String title;

        if (currentBlackboardEvent != null) {
            title = "Events (" + eventUpdateType + ") WT: " + currentBlackboardEvent.getText();
        } else {
            title = "Events (" + eventUpdateType + ")";
        }

        Border border = BorderFactory.createTitledBorder(title);
        frame.getEventsField().setBorder(border);

        eventDistances = eventDistances // Sort by ascending order
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        boolean anyEvent = false;

        int lineCounter = 0;
        int eventCounter = 0;

        ArrayList<String> shownEvents = new ArrayList<>();
        HashMap<Event, Long> finalEvents = new HashMap<>();

        for (Event event : eventDistances.keySet()) {
            if (event.getEndTimestamp() > now) {
                eventCounter++;
                finalEvents.put(event, eventDistances.get(event));
            }

            if (eventCounter > page * 7) break;
        }

        print("Final Events size: " + finalEvents.size());

        for (Event event : finalEvents.keySet()) {
            long daysRemaining = finalEvents.get(event);

            if (event.getEndTimestamp() > now) {
                if (lineCounter < (page - 1) * 7) continue;

                if (lineCounter > page * 7 + 1) break;
                anyEvent = true;

                shownEvents.add(event.getOutput());

                if (daysRemaining > 0) {
                    text.append(formatCountdown(daysRemaining));
                    text.append(" bis ");
                    text.append(defaultEventMessages.get(event.getType()).get(0));
                    text.append(event.getText());
                    text.append(defaultEventMessages.get(event.getType()).get(1));
                } else {
                    text.append("Heute");
                    text.append(defaultEventMessages.get(event.getType()).get(2));
                    text.append(event.getText());
                    text.append(defaultEventMessages.get(event.getType()).get(3));
                }

                text.append("\n");
                lineCounter++;

                if (!event.getDescription().equals("")) {
                    text.append("    (");

                    boolean newLine = false;
                    for (int i = 0; i < event.getDescription().length(); i++) {
                        String character = event.getDescription().substring(i, i + 1);
                        if (character.equals(" ") && newLine) {
                            newLine = false;
                            text.append("\n    ");
                            lineCounter++;
                        } else {
                            text.append(character);
                        }

                        if ((i + 1) % 80 == 0) newLine = true;
                    }

                    text.append(")\n");
                }
            }
        }

        boolean same = true;

        if (lastEvents.size() > 0) {
            for (String shownEvent : shownEvents) {
                if (!lastEvents.contains(shownEvent)) {
                    print("Found other event, not similar. Updating...");
                    print(shownEvent);
                    same = false;
                    break;
                }
            }
        }

        if (!same || lastEvents.size() == 0 || eventsTypeUpdated) {
            eventsTypeUpdated = false;
            frame.getEventsText().setText(text.toString());
            lastEvents = shownEvents;
        }

        if (!anyEvent) {
            frame.getEventsText().setText(text.toString() + "Keine Events.");
        }
    }

    private static void updateNoten() { // TODO Sync with server
        HashMap<Note, Long> notenDistances = new HashMap<>();

        ArrayList<String> shownNoten = new ArrayList<>();

        for (Note note : person.getNoten()) {
            notenDistances.put(note, 0 - note.getRegisteredTimestamp()); // Descending order
        }

        notenDistances = notenDistances // Sort by ascending order
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        StringBuilder text = new StringBuilder();

        int counter = 0;

        for (Note note : notenDistances.keySet()) {
            counter++;
            if (counter == 8) break;

            shownNoten.add(note.getOutput());

            String subject = note.getSubject();
            //String abbreviatedSubject = subjectAbbreviations.getOrDefault(subject.contains("-") ? subject.split("-")[0] : subject.split("\\.")[0], subject); // subject_abbreviations2
            String abbreviatedSubject = subjectAbbreviations.getOrDefault(subject, subject);

            text.append(abbreviatedSubject);
            text.append(" - ");
            text.append(note.getTopic());
            text.append(" (");
            text.append(note.getGrade());
            text.append(")\n");
        }

        boolean same = true;

        if (lastNoten.size() > 0) { // TODO Implement this in updateEvents()
            for (String shownNote : shownNoten) {
                if (!lastNoten.contains(shownNote)) {
                    print("Found other note, not similar. Updating...");
                    print(shownNote);
                    same = false;
                    break;
                }
            }
        }

        if (!same || lastNoten.size() == 0) {
            frame.getNotenText().setText(text.toString());
            lastNoten = shownNoten;
        }

        if (notenDistances.size() == 0) {
            frame.getNotenText().setText("Keine Noten.");
        }
    }

    private static void updateLektionenTable() { // TODO Before first lesson countdown fix "55 Minutes"
        ArrayList<Lektion> lektionen = person.getLektionen();

        DefaultTableModel model = new DefaultTableModel();
        List<String> subjects = new ArrayList<>();
        List<String> startTimes = new ArrayList<>();
        List<String> rooms = new ArrayList<>();

        for (int i = 0; i < lektionen.size(); i++) {
            Lektion lektion = lektionen.get(i);
            long now = System.currentTimeMillis();

            // TODO RowsToColor: Make special color for DUPLICATE LESSONS / EXAMS
            if (lektion.isYellow()) getRowsToColor().put(i, 16645979);
            if (lektion.isRed()) getRowsToColor().put(i, 16485472);
            if (lektion.isLined()) getRowsToColor().put(i, 16485472);
            if (lektion.getEndTimestamp() < now) getRowsToColor().put(i, 65280);
            if (lektion.getStartTimestamp() < now && lektion.getEndTimestamp() > now) getRowsToColor().put(i, 16765851);

            String subject = lektion.getSubject();

            //String abbreviatedSubject = subjectAbbreviations.getOrDefault(subject.contains("-") ? subject.split("-")[0] : subject.split("\\.")[0], lektion.getSubject()); // subject_abbreviations2
            String abbreviatedSubject = subjectAbbreviations.getOrDefault(subject, subject);
            subjects.add(abbreviatedSubject);
            startTimes.add(lektion.getStartTime());
            rooms.add(lektion.getRoom());
        }

        model.addColumn("Lektionen", subjects.toArray());
        model.addColumn("Zeit", startTimes.toArray());
        model.addColumn("Zimmer", rooms.toArray());

        frame.getLektionenTable().setRowHeight(26);
        frame.getLektionenTable().setModel(model);

        CellRenderer renderer = new CellRenderer();
        frame.getLektionenTable().setDefaultRenderer(Object.class, renderer);
    }

    private static void updateProgressBars() {
        ArrayList<Lektion> lessons = person.getLektionen();
        long duration = 1000 * 60 * 45; // 45 minutes per lesson

        boolean currentLessonActive = false;

        Lektion lektionBeforeBreak = null;
        Lektion lektionAfterBreak = null;

        if (lessons.size() > 1) {
            lektionBeforeBreak = lessons.get(0);
            lektionAfterBreak = lessons.get(1);
        }

        boolean dayOver = false;

        ArrayList<Long> startTimes = new ArrayList<>();
        long now = System.currentTimeMillis();
        double difference;

        long totalDayHours = 0;
        long dayHoursDone = 0;

        for (int i = 0; i < lessons.size(); i++) {
            Lektion lektion = lessons.get(i);

            long startTimestamp = lektion.getStartTimestamp();
            long endTimestamp = lektion.getEndTimestamp();

            if (!startTimes.contains(startTimestamp) && (!lektion.isLined() && !lektion.isRed())) { // No double lessons and no red/lined lessons
                startTimes.add(startTimestamp);
                totalDayHours += duration;

                if (endTimestamp < now) {
                    dayHoursDone += duration;
                } else if (startTimestamp < now && endTimestamp > now) {
                    dayHoursDone += now - startTimestamp;
                }
            }

            if (now > endTimestamp && i + 1 != lessons.size() && lessons.get(i + 1).getStartTimestamp() > now) { // if i + 1 is lessons.size(), then there won't be a i + 1 lesson
                //print("Between two lessons lesson found");
                lektionBeforeBreak = lektion;
                lektionAfterBreak = lessons.get(i + 1);
            }

            if (startTimestamp < now && endTimestamp > now) {
                //print("Active lesson found");
                currentLessonActive = true;

                difference = now - startTimestamp;
                long timeLeft = endTimestamp - now;
                double percent = difference / duration * 100;

                String countdown = countdownMMHH(timeLeft);
                frame.getCurrentLessonProgressBar().setValue((int) percent);
                frame.getCurrentLessonProgressBar().setString(countdown + " bis zur Pause");
            }
        }

        // Schultag Progress Bar Updater
        if (totalDayHours - dayHoursDone == 0) {
            frame.getCurrentDayProgressBar().setValue(100);
            frame.getCurrentDayProgressBar().setString("Kein aktiver Schultag");
            dayOver = true;
        } else {
            long timeLeft = totalDayHours - dayHoursDone;
            double percent = (double) dayHoursDone / totalDayHours * 100;

            String countdown = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(timeLeft),
                    TimeUnit.MILLISECONDS.toMinutes(timeLeft) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(timeLeft)),
                    TimeUnit.MILLISECONDS.toSeconds(timeLeft) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeLeft)));

            String lessonsLeft = twoDecimalFormatter.format(startTimes.size() - (double) dayHoursDone / totalDayHours * startTimes.size());

            frame.getCurrentDayProgressBar().setValue((int) percent);
            frame.getCurrentDayProgressBar().setString("(" + lessonsLeft + ") " + countdown + " bis zum Feierabend");
        }

        // Lesson Progress Bar Updater
        if (!currentLessonActive) {
            if (dayOver) {
                frame.getCurrentLessonProgressBar().setValue(100);
                frame.getCurrentLessonProgressBar().setString("Kein aktiver Schultag");
            } else {
                if (lektionAfterBreak != null && lektionBeforeBreak != null) {
                    long timeLeft = lektionAfterBreak.getStartTimestamp() - now;
                    double percent = (double) (now - lektionBeforeBreak.getEndTimestamp()) / (lektionAfterBreak.getStartTimestamp() - lektionBeforeBreak.getEndTimestamp()) * 100D;

                    String countdown = countdownMMHH(timeLeft);
                    frame.getCurrentLessonProgressBar().setValue((int) percent);
                    frame.getCurrentLessonProgressBar().setString(countdown + " bis zur Lektion");
                }
            }
        }

        // Ferien Progress Bar Updater TODO Add Holidays Countdown when no school time is active
        long lastHolidaysEndTimestamp = 0;
        long nextHolidaysStartTimestamp = 0;
        long nextHolidaysEndTimestamp = 0;

        for (Event event : events) {
            long startTimestamp = event.getStartTimestamp();
            long endTimestamp = event.getEndTimestamp();

            if (event.getType() != EventType.HOLIDAYS) continue;

            if (lastHolidaysEndTimestamp == 0) {
                lastHolidaysEndTimestamp = endTimestamp;
            }

            if (nextHolidaysStartTimestamp == 0) {
                nextHolidaysStartTimestamp = startTimestamp;
            }

            if (nextHolidaysEndTimestamp == 0) {
                nextHolidaysEndTimestamp = endTimestamp;
            }

            if (lastHolidaysEndTimestamp < endTimestamp && endTimestamp < now) {
                lastHolidaysEndTimestamp = endTimestamp;
            }

            if (nextHolidaysStartTimestamp < startTimestamp && startTimestamp > now) {
                nextHolidaysStartTimestamp = startTimestamp;
            }
        }

        print("Last Holidays End Timestamp: " + lastHolidaysEndTimestamp);
        print("Next Holidays Start Timestamp: " + nextHolidaysStartTimestamp);

        Calendar currentCalendar = Calendar.getInstance();
        Calendar nextHolidaysCalendar = Calendar.getInstance();
        nextHolidaysCalendar.setTimeInMillis(nextHolidaysStartTimestamp);

        double percent = (double) (now - lastHolidaysEndTimestamp) / (nextHolidaysStartTimestamp - lastHolidaysEndTimestamp) * 100;

        long daysUntilHolidays = Duration.between(currentCalendar.toInstant(), nextHolidaysCalendar.toInstant()).toDays() + 1;

        if (percent < 0) { // Ferien sind aktiv
            frame.getUntilHolidaysProgressBar().setValue(100);
            frame.getUntilHolidaysProgressBar().setString("Keine aktive Schulzeit");
        } else {
            frame.getUntilHolidaysProgressBar().setValue((int) percent);
            frame.getUntilHolidaysProgressBar().setString(formatCountdown(daysUntilHolidays) + " bis zur Ferien");
        }

        /* OLD TIMESTAMP FINDER

        long lastHolidaysTimestamp = 0;
        long nextHolidaysTimestamp = 0;

        for (Event event : events) {
            if (event.getType() != EventType.HOLIDAYS) continue;

            if ((event.getEndTimestamp() > lastHolidaysTimestamp && now > event.getEndTimestamp()) || lastHolidaysTimestamp == 0) { // Find latest holidays end
                lastHolidaysTimestamp = event.getEndTimestamp();
            }

            if (nextHolidaysTimestamp == 0) {
                nextHolidaysTimestamp = event.getStartTimestamp();
                continue;
            }

            if (event.getStartTimestamp() > now && event.getStartTimestamp() < nextHolidaysTimestamp) { // Find closest holidays TODO FIX
                nextHolidaysTimestamp = event.getStartTimestamp();
            }
        }


        Calendar currentCalendar = Calendar.getInstance();
        Calendar nextHolidaysCalendar = Calendar.getInstance();
        nextHolidaysCalendar.setTimeInMillis(nextHolidaysTimestamp);

        double percent = (double) (now - lastHolidaysTimestamp) / (nextHolidaysTimestamp - lastHolidaysTimestamp) * 100;

        long daysUntilHolidays = Duration.between(currentCalendar.toInstant(), nextHolidaysCalendar.toInstant()).toDays() + 1;

        if (percent < 0) { // Ferien sind aktiv
            frame.getUntilHolidaysProgressBar().setValue(100);
            frame.getUntilHolidaysProgressBar().setString("Keine aktive Schulzeit");
        } else {
            frame.getUntilHolidaysProgressBar().setValue((int) percent);
            frame.getUntilHolidaysProgressBar().setString(formatCountdown(daysUntilHolidays) + " bis zur Ferien");
        }
        */
    }

    private static String countdownMMHH(long timeLeft) {
        return String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(timeLeft),
                TimeUnit.MILLISECONDS.toSeconds(timeLeft) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeLeft)));
    }

    private static String formatCountdown(long days) {
        StringBuilder countdown = new StringBuilder();
        double remainingDays = days;

        int months = (int) Math.floor(remainingDays / 28D);
        remainingDays -= months * 28;

        if (months > 0) {
            countdown.append(months);
            countdown.append(months > 1 ? " Monate" : " Monat");
        }

        int weeks = (int) Math.floor(remainingDays / 7D);
        remainingDays -= weeks * 7;

        if (weeks > 0) {
            if (months > 0) {
                countdown.append(", ");
            }
            countdown.append(weeks);
            countdown.append(weeks > 1 ? " Wochen" : " Woche");
        }

        if (remainingDays > 0) {
            if (months > 0 || weeks > 0) {
                countdown.append(", ");
            }

            countdown.append((int) remainingDays);
            countdown.append((int) remainingDays > 1 ? " Tage" : " Tag");
        }

        if (remainingDays == 0) {
            if (months == 0 && weeks == 0) {
                countdown.append((int) remainingDays);
                countdown.append(" Tage");
            }
        }

        return countdown.toString();
    }

    @SuppressWarnings("Duplicates")
    private static void createPopUp(Object object) {
        StringBuilder popUpText = new StringBuilder();

        if (object instanceof Note) {
            Note note = (Note) object;

            popUpText.append("Neue Note");
            popUpText.append("\n");
            popUpText.append("Fach: ");
            popUpText.append(note.getSubject());
            popUpText.append("\n");
            popUpText.append("Thema: ");
            popUpText.append(note.getTopic());
            popUpText.append("\n");
            popUpText.append("Note: ");
            popUpText.append(note.getGrade());
        } else if (object instanceof OffeneAbsenz) {
            OffeneAbsenz offeneAbsenz = (OffeneAbsenz) object;

            popUpText.append("Neue offene Absenz");
            popUpText.append("\n");
            popUpText.append("Start-Datum: ");
            popUpText.append(offeneAbsenz.getStartDate());
            popUpText.append("\n");
            popUpText.append("End-Datum: ");
            popUpText.append(offeneAbsenz.getEndDate());
            popUpText.append("\n");
            popUpText.append("Bis-Datum: ");
            popUpText.append(offeneAbsenz.getUntilDate());
        } else if (object instanceof AbsenzmeldungOhneAbsenz) {
            AbsenzmeldungOhneAbsenz absenzmeldungOhneAbsenz = (AbsenzmeldungOhneAbsenz) object;

            popUpText.append("Neue Absenzmeldung ohne Absenz");
            popUpText.append("\n");
            popUpText.append("Datum: ");
            popUpText.append(absenzmeldungOhneAbsenz.getDate());
            popUpText.append("\n");
            popUpText.append("Zeit: ");
            popUpText.append(absenzmeldungOhneAbsenz.getStartTime());
            popUpText.append(" - ");
            popUpText.append(absenzmeldungOhneAbsenz.getEndTime());
        } else if (object instanceof Lektion) {
            Lektion lektion = (Lektion) object;

            popUpText.append("Lektionen Änderung");
            popUpText.append("\n");
            popUpText.append("Thema: ");
            popUpText.append(lektion.getSubject());
            popUpText.append("\n");
            popUpText.append("Zeit: ");
            popUpText.append(lektion.getStartTime());
            popUpText.append(" - ");
            popUpText.append(lektion.getEndTime());
            popUpText.append("\n");
            popUpText.append("Raum: ");
            popUpText.append(lektion.getRoom());
        } else {
            print(object);
            JOptionPane.showMessageDialog(null, "Fatal Error: No matching object to " + object, "Fatal Error", JOptionPane.ERROR_MESSAGE);
            Runtime.getRuntime().exit(1);
        }

        PopUpManager.createPopUp(popUpText.toString());
    }

    private static void comparePersonObjects(Person newPerson) { // Parameter is the new person object, the old person object is the global person variable
        Person oldPerson = person;

        if (newPerson.getSourceSplit().equals(oldPerson.getSourceSplit())) {
            print("No source changes detected.");
        } else {
            print("Source changes detected.");

            StringBuilder output = new StringBuilder();
            int changes = 0;

            for (Note note : newPerson.getNoten()) {
                if (!oldPerson.containsNote(note)) {
                    changes++;
                    output.append(note.getOutput());
                    output.append("\n");
                    print("Found new Note: " + note.getOutput());
                    createPopUp(note);
                }
            }

            for (OffeneAbsenz offeneAbsenz : newPerson.getOffeneAbsenzen()) {
                if (!oldPerson.containsOffeneAbsenz(offeneAbsenz)) {
                    changes++;
                    output.append(offeneAbsenz.getOutput());
                    output.append("\n");
                    print("Found new offene Absenz: " + offeneAbsenz.getOutput());
                    createPopUp(offeneAbsenz);
                }
            }

            for (AbsenzmeldungOhneAbsenz absenzmeldungOhneAbsenz : newPerson.getAbsenzmeldungenOhneAbsenz()) {
                if (!oldPerson.containsAbsenzeldungOhneAbsenz(absenzmeldungOhneAbsenz)) {
                    changes++;
                    output.append(absenzmeldungOhneAbsenz.getOutput());
                    output.append("\n");
                    print("Found new Absenzmeldung ohne Absenz: " + absenzmeldungOhneAbsenz.getOutput());
                    createPopUp(absenzmeldungOhneAbsenz);
                }
            }

            for (Lektion lektion : newPerson.getLektionen()) {
                if (oldPerson.getDate().equals(newPerson.getDate()) && !oldPerson.containsLesson(lektion)) { // check if date is also the same
                    changes++;
                    output.append(lektion.getOutput());
                    output.append("\n");
                    print("Found new Lektion: " + lektion);
                    createPopUp(lektion);
                }
            }

            if (changes > 0) {
                print("Changes detected (" + changes + ").");
                print(output.toString());
                updateNoten();
            } else {
                print("No changes detected. Date switch: " + !oldPerson.getDate().equals(newPerson.getDate()));
            }
        }

        /*if (person.isEventsUpdate()) {
            updateEvents();
        }*/

        saveFile(convertPersonToJson(newPerson).toString(4), "person.json");
        person = newPerson;
    }

    @SuppressWarnings("Duplicates")
    private static JSONObject convertPersonToJson(Person person) { // Save everytime when new request returns, and read json first time only in initialize()
        JSONObject jsonPerson = new JSONObject();
        jsonPerson.put("date", person.getDate());
        jsonPerson.put("time", person.getTime());
        jsonPerson.put("timestamp", person.getTimestamp());

        jsonPerson.put("source", person.getSource());
        jsonPerson.put("sourceSplit", person.getSourceSplit());

        JSONArray jsonNoten = new JSONArray();

        for (Note note : person.getNoten()) {
            JSONObject jsonNote = new JSONObject();

            jsonNote.put("subject", note.getSubject());
            jsonNote.put("topic", note.getTopic());
            jsonNote.put("grade", note.getGrade());
            jsonNote.put("date", note.getDate());
            jsonNote.put("dateTimestamp", note.getDateTimestamp());
            jsonNote.put("output", note.getOutput());

            jsonNoten.put(jsonNote);
        }

        jsonPerson.put("noten", jsonNoten);


        JSONArray jsonOffeneAbsenzen = new JSONArray();

        for (OffeneAbsenz offeneAbsenz : person.getOffeneAbsenzen()) {
            JSONObject jsonOffeneAbsenz = new JSONObject();

            jsonOffeneAbsenz.put("startDate", offeneAbsenz.getStartDate());
            jsonOffeneAbsenz.put("endDate", offeneAbsenz.getEndDate());
            jsonOffeneAbsenz.put("untilDate", offeneAbsenz.getUntilDate());
            jsonOffeneAbsenz.put("startTimestamp", offeneAbsenz.getStartTimestamp());
            jsonOffeneAbsenz.put("endTimestamp", offeneAbsenz.getEndTimestamp());
            jsonOffeneAbsenz.put("untilTimestamp", offeneAbsenz.getUntilTimestamp());
            jsonOffeneAbsenz.put("output", offeneAbsenz.getOutput());

            jsonOffeneAbsenzen.put(jsonOffeneAbsenz);
        }

        jsonPerson.put("offene_absenzen", jsonOffeneAbsenzen);


        JSONArray jsonAbsenzmeldungenOhneAbsenz = new JSONArray();

        for (AbsenzmeldungOhneAbsenz absenzmeldungOhneAbsenz : person.getAbsenzmeldungenOhneAbsenz()) {
            JSONObject jsonAbsenzmeldungOhneAbsenz = new JSONObject();

            jsonAbsenzmeldungOhneAbsenz.put("date", absenzmeldungOhneAbsenz.getDate());
            jsonAbsenzmeldungOhneAbsenz.put("startTime", absenzmeldungOhneAbsenz.getStartTime());
            jsonAbsenzmeldungOhneAbsenz.put("endTime", absenzmeldungOhneAbsenz.getEndTime());
            jsonAbsenzmeldungOhneAbsenz.put("startTimestamp", absenzmeldungOhneAbsenz.getStartTimestamp());
            jsonAbsenzmeldungOhneAbsenz.put("endTimestamp", absenzmeldungOhneAbsenz.getEndTimestamp());
            jsonAbsenzmeldungOhneAbsenz.put("output", absenzmeldungOhneAbsenz.getOutput());

            jsonAbsenzmeldungenOhneAbsenz.put(jsonAbsenzmeldungOhneAbsenz);
        }

        jsonPerson.put("absenzmeldungen_ohne_absenz", jsonAbsenzmeldungenOhneAbsenz);


        JSONArray jsonLektionen = new JSONArray();

        for (Lektion lektion : person.getLektionen()) {
            JSONObject jsonLektion = new JSONObject();

            jsonLektion.put("subject", lektion.getSubject());
            jsonLektion.put("room", lektion.getRoom());
            jsonLektion.put("yellow", lektion.isYellow());
            jsonLektion.put("red", lektion.isRed());
            jsonLektion.put("lined", lektion.isLined());
            jsonLektion.put("startTime", lektion.getStartTime());
            jsonLektion.put("endTime", lektion.getEndTime());
            jsonLektion.put("startTimestamp", lektion.getStartTimestamp());
            jsonLektion.put("endTimestamp", lektion.getEndTimestamp());
            jsonLektion.put("output", lektion.getOutput());

            jsonLektionen.put(jsonLektion);
        }

        jsonPerson.put("lektionen", jsonLektionen);

        return jsonPerson;
    }

    private static Person convertJsonToPerson(org.json.JSONObject jsonObject) {
        ArrayList<Note> noten = new ArrayList<>();
        ArrayList<OffeneAbsenz> offeneAbsenzen = new ArrayList<>();
        ArrayList<AbsenzmeldungOhneAbsenz> absenzmeldungenOhneAbsenz = new ArrayList<>();
        ArrayList<Lektion> lektionen = new ArrayList<>();

        org.json.JSONArray jsonNoten = jsonObject.getJSONArray("noten");
        org.json.JSONArray jsonOffeneAbsenzen = jsonObject.getJSONArray("offene_absenzen");
        org.json.JSONArray jsonAbsenzmeldungenOhneAbsenz = jsonObject.getJSONArray("absenzmeldungen_ohne_absenz");
        org.json.JSONArray jsonLektionen = jsonObject.getJSONArray("lektionen");

        for (int i = 0; i < jsonNoten.length(); i++) {
            org.json.JSONObject jsonNote = jsonNoten.getJSONObject(i);

            Note note = new Note(
                    jsonNote.getString("date"),
                    jsonNote.getLong("dateTimestamp"),
                    jsonNote.getLong("dateTimestamp"), // TODO Change to registeredTimestamp once server is updated
                    jsonNote.getString("subject"),
                    jsonNote.getString("topic"),
                    jsonNote.getString("grade"),
                    jsonNote.getString("output")
            );

            noten.add(note);
        }

        for (int i = 0; i < jsonOffeneAbsenzen.length(); i++) {
            org.json.JSONObject jsonOffeneAbsenz = jsonOffeneAbsenzen.getJSONObject(i);

            OffeneAbsenz offeneAbsenz = new OffeneAbsenz(
                    jsonOffeneAbsenz.getString("startDate"),
                    jsonOffeneAbsenz.getString("endDate"),
                    jsonOffeneAbsenz.getString("untilDate"),
                    jsonOffeneAbsenz.getLong("startTimestamp"),
                    jsonOffeneAbsenz.getLong("endTimestamp"),
                    jsonOffeneAbsenz.getLong("untilTimestamp"),
                    jsonOffeneAbsenz.getString("output")
            );

            offeneAbsenzen.add(offeneAbsenz);
        }

        for (int i = 0; i < jsonAbsenzmeldungenOhneAbsenz.length(); i++) {
            org.json.JSONObject jsonAbsenzmeldungOhneAbsenz = jsonAbsenzmeldungenOhneAbsenz.getJSONObject(i);

            AbsenzmeldungOhneAbsenz absenzmeldungOhneAbsenz = new AbsenzmeldungOhneAbsenz(
                    jsonAbsenzmeldungOhneAbsenz.getString("date"),
                    jsonAbsenzmeldungOhneAbsenz.getString("startTime"),
                    jsonAbsenzmeldungOhneAbsenz.getString("endTime"),
                    jsonAbsenzmeldungOhneAbsenz.getLong("startTimestamp"),
                    jsonAbsenzmeldungOhneAbsenz.getLong("endTimestamp"),
                    jsonAbsenzmeldungOhneAbsenz.getString("output")
            );

            absenzmeldungenOhneAbsenz.add(absenzmeldungOhneAbsenz);
        }

        for (int i = 0; i < jsonLektionen.length(); i++) {
            org.json.JSONObject jsonLektion = jsonLektionen.getJSONObject(i);

            Lektion lektion = new Lektion(
                    jsonLektion.getString("subject"),
                    jsonLektion.getString("room"),
                    jsonLektion.getString("startTime"),
                    jsonLektion.getString("endTime"),
                    jsonLektion.getBoolean("red"),
                    jsonLektion.getBoolean("yellow"),
                    jsonLektion.getBoolean("lined"),
                    jsonLektion.getLong("startTimestamp"),
                    jsonLektion.getLong("endTimestamp"),
                    jsonLektion.getString("output")
            );

            lektionen.add(lektion);
        }

        return new Person(
                jsonObject.getLong("timestamp"),
                jsonObject.getString("date"),
                jsonObject.getString("time"),
                jsonObject.getString("source"),
                jsonObject.getString("sourceSplit"),
                noten,
                offeneAbsenzen,
                absenzmeldungenOhneAbsenz,
                lektionen
        );
    }

    @SuppressWarnings("SameParameterValue")
    private static void exit(int status) {
        print("Exiting with code: " + status);
        Runtime.getRuntime().exit(status);
    }

    private static void update() {
        updateEvents();
        updateNoten();
        updateLektionenTable();
        updateProgressBars();
    }

    public static void main(String[] args) {
        if (!preinitialize()) exit(1);

        frame = new Frame();

        jFrame = new JFrame(Reference.VERSION);
        jFrame.setIconImage(new ImageIcon(Objects.requireNonNull(Client.class.getClassLoader().getResource("ss.gif"))).getImage());
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setContentPane(frame.getPanel());
        jFrame.pack();

        if (!postinitialize()) exit(1);

        Timer timerSecondly = new Timer();
        TimerTask timerTaskSecondly = new TimerTask() {
            @Override
            public void run() {
                update();
            }
        };

        Timer timerMutliSecondly = new Timer();
        TimerTask timerTaskMultiSecondly = new TimerTask() {
            @Override
            public void run() {
                try {
                    if (System.currentTimeMillis() - lastRequest < 750 && lastRequest != 0) return; // Debug

                    lastRequest = System.currentTimeMillis();

                    Person newPerson = convertJsonToPerson(downloadData().getObject());

                    comparePersonObjects(newPerson);

                    //loadEvents(downloadEvents().getArray()); // Update events
                } catch (Exception e) {
                    e.printStackTrace();
                    failures++;
                    jFrame.setTitle(Reference.VERSION + " - Failure count: " + failures);
                    print("Exception caught while running multi seconds timer task");
                }
            }
        };

        timerSecondly.scheduleAtFixedRate(timerTaskSecondly, 0, 1000);
        timerMutliSecondly.scheduleAtFixedRate(timerTaskMultiSecondly, 1, 1000 * 3);

        frame.getAddEventButton().addActionListener(e -> {
            print("Add Event Button clicked");

            JComboBox eventTypesBox = new JComboBox();
            DefaultComboBoxModel eventTypesModel = new DefaultComboBoxModel();

            for (EventType eventType : EventType.values()) {
                eventTypesModel.addElement(eventType);
            }

            eventTypesBox.setModel(eventTypesModel);

            JTextField eventTextText = new JTextField();
            JTextField eventDescriptionText = new JTextField();
            JDateChooser jDateChooser = new JDateChooser();

            Object[] message = {
                    "Typ: ", eventTypesBox,
                    "Text: ", eventTextText,
                    "Beschreibung: ", eventDescriptionText,
                    "Datum: ", jDateChooser // TODO Add End Datum!
            };

            int option = JOptionPane.showConfirmDialog(null, message, "Event hinzufügen", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                EventType eventType = EventType.valueOf(String.valueOf(eventTypesBox.getSelectedItem()));
                Date startDate = jDateChooser.getDate();
                addEvent(eventType, eventTextText.getText(), eventDescriptionText.getText(), startDate.getTime());
            }
        });

        frame.getRemoveEventButton().addActionListener(e -> {
            print("Remove Event Button clicked");

            JComboBox eventTypesBox = new JComboBox();
            DefaultComboBoxModel eventTypesModel = new DefaultComboBoxModel();

            for (EventType eventType : EventType.values()) {
                eventTypesModel.addElement(eventType);
            }

            eventTypesBox.setModel(eventTypesModel);

            Object[] message = {
                    "Event Typ: ", eventTypesBox
            };

            int option = JOptionPane.showConfirmDialog(null, message, "Event entfernen", JOptionPane.OK_CANCEL_OPTION);

            EventType selectedType;

            if (option == JOptionPane.OK_OPTION) {
                selectedType = EventType.valueOf(String.valueOf(eventTypesBox.getSelectedItem()));
            } else {
                return;
            }

            JComboBox eventsBox = new JComboBox();
            DefaultComboBoxModel eventsModel = new DefaultComboBoxModel();

            LinkedHashMap<Event, Long> selectedEvents = new LinkedHashMap<>();

            for (Event event : events) {
                if (event.getType() == selectedType || selectedType == EventType.ALL) {
                    selectedEvents.put(event, event.getStartTimestamp());
                }
            }

            selectedEvents = selectedEvents // Sort by ascending order
                    .entrySet()
                    .stream()
                    .sorted(comparingByValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

            for (Event event : selectedEvents.keySet()) {
                String element = "";

                if (event.getType() == selectedType || selectedType == EventType.ALL) {
                    element = ddMMyyyy.format(new Date(event.getStartTimestamp())) + " " + event.getText();
                }

                if (selectedType == EventType.ALL) {
                    element += " " + event.getType();
                }

                eventsModel.addElement(element);
            }

            eventsBox.setModel(eventsModel);

            message = new Object[]{
                    "Event: ", eventsBox
            };

            option = JOptionPane.showConfirmDialog(null, message, "Event entfernen", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                Event event = (Event) (selectedEvents.keySet().toArray()[eventsBox.getSelectedIndex()]);
                print(event.getType() + " " + event.getText() + " " + event.getStartTimestamp());
            }
        });

        frame.getPreviousButton().addActionListener(e -> { // TODO Finish this
            print("Previous Button clicked");

            page--;

            if (page == 1) {
                frame.getPreviousButton().setEnabled(false);
            }

            if (page < maxPage) {
                frame.getNextButton().setEnabled(true);
            }

            updateEvents();
        });

        frame.getNextButton().addActionListener(e -> { // TODO Finish this
            print("Next Button clicked");

            page++;

            if (page == maxPage) {
                frame.getNextButton().setEnabled(false);
            }

            if (page > 1) {
                frame.getPreviousButton().setEnabled(true);
            }

            updateEvents();
        });

        frame.getSettingsButton().addActionListener(e -> { // TODO Settings: Config
            print("Settings Button clicked");

            JButton creditsButton = new JButton();

            Object[] message = {
                    "Credits: ", creditsButton
            };

            creditsButton.addActionListener(e2 -> {
                Object[] credits = {
                        "Ardit Citaku 2019"
                };
                JOptionPane.showConfirmDialog(null, credits, "Credits", JOptionPane.OK_CANCEL_OPTION);
            });

            JOptionPane jOptionPane = new JOptionPane();

            int option = jOptionPane.showConfirmDialog(null, message, "Event hinzufügen", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {

            }
        });

        frame.getAllHausaufgabenButton().addActionListener(e -> {
            print("All Hausaufgaben Button clicked");
            setUpdateType(EventType.HOMEWORK);
        });

        frame.getAllExamsButton().addActionListener(e -> {
            print("All Exams Button clicked");
            setUpdateType(EventType.EXAM);
        });

        frame.getAllExcursionsButton().addActionListener(e -> {
            print("All Excursions Button clicked");
            setUpdateType(EventType.EXCURSION);
        });

        frame.getAllHolidaysButton().addActionListener(e -> {
            print("All Holidays Button clicked");
            setUpdateType(EventType.HOLIDAYS);
        });

        frame.getAllBirthdaysButton().addActionListener(e -> {
            print("All Birthdays Button clicked");
            setUpdateType(EventType.BIRTHDAY);
        });

        frame.getAllCustomButton().addActionListener(e -> {
            print("All Custom Button clicked");
            setUpdateType(EventType.CUSTOM);
        });
    }
}
