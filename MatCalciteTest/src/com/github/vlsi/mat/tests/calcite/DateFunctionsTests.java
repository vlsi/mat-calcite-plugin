package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

/**
 * Date functions on the sample dump (a JDK 8 dump with java.util.Date and GregorianCalendar objects) and on plain
 * numbers. The java.time and java.sql types were checked by hand against a dump made for it; see MIGRATION-NOTES.
 */
public class DateFunctionsTests extends SampleHeapDumpTests {

  @Test
  public void utilDateInUtcAndInTheDumpedJvmZone() throws SQLException {
    returnsInOrder("select toDateTime(this) d, toDateTime(this, 'jvm') jvm, toEpochMillis(this) ms "
            + "from \"java.util.Date\" limit 1",
        "d|jvm|ms",
        "1934-11-08 21:09:09.328|1934-11-09 00:09:09.328|-1109127050672");
  }

  @Test
  public void calendarInItsOwnZone() throws SQLException {
    returnsInOrder("select toDateTime(this) d, toDateTime(this, 'UTC') utc, toEpochMillis(this) ms "
            + "from \"java.util.GregorianCalendar\" limit 1",
        "d|utc|ms",
        "1934-11-09 00:09:09.328 Europe/Moscow|1934-11-08 21:09:09.328|-1109127050672");
  }

  @Test
  public void epochNumbers() throws SQLException {
    returnsInOrder("select millisToDateTime(0) a, millisToDateTime(0, 'Europe/Madrid') b, "
            + "secondsToDateTime(1790853071) c, toDateTime(1790853071224) d",
        "a|b|c|d",
        "1970-01-01 00:00:00.000|1970-01-01 01:00:00.000|2026-10-01 11:11:11.000|2026-10-01 11:11:11.224");
  }

  @Test
  public void durations() throws SQLException {
    returnsInOrder("select toDuration(93784567) a, toDuration(-1500) b, toDuration(250) c",
        "a|b|c",
        "1d 2h 3m 4.567s|-1.500s|0.250s");
  }

  @Test
  public void extremeDurationsDoNotOverflow() throws SQLException {
    // Long.MAX_VALUE milliseconds is about 292 million years: seconds * 10^9 used to wrap around
    returnsInOrder("select toDuration(9223372036854775807) a, toDuration(-9223372036854775808) b",
        "a|b",
        "106751991167d 7h 12m 55.807s|-106751991167d 7h 12m 55.808s");
  }

  @Test
  public void notADate() throws SQLException {
    returnsInOrder("select toDateTime(this) d, toEpochMillis(this) ms from java.util.HashMap limit 1",
        "d|ms",
        "null|null");
  }
}
