# خادم إيمانيات الذكي للتسميع وتصحيح التلاوة (Cloudflare Workers)

خادم HTTPS خفيف وسريع متوافق مع مواصفات **Cloudflare Workers** القياسية، مصمم حصراً لتدقيق التسميع القرآني (النصي والصوتي) بآلية تحقق ذكية مزدوجة تمنع التقييم العشوائي أو إعطاء درجات مرتفعة عند قول كلام غير قرآني، مع حفظ `GEMINI_API_KEY` بأمان خارج حزمة APK وتوفير صفحات الخصوصية وحذف الحساب لـ Google Play.

## آلية التمييز والتحقق الذكي (Smart Recitation Verification)
1. **تفريغ صوتي حرفي إلزامي (`heardTranscript`) + مخطط JSON صارم (`responseSchema` + `temperature: 0.0`):**
   - يُجبر نموذج Gemini على تفريغ ما سمعه فعلياً في الصوت دون نسخ النص المرجعي، وتصنيف هل المتلو آيات من السورة المطلوبة أم حديث عادي/ضوضاء/سورة أخرى (`isTargetSurahRecitation`).
2. **مطابقة عربية حاسوبية دقيقة على الخادم (`analyzeArabicRecitation`):**
   - تجريد التشكيل وعلامات الضبط وتوحيد الهمزات والألف المقصورة والتاء المربوطة، ثم حساب أطول سلسلة كلمات متطابقة بالترتيب (LCS) مع مقارنة المقاطع المتجاورة (Bigrams).
   - إذا كان الكلام عادياً (مثل: *ألو السلام عليكم تجربة*) أو من سورة أخرى أو استعاذة/بسملة فقط بدون آيات السورة، يرفض الخادم التسميع فوراً بدرجة `0.0`–`0.5` مع توضيح ما سُمِع فعلياً بدلاً من إعطاء `9/10`.
   - يفرض الخادم سقفاً موضوعياً للدرجة (`maxObjectiveScore`) بحيث لا يمكن للذكاء الاصطناعي المبالغة في التقييم.

## مواصفات Cloudflare Workers التقنية
| المواصفة | القيمة / الإعداد |
| :--- | :--- |
| نوع العامل (Runtime) | ES Module Worker (`compatibility_date = "2026-01-01"`) |
| الحالة والتخزين (State & Storage) | `Stateless` بالكامل في الذاكرة — لا KV ولا R2 ولا D1 ولا تسجيل للملفات الصوتية |
| ذاكرة الـ Isolate | ضمن حد Cloudflare الافتراضي (`128 MB`) مع سقف طلب أقصى `9 MB` |
| الصوت المسموح | حتى `8,000,000` حرف Base64 (~6 MB ملف صوتي) بصيغ `mp4/m4a/aac/mpeg/wav/webm/ogg` |
| مهلة الاتصال بـ Gemini | `25,000 ms` عبر `AbortSignal.timeout` مع تتابع نماذج `2.5-flash` ← `2.5-flash-lite` ← `2.0-flash` |
| الأمان والترويسات | HTTPS إلزامي، `HSTS`، `X-Frame-Options: DENY`، `Cache-Control: no-store`، `X-App-Token` اختياري |
| خطة Cloudflare | متوافق 100% مع الخطة المجانية (Free 10ms CPU — خوارزمية LCS الموضعية تعمل بأقل من `1 ms`) والمدفوعة |

## المسارات المتوفرة
- `GET /health` — فحص حالة الخادم والمواصفات وحدود الحجم وسياسة عدم حفظ التسجيلات.
- `GET /privacy` — صفحة سياسة الخصوصية الرسمية لتطبيق إيمانيات (Google Play).
- `GET /delete-account` — صفحة تعليمات حذف الحساب والبيانات (Google Play).
- `POST /api/gemini/recitation-text` — تصحيح التسميع الكتابي للقرآن الكريم.
- `POST /api/gemini/recitation-audio` — تصحيح التسميع الصوتي مباشرة في الذاكرة دون أي تخزين.

## خطوات النشر (Deploy)

1. تثبيت Wrangler وتسجيل الدخول إلى Cloudflare:
```bash
cd cloudflare-worker
npm install -g wrangler
wrangler login
```

2. إضافة مفتاح Gemini السري بأمان داخل Cloudflare Secrets:
```bash
wrangler secret put GEMINI_API_KEY
# اختياري لحماية إضافية للطلبات:
wrangler secret put APP_CLIENT_TOKEN
```

3. نشر الخادم:
```bash
wrangler deploy
```

4. بعد النشر، أضف رابط العامل الناتج (`https://emaniat-api.<your-subdomain>.workers.dev`) في Secrets في GitHub Actions / AI Studio:
```env
BACKEND_BASE_URL=https://emaniat-api.<your-subdomain>.workers.dev
```
واستخدم في Google Play Console:
- **Privacy Policy URL:** `https://emaniat-api.<your-subdomain>.workers.dev/privacy`
- **Delete Account URL:** `https://emaniat-api.<your-subdomain>.workers.dev/delete-account`

## تشغيل الاختبارات محلياً
```bash
npm test
```
