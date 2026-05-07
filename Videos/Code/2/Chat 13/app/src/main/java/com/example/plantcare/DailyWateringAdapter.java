package com.example.plantcare;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.plantcare.feature.streak.StreakBridge;
import com.example.plantcare.weekbar.DefaultPlantIcon;
import com.example.plantcare.weekbar.PlantImageLoader;

import java.util.*;

public class DailyWateringAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ROOM_HEADER = 0;
    private static final int TYPE_REMINDER = 1;

    private final List<Object> items = new ArrayList<>(); // RoomHeader or WateringReminder
    private final Context context;
    private final String userEmail;
    /**
     * Per-refresh cache from plant display name → resolved Plant. Without
     * this, every onBindViewHolder fires 3 DB queries (name, nickname, fallback)
     * AND openPlantDetails fires another 3 — a 30-row Today screen could hit
     * the DB 180 times on a single bind cycle.
     */
    private final java.util.Map<String, Plant> plantCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * يمثّل عنوان الغرفة في القائمة
     */
    public static class RoomHeader {
        public final int roomId;
        public final String roomName;
        public final List<WateringReminder> reminders; // التذكيرات الخاصة بهذه الغرفة

        public RoomHeader(int roomId, String roomName, List<WateringReminder> reminders) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.reminders = reminders;
        }
    }

    public DailyWateringAdapter(List<WateringReminder> reminders, Context context, String userEmail) {
        this.context = context;
        this.userEmail = userEmail;
        // Backwards compatibility: flat list
        setItemsFlat(reminders);
    }

    /**
     * Set items grouped by rooms. Each RoomHeader is followed by its reminders.
     */
    public void setGroupedItems(List<RoomHeader> roomGroups) {
        items.clear();
        // Invalidate the per-refresh plant cache so renames/edits done since
        // last bind aren't served stale from cache.
        plantCache.clear();
        for (RoomHeader group : roomGroups) {
            items.add(group);
            // ترتيب: غير المنجز أولاً، ثم المتأخر أكثر
            List<WateringReminder> sorted = new ArrayList<>(group.reminders);
            Collections.sort(sorted, (a, b) -> {
                if (a.done != b.done) return a.done ? 1 : -1;
                int overdueA = a.getDaysOverdue();
                int overdueB = b.getDaysOverdue();
                if (overdueA != overdueB) return Integer.compare(overdueB, overdueA);
                return a.date.compareTo(b.date);
            });
            items.addAll(sorted);
        }
        notifyDataSetChanged();
    }

    /**
     * Flat list (backwards compatibility, no room headers)
     */
    public void setItems(List<WateringReminder> reminderList) {
        setItemsFlat(reminderList);
    }

    private void setItemsFlat(List<WateringReminder> reminderList) {
        items.clear();
        plantCache.clear();
        if (reminderList != null) {
            List<WateringReminder> sorted = new ArrayList<>(reminderList);
            Collections.sort(sorted, (a, b) -> {
                if (a.done != b.done) return a.done ? 1 : -1;
                int overdueA = a.getDaysOverdue();
                int overdueB = b.getDaysOverdue();
                if (overdueA != overdueB) return Integer.compare(overdueB, overdueA);
                return a.date.compareTo(b.date);
            });
            items.addAll(sorted);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof RoomHeader) ? TYPE_ROOM_HEADER : TYPE_REMINDER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ROOM_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_today_room_header, parent, false);
            return new RoomHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_daily_watering, parent, false);
            return new ReminderViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof RoomHeaderViewHolder && item instanceof RoomHeader) {
            ((RoomHeaderViewHolder) holder).bind((RoomHeader) item);
        } else if (holder instanceof ReminderViewHolder && item instanceof WateringReminder) {
            ((ReminderViewHolder) holder).bind((WateringReminder) item, position);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ========== ViewHolders ==========

    class RoomHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textRoomHeader;

        RoomHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textRoomHeader = itemView.findViewById(R.id.textRoomHeader);
        }

        void bind(RoomHeader header) {
            textRoomHeader.setText(header.roomName);

            // نقرة طويلة: سقاية جميع نباتات الغرفة
            itemView.setOnLongClickListener(v -> {
                showBulkWaterDialog(header);
                return true;
            });
        }
    }

    private void showBulkWaterDialog(RoomHeader header) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_water_all_title)
                .setMessage(context.getString(R.string.dialog_water_all_message, header.roomName))
                .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                    com.example.plantcare.util.BgExecutor.io(() -> {
                        com.example.plantcare.data.repository.ReminderRepository reminderRepo =
                                com.example.plantcare.data.repository.ReminderRepository
                                        .getInstance(context);
                        boolean anyMarkedDone = false;
                        for (WateringReminder r : header.reminders) {
                            if (!r.done) {
                                r.done = true;
                                r.completedDate = new Date();
                                r.wateredBy = userEmail;
                                reminderRepo.updateBlocking(r);
                                anyMarkedDone = true;
                            }
                        }
                        // Streak/Challenge-Update: idempotent pro Tag — ein Aufruf
                        // genügt auch bei Bulk-Water.
                        if (anyMarkedDone) {
                            StreakBridge.onReminderMarkedDone(context, userEmail);
                        }
                        ((MainActivity) context).runOnUiThread(() -> {
                            notifyDataSetChanged();
                            DataChangeNotifier.notifyChange();
                        });
                    });
                })
                .setNegativeButton(R.string.action_no, null)
                .show();
    }

    class ReminderViewHolder extends RecyclerView.ViewHolder {
        View sideStripe;
        ImageView imageThumb;
        TextView plantName;
        TextView textOverdue;
        TextView textDueDate;
        ImageView typeIcon;

        ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            sideStripe = itemView.findViewById(R.id.sideStripe);
            imageThumb = itemView.findViewById(R.id.imagePlantThumb);
            plantName = itemView.findViewById(R.id.textPlantName);
            textOverdue = itemView.findViewById(R.id.textOverdue);
            textDueDate = itemView.findViewById(R.id.textDueDate);
            typeIcon = itemView.findViewById(R.id.imageTypeIcon);
        }

        void bind(WateringReminder reminder, int position) {
            // اسم النبتة
            plantName.setText(reminder.plantName);

            // شريط جانبي: أخضر عند غير منجز، رمادي باهت عند المنجز
            int stripeColor = reminder.done ? 0xFFDDDDDD
                    : context.getResources().getColor(R.color.pc_success);
            sideStripe.setBackgroundColor(stripeColor);

            // شفافية خفيفة عند الاكتمال
            float alpha = reminder.done ? 0.5f : 1.0f;
            itemView.setAlpha(alpha);
            plantName.setAlpha(alpha);

            // شارة التأخير +N
            int overdueDays = reminder.getDaysOverdue();
            if (!reminder.done && overdueDays > 0) {
                textOverdue.setText("+" + overdueDays);
                textOverdue.setVisibility(View.VISIBLE);
            } else {
                textOverdue.setVisibility(View.GONE);
            }

            // Locale-formatted due date (e.g. "12. Mai" / "May 12") so the
            // user can see WHEN, not just whether it's overdue. The overdue
            // badge above is relative; the date here is absolute.
            if (textDueDate != null) {
                String formatted = formatReminderDate(reminder.date);
                if (formatted == null) {
                    textDueDate.setVisibility(View.GONE);
                } else {
                    textDueDate.setVisibility(View.VISIBLE);
                    textDueDate.setText(formatted);
                }
            }

            // أيقونة السقاية: تظهر فقط للتذكيرات التلقائية
            boolean isManual = (reminder.description != null && !reminder.description.trim().isEmpty())
                    || (reminder.repeat == null || reminder.repeat.equals("0") || reminder.repeat.isEmpty());
            typeIcon.setVisibility(isManual ? View.GONE : View.VISIBLE);

            // تحميل صورة النبتة عبر PlantImageLoader الموحّد
            loadPlantThumbAsync(reminder, imageThumb);

            // نقرة على الصورة = فتح تفاصيل النبتة (مختصر النقرة الطويلة على الكارت).
            // تمّ الفصل عن نقرة الكارت الأساسية حتى لا نكسر سلوك «اسقِ/تراجَع» الحالي.
            imageThumb.setOnClickListener(v -> openPlantDetails(reminder));

            // نقرة واحدة: إنهاء/إلغاء أو إعادة جدولة لو متأخر
            itemView.setOnClickListener(v -> {
                if (reminder.done) {
                    reminder.done = false;
                    reminder.completedDate = null;
                    updateReminderAndNotify(reminder);
                } else {
                    int od = reminder.getDaysOverdue();
                    if (od > 0) {
                        showRescheduleDialogCustom(reminder, od);
                    } else {
                        reminder.done = true;
                        reminder.completedDate = new Date();
                        reminder.wateredBy = userEmail;
                        updateReminderAndNotify(reminder);
                        StreakBridge.onReminderMarkedDone(context, userEmail);
                        Analytics.INSTANCE.logReminderCompleted(context);
                    }
                }
            });

            // Long-press: manage manual reminders (edit/delete). Auto
            // reminders open plant details instead — they're regenerated
            // by ReminderTopUpWorker every 24 h based on the plant's
            // watering interval, so a "Delete" here would silently come
            // back the next day. The right way to stop auto reminders is
            // to clear `wateringInterval` on the plant itself.
            itemView.setOnLongClickListener(v -> {
                if (isManual) {
                    showManualReminderActions(reminder, position);
                } else {
                    openPlantDetails(reminder);
                }
                return true;
            });
        }
    }

    private void showManualReminderActions(WateringReminder reminder, int position) {
        CharSequence[] actions = new CharSequence[]{
                context.getString(R.string.reminder_action_edit),
                context.getString(R.string.reminder_action_delete)
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.reminder_manage_title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        EditManualReminderDialogFragment dialogFragment =
                                EditManualReminderDialogFragment.newInstance(reminder);
                        dialogFragment.show(((MainActivity) context).getSupportFragmentManager(),
                                "edit_manual_reminder");
                    } else {
                        deleteReminderInline(reminder, position);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Inline delete with safer adapter mutation: notifyItemRemoved instead
     * of the previous notifyDataSetChanged + items.remove(position) combo,
     * which could fall out of sync when other binds happened in parallel.
     * Manual-only — auto reminders are gated above because the top-up
     * worker would resurrect them within 24 h.
     */
    private void deleteReminderInline(WateringReminder reminder, int position) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            com.example.plantcare.data.repository.ReminderRepository
                    .getInstance(context).deleteBlocking(reminder);
            try { FirebaseSyncManager.get().deleteReminder(reminder); }
            catch (Throwable t) { CrashReporter.INSTANCE.log(t); }
            ((MainActivity) context).runOnUiThread(() -> {
                if (position >= 0 && position < items.size()
                        && items.get(position) == reminder) {
                    items.remove(position);
                    notifyItemRemoved(position);
                } else {
                    notifyDataSetChanged();
                }
                DataChangeNotifier.notifyChange();
            });
        });
    }

    /**
     * يفتح تفاصيل النبتة المرتبطة بهذا التذكير. يعمل في الخلفية لاستعلام DAO
     * ثم يعود للخيط الرئيسي لعرض الحوار.
     *
     * السبب: التقرير الجوهري (٢٣ أبريل) رصد أن نقرة على بند «اليوم» كانت نقرة ميّتة؛
     * أُضيفت هذه الطريقة حتى يربط الإجراء «اضغط → افتح النبتة» للصورة وللنقرة الطويلة.
     */
    private void openPlantDetails(WateringReminder reminder) {
        if (reminder == null || reminder.plantName == null) return;
        com.example.plantcare.util.BgExecutor.io(() -> {
            Plant foundPlant = resolvePlantCached(reminder.plantName);
            if (foundPlant != null && context instanceof MainActivity) {
                ((MainActivity) context).runOnUiThread(() -> {
                    PlantDetailDialogFragment dialog =
                            PlantDetailDialogFragment.newInstance(foundPlant, true);
                    dialog.setReadOnlyMode(true);
                    dialog.show(((MainActivity) context).getSupportFragmentManager(),
                            "plant_detail_popup");
                });
            }
        });
    }

    private void loadPlantThumbAsync(WateringReminder reminder, ImageView target) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            final Plant p = resolvePlantCached(reminder.plantName);
            ((MainActivity) context).runOnUiThread(() -> {
                if (p != null) {
                    PlantImageLoader.loadInto(context, target, (long) p.id, p.name, userEmail);
                } else {
                    // Variante anhand des Pflanzen­namens wählen — gleicher Geist wie PlantImageLoader.
                    target.setImageResource(
                            DefaultPlantIcon.INSTANCE.forPlant(reminder.plantName, 0L)
                    );
                }
            });
        });
    }

    /**
     * Single resolution path used by both thumbnail loader and detail dialog
     * opener. Backed by a per-refresh cache so a 30-row Today screen issues
     * at most ~30 plant lookups instead of 180. Identical fallback chain
     * (user plant by name → by nickname → any catalog match) so behaviour
     * is unchanged.
     */
    private Plant resolvePlantCached(String plantName) {
        if (plantName == null) return null;
        Plant cached = plantCache.get(plantName);
        if (cached != null) return cached;
        com.example.plantcare.data.repository.PlantRepository plantRepo =
                com.example.plantcare.data.repository.PlantRepository.getInstance(context);
        Plant plant = null;
        List<Plant> userCopies = plantRepo.getAllUserPlantsWithNameAndUserBlocking(plantName, userEmail);
        if (!userCopies.isEmpty()) {
            plant = plantRepo.findByIdBlocking(userCopies.get(0).id);
        }
        if (plant == null) plant = plantRepo.findByNicknameBlocking(plantName);
        if (plant == null) plant = plantRepo.findByNameBlocking(plantName);
        if (plant != null) plantCache.put(plantName, plant);
        return plant;
    }

    /**
     * Format a yyyy-MM-dd reminder date for display. Returns null on parse
     * failure so the caller can hide the field instead of showing junk.
     * Uses the system's medium date format which respects German formatting
     * ("12. Mai 2026") while staying readable.
     */
    private String formatReminderDate(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            java.text.SimpleDateFormat src =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            Date d = src.parse(iso);
            if (d == null) return null;
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM,
                    java.util.Locale.getDefault()).format(d);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    private void updateReminderAndNotify(WateringReminder reminder) {
        com.example.plantcare.util.BgExecutor.io(() -> {
            com.example.plantcare.data.repository.ReminderRepository
                    .getInstance(context).updateBlocking(reminder);
            ((MainActivity) context).runOnUiThread(() -> {
                notifyDataSetChanged();
                DataChangeNotifier.notifyChange();
            });
        });
    }

    private void showRescheduleDialogCustom(WateringReminder reminder, int overdue) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_reschedule, null, false);
        TextView title = content.findViewById(R.id.title);
        TextView message = content.findViewById(R.id.message);
        Button btnYes = content.findViewById(R.id.btnYes);
        Button btnNo = content.findViewById(R.id.btnNo);
        Button btnClose = content.findViewById(R.id.btnClose);

        title.setText(context.getString(R.string.dialog_reschedule_title));
        message.setText(context.getString(R.string.dialog_reschedule_message, overdue));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .create();

        btnYes.setOnClickListener(v -> {
            com.example.plantcare.util.BgExecutor.io(() -> {
                ReminderUtils.rescheduleFromToday(reminder, context);
                ((MainActivity) context).runOnUiThread(() -> {
                    dialog.dismiss();
                    DataChangeNotifier.notifyChange();
                });
            });
        });
        btnNo.setOnClickListener(v -> {
            reminder.done = true;
            reminder.completedDate = new Date();
            reminder.wateredBy = userEmail;
            updateReminderAndNotify(reminder);
            StreakBridge.onReminderMarkedDone(context, userEmail);
            Analytics.INSTANCE.logReminderCompleted(context);
            dialog.dismiss();
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        // Make dialog window transparent so only the rounded card is visible
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
