package com.np.schedule.time;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern TIME_RE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2})$");
    private static final Pattern DATE_RE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern CLOCK_RE = Pattern.compile("^(\\d{2}):(\\d{2})$");

    private TimeRules() {
    }

    public static final class Stamp {
        public final boolean ok;
        public final String message;
        public final String text;
        public final long ms;

        private Stamp(boolean ok, String message, String text, long ms) {
            this.ok = ok;
            this.message = message;
            this.text = text;
            this.ms = ms;
        }
    }

    public static final class Clock {
        public final boolean ok;
        public final String message;
        public final String text;

        private Clock(boolean ok, String message, String text) {
            this.ok = ok;
            this.message = message;
            this.text = text;
        }
    }

    public static final class Range {
        public final boolean ok;
        public final String message;
        public final Stamp start;
        public final Stamp end;

        private Range(boolean ok, String message, Stamp start, Stamp end) {
            this.ok = ok;
            this.message = message;
            this.start = start;
            this.end = end;
        }
    }

    public static final class Dates {
        public final boolean ok;
        public final String message;
        public final List<String> days;

        private Dates(boolean ok, String message, List<String> days) {
            this.ok = ok;
            this.message = message;
            this.days = days;
        }
    }

    public static Stamp parseTime(String value) {
        String text = value == null ? "" : value.trim();
        Matcher match = TIME_RE.matcher(text);
        if (!match.matches()) {
            return failStamp("时间必须为 yyyy-MM-dd HH:mm，不能填写“上午”这类模糊内容");
        }
        int year = Integer.parseInt(match.group(1));
        int month = Integer.parseInt(match.group(2));
        int day = Integer.parseInt(match.group(3));
        int hour = Integer.parseInt(match.group(4));
        int minute = Integer.parseInt(match.group(5));
        if (month < 1 || month > 12 || hour > 23 || minute > 59) {
            return failStamp("时间字段超出范围");
        }
        try {
            LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute);
            long ms = dateTime.atZone(ZONE).toInstant().toEpochMilli();
            return new Stamp(true, "", text, ms);
        } catch (DateTimeException ex) {
            return failStamp("日期不存在");
        }
    }

    public static Clock parseClock(String value) {
        String text = value == null ? "" : value.trim();
        Matcher match = CLOCK_RE.matcher(text);
        if (!match.matches()) {
            return new Clock(false, "每日时刻必须由你填写为 HH:mm，例如 09:30", "");
        }
        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour > 23 || minute > 59) {
            return new Clock(false, "时刻超出范围", "");
        }
        return new Clock(true, "", text);
    }

    public static Stamp parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (!DATE_RE.matcher(text).matches()) {
            return failStamp("日期必须为 yyyy-MM-dd");
        }
        return parseTime(text + " 00:00");
    }

    public static Range parseRange(String startText, String endText) {
        Stamp start = parseTime(startText);
        if (!start.ok) {
            return new Range(false, start.message, null, null);
        }
        Stamp end = parseTime(endText);
        if (!end.ok) {
            return new Range(false, end.message, null, null);
        }
        if (end.ms <= start.ms) {
            return new Range(false, "结束时间必须晚于开始时间", null, null);
        }
        return new Range(true, "", start, end);
    }

    public static boolean overlaps(long startA, long endA, long startB, long endB) {
        return startA < endB && startB < endA;
    }

    public static boolean clockOrderOk(String startClock, String endClock) {
        LocalTime start = LocalTime.parse(startClock);
        LocalTime end = LocalTime.parse(endClock);
        return end.isAfter(start);
    }

    public static Dates eachDate(String startText, String endText) {
        Stamp start = parseDate(startText);
        if (!start.ok) {
            return new Dates(false, start.message, null);
        }
        Stamp end = parseDate(endText);
        if (!end.ok) {
            return new Dates(false, end.message, null);
        }
        LocalDate first = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start.ms), ZONE).toLocalDate();
        LocalDate last = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end.ms), ZONE).toLocalDate();
        if (last.isBefore(first)) {
            return new Dates(false, "结束日期不能早于开始日期", null);
        }
        List<String> days = new ArrayList<String>();
        for (LocalDate cursor = first; !cursor.isAfter(last); cursor = cursor.plusDays(1)) {
            days.add(cursor.toString());
            if (days.size() > 31) {
                return new Dates(false, "一次模板展开不超过 31 天", null);
            }
        }
        return new Dates(true, "", days);
    }

    private static Stamp failStamp(String message) {
        return new Stamp(false, message, "", 0L);
    }
}
