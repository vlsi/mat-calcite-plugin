package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.Set;

/**
 * SQL functions that turn date and time objects of the heap into readable text and epoch numbers. Supported:
 * java.util.Date (and java.sql.Date, Time, Timestamp), java.util.Calendar, java.time.Instant, LocalDate, LocalTime,
 * LocalDateTime, ZonedDateTime, OffsetDateTime and Duration, and plain numbers as epoch milliseconds.
 *
 * <p>Values that are a point in time without a zone of their own (Date, Instant, epoch numbers) are shown in UTC
 * unless a zone is given, e.g. toDateTime(this, 'Europe/Madrid'). Calendar, ZonedDateTime and OffsetDateTime are
 * shown in their own zone or offset. Local values have no zone and are shown as they are.
 */
public class DateFunctions extends HeapFunctionsBase {
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  // ---------------------------------------------------------------- SQL functions

  /** Readable date and time of a heap date object or epoch milliseconds, e.g. 2026-10-01 11:11:11.224. */
  @SuppressWarnings("unused")
  public static String toDateTime(Object r) {
    return toDateTime(r, null);
  }

  /** Same as toDateTime(r), with points in time shown in the given zone, e.g. 'Europe/Madrid' or '+02:00'. */
  @SuppressWarnings("unused")
  public static String toDateTime(Object r, String zone) {
    Value value = read(r);
    return value == null ? null : value.format(zone(zone, r));
  }

  /** Readable date and time of epoch milliseconds, in UTC. */
  @SuppressWarnings("unused")
  public static String millisToDateTime(long millis) {
    return millisToDateTime(millis, null);
  }

  @SuppressWarnings("unused")
  public static String millisToDateTime(long millis, String zone) {
    return Value.instant(Instant.ofEpochMilli(millis), Kind.DATE_TIME).format(zone(zone, null));
  }

  /** Readable date and time of epoch seconds, in UTC. */
  @SuppressWarnings("unused")
  public static String secondsToDateTime(long seconds) {
    return secondsToDateTime(seconds, null);
  }

  @SuppressWarnings("unused")
  public static String secondsToDateTime(long seconds, String zone) {
    return Value.instant(Instant.ofEpochSecond(seconds), Kind.DATE_TIME).format(zone(zone, null));
  }

  /**
   * Epoch milliseconds of a heap date object. Local date-times and dates (no zone of their own) are taken as UTC; a
   * LocalTime has no epoch value and gives null.
   */
  @SuppressWarnings("unused")
  public static Long toEpochMillis(Object r) {
    return toEpochMillis(r, null);
  }

  /** Same as toEpochMillis(r), with local date-times and dates taken in the given zone. */
  @SuppressWarnings("unused")
  public static Long toEpochMillis(Object r, String zone) {
    Value value = read(r);
    return value == null ? null : value.epochMillis(zone(zone, r));
  }

  /** A java.time.Duration, or a number of milliseconds, as readable text, e.g. 1d 2h 3m 4.567s. */
  @SuppressWarnings("unused")
  public static String toDuration(Object r) {
    long seconds;
    long nanos;
    if (r instanceof Number) {
      long millis = ((Number) r).longValue();
      seconds = Math.floorDiv(millis, 1000);
      nanos = Math.floorMod(millis, 1000) * 1_000_000L;
    } else {
      IObject o = heapObject(r);
      if (o == null || !isA(o, "java.time.Duration")) {
        return null;
      }
      try {
        seconds = number(o, "seconds");
        nanos = number(o, "nanos");
      } catch (SnapshotException e) {
        return null;
      }
    }
    return formatDuration(seconds, nanos);
  }

  // ---------------------------------------------------------------- reading heap objects

  private enum Kind {
    DATE_TIME, DATE, TIME
  }

  /** A date or time read from the heap. */
  private static final class Value {
    final Kind kind;
    /** Point in time, or null for local values. */
    final Instant instant;
    /** Zone of its own (Calendar, ZonedDateTime, OffsetDateTime), or null. */
    final ZoneId ownZone;
    /** Local value, when there is no point in time. */
    final LocalDateTime local;
    /** Zone to show the point in time in by default, e.g. the dumped JVM's zone for java.sql.Date; null for UTC. */
    final ZoneId defaultZone;

    private Value(Kind kind, Instant instant, ZoneId ownZone, LocalDateTime local, ZoneId defaultZone) {
      this.kind = kind;
      this.instant = instant;
      this.ownZone = ownZone;
      this.local = local;
      this.defaultZone = defaultZone;
    }

    static Value instant(Instant instant, Kind kind) {
      return new Value(kind, instant, null, null, null);
    }

    /** A point in time the application meant in its JVM's default zone, such as java.sql.Date. */
    static Value inJvmZone(Instant instant, Kind kind, ZoneId jvmZone) {
      return new Value(kind, instant, null, null, jvmZone);
    }

    static Value zoned(Instant instant, ZoneId zone) {
      return new Value(Kind.DATE_TIME, instant, zone, null, null);
    }

    static Value local(LocalDateTime local, Kind kind) {
      return new Value(kind, null, null, local, null);
    }

    String format(ZoneId requested) {
      if (instant == null) {
        return pattern().format(local);
      }
      if (ownZone != null && requested == null) {
        // In the value's own zone, which the text names, e.g. 2026-10-01 11:11:11.224 +02:00
        return pattern().format(ZonedDateTime.ofInstant(instant, ownZone)) + " " + ownZone.getId();
      }
      ZoneId zone = requested != null ? requested : defaultZone != null ? defaultZone : ZoneOffset.UTC;
      return pattern().format(ZonedDateTime.ofInstant(instant, zone));
    }

    Long epochMillis(ZoneId requested) {
      if (instant != null) {
        return instant.toEpochMilli();
      }
      if (kind == Kind.TIME) {
        return null;
      }
      return local.atZone(requested == null ? ZoneOffset.UTC : requested).toInstant().toEpochMilli();
    }

    private DateTimeFormatter pattern() {
      return kind == Kind.DATE ? DATE : kind == Kind.TIME ? TIME : DATE_TIME;
    }
  }

  private static Value read(Object r) {
    if (r instanceof Number) {
      return Value.instant(Instant.ofEpochMilli(((Number) r).longValue()), Kind.DATE_TIME);
    }
    IObject o = heapObject(r);
    if (o == null) {
      return null;
    }
    try {
      Set<String> types = types(o);
      if (types.contains("java.util.Date") && !dateTimeIsCurrent(o)) {
        return null; // changed through a deprecated setter: fastTime is stale until the JVM normalizes it
      }
      if (types.contains("java.sql.Timestamp")) {
        // Whole seconds in fastTime, the fraction in nanos
        long seconds = Math.floorDiv(number(o, "fastTime"), 1000);
        return Value.instant(Instant.ofEpochSecond(seconds, number(o, "nanos")), Kind.DATE_TIME);
      }
      // java.sql.Date and Time are built in the JVM's default zone (Date.valueOf("2026-10-01") is local midnight),
      // so they read right only in that zone
      if (types.contains("java.sql.Date")) {
        return Value.inJvmZone(Instant.ofEpochMilli(number(o, "fastTime")), Kind.DATE, jvmZone(o.getSnapshot()));
      }
      if (types.contains("java.sql.Time")) {
        return Value.inJvmZone(Instant.ofEpochMilli(number(o, "fastTime")), Kind.TIME, jvmZone(o.getSnapshot()));
      }
      if (types.contains("java.util.Date")) {
        return Value.instant(Instant.ofEpochMilli(number(o, "fastTime")), Kind.DATE_TIME);
      }
      if (types.contains("java.util.Calendar")) {
        if (!Boolean.TRUE.equals(o.resolveValue("isTimeSet"))) {
          return null; // fields set since the time was computed: "time" is stale until the JVM recomputes it
        }
        Instant instant = Instant.ofEpochMilli(number(o, "time"));
        ZoneId zone = timeZone(o.resolveValue("zone"));
        return zone == null ? Value.instant(instant, Kind.DATE_TIME) : Value.zoned(instant, zone);
      }
      if (types.contains("java.time.Instant")) {
        return Value.instant(Instant.ofEpochSecond(number(o, "seconds"), number(o, "nanos")), Kind.DATE_TIME);
      }
      if (types.contains("java.time.LocalDateTime")) {
        LocalDateTime local = localDateTime(o);
        return local == null ? null : Value.local(local, Kind.DATE_TIME);
      }
      if (types.contains("java.time.LocalDate")) {
        return Value.local(localDate(o).atStartOfDay(), Kind.DATE);
      }
      if (types.contains("java.time.LocalTime")) {
        return Value.local(LocalDate.EPOCH.atTime(localTime(o)), Kind.TIME);
      }
      if (types.contains("java.time.ZonedDateTime") || types.contains("java.time.OffsetDateTime")) {
        LocalDateTime local = localDateTime((IObject) o.resolveValue("dateTime"));
        ZoneOffset offset = offset(o.resolveValue("offset"));
        if (local == null || offset == null) {
          return null;
        }
        ZoneId zone = types.contains("java.time.ZonedDateTime") ? zoneId(o.resolveValue("zone"), offset) : offset;
        return Value.zoned(local.toInstant(offset), zone);
      }
      return null;
    } catch (SnapshotException | DateTimeException | ClassCastException e) {
      return null; // not a value this dump lets us read
    }
  }

  private static LocalDateTime localDateTime(IObject o) throws SnapshotException {
    if (o == null) {
      return null;
    }
    IObject date = (IObject) o.resolveValue("date");
    IObject time = (IObject) o.resolveValue("time");
    return date == null || time == null ? null : LocalDateTime.of(localDate(date), localTime(time));
  }

  private static LocalDate localDate(IObject o) throws SnapshotException {
    return LocalDate.of((int) number(o, "year"), (int) number(o, "month"), (int) number(o, "day"));
  }

  private static LocalTime localTime(IObject o) throws SnapshotException {
    return LocalTime.of((int) number(o, "hour"), (int) number(o, "minute"), (int) number(o, "second"),
        (int) number(o, "nano"));
  }

  private static ZoneOffset offset(Object zoneOffset) throws SnapshotException {
    return zoneOffset instanceof IObject
        ? ZoneOffset.ofTotalSeconds((int) number((IObject) zoneOffset, "totalSeconds")) : null;
  }

  /** The zone of a ZonedDateTime: a region such as Europe/Madrid, or the offset itself. */
  private static ZoneId zoneId(Object zone, ZoneOffset offset) throws SnapshotException {
    if (zone instanceof IObject && types((IObject) zone).contains("java.time.ZoneRegion")) {
      String id = string(((IObject) zone).resolveValue("id"));
      if (id != null) {
        try {
          return ZoneId.of(id);
        } catch (DateTimeException e) {
          // a zone id this JVM does not know: fall back to the offset
        }
      }
    }
    return offset;
  }

  /** The zone of a java.util.TimeZone (e.g. sun.util.calendar.ZoneInfo), from its ID. */
  private static ZoneId timeZone(Object timeZone) throws SnapshotException {
    if (!(timeZone instanceof IObject)) {
      return null;
    }
    String id = string(((IObject) timeZone).resolveValue("ID"));
    if (id == null) {
      return null;
    }
    try {
      return ZoneId.of(id, ZoneId.SHORT_IDS);
    } catch (DateTimeException e) {
      return null;
    }
  }

  /** Default zone of the dumped JVM, from java.util.TimeZone.defaultTimeZone, per snapshot. */
  private static final Map<ISnapshot, Optional<ZoneId>> JVM_ZONES =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static ZoneId jvmZone(ISnapshot snapshot) {
    return JVM_ZONES.computeIfAbsent(snapshot, s -> {
      try {
        for (IClass timeZoneClass : s.getClassesByName("java.util.TimeZone", false)) {
          for (Field field : timeZoneClass.getStaticFields()) {
            if ("defaultTimeZone".equals(field.getName()) && field.getValue() instanceof ObjectReference) {
              return Optional.ofNullable(timeZone(((ObjectReference) field.getValue()).getObject()));
            }
          }
        }
      } catch (SnapshotException e) {
        // fall through: not known
      }
      return Optional.empty();
    }).orElse(null);
  }

  // ---------------------------------------------------------------- helpers

  private static IObject heapObject(Object r) {
    HeapReference ref = ensureHeapReference(r);
    return ref == null ? null : ref.getIObject();
  }

  /** The class of an object and its superclasses. */
  private static Set<String> types(IObject o) {
    Set<String> names = new HashSet<>();
    for (IClass c = o.getClazz(); c != null; c = c.getSuperClass()) {
      names.add(c.getName());
    }
    return names;
  }

  private static boolean isA(IObject o, String className) {
    return types(o).contains(className);
  }

  private static long number(IObject o, String field) throws SnapshotException {
    Object value = o.resolveValue(field);
    if (!(value instanceof Number)) {
      throw new SnapshotException("No numeric field " + field + " in " + o.getTechnicalName());
    }
    return ((Number) value).longValue();
  }

  private static String string(Object value) {
    return value instanceof IObject ? ((IObject) value).getClassSpecificName() : null;
  }

  /**
   * The zone argument: a zone id or offset, or 'jvm' for the default zone of the JVM the heap dump was taken from.
   */
  private static ZoneId zone(String zone, Object heapValue) {
    if (zone == null || zone.trim().isEmpty()) {
      return null;
    }
    if ("jvm".equalsIgnoreCase(zone.trim())) {
      IObject o = heapObject(heapValue);
      ZoneId jvm = o == null ? null : jvmZone(o.getSnapshot());
      if (jvm == null) {
        throw new IllegalArgumentException("The dumped JVM's time zone is not known"
            + (o == null ? " for a plain number; pass a zone id instead" : " in this heap dump"));
      }
      return jvm;
    }
    try {
      return ZoneId.of(zone.trim(), ZoneId.SHORT_IDS);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Unknown time zone: " + zone
          + " (use e.g. 'UTC', 'Europe/Madrid', '+02:00', or 'jvm' for the dumped JVM's zone)");
    }
  }

  /**
   * Formats seconds plus nanoseconds (0 to 999,999,999, as java.time.Duration stores them) without overflow:
   * Duration.ofSeconds(Long.MAX_VALUE), a common "forever", is about 292 billion years.
   */
  /**
   * Whether a java.util.Date's fastTime is its time. A deprecated setter (setHours...) changes only the cached
   * calendar fields (cdate) and marks them not normalized; fastTime follows when the JVM next reads the time.
   */
  private static boolean dateTimeIsCurrent(IObject date) throws SnapshotException {
    Object cdate = date.resolveValue("cdate");
    return !(cdate instanceof IObject) || !Boolean.FALSE.equals(((IObject) cdate).resolveValue("normalized"));
  }

  static String formatDuration(long seconds, long nanos) {
    boolean negative = seconds < 0;
    long absSeconds = seconds;
    long absNanos = nanos;
    if (negative) {
      if (absNanos > 0) {
        absSeconds++; // -1.25 s is stored as -2 s + 0.75 s
        absNanos = 1_000_000_000L - absNanos;
      }
      absSeconds = -absSeconds; // for Long.MIN_VALUE this stays 2^63, which the unsigned arithmetic below reads right
    }
    long millis = absNanos / 1_000_000L;
    long days = Long.divideUnsigned(absSeconds, 86_400);
    long rest = Long.remainderUnsigned(absSeconds, 86_400);
    long hours = rest / 3600;
    long minutes = rest / 60 % 60;
    long secs = rest % 60;
    StringBuilder sb = new StringBuilder(negative ? "-" : "");
    if (days > 0) {
      sb.append(Long.toUnsignedString(days)).append("d ");
    }
    if (days > 0 || hours > 0) {
      sb.append(hours).append("h ");
    }
    if (days > 0 || hours > 0 || minutes > 0) {
      sb.append(minutes).append("m ");
    }
    sb.append(secs);
    if (millis > 0) {
      sb.append('.').append(String.format(java.util.Locale.ROOT, "%03d", millis));
    }
    return sb.append('s').toString();
  }
}
