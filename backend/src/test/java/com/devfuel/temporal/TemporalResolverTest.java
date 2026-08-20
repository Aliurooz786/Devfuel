package com.devfuel.temporal;

import com.devfuel.common.EventTimePrecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporalResolverTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant DEFAULT_NOW = local(2026, 8, 20, 17, 10);
    private static final Instant ONE_AM = local(2026, 8, 21, 1, 15);
    private static final Instant AFTERNOON = local(2026, 8, 21, 15, 0);

    private final TemporalResolver resolver = new TemporalResolver(IST);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "U1, 1 cigarette pee li, 2026-08-20, 17:10, NOW",
            "U2, Aaj subah 2 cigarette pi, 2026-08-20, 08:00, PERIOD",
            "U3, today mood kharab hai, 2026-08-20, 17:10, NOW",
            "U4, Kal raat tahri khayi thi, 2026-08-19, 21:00, PERIOD",
            "U5, yesterday I had tahri, 2026-08-19, 12:00, DAY",
            "U6, Yesterday morning mood kharab tha, 2026-08-19, 08:00, PERIOD",
            "U7, Parso 2 cigarette pi thi, 2026-08-18, 12:00, DAY",
            "U8, Kal office lunch me samosa khaya tha, 2026-08-19, 13:00, PERIOD",
            "U9, 15 Aug ko weight 93.5, 2026-08-15, 12:00, DAY",
            "U10, 2026-08-18 2 cigarette, 2026-08-18, 12:00, DAY",
            "U11, tahri khayi thi, 2026-08-20, 17:10, NOW",
            "U12, '  ', 2026-08-20, 17:10, NOW",
            "U13, kal 2026-08-01 tahri, 2026-08-01, 12:00, DAY"
    })
    void defaultClockCases(String id, String input, String date, String time, EventTimePrecision precision) {
        assertResolution(DEFAULT_NOW, input, date, time, precision);
    }

    @Test
    void u14KalRaatAtOneAmIsPreviousCalendarNight() {
        assertResolution(ONE_AM, "kal raat tahri khayi thi", "2026-08-20", "21:00", EventTimePrecision.PERIOD);
    }

    @Test
    void u15AajRaatAtOneAmUsesNightOwlRule() {
        assertResolution(ONE_AM, "aaj raat", "2026-08-20", "21:00", EventTimePrecision.PERIOD);
    }

    @Test
    void u16AajRaatInAfternoonIsTonight() {
        assertResolution(AFTERNOON, "aaj raat", "2026-08-21", "21:00", EventTimePrecision.PERIOD);
    }

    @Test
    void u17FutureKalIsNow() {
        assertResolution(DEFAULT_NOW, "kal cigarette piungi", "2026-08-20", "17:10", EventTimePrecision.NOW);
    }

    @Test
    void periodWithoutDayIsToday() {
        assertResolution(DEFAULT_NOW, "subah 2 cigarette", "2026-08-20", "08:00", EventTimePrecision.PERIOD);
    }

    @Test
    void blankIsNow() {
        TemporalResolution result = resolver.resolve("", DEFAULT_NOW);
        assertEquals(DEFAULT_NOW, result.occurrence());
        assertEquals(EventTimePrecision.NOW, result.precision());
        assertEquals("Asia/Kolkata", result.zone());
    }

    @Test
    void nullIsNow() {
        TemporalResolution result = resolver.resolve(null, DEFAULT_NOW);
        assertEquals(DEFAULT_NOW, result.occurrence());
        assertEquals(EventTimePrecision.NOW, result.precision());
    }

    private void assertResolution(
            Instant now,
            String input,
            String date,
            String time,
            EventTimePrecision precision
    ) {
        TemporalResolution result = resolver.resolve(input, now);
        LocalDate expectedDate = LocalDate.parse(date);
        LocalTime expectedTime = LocalTime.parse(time);
        Instant expected = ZonedDateTime.of(expectedDate, expectedTime, IST).toInstant();
        assertEquals(precision, result.precision(), input);
        assertEquals(expected, result.occurrence(), input);
        assertEquals("Asia/Kolkata", result.zone());
    }

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, IST).toInstant();
    }
}
