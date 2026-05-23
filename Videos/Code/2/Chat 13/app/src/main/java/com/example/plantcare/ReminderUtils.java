package com.example.plantcare;

import android.content.Context;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReminderUtils {

    /**
     * Window the auto-generator covers — 180 days. Was 60 before
     * 2026-05-06; a long-cycle plant (e.g. cacti at 21d intervals) only got
     * 3 reminders, then nothing for half a year. The {@link
     * com.example.plantcare.feature.reminder.ReminderTopUpWorker} keeps
     * each plant topped up to this horizon as time advances.
     */
    public static final int GENERATION_WINDOW_DAYS = 180;

    /**
     * v16 — Reminder type constants. Match `WateringReminder.type` values
     * read by PlantReminderWorker for the per-type notification toggles.
     */
    public static final String TYPE_WATER     = "water";
    public static final String TYPE_FERTILIZE = "fertilize";
    public static final String TYPE_MIST      = "mist";
    public static final String TYPE_REPOT     = "repot";

    /**
     * Backward-compatible single-type generator — kept so existing call
     * sites that only care about the watering schedule (legacy code in
     * `rescheduleFromToday`, etc.) keep compiling. New code should call
     * {@link #generateAllReminders(Plant)} which fans out to all four
     * configured intervals.
     */
    public static List<WateringReminder> generateReminders(Plant plant) {
        return generateForType(plant, TYPE_WATER, plant.getWateringInterval());
    }

    /**
     * v16 — emit reminders for every care type the plant is configured
     * for: water, fertilize, mist, repot. Each interval > 0 contributes
     * its own series; intervals = 0 are silently skipped (the user
     * disabled that type). All series share the same start date and
     * GENERATION_WINDOW_DAYS horizon so the calendar shows them on the
     * same axis. Per-row `type` discriminates them downstream.
     */
    public static List<WateringReminder> generateAllReminders(Plant plant) {
        List<WateringReminder> all = new ArrayList<>();
        if (plant == null || plant.getStartDate() == null) return all;

        all.addAll(generateForType(plant, TYPE_WATER,     plant.getWateringInterval()));
        all.addAll(generateForType(plant, TYPE_FERTILIZE, plant.fertilizingInterval));
        all.addAll(generateForType(plant, TYPE_MIST,      plant.mistingInterval));
        all.addAll(generateForType(plant, TYPE_REPOT,     plant.repottingIntervalDays));
        return all;
    }

    /**
     * Generate a reminder series of one type.
     * Same horizon + per-day stride logic the original watering generator
     * used — extracted so all four types share a single implementation.
     */
    public static List<WateringReminder> generateForType(Plant plant, String type, int interval) {
        List<WateringReminder> reminders = new ArrayList<>();

        if (plant == null || plant.getStartDate() == null || interval <= 0) return reminders;

        // Per Fady's 2026-05-09 directive: only WATER fires a reminder on
        // the start date. Fertilize/mist/repot first reminder lands at
        // startDate+interval — a freshly added Basilikum shouldn't ask the
        // user to fertilize/repot the same day they planted it.
        // For long-cycle non-water types where interval > GENERATION_WINDOW_DAYS
        // (e.g. repot 730d / window 180d) the do-while below still emits one
        // reminder so the calendar has SOMETHING to show; ReminderTopUpWorker
        // tops up the rest as time advances.
        boolean isWater = TYPE_WATER.equals(type);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plant.getStartDate());
        if (!isWater) {
            calendar.add(Calendar.DAY_OF_YEAR, interval);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdf.format(new Date());

        int dayOffset = isWater ? 0 : interval;
        do {
            Date reminderDate = calendar.getTime();
            String dateStr = sdf.format(reminderDate);

            if (dateStr.compareTo(todayStr) >= 0) {
                WateringReminder reminder = new WateringReminder();
                reminder.plantId = plant.getId();
                reminder.plantName = plant.getNickname();
                reminder.date = dateStr;
                reminder.done = false;
                reminder.repeat = String.valueOf(interval); // auto reminder (repeat > 0)
                reminder.description = ""; // not manual
                reminder.userEmail = plant.getUserEmail();
                reminder.type = type;
                reminders.add(reminder);
            }

            calendar.add(Calendar.DAY_OF_YEAR, interval);
            dayOffset += interval;
        } while (dayOffset < GENERATION_WINDOW_DAYS);

        return reminders;
    }

    /**
     * v16 multi-type aware rescheduling. The user marks an OVERDUE
     * reminder done from today; we shift JUST that type's series so
     * the next reminder of the same type is `today + interval`, and
     * we leave the other three care-type series untouched.
     *
     * Pre-fix this function was watering-only and had three bugs that
     * silently corrupted the multi-type schedule:
     *   1. The interval-fallback path (when `reminder.repeat` couldn't
     *      be parsed) read `plant.getWateringInterval()` regardless of
     *      reminder.type — so rescheduling a mist reminder used the
     *      water cadence.
     *   2. `deleteFutureRemindersForPlantBlocking` wiped every future
     *      auto reminder for the plant — so rescheduling mist also
     *      destroyed water/fertilize/repot.
     *   3. `plant.setWateringInterval(repeatDays)` overwrote the
     *      plant's water interval with whatever the rescheduled type's
     *      cadence was — so rescheduling a 3-day mist reminder pinned
     *      water to 3 days too.
     *   4. `generateReminders(plant)` (water-only) was used to refill
     *      the calendar after the wipe, so the other three series
     *      vanished entirely.
     */
    public static void rescheduleFromToday(WateringReminder reminder, Context context) {
        try {
            com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                    com.example.plantcare.data.repository.ReminderRepository.getInstance(context);
            com.example.plantcare.data.repository.PlantRepository plantRepo =
                    com.example.plantcare.data.repository.PlantRepository.getInstance(context);

            // Resolve which series this reminder belongs to — NULL/blank/unknown
            // is treated as "water" to match the legacy default everywhere
            // else in the app.
            String type = reminder.type;
            if (type == null || type.trim().isEmpty()) type = TYPE_WATER;

            // Look the plant up once — needed both for the interval fallback
            // and for the regeneration call. Bail if it's gone (FK orphan).
            Plant plant = plantRepo.findByNicknameBlocking(reminder.plantName);
            if (plant == null) {
                plant = plantRepo.findUserPlantByNameAndEmailBlocking(reminder.plantName, reminder.userEmail);
            }

            int repeatDays;
            try {
                repeatDays = Integer.parseInt(reminder.repeat);
            } catch (NumberFormatException e) {
                repeatDays = 0;
            }
            if (repeatDays <= 0 && plant != null) {
                // v16: pick the right interval per type, not always water.
                repeatDays = intervalForType(plant, type);
            }
            if (repeatDays <= 0) return;

            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String newDate = sdf.format(calendar.getTime());

            // v16: delete only future reminders OF THIS TYPE — keep the
            // other three series intact. Pre-fix this wiped everything.
            reminderRepo.deleteFutureRemindersForPlantAndTypeBlocking(
                    reminder.plantId, newDate, type);

            reminder.date = newDate;
            reminder.done = false;
            reminder.completedDate = null;
            reminder.repeat = String.valueOf(repeatDays);
            reminder.type = type;
            reminderRepo.updateBlocking(reminder);

            if (plant != null) {
                Date dateObj = sdf.parse(newDate);
                plant.setStartDate(dateObj);
                // v16: stamp the new interval onto the right field for THIS
                // type — pre-fix this overwrote wateringInterval regardless
                // and corrupted multi-type plants.
                applyIntervalForType(plant, type, repeatDays);
                plantRepo.updateBlocking(plant);

                // v16: regenerate ONLY this type's series.
                // For WATER: generateForType emits a today entry as item[0];
                // we already wrote today via reminderRepo.updateBlocking
                // above so drop[0] avoids the duplicate.
                // For non-water: post-2026-05-09 generateForType already
                // skips the start-date occurrence, so item[0] is
                // today+interval — keep all entries.
                List<WateringReminder> newReminders =
                        ReminderUtils.generateForType(plant, type, repeatDays);
                if (!newReminders.isEmpty()) {
                    if (TYPE_WATER.equals(type)) {
                        newReminders.remove(0);
                    }
                    if (!newReminders.isEmpty()) {
                        reminderRepo.insertAllBlocking(newReminders);
                    }
                }
            }

            DataChangeNotifier.notifyChange();
            if (context != null) {
                DataChangeNotifier.notifyCalendar(context);
            }
        } catch (Exception e) {
            com.example.plantcare.CrashReporter.INSTANCE.log(e);
        }
    }

    /** v16 — pick the matching interval field on the plant for a given type. */
    private static int intervalForType(Plant plant, String type) {
        if (plant == null) return 0;
        if (type == null) return plant.getWateringInterval();
        switch (type.toLowerCase(Locale.US)) {
            case TYPE_FERTILIZE: return plant.fertilizingInterval;
            case TYPE_MIST:      return plant.mistingInterval;
            case TYPE_REPOT:     return plant.repottingIntervalDays;
            case TYPE_WATER:
            default:             return plant.getWateringInterval();
        }
    }

    /** v16 — write a new interval into the matching plant field for a given type. */
    private static void applyIntervalForType(Plant plant, String type, int interval) {
        if (plant == null) return;
        if (type == null) { plant.setWateringInterval(interval); return; }
        switch (type.toLowerCase(Locale.US)) {
            case TYPE_FERTILIZE: plant.fertilizingInterval = interval; break;
            case TYPE_MIST:      plant.mistingInterval = interval; break;
            case TYPE_REPOT:     plant.repottingIntervalDays = interval; break;
            case TYPE_WATER:
            default:             plant.setWateringInterval(interval); break;
        }
    }

    public static int parseWateringInterval(String watering) {
        if (watering == null || watering.isEmpty()) return 0;
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(watering);
            int interval = 0;
            while (matcher.find()) {
                interval = Integer.parseInt(matcher.group(1));
            }
            return interval;
        } catch (Exception e) {
            // expected: parseInt may overflow on malformed input — fall through to 0
            return 0;
        }
    }

    /**
     * Parse a German plant-care interval description into days. Handles the
     * shapes the catalog actually uses (and a few common variants the
     * PlantNet free-text API returns):
     * <ul>
     *   <li>"Alle 3 Tage" → 3</li>
     *   <li>"Alle 4 Wochen düngen. Im Winter nicht düngen." → 28</li>
     *   <li>"Alle 2 Monate" → 60</li>
     *   <li>"Alle 3 Jahre" → 1095</li>
     *   <li>"Einmal im Monat" / "Einmal pro Monat" → 30</li>
     *   <li>"Einmal pro Woche" → 7</li>
     *   <li>"Täglich" → 1, "Wöchentlich" → 7, "Monatlich" → 30, "Jährlich" → 365</li>
     *   <li>"Alle 2-3 Wochen" → 14 (lower bound — safer to remind early)</li>
     * </ul>
     * Returns 0 when no recognisable pattern is present so the caller can
     * fall through to family defaults / hardcoded fallback. Days/weeks/
     * months/years map to 1/7/30/365 — the standard plant-care convention,
     * not calendar-accurate, but the user can edit any pre-fill before
     * persisting.
     */
    public static int parseIntervalDays(String text) {
        if (text == null || text.isEmpty()) return 0;
        String s = text.toLowerCase(Locale.GERMAN);

        // Pattern 1 — "Alle N Einheit" / "alle 2-3 wochen" / "14 tage": numeric
        // value plus a unit. The optional `[-–](\d+)` swallows the upper
        // bound of a range and we keep the LOWER bound as the conservative
        // pick (more frequent watering is safer than under-watering).
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d+)\\s*(?:[-–]\\s*\\d+\\s*)?(tag(?:e|en)?|woche(?:n)?|monat(?:e|en)?|jahr(?:e|en)?)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                int mult = unitToDays(m.group(2));
                if (n > 0 && mult > 0) return n * mult;
            } catch (NumberFormatException e) {
                // expected: malformed digit group — fall through to next pattern
            }
        }

        // Pattern 2 — "Einmal im/pro Einheit" → 1 × unit. Captures
        // "Einmal im Monat" (no leading digit) which is the most common
        // German way to say "once a month" in plant-care text.
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile(
                "einmal\\s+(?:im|pro)\\s+(tag|woche|monat|jahr)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
        if (m2.find()) {
            int days = unitToDays(m2.group(1));
            if (days > 0) return days;
        }

        // Pattern 3 — adverb forms with no digit at all.
        if (s.contains("täglich")) return 1;
        if (s.contains("wöchentlich")) return 7;
        if (s.contains("monatlich")) return 30;
        if (s.contains("jährlich")) return 365;

        return 0;
    }

    private static int unitToDays(String unit) {
        if (unit == null) return 0;
        String u = unit.toLowerCase(Locale.GERMAN);
        if (u.startsWith("tag")) return 1;
        if (u.startsWith("woche")) return 7;
        if (u.startsWith("monat")) return 30;
        if (u.startsWith("jahr")) return 365;
        return 0;
    }

    public static Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
}