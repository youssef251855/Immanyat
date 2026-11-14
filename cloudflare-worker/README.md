# خادم إيمانيات للتسميع وتصحيح التلاوة (Cloudflare Workers)

خادم HTTPS خفيف بدون تخزين للملفات الصوتية (Stateless) مخصص لتسميع وتصحيح القرآن الكريم وحفظ `GEMINI_API_KEY` بأمان خارج حزمة APK، مع توفير صفحات الخصوصية وحذف الحساب المطلوبة في Google Play.

## المسارات المتوفرة
- `GET /health` — فحص حالة الخادم وسياسة عدم حفظ التسجيلات.
- `GET /privacy` — صفحة سياسة الخصوصية الرسمية لتطبيق إيمانيات (مطلوبة في Google Play Console).
- `GET /delete-account` — صفحة تعليمات حذف الحساب والبيانات (مطلوبة في نموذج أمان البيانات على Google Play).
- `POST /api/gemini/recitation-text` — تصحيح التسميع الكتابي للقرآن الكريم.
- `POST /api/gemini/recitation-audio` — تصحيح التسميع الصوتي مباشرة في الذاكرة دون أي تخزين أو تسجيل.

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

4. بعد النشر، ضع رابط العامل الناتج (`https://emaniat-api.<your-subdomain>.workers.dev`) في Secrets في GitHub Actions / AI Studio:
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
