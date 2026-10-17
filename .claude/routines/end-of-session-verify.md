# PlantCare — End-of-Session verification routine

<!-- جدولة مقترحة: كل يوم 06:00 (cron: 0 6 * * *). انسخ كل ما تحت هذا السطر كـ prompt للمهمة. -->

أنت تعمل **دون حضور Fady**. المشروع: `C:\PlantC 01` (Android: Java + Kotlin، Room، Firebase). استخدم أداة Bash (Git Bash). لا تطرح أسئلة.
**هذا الروتين لا يعدّل أي كود ولا أي ملف** سوى: `PROGRESS.md`، و`.routine-backups/LOCK`، و`.routine-backups/last-verify.txt`. لا تُصلح شيئاً حتى لو كان الإصلاح بسيطاً — سجّله فقط. `CLAUDE.md` هو القانون الأعلى (خصوصاً §1 القاعدة الذهبية و§5 و§9).

## 0. حواجز
1. **القفل:** إن وُجد `.routine-backups/LOCK` وعمره أقل من 4 ساعات ⇒ روتين آخر يعمل؛ اخرج بلا أي تعديل. وإلا: `mkdir -p .routine-backups && echo "verify $(date -Iseconds)" > .routine-backups/LOCK`. **احذفه في النهاية مهما كانت النتيجة.**
2. **Fady يعمل الآن؟** إن عُدِّل أي ملف تحت `app/src` أو `PROGRESS.md` خلال آخر 30 دقيقة ⇒ احذف LOCK واخرج ("skipped — recent manual edits").
3. **لا تغيير منذ آخر تحقّق؟** احسب البصمة: `fp=$(find app/src app/build.gradle PROGRESS.md -type f -printf '%T@ %s %p\n' | sort | md5sum | cut -d' ' -f1)`. إن طابقت محتوى `.routine-backups/last-verify.txt` ⇒ لا تكتب entry؛ احذف LOCK واخرج ("no changes since last verification").

## 1. السياق
- أعلى 250 سطراً من `PROGRESS.md`. حدّد **كل الـentries الأحدث من آخر entry عنوانه يحتوي "End-of-Session verification routine"** — هذه هي الادّعاءات التي ستتحقّق منها.
- من `PlantCare_Pre_Release_Audit.md`: القسم 7 (المقاييس) ومعايير قبول كل مهمة مذكورة في تلك الـentries. ومن `PlantCare_Functional_Report.md` لمهام Phase F.
- `.claude/commands/audit-snapshot.md` (قائمة المقاييس الـ13) و `.claude/commands/verify-task.md`.

## 2. STEP 1 — Build + tests
1. `ts=$(date +%Y-%m-%d_%H%M); mkdir -p .routine-backups/verify-$ts`
2. `./gradlew assembleProdRelease --console=plain > .routine-backups/verify-$ts/build.log 2>&1; echo "exit=$?"` — سجّل النتيجة والزمن، و `warnings = grep -cE "^w: |warning:" build.log`. قارن بآخر عدد warnings مسجّل في PROGRESS.md.
3. `./gradlew test --rerun-tasks --console=plain > .routine-backups/verify-$ts/test.log 2>&1` — سجّل pass/fail وعدد الاختبارات الفاشلة.
4. إن فشل البناء: انسخ أول أخطاء `e:`/`error:` حرفياً، وحدّد الملف والسبب. **لا تُصلح.**
5. حماية من البتر: لكل ملف `.kt`/`.java` عُدّل منذ آخر تحقّق، تأكّد أن آخر سطر غير فارغ هو `}`. أي ملف مبتور = BLOCKER.

## 3. STEP 2 — Grep verification (انسخ النواتج حرفياً)
- جدول CLAUDE.md §1 كاملاً: A1، C2، C4، B1، A3، A4، بالأوامر والنواتج المقبولة كما هي هناك.
- المقاييس الـ13 من `.claude/commands/audit-snapshot.md`، وقارن كل مقياس بآخر قيمة مسجّلة في PROGRESS.md: ✅ تحسّن / ⚠️ ثابت / 🔴 regression.
- لكل entry أحدث من آخر تحقّق: أعد تشغيل أوامر grep التي يذكرها في Evidence. إن تعارض الناتج مع الادّعاء، أو كان Build Status ⏳ والبناء الآن فشل ⇒ أضِف قسم `## Re-opened: <task code> (<date>) — <سبب>` كما يفرض CLAUDE.md §5.

## 4. STEP 3 — اكتب entry في رأس PROGRESS.md
تحت سطر `## Last Updated:` (حدّثه لتاريخ اليوم)، بنفس صيغة entries التحقّق السابقة في الملف:
```
## Session: <date> (End-of-Session verification routine — BUILD GREEN|BUILD BROKEN)
### Routine: automated end-of-session verification (CLAUDE.md §9). No code changed; no task marked Completed by this run.
### Verified entries: <قائمة عناوين الـentries التي فُحصت>
### STEP 1 — Build: ✅/❌ (<الزمن>) — warnings: <n> (previous: <m>)
### STEP 1b — Tests: ✅/❌ (<n> failed)
### STEP 2 — Grep verification (جدول: Check | Command | Result | Verdict)
### Claims check: <لكل entry: مؤكَّد / Re-opened + السبب>
### Regressions: <none / list>
### Next Task: <المهمة التالية حسب CLAUDE.md §2، والبنود التي تحتاج Fady>
```
**ممنوع** وضع ✅ على شيء لم تشغّل أمره فعلياً في هذا التشغيل.

## 5. الإنهاء
1. أعد حساب البصمة (بعد كتابة PROGRESS.md) واكتبها في `.routine-backups/last-verify.txt`.
2. أبلغ (لا تحذف) إن كان حجم `.routine-backups/` أكبر من 500 MB أو فيه نسخ أقدم من 30 يوماً.
3. احذف LOCK.
4. الرسالة الختامية بالعربية (≤ 8 أسطر): البناء، الاختبارات، warnings، عدد الـregressions والـRe-opened، أهم ما يحتاجه Fady.
