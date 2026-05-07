package com.example.plantcare.feature.memoir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.plantcare.R
import com.example.plantcare.data.journal.JournalEntry
import com.example.plantcare.data.journal.JournalSnapshot
import com.example.plantcare.data.repository.PlantJournalRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sprint-2 / F15: a multi-page PDF "growth report" that supersedes the
 * earlier 2x2/3x4 PNG collage (deleted alongside this file's introduction).
 *
 * The PNG collage was a nice MVP — show the photos and call it a day —
 * but the Plant Journal (Sprint-1 task 1.2) made it possible to tell a
 * fuller story: a chronological merge of completed waterings, photos,
 * disease checks and free-text memos, plus the summary counters from
 * [com.example.plantcare.data.journal.JournalSummary]. PDF is the right
 * container for that: paginates cleanly, embeds photos at print quality,
 * shareable to the same channels as PNG (WhatsApp, email, Drive).
 *
 * Page layout (A4 portrait, 595 × 842 pt):
 *   1.  Cover           — title + plant name + room + date range + creation date
 *   2.  Statistics      — counters block (days since start, waterings,
 *                         photos, checks, memos, last watered)
 *   3+. Timeline pages  — every JournalEntry, newest first, grouped by
 *                         month header. Continues onto N pages until
 *                         all entries are placed.
 *   N.  Photo grid      — 3×4 thumbnail grid of all photos, oldest top-left
 *                         (excluded if the plant has no photos at all).
 *
 * Uses [android.graphics.pdf.PdfDocument] from the platform — no external
 * dependency. Single-file build to keep the surface tight; if more
 * polishing is needed later (font embedding, RTL support, etc.), this
 * is the place to grow.
 *
 * Output is written under `getExternalFilesDir("memoir")` and shared via
 * the existing FileProvider authority `<package>.provider` so the same
 * `Intent.ACTION_SEND` chooser the legacy PNG path used keeps working.
 */
object MemoirPdfBuilder {

    /** Build result returned to the caller — file path + sharable Uri. */
    data class Result(val file: File, val uri: Uri, val photoCount: Int, val entryCount: Int)

    // A4 in points (1 pt = 1/72 inch).
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40

    // Sage palette matching the in-app theme.
    private val COLOR_PRIMARY = Color.parseColor("#2E5B2E")
    private val COLOR_TEXT = Color.parseColor("#1F1F1F")
    private val COLOR_MUTED = Color.parseColor("#666666")
    private val COLOR_ACCENT_BG = Color.parseColor("#F0F4EE")
    private val COLOR_DIVIDER = Color.parseColor("#D8DDD2")

    @JvmStatic
    fun build(
        context: Context,
        email: String?,
        plantId: Int,
        plantName: String?
    ): Result? {
        val repo = PlantJournalRepository.getInstance(context)
        val snapshot = repo.getJournalForPlantBlocking(plantId, email)
        if (snapshot.entries.isEmpty() && snapshot.summary.completedWateringCount == 0) {
            // Nothing to render — caller shows the same "no data" toast as before.
            return null
        }

        val pdf = PdfDocument()
        // PdfDocument has no pageCount accessor on all API levels — track it
        // ourselves so each page gets a distinct pageNumber.
        val pageCounter = intArrayOf(0)
        try {
            drawCoverPage(context, pdf, snapshot, plantName, pageCounter)
            drawStatsPage(context, pdf, snapshot, pageCounter)
            drawTimelinePages(context, pdf, snapshot, pageCounter)

            val photoEntries = snapshot.entries.filterIsInstance<JournalEntry.PhotoEntry>()
            if (photoEntries.isNotEmpty()) {
                drawPhotoGridPage(context, pdf, photoEntries, pageCounter)
            }

            val outDir = File(context.getExternalFilesDir(null), "memoir").apply { mkdirs() }
            val stampFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFile = File(outDir, "memoir_${plantId}_${stampFmt.format(Date())}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }

            val authority = context.packageName + ".provider"
            val uri = FileProvider.getUriForFile(context, authority, outFile)
            return Result(
                file = outFile,
                uri = uri,
                photoCount = photoEntries.size,
                entryCount = snapshot.entries.size
            )
        } finally {
            pdf.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Page 1 — Cover
    // ─────────────────────────────────────────────────────────────────────
    private fun drawCoverPage(
        context: Context,
        pdf: PdfDocument,
        snapshot: JournalSnapshot,
        plantName: String?,
        pageCounter: IntArray
    ) {
        pageCounter[0]++
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCounter[0]).create())
        val c = page.canvas

        // Background tint at top
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 280f, Paint().apply { color = COLOR_ACCENT_BG })

        val title = context.getString(R.string.memoir_pdf_title)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY; textSize = 30f; isFakeBoldText = true
        }
        c.drawText(title, MARGIN.toFloat(), 100f, titlePaint)

        val name = (plantName?.takeIf { it.isNotBlank() } ?: snapshot.summary.plantDisplayName ?: "—")
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT; textSize = 44f; isFakeBoldText = true
        }
        c.drawText(name, MARGIN.toFloat(), 170f, namePaint)

        val roomLine = snapshot.summary.roomName?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.memoir_pdf_room_format, it) }
        if (roomLine != null) {
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED; textSize = 16f
            }
            c.drawText(roomLine, MARGIN.toFloat(), 200f, mutedPaint)
        }

        // Date range derived from oldest/newest journal entry
        val rangeText = formatRange(context, snapshot)
        if (rangeText != null) {
            val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED; textSize = 16f
            }
            c.drawText(rangeText, MARGIN.toFloat(), 230f, rangePaint)
        }

        // Footer "created on" stamp
        val createdLabel = context.getString(
            R.string.memoir_pdf_created_format,
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        )
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED; textSize = 12f
        }
        c.drawText(createdLabel, MARGIN.toFloat(), (PAGE_H - MARGIN).toFloat(), footerPaint)

        pdf.finishPage(page)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Page 2 — Stats block
    // ─────────────────────────────────────────────────────────────────────
    private fun drawStatsPage(
        context: Context,
        pdf: PdfDocument,
        snapshot: JournalSnapshot,
        pageCounter: IntArray
    ) {
        pageCounter[0]++
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCounter[0]).create())
        val c = page.canvas

        drawSectionHeader(c, context.getString(R.string.memoir_pdf_stats_header), 80f)

        val s = snapshot.summary
        // 2-column stat tiles
        val tileW = (PAGE_W - 3 * MARGIN) / 2
        val tileH = 90
        var row = 0
        val tiles = mutableListOf<Pair<String, String>>()
        s.daysSinceStart?.let {
            tiles += context.getString(R.string.memoir_pdf_stat_days_label) to it.toString()
        }
        tiles += context.getString(R.string.memoir_pdf_stat_waterings_label) to
            s.completedWateringCount.toString()
        tiles += context.getString(R.string.memoir_pdf_stat_photos_label) to
            s.photoCount.toString()
        tiles += context.getString(R.string.memoir_pdf_stat_diagnoses_label) to
            s.diagnosisCount.toString()
        if (s.memoCount > 0) {
            tiles += context.getString(R.string.memoir_pdf_stat_memos_label) to
                s.memoCount.toString()
        }
        s.lastWateringDate?.let {
            tiles += context.getString(R.string.memoir_pdf_stat_last_label) to it
        }

        var startY = 130f
        tiles.forEachIndexed { idx, (label, value) ->
            val col = idx % 2
            row = idx / 2
            val left = MARGIN + col * (tileW + MARGIN)
            val top = startY + row * (tileH + 16)
            val rect = RectF(left.toFloat(), top, (left + tileW).toFloat(), top + tileH)

            // Tile background
            c.drawRoundRect(rect, 12f, 12f, Paint().apply { color = COLOR_ACCENT_BG })

            // Big value
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PRIMARY; textSize = 30f; isFakeBoldText = true
            }
            c.drawText(value, rect.left + 16f, rect.top + 42f, valPaint)
            // Label below
            val labPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED; textSize = 12f
            }
            c.drawText(label, rect.left + 16f, rect.top + 70f, labPaint)
        }

        pdf.finishPage(page)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Pages 3+ — Timeline (paginates as needed)
    // ─────────────────────────────────────────────────────────────────────
    private fun drawTimelinePages(
        context: Context,
        pdf: PdfDocument,
        snapshot: JournalSnapshot,
        pageCounter: IntArray
    ) {
        val entries = snapshot.entries
        if (entries.isEmpty()) return

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY; textSize = 18f; isFakeBoldText = true
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED; textSize = 11f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT; textSize = 12f
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT; textSize = 11f; isFakeBoldText = false
            // serif italic to mirror the journal's note style
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
        }
        val dividerPaint = Paint().apply { color = COLOR_DIVIDER; strokeWidth = 0.5f }

        val maxY = (PAGE_H - MARGIN).toFloat()
        pageCounter[0]++
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCounter[0]).create())
        var c = page.canvas
        drawSectionHeader(c, context.getString(R.string.memoir_pdf_timeline_header), 60f)
        var y = 100f

        entries.forEach { entry ->
            // Estimate the height this entry will occupy; if it overflows,
            // start a new page. Conservative ceiling: heading + 2 body lines + spacing.
            val estimated = estimateEntryHeight(entry)
            if (y + estimated > maxY) {
                pdf.finishPage(page)
                pageCounter[0]++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCounter[0]).create())
                c = page.canvas
                drawSectionHeader(c, context.getString(R.string.memoir_pdf_timeline_header_cont), 60f)
                y = 100f
            }

            // Date line
            c.drawText(entry.dateString, MARGIN.toFloat(), y, datePaint)
            y += 16f

            when (entry) {
                is JournalEntry.WateringEvent -> {
                    val by = entry.reminder.wateredBy?.takeIf { it.isNotBlank() }
                    val title = if (by != null)
                        context.getString(R.string.memoir_pdf_event_watered_by, by)
                    else context.getString(R.string.memoir_pdf_event_watered)
                    c.drawText("💧  $title", MARGIN.toFloat(), y, titlePaint.copy(size = 13f))
                    y += 18f
                    val n = entry.reminder.notes?.takeIf { it.isNotBlank() }
                    if (n != null) {
                        val quoted = "  „" + n + "“"
                        y = drawWrappedText(c, quoted, MARGIN + 18f, y, PAGE_W - 2 * MARGIN - 18f, notePaint)
                    }
                }
                is JournalEntry.PhotoEntry -> {
                    c.drawText(
                        "📷  " + context.getString(R.string.memoir_pdf_event_photo),
                        MARGIN.toFloat(), y, titlePaint.copy(size = 13f)
                    )
                    y += 18f
                    val thumb = decodeScaled(context, entry.photo.imagePath, 220, 160)
                    if (thumb != null) {
                        val left = MARGIN + 18f
                        val rect = Rect(left.toInt(), y.toInt(), (left + 220).toInt(), (y + 160).toInt())
                        val src = centerCropSrc(thumb.width, thumb.height, 220, 160)
                        c.drawBitmap(thumb, src, rect, null)
                        thumb.recycle()
                        y += 168f
                    }
                }
                is JournalEntry.DiagnosisEntry -> {
                    val pct = (entry.diagnosis.confidence * 100f).roundToInt().coerceIn(0, 100)
                    val title = context.getString(
                        R.string.memoir_pdf_event_diagnosis,
                        entry.diagnosis.displayName, pct
                    )
                    c.drawText("🩺  $title", MARGIN.toFloat(), y, titlePaint.copy(size = 13f))
                    y += 18f
                    val note = entry.diagnosis.note?.takeIf { it.isNotBlank() }
                    if (note != null) {
                        y = drawWrappedText(c, "  $note", MARGIN + 18f, y, PAGE_W - 2 * MARGIN - 18f, bodyPaint)
                    }
                    val thumb = decodeScaled(context, entry.diagnosis.imagePath, 180, 130)
                    if (thumb != null) {
                        val left = MARGIN + 18f
                        val rect = Rect(left.toInt(), y.toInt(), (left + 180).toInt(), (y + 130).toInt())
                        val src = centerCropSrc(thumb.width, thumb.height, 180, 130)
                        c.drawBitmap(thumb, src, rect, null)
                        thumb.recycle()
                        y += 138f
                    }
                }
                is JournalEntry.MemoEntry -> {
                    c.drawText(
                        "📝  " + context.getString(R.string.memoir_pdf_event_memo),
                        MARGIN.toFloat(), y, titlePaint.copy(size = 13f)
                    )
                    y += 18f
                    y = drawWrappedText(c, "  " + entry.memo.text, MARGIN + 18f, y, PAGE_W - 2 * MARGIN - 18f, notePaint)
                }
            }

            // divider between entries
            y += 8f
            c.drawLine(MARGIN.toFloat(), y, (PAGE_W - MARGIN).toFloat(), y, dividerPaint)
            y += 12f
        }

        pdf.finishPage(page)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Last page — Photo grid (3×4 thumbnail layout, oldest first)
    // ─────────────────────────────────────────────────────────────────────
    private fun drawPhotoGridPage(
        context: Context,
        pdf: PdfDocument,
        photos: List<JournalEntry.PhotoEntry>,
        pageCounter: IntArray
    ) {
        pageCounter[0]++
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCounter[0]).create())
        val c = page.canvas

        drawSectionHeader(c, context.getString(R.string.memoir_pdf_photos_header), 60f)

        val sorted = photos.sortedBy { it.timestamp } // oldest first → growth narrative
        val cols = 3
        val rows = 4
        val maxTiles = cols * rows
        val chosen: List<JournalEntry.PhotoEntry> = if (sorted.size > maxTiles) {
            // Even sample across the timeline.
            val step = sorted.size.toFloat() / maxTiles
            (0 until maxTiles).map { i -> sorted[(i * step).toInt().coerceAtMost(sorted.size - 1)] }
        } else sorted

        val gridTop = 100f
        val gridLeft = MARGIN.toFloat()
        val gridW = (PAGE_W - 2 * MARGIN).toFloat()
        val gutter = 8f
        val tileW = ((gridW - (cols - 1) * gutter) / cols).toInt()
        val tileH = (tileW * 0.85f).toInt() // slightly portrait

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 9f; isFakeBoldText = true
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA2E5B2E.toInt() }
        val placeholderPaint = Paint().apply { color = COLOR_ACCENT_BG }

        chosen.forEachIndexed { idx, photo ->
            val col = idx % cols
            val row = idx / cols
            val left = (gridLeft + col * (tileW + gutter)).toInt()
            val top = (gridTop + row * (tileH + gutter)).toInt()
            val dst = Rect(left, top, left + tileW, top + tileH)

            val bmp = decodeScaled(context, photo.photo.imagePath, tileW, tileH)
            if (bmp != null) {
                val src = centerCropSrc(bmp.width, bmp.height, tileW, tileH)
                c.drawBitmap(bmp, src, dst, null)
                bmp.recycle()
            } else {
                c.drawRect(dst, placeholderPaint)
            }

            val label = photo.dateString
            val labelW = datePaint.measureText(label) + 10f
            val badge = RectF(
                left.toFloat() + 4f,
                (top + tileH - 18).toFloat(),
                left + 4f + labelW,
                (top + tileH - 4).toFloat()
            )
            c.drawRoundRect(badge, 4f, 4f, badgePaint)
            c.drawText(label, badge.left + 5f, badge.bottom - 4f, datePaint)
        }

        pdf.finishPage(page)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun drawSectionHeader(c: Canvas, text: String, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY; textSize = 22f; isFakeBoldText = true
        }
        c.drawText(text, MARGIN.toFloat(), y, paint)
        val divider = Paint().apply { color = COLOR_PRIMARY; strokeWidth = 1.5f }
        c.drawLine(MARGIN.toFloat(), y + 6f, (PAGE_W - MARGIN).toFloat(), y + 6f, divider)
    }

    /** Returns the new Y cursor after drawing wrapped text. Manual word-wrap
     *  is sufficient here — StaticLayout won't render into a PdfDocument
     *  canvas reliably across API levels. */
    private fun drawWrappedText(
        c: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        val words = text.split(' ')
        val lineHeight = paint.textSize + 4f
        var y = startY
        val cur = StringBuilder()
        for (w in words) {
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(candidate) <= maxWidth) {
                cur.clear()
                cur.append(candidate)
            } else {
                if (cur.isNotEmpty()) {
                    c.drawText(cur.toString(), x, y, paint)
                    y += lineHeight
                    cur.clear()
                    cur.append(w)
                } else {
                    // single word longer than the line — draw and break anyway
                    c.drawText(w, x, y, paint)
                    y += lineHeight
                }
            }
        }
        if (cur.isNotEmpty()) {
            c.drawText(cur.toString(), x, y, paint)
            y += lineHeight
        }
        return y
    }

    /** Conservative height estimate so we know when to flip to a new page. */
    private fun estimateEntryHeight(entry: JournalEntry): Float = when (entry) {
        is JournalEntry.WateringEvent -> {
            val noteLines = (entry.reminder.notes?.length ?: 0) / 70 + 1
            34f + noteLines * 16f + 20f
        }
        is JournalEntry.PhotoEntry -> 34f + 168f + 20f
        is JournalEntry.DiagnosisEntry -> {
            val noteLines = (entry.diagnosis.note?.length ?: 0) / 80 + 1
            34f + noteLines * 16f + 138f + 20f
        }
        is JournalEntry.MemoEntry -> {
            val lines = (entry.memo.text.length / 80) + 1
            34f + lines * 16f + 20f
        }
    }

    private fun formatRange(context: Context, snapshot: JournalSnapshot): String? {
        val newest = snapshot.entries.maxByOrNull { it.timestamp }?.dateString
        val oldest = snapshot.entries.minByOrNull { it.timestamp }?.dateString
        if (newest.isNullOrBlank() || oldest.isNullOrBlank()) return null
        return if (newest == oldest) newest
        else context.getString(R.string.memoir_pdf_range_format, oldest, newest)
    }

    /** Same path-aware loader the rest of the app uses, but minimal
     *  here: we only ever read local files, FileProvider URIs, or http(s)
     *  URLs that have been mirrored locally. */
    private fun decodeScaled(context: Context, raw: String?, maxW: Int, maxH: Int): Bitmap? {
        if (raw.isNullOrBlank() || raw.startsWith("PENDING_DOC:")) return null
        return try {
            when {
                raw.startsWith("content://") -> {
                    val uri = Uri.parse(raw)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    val sample = computeInSampleSize(bounds, maxW, maxH)
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }
                raw.startsWith("file://") -> {
                    val f = File(Uri.parse(raw).path ?: return null)
                    if (!f.exists() || f.length() <= 0) return null
                    decodeFile(f, maxW, maxH)
                }
                raw.startsWith("http://") || raw.startsWith("https://") -> {
                    // Skip remote loading inside the PDF builder — keep it offline.
                    null
                }
                else -> {
                    val f = File(raw)
                    if (!f.exists() || f.length() <= 0) return null
                    decodeFile(f, maxW, maxH)
                }
            }
        } catch (oom: OutOfMemoryError) {
            // #18 fix: OOM during photo decode means we're out of
            // headroom — letting the catch swallow it would silently
            // produce a PDF missing this photo (and likely the next
            // ones too as memory stays pressured). Re-throw so the
            // caller's PDF build aborts visibly instead.
            throw oom
        } catch (c: kotlinx.coroutines.CancellationException) {
            // PDF builder is sometimes invoked from a coroutine path
            // (Sprint-2 calling); cooperate with cancellation.
            throw c
        } catch (t: Throwable) {
            com.example.plantcare.CrashReporter.log(t)
            null
        }
    }

    private fun decodeFile(f: File, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        val sample = computeInSampleSize(bounds, maxW, maxH)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    private fun computeInSampleSize(o: BitmapFactory.Options, maxW: Int, maxH: Int): Int {
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > 2 * max(maxW, maxH)) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun centerCropSrc(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcRatio = srcW.toFloat() / srcH
        val dstRatio = dstW.toFloat() / dstH
        return if (srcRatio > dstRatio) {
            val targetW = (srcH * dstRatio).toInt()
            val x = (srcW - targetW) / 2
            Rect(x, 0, x + targetW, srcH)
        } else {
            val targetH = (srcW / dstRatio).toInt()
            val y = (srcH - targetH) / 2
            Rect(0, y, srcW, y + targetH)
        }
    }

    /** Convenience to clone a Paint with a new size — used to vary the
     *  title style between sections without three near-identical Paints. */
    private fun Paint.copy(size: Float): Paint = Paint(this).also { it.textSize = size }
}
