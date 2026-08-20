package com.devfuel.temporal;

import com.devfuel.common.EventTimePrecision;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic occurrence-time resolver. Does not call a clock or the network.
 * Calendar math uses the configured IANA zone; stored instants are UTC.
 */
public class TemporalResolver {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private static final LocalTime MORNING = LocalTime.of(8, 0);
    private static final LocalTime LUNCH = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON = LocalTime.of(14, 0);
    private static final LocalTime EVENING = LocalTime.of(18, 0);
    private static final LocalTime NIGHT = LocalTime.of(21, 0);
    private static final LocalTime NOON = LocalTime.of(12, 0);

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_DATE = Pattern.compile("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "\\b(\\d{1,2})\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?"
                    + "|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLOCK_AMPM = Pattern.compile(
            "\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLOCK_24H = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    private static final Pattern PAST = Pattern.compile(
            "\\b(tha|thi|the|khaya|khayi|kiye|liye|hua|hui|peeli|piya)\\b"
    );
    private static final Pattern FUTURE = Pattern.compile("\\b(will|unga|ungi|khaunga|khaungi|piunga|piungi)\\b");

    private final ZoneId zone;

    public TemporalResolver() {
        this(DEFAULT_ZONE);
    }

    public TemporalResolver(ZoneId zone) {
        this.zone = zone == null ? DEFAULT_ZONE : zone;
    }

    public TemporalResolution resolve(String rawText, Instant now) {
        Instant captured = now == null ? Instant.EPOCH : now;
        try {
            return resolveInternal(rawText, captured);
        } catch (RuntimeException ex) {
            return nowResolution(captured);
        }
    }

    private TemporalResolution resolveInternal(String rawText, Instant now) {
        if (rawText == null || rawText.isBlank()) {
            return nowResolution(now);
        }

        String text = normalize(rawText);
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate today = localNow.toLocalDate();
        int hour = localNow.getHour();

        if (isFutureKal(text)) {
            return nowResolution(now);
        }

        LocalDate explicitDate = parseExplicitDate(text, today);
        RelativeDay relative = parseRelativeDay(text);
        Period period = parsePeriod(text);
        LocalTime clock = parseClock(text);

        LocalDate date = explicitDate;
        if (date == null) {
            date = switch (relative) {
                case PARSO -> today.minusDays(2);
                case YESTERDAY -> today.minusDays(1);
                case TODAY -> today;
                case NONE -> null;
            };
        }

        if (explicitDate == null) {
            if (period == Period.LAST_NIGHT) {
                date = today.minusDays(1);
            } else if (period == Period.AAJ_RAAT) {
                date = hour < 5 ? today.minusDays(1) : today;
            }
        }

        if (clock != null) {
            LocalDate clockDate = date == null ? today : date;
            return at(clockDate, clock, EventTimePrecision.EXACT);
        }

        LocalTime periodTime = sentinel(period);
        if (periodTime != null) {
            LocalDate periodDate = date == null ? today : date;
            return at(periodDate, periodTime, EventTimePrecision.PERIOD);
        }

        if (date != null && relative != RelativeDay.TODAY) {
            return at(date, NOON, EventTimePrecision.DAY);
        }

        return nowResolution(now);
    }

    private TemporalResolution at(LocalDate date, LocalTime time, EventTimePrecision precision) {
        Instant occurrence = ZonedDateTime.of(date, time, zone).toInstant();
        return new TemporalResolution(occurrence, precision, zone.getId());
    }

    private TemporalResolution nowResolution(Instant now) {
        return new TemporalResolution(now, EventTimePrecision.NOW, zone.getId());
    }

    private static String normalize(String rawText) {
        return rawText.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean isFutureKal(String text) {
        if (!containsWord(text, "kal")) {
            return false;
        }
        boolean future = FUTURE.matcher(text).find();
        boolean past = PAST.matcher(text).find();
        return future && !past;
    }

    private LocalDate parseExplicitDate(String text, LocalDate today) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group(1));
            } catch (DateTimeParseException ex) {
                return null;
            }
        }

        Matcher dmy = DMY_DATE.matcher(text);
        if (dmy.find()) {
            try {
                int day = Integer.parseInt(dmy.group(1));
                int month = Integer.parseInt(dmy.group(2));
                int year = Integer.parseInt(dmy.group(3));
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ex) {
                return null;
            }
        }

        Matcher dayMonth = DAY_MONTH.matcher(text);
        if (dayMonth.find()) {
            String fragment = dayMonth.group(1) + " " + dayMonth.group(2);
            LocalDate parsed = parseDayMonth(fragment, today.getYear());
            if (parsed == null) {
                return null;
            }
            if (parsed.isAfter(today)) {
                parsed = parsed.minusYears(1);
            }
            return parsed;
        }

        return null;
    }

    private LocalDate parseDayMonth(String fragment, int year) {
        String withYear = fragment + " " + year;
        for (String pattern : List.of("d MMM uuuu", "d MMMM uuuu")) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pattern)
                        .toFormatter(Locale.ENGLISH);
                return LocalDate.parse(withYear, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }

    private RelativeDay parseRelativeDay(String text) {
        if (text.contains("day before yesterday") || containsWord(text, "parson") || containsWord(text, "parso")) {
            return RelativeDay.PARSO;
        }
        if (containsWord(text, "yesterday") || containsWord(text, "kal")) {
            return RelativeDay.YESTERDAY;
        }
        if (containsWord(text, "today") || containsWord(text, "aaj")) {
            return RelativeDay.TODAY;
        }
        return RelativeDay.NONE;
    }

    private Period parsePeriod(String text) {
        if (text.contains("last night")) {
            return Period.LAST_NIGHT;
        }
        if (text.contains("aaj raat")) {
            return Period.AAJ_RAAT;
        }
        if (text.contains("office lunch") || containsWord(text, "lunch")) {
            return Period.LUNCH;
        }
        if (containsWord(text, "subah") || containsWord(text, "morning")) {
            return Period.MORNING;
        }
        if (containsWord(text, "dopahar") || containsWord(text, "afternoon")) {
            return Period.AFTERNOON;
        }
        if (containsWord(text, "shaam") || containsWord(text, "evening")) {
            return Period.EVENING;
        }
        if (containsWord(text, "raat") || containsWord(text, "night")) {
            return Period.NIGHT;
        }
        return Period.NONE;
    }

    private LocalTime sentinel(Period period) {
        return switch (period) {
            case MORNING -> MORNING;
            case LUNCH -> LUNCH;
            case AFTERNOON -> AFTERNOON;
            case EVENING -> EVENING;
            case NIGHT, LAST_NIGHT, AAJ_RAAT -> NIGHT;
            case NONE -> null;
        };
    }

    private LocalTime parseClock(String text) {
        Matcher ampm = CLOCK_AMPM.matcher(text);
        if (ampm.find()) {
            int hour = Integer.parseInt(ampm.group(1));
            int minute = ampm.group(2) == null ? 0 : Integer.parseInt(ampm.group(2));
            String meridiem = ampm.group(3).replace(".", "").toLowerCase(Locale.ROOT);
            if (meridiem.startsWith("p") && hour != 12) {
                hour += 12;
            }
            if (meridiem.startsWith("a") && hour == 12) {
                hour = 0;
            }
            return LocalTime.of(hour, minute);
        }
        Matcher h24 = CLOCK_24H.matcher(text);
        if (h24.find()) {
            return LocalTime.of(Integer.parseInt(h24.group(1)), Integer.parseInt(h24.group(2)));
        }
        return null;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private enum RelativeDay {
        NONE,
        TODAY,
        YESTERDAY,
        PARSO
    }

    private enum Period {
        NONE,
        MORNING,
        LUNCH,
        AFTERNOON,
        EVENING,
        NIGHT,
        LAST_NIGHT,
        AAJ_RAAT
    }
}
