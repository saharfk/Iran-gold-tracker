package com.codogrammer.irangoldtracker.utils;

import com.github.mfathi91.time.PersianDate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class JalaliDateTime {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private JalaliDateTime() {
    }

    public static String now() {
        return format(LocalDateTime.now(TEHRAN));
    }

    public static String format(LocalDateTime dateTime) {

        PersianDate date = PersianDate.fromGregorian(dateTime.toLocalDate());

        return "%04d/%02d/%02d - %s".formatted(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                TIME_FORMAT.format(dateTime)
        );
    }
}
