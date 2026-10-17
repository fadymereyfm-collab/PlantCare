package com.example.plantcare.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.plantcare.CrashReporter
import com.example.plantcare.EmailContext
import com.example.plantcare.FirebaseSyncManager
import com.example.plantcare.Plant
import com.example.plantcare.ReminderUtils
import com.example.plantcare.WateringReminder
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Rolling reminder generator. ReminderUtils.generateReminders only emits
 * reminders for the next [ReminderUtils.GENERATION_WINDOW_DAYS] days, so a
 * plant added 5 months ago would have 0 future reminders left despite
 * being on a perfectly healthy 14-day cycle.
 *
 * This worker runs daily, looks at every user plant with a positive
 * `wateringInterval`, finds the **latest auto reminder** that already
 * exists for the plant, and walks forward by interval until the
 * 180-day horizon — inserting only the dates beyond what's already
 * stored.
 *
 * The "extend from latest" approach is deliberate. The previous
 * "compute expected dates from startDate, insert any missing" logic
 * could generate duplicates: if WeatherAdjustmentWorker had shifted
 * `Mon-29` to `Wed-31`, the worker would treat `Mon-29` as missing
 * and re-insert it, leaving the user with both reminders for the
 * same watering. By extending from the actual latest stored date we
 * preserve weather-shifted schedules untouched.
 *
 * Idempotent — the daily cadence makes re-runs essentially free when
 * the plant is already topped-up.
 *
 * Scheduled in App.scheduleReminderTopUpWorker.
 */
class ReminderTopUpWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ctx = applicationContext
            val email = EmailContext.current(ctx) ?: return@withContext Result.success()

            val plantRepo = PlantRepository.getInstance(ctx)
            val reminderRepo = ReminderRepository.getInstance(ctx)

            // v16 close-out backfill — runs once per upgrade. Moved out
            // of CatalogSeeder.seedIfEmpty (Application startup) to here
            // because the original placement contributed to a startup ANR
            // when a v15 user with N plants triggered ~240 reminder
            // inserts plus Firebase syncs while Glide/Compose/AdMob were
            // still initialising. Doing it from the daily worker means
            // the device is already warm and the user is not staring at
            // a blocked main thread.
            runV16Backfill(ctx, plantRepo, reminderRepo)

            // v16: include plants whose watering OR any other care interval
            // is configured. Pre-fix the worker only topped up plants with
            // wateringInterval>0 — multi-type users with watering disabled
            // (e.g. a desert succulent on rain-only) saw zero fertilize/
            // misting/repot top-ups even though those series existed.
            val plants = plantRepo.getAllUserPlantsForUserBlocking(email)
                .filter {
                    it.startDate != null && (
                        it.wateringInterval > 0 ||
                        it.fertilizingInterval > 0 ||
                        it.mistingInterval > 0 ||
                        it.repottingIntervalDays > 0
                    )
                }
            if (plants.isEmpty()) return@withContext Result.success()

            // Locale.US for the wire format used in SQLite queries and
            // reminder rows. Locale.getDefault() on ar/fa devices emits
            // Eastern-Arabic digits which never match Latin-digit rows.
            // Same A2 fix as PlantReminderWorker.
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val horizonCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, ReminderUtils.GENERATION_WINDOW_DAYS)
            }
            val horizonStr = sdf.format(horizonCal.time)
            val todayStr = sdf.format(Date())

            var inserted = 0
            for (plant in plants) {
                // v16: top up each enabled care type independently. Each
                // type carries its own auto-reminder series in the DB,
                // discriminated by the `type` column, so the
                // "extend from latest" anchor is computed per-type.
                inserted += topUpPlantForType(plant, "water",     plant.wateringInterval,     todayStr, horizonStr, sdf, reminderRepo)
                inserted += topUpPlantForType(plant, "fertilize", plant.fertilizingInterval,  todayStr, horizonStr, sdf, reminderRepo)
                inserted += topUpPlantForType(plant, "mist",      plant.mistingInterval,      todayStr, horizonStr, sdf, reminderRepo)
                inserted += topUpPlantForType(plant, "repot",     plant.repottingIntervalDays, todayStr, horizonStr, sdf, reminderRepo)
            }
            if (inserted > 0) com.example.plantcare.DataChangeNotifier.notifyChange()

            // Piggyback identification cache cleanup onto the daily worker
            // — keeps the cache from growing unbounded without scheduling a
            // separate job. Same 7-day TTL as `PlantIdentificationRepository`
            // honours on read, so this just removes rows that would never
            // produce a cache hit anyway.
            try {
                val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                com.example.plantcare.AppDatabase.getInstance(ctx)
                    .identificationCacheDao()
                    .deleteOlderThan(cutoff)
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }

            Result.success()
        } catch (t: Throwable) {
            CrashReporter.log(t)
            Result.retry()
        }
    }

    /**
     * Compute the next reminder date for this plant and insert all
     * missing future dates up to horizon.
     *
     * Anchor logic:
     *  - If the plant has any AUTO reminder (description empty + repeat
     *    parses to a positive int), start from `latestAutoDate +
     *    interval`. This preserves any weather-shifted dates already
     *    in the table — we don't re-insert their original counterparts.
     *  - If no AUTO reminders exist (fresh plant or all manual), fall
     *    back to `plant.startDate`, fast-forwarded to today so we don't
     *    backfill historical dates.
     *
     * Each insert is wrapped in try/catch because the plant could be
     * deleted between our read and our write — Plant→WateringReminder
     * has FK CASCADE, so a freshly orphaned row would violate the FK
     * constraint.
     */
    /**
     * v16 — type-aware top-up. Anchors per-type: a plant on a 14-day
     * watering cycle and a 28-day fertilizing cycle has independent
     * "latest auto date" anchors per series, so each is extended only
     * by its own interval. Pre-v16 the worker had a single anchor for
     * the watering series and would silently never top up other types.
     */
    private fun topUpPlantForType(
        plant: Plant,
        type: String,
        interval: Int,
        todayStr: String,
        horizonStr: String,
        sdf: SimpleDateFormat,
        reminderRepo: ReminderRepository
    ): Int {
        if (interval <= 0) return 0
        val all = reminderRepo.getRemindersForPlantBlocking(plant.id)
        // Anchor only on auto reminders of THIS type. NULL `type` rows
        // (created before v15) count as "water" — same default the
        // notification worker uses (PlantReminderWorker.java:152).
        val latestAutoDate = all
            .filter { isAutoReminder(it) && matchesType(it, type) }
            .mapNotNull { it.date }
            .maxOrNull()

        val nextCal = Calendar.getInstance()
        if (latestAutoDate != null) {
            val parsed = try { sdf.parse(latestAutoDate) } catch (_: Throwable) { null }
            if (parsed == null) {
                nextCal.time = plant.startDate!!
                // Same start-date skip as ReminderUtils.generateForType for
                // non-water types — see comment below.
                if (type != "water") nextCal.add(Calendar.DAY_OF_YEAR, interval)
            } else {
                nextCal.time = parsed
                nextCal.add(Calendar.DAY_OF_YEAR, interval)
            }
        } else {
            nextCal.time = plant.startDate!!
            // Per Fady's 2026-05-09 directive, only WATER fires on the
            // start date. If the user mutes water reminders for a few
            // months on a multi-type plant and then re-enables, this
            // worker would otherwise resurrect a "fertilize today"
            // entry on the resync — we don't want that. Match the
            // generateForType behaviour so both pipelines agree on
            // when the first reminder of each type lands.
            if (type != "water") nextCal.add(Calendar.DAY_OF_YEAR, interval)
        }

        while (sdf.format(nextCal.time) < todayStr) {
            nextCal.add(Calendar.DAY_OF_YEAR, interval)
        }

        var inserted = 0
        while (sdf.format(nextCal.time) <= horizonStr) {
            val dateStr = sdf.format(nextCal.time)
            val r = WateringReminder().apply {
                plantId = plant.id
                plantName = plant.nickname ?: plant.name
                date = dateStr
                done = false
                repeat = interval.toString()
                description = ""
                userEmail = plant.userEmail
                this.type = type
            }
            try {
                reminderRepo.insertBlocking(r)
                inserted++
                try { FirebaseSyncManager.get().syncReminder(r) }
                catch (t: Throwable) { CrashReporter.log(t) }
            } catch (t: Throwable) {
                CrashReporter.log(t)
            }
            nextCal.add(Calendar.DAY_OF_YEAR, interval)
        }
        return inserted
    }

    /**
     * v16 — type matcher. Treats NULL/blank `type` as "water" so
     * pre-v15 reminders continue to anchor the watering series after
     * upgrade (matches PlantReminderWorker's NULL→water default).
     */
    private fun matchesType(r: WateringReminder, want: String): Boolean {
        val t = r.type
        return if (t.isNullOrBlank()) want == "water" else t.equals(want, ignoreCase = true)
    }

    /**
     * Auto reminders are the ones the watering-interval pipeline emits:
     * empty description + a `repeat` field that parses to a positive
     * int. Manual reminders carry a non-empty description or a "0"
     * repeat. We only use AUTO ones to anchor the next-date computation
     * so a one-off "fertilizer reminder" doesn't push us forward by an
     * unrelated cadence.
     */
    private fun isAutoReminder(r: WateringReminder): Boolean {
        val desc = r.description
        if (!desc.isNullOrBlank()) return false
        val repeatInt = r.repeat?.toIntOrNull() ?: return false
        return repeatInt > 0
    }

    /**
     * v16 close-out — one-time per-device backfill, gated by SharedPreferences
     * flags so it only runs once per upgrade. Two passes:
     *
     *   1. **Family backfill** — for every plant with `family=null`,
     *      lookup [com.example.plantcare.data.CatalogFamilyMap] and stamp
     *      `family` + `scientificName` if known. Targets catalog rows
     *      seeded before v16 (the seed loop only runs on first install)
     *      AND user plants whose name matches a catalog entry.
     *
     *   2. **Multi-type interval backfill** — for every USER plant with
     *      a watering schedule but no fertilize/mist/repot intervals,
     *      pull family-defaults from [PlantCareDefaults] and generate
     *      the three new reminder series. Existing watering reminders
     *      stay untouched (so weather-shifted dates survive).
     *
     * Both passes are bounded by Plant count, not Reminder count — the
     * generator inside [ReminderUtils.generateForType] caps each series
     * at GENERATION_WINDOW_DAYS. Memory cost is therefore O(plants),
     * which is what we want from a daily worker invocation.
     */
    private fun runV16Backfill(
        ctx: Context,
        plantRepo: PlantRepository,
        reminderRepo: ReminderRepository
    ) {
        val prefs = ctx.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)

        // Pass 1 — family backfill.
        try {
            if (!prefs.getBoolean("v16_family_backfill_done", false)) {
                val all = plantRepo.getAllBlocking()
                var touched = 0
                for (p in all) {
                    if (!p.family.isNullOrBlank()) continue
                    val info = com.example.plantcare.data.CatalogFamilyMap.lookup(p.name)
                        ?: continue
                    p.family = info.family
                    if (p.scientificName.isNullOrBlank())
                        p.scientificName = info.scientificName
                    plantRepo.updateBlocking(p)
                    touched++
                }
                prefs.edit().putBoolean("v16_family_backfill_done", true).apply()
                android.util.Log.d("TopUpWorker", "v16 family backfill: tagged $touched plants")
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }

        // Pass 2 — multi-type interval + reminder backfill.
        try {
            if (!prefs.getBoolean("v16_multitype_backfill_done", false)) {
                val all = plantRepo.getAllBlocking()
                var touched = 0
                for (p in all) {
                    if (!p.isUserPlant) continue
                    if (p.startDate == null) continue
                    if (p.wateringInterval <= 0) continue
                    if (p.fertilizingInterval > 0 || p.mistingInterval > 0
                        || p.repottingIntervalDays > 0
                    ) continue  // already populated — skip
                    val care = com.example.plantcare.data.plantnet.PlantCareDefaults
                        .forFamily(p.family)
                    p.fertilizingInterval = care.fertilizingIntervalDays
                    p.mistingInterval = care.mistingIntervalDays
                    p.repottingIntervalDays = care.repottingIntervalDays
                    plantRepo.updateBlocking(p)
                    val newRems = mutableListOf<com.example.plantcare.WateringReminder>()
                    newRems += com.example.plantcare.ReminderUtils.generateForType(
                        p, "fertilize", p.fertilizingInterval)
                    newRems += com.example.plantcare.ReminderUtils.generateForType(
                        p, "mist", p.mistingInterval)
                    newRems += com.example.plantcare.ReminderUtils.generateForType(
                        p, "repot", p.repottingIntervalDays)
                    if (newRems.isNotEmpty()) {
                        reminderRepo.insertAllBlocking(newRems)
                    }
                    touched++
                }
                prefs.edit().putBoolean("v16_multitype_backfill_done", true).apply()
                android.util.Log.d("TopUpWorker", "v16 multi-type backfill: hydrated $touched user plants")
            }
        } catch (t: Throwable) {
            CrashReporter.log(t)
        }
    }
}
