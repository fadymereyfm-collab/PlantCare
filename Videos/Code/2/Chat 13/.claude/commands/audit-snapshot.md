---
description: شغّل مقاييس Audit القسم 7 وقارنها بآخر snapshot لكشف الـregressions
---

# /audit-snapshot

نفّذ snapshot للمقاييس الكميّة في `PlantCare_Pre_Release_Audit.md` القسم 7، وقارنه بأقرب snapshot في `PROGRESS.md`.

## الأوامر المطلوب تشغيلها

```bash
# 1. AppDatabase.getInstance خارج repository
grep -rn "AppDatabase.getInstance\|DatabaseClient\." app/src/main/java | grep -v "data/repository/" | wc -l

# 2. getEmail() في src/main/java
grep -rn "\.getEmail()" app/src/main/java | wc -l

# 3. new Thread في UI/weekbar
grep -rn "new Thread(" app/src/main/java | wc -l

# 4. catch (... ignored)
grep -rn "catch (.*ignored" app/src/main/java | wc -l

# 5. Hilt usage
grep -rln "@HiltAndroidApp\|@AndroidEntryPoint\|@HiltViewModel\|@Inject" app/src/main/java | wc -l

# 6. Test files count
find app/src/test app/src/androidTest -name "*.kt" -o -name "*.java" 2>/dev/null | wc -l

# 7. resConfigs check
grep -n 'resConfigs' app/build.gradle

# 8. values-en exists?
ls -d app/src/main/res/values-en 2>&1 | head -1

# 9. Hardcoded API keys
grep -rn "YOUR_API_KEY_HERE\|ca-app-pub-3940256099942544" app/src/main | wc -l

# 10. TFLite asset
ls -la app/src/main/assets/plant_disease_model.tflite 2>&1 | head -1

# 11. Billing wired up?
grep -rn "BillingManager.getInstance" app/src/main/java | grep -v "billing/" | wc -l

# 12. Paywall triggered?
grep -rn "PaywallDialogFragment" app/src/main/java | grep -v "billing/PaywallDialogFragment.kt" | wc -l

# 13. AAB size (إن وُجد)
ls -la app/build/outputs/bundle/prodRelease/*.aab 2>/dev/null
```

## الناتج المطلوب

اعرض جدول مقارنة:

| المقياس | الهدف | الحالي | السابق | Δ | حكم |
|--------|-------|--------|--------|---|-----|
| AppDatabase.getInstance خارج repo | 0 | <new> | <old> | <diff> | ✅ تحسّن / ⚠️ ثابت / 🔴 regression |
| ... | ... | ... | ... | ... | ... |

ثم احفظ النتائج في PROGRESS.md تحت entry جديد:

```markdown
## Audit Snapshot: <date>
- DAO out of repo: <n> (target 0)
- new Thread: <n>
- catch ignored: <n>
- ...
- Regressions: <list>
- Improvements: <list>
```

**إذا اكتُشف regression** (مقياس ساء عمّا كان عليه): اكتب تحذيراً واضحاً للمستخدم واقترح إيقاف العمل في المهمة الحالية للتحقيق.
