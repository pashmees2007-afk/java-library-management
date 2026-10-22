package com.library.model;

import java.time.LocalDate;

/** Books issued on one day, for the activity chart. */
public record DailyCount(LocalDate day, int issued) {
}
