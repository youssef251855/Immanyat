const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
const GEMINI_MODELS = ["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash"];
const MAX_AUDIO_BASE64_LENGTH = 8 * 1024 * 1024; // ~6 MB decoded audio limit
const MIN_AUDIO_BASE64_LENGTH = 24000;
const ALLOWED_AUDIO_MIME_TYPES = new Set([
  "audio/mp4",
  "audio/m4a",
  "audio/x-m4a",
  "audio/aac",
  "audio/mpeg",
  "audio/wav",
  "audio/webm",
  "audio/ogg"
]);

function securityHeaders(origin, env, contentType = "application/json; charset=utf-8") {
  const allowedConfig = (env?.ALLOWED_ORIGINS || "*").trim();
  let allowOrigin = "*";
  if (allowedConfig !== "*" && origin) {
    const allowedList = allowedConfig.split(",").map((s) => s.trim()).filter(Boolean);
    allowOrigin = allowedList.includes(origin) ? origin : allowedList[0] || "null";
  }
  return {
    "Content-Type": contentType,
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-App-Token",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Cache-Control": "no-store, no-cache, must-revalidate, private"
  };
}

function jsonResponse(data, status = 200, request = null, env = {}) {
  const origin = request?.headers?.get("Origin") || "";
  return new Response(JSON.stringify(data), {
    status,
    headers: securityHeaders(origin, env, "application/json; charset=utf-8")
  });
}

function htmlResponse(html, status = 200, request = null, env = {}) {
  const origin = request?.headers?.get("Origin") || "";
  return new Response(html, {
    status,
    headers: {
      ...securityHeaders(origin, env, "text/html; charset=utf-8"),
      "Cache-Control": "public, max-age=3600"
    }
  });
}

function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function isClientAuthorized(request, env) {
  const expectedToken = (env?.APP_CLIENT_TOKEN || "").trim();
  if (!expectedToken) {
    // Public mode when APP_CLIENT_TOKEN secret is not set
    return true;
  }
  const provided = (request.headers.get("X-App-Token") || "").trim();
  return constantTimeEqual(provided, expectedToken);
}

async function readJsonBody(request, maxBytes = 9 * 1024 * 1024) {
  const contentLength = Number(request.headers.get("Content-Length") || 0);
  if (contentLength > maxBytes) {
    throw new HttpError(413, "حجم الطلب يتجاوز الحد الأقصى المسموح به.");
  }
  const raw = await request.text();
  if (raw.length > maxBytes) {
    throw new HttpError(413, "حجم البيانات المرسلة يتجاوز الحد الأقصى المسموح به.");
  }
  try {
    return JSON.parse(raw || "{}");
  } catch {
    throw new HttpError(400, "تنسيق JSON غير صالح.");
  }
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function callGeminiWithFallback(env, contents, fetcher = fetch) {
  const apiKey = (env?.GEMINI_API_KEY || "").trim();
  if (!apiKey) {
    throw new HttpError(503, "خدمة الذكاء الاصطناعي غير مفعلة حالياً على الخادم (GEMINI_API_KEY غير مضاف في Cloudflare Secrets).");
  }

  let lastStatus = 502;
  let lastErrorText = "";

  for (const model of GEMINI_MODELS) {
    const endpoint = `${GEMINI_BASE_URL}/${model}:generateContent?key=${encodeURIComponent(apiKey)}`;
    try {
      const res = await fetcher(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contents })
      });
      if (res.ok) {
        const json = await res.json();
        const text = json?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (text && typeof text === "string") {
          return text;
        }
        lastErrorText = "Empty Gemini candidate response";
        continue;
      }
      lastStatus = res.status;
      lastErrorText = await res.text();
      if (res.status === 401 || res.status === 403) {
        throw new HttpError(502, "مفتاح الذكاء الاصطناعي على الخادم غير مخوّل حالياً.");
      }
    } catch (err) {
      if (err instanceof HttpError) throw err;
      lastErrorText = err?.message || "Upstream network error";
    }
  }

  throw new HttpError(lastStatus >= 400 && lastStatus < 600 ? 502 : 502, `تعذر الاتصال بخدمة الذكاء الاصطناعي مؤقتاً (${lastErrorText.slice(0, 120)}).`);
}

function parseEvaluationJson(rawText) {
  const cleaned = String(rawText || "")
    .trim()
    .replace(/^```json\s*/i, "")
    .replace(/^```\s*/i, "")
    .replace(/```\s*$/i, "")
    .trim();

  try {
    const parsed = JSON.parse(cleaned);
    const score = typeof parsed.score === "number" ? Math.min(10, Math.max(0, parsed.score)) : 0;
    const status = typeof parsed.status === "string" && parsed.status.trim() ? parsed.status.trim() : "نتيجة التقييم";
    const feedback = typeof parsed.feedback === "string" ? parsed.feedback.trim() : "";
    return { score, status, feedback };
  } catch {
    return {
      score: 0.0,
      status: "تنبيه المعالجة ⚠️",
      feedback: cleaned || "تعذر تحليل مخرجات التقييم. يرجى إعادة المحاولة."
    };
  }
}



async function handleRecitationText(request, env, fetcher) {
  const body = await readJsonBody(request, 256 * 1024);
  const surahName = String(body.surahName || "").trim().slice(0, 80);
  const correctText = String(body.correctText || "").trim().slice(0, 80000);
  const recitationText = String(body.recitationText || "").trim();

  if (!surahName) {
    throw new HttpError(400, "اسم السورة مطلوب.");
  }
  if (recitationText.length < 5) {
    return jsonResponse({
      score: 0.0,
      status: "تسميع قصير جداً ⚠️",
      feedback: "أهلاً بك يا طيب، يبدو أن النص المكتوب قصير جداً أو فارغ. يرجى كتابة ما تيسر لك من السورة الكريمة بوضوح لتدقيق حفظك."
    }, 200, request, env);
  }
  if (recitationText.length > 25000) {
    throw new HttpError(400, "النص المرسل أطول من الحد المسموح به في الطلب الواحد.");
  }

  const prompt = [
    'أنت مدقق حفظ وتسميع القرآن الكريم ومعلم تجويد خبير ومحفز في تطبيق "إيمانيات".',
    `السورة التي يحاول المستخدم تسميعها هي: سورة ${surahName}.`,
    `النص الصحيح الكامل لهذه السورة من المصحف الشريف هو:\n${correctText}`,
    "",
    `نص التسميع الذي قدمه المستخدم:\n"${recitationText}"`,
    "",
    "الرجاء إجراء تدقيق تفصيلي للنص المقدم مقابل النص الصحيح للسورة:",
    "1. حدد بدقة الكلمات أو الآيات المفقودة، المغلوطة، أو المستبدلة وصححها.",
    "2. قيّم مستوى جودة وصحة الحفظ بوضع درجة من 10.",
    "3. أذكر كلمات توجيهية لطيفة ودافئة.",
    '4. أجب بتنسيق JSON فقط دون أي نص خارجه: {"score": 9.0, "status": "رائع وممتاز", "feedback": "..."}'
  ].join("\n");

  const raw = await callGeminiWithFallback(
    env,
    [{ parts: [{ text: prompt }] }],
    fetcher
  );
  return jsonResponse(parseEvaluationJson(raw), 200, request, env);
}

async function handleRecitationAudio(request, env, fetcher) {
  // Stateless audio evaluation: audio bytes live strictly in memory for the single request and are NEVER written or logged.
  const body = await readJsonBody(request, MAX_AUDIO_BASE64_LENGTH + 256 * 1024);
  const surahName = String(body.surahName || "").trim().slice(0, 80);
  const correctText = String(body.correctText || "").trim().slice(0, 80000);
  const mimeType = String(body.mimeType || "audio/mp4").trim().toLowerCase();
  const audioBase64 = typeof body.audioBase64 === "string" ? body.audioBase64.trim() : "";

  if (!surahName) {
    throw new HttpError(400, "اسم السورة مطلوب.");
  }
  if (!ALLOWED_AUDIO_MIME_TYPES.has(mimeType)) {
    throw new HttpError(400, "نوع الملف الصوتي غير مدعوم.");
  }
  if (!audioBase64 || audioBase64.length < MIN_AUDIO_BASE64_LENGTH) {
    return jsonResponse({
      score: 0.0,
      status: "تسجيل صامت أو قصير جداً ⚠️",
      feedback: "نعتذر يا طيب، يبدو أن التسجيل الصوتي صامت أو قصير جداً ولم يتضمن تلاوة واضحة. يُرجى التحدث بوضوح وقرب الميكروفون ثم إعادة المحاولة."
    }, 200, request, env);
  }
  if (audioBase64.length > MAX_AUDIO_BASE64_LENGTH) {
    throw new HttpError(413, "حجم التسجيل الصوتي كبير جداً. يُرجى تقسيم التسميع إلى مقاطع أقصر.");
  }

  const prompt = [
    'أنت مدقق حفظ وتسميع القرآن الكريم العظيم ومعلم تجويد خبير رصين ومحفز في تطبيق "إيمانيات".',
    `السورة التي يحاول المستخدم تسميعها تلاوة بالصوت هي: سورة ${surahName}.`,
    `النص الصحيح المعتمد بالكامل لهذه السورة من المصحف الشريف هو:\n${correctText}`,
    "",
    "المطلوب منك بدقة بالغة:",
    `1. استمع للملف الصوتي المرفق وقم بتفريغه ومقارنته بالآيات الصحيحة لسورة ${surahName}.`,
    "2. قيّم جودة الحفظ وصحة التلاوة وأعطِ درجة من 10.",
    '3. إذا كان التسجيل صمتاً أو ضوضاء دون كلام مسموع يرتل القرآن، فأعطِ score = 0.0 و status = "تسجيل غير مسموع ⚠️".',
    "4. بيّن الأخطاء برفق وصحح الكلمات المفقودة أو المستبدلة.",
    '5. أجب بتنسيق JSON فقط دون أي نص خارجه: {"score": 8.5, "status": "جيد جداً", "feedback": "..."}'
  ].join("\n");

  const raw = await callGeminiWithFallback(
    env,
    [{ parts: [{ text: prompt }, { inlineData: { mimeType, data: audioBase64 } }] }],
    fetcher
  );
  return jsonResponse(parseEvaluationJson(raw), 200, request, env);
}



function renderPrivacyHtml(env) {
  const supportEmail = env?.SUPPORT_EMAIL || "support@immanyat.com";
  const lastUpdated = env?.PRIVACY_LAST_UPDATED || "2026-11-14";
  return `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>سياسة الخصوصية — تطبيق إيمانيات (Imaniyat)</title>
  <style>
    body { font-family: system-ui, -apple-system, sans-serif; background:#091512; color:#f4f7f5; max-width:860px; margin:0 auto; padding:24px; line-height:1.8; }
    h1,h2 { color:#e2b857; }
    .card { background:#10221d; border:1px solid #1f4037; border-radius:14px; padding:20px; margin:16px 0; }
    .en { direction:ltr; text-align:left; color:#cfe0da; font-size:0.95rem; }
    a { color:#5eead4; }
  </style>
</head>
<body>
  <h1>سياسة الخصوصية — تطبيق إيمانيات (Imaniyat)</h1>
  <p>آخر تحديث: ${lastUpdated} | اسم الحزمة: <code>com.immanyat.com</code></p>

  <div class="card">
    <h2>١. البيانات التي يجمعها التطبيق وطريقة استخدامها</h2>
    <ul>
      <li><strong>حساب Google والمزامنة السحابية (اختياري):</strong> عند اختيار تسجيل الدخول بحساب Google، نحصل على الاسم، البريد الإلكتروني، ومعرّف Firebase UID لحفظ ومزامنة نقاط التسبيح والتحديات والختمة في قاعدة بيانات Cloud Firestore الخاصة بحسابك فقط.</li>
      <li><strong>ميزة التسميع الصوتي للقرآن الكريم (الميكروفون):</strong> لا يتم تشغيل الميكروفون إطلاقاً إلا بموافقتك الصريحة وعند الضغط على زر التسميع داخل شاشة القرآن. عند اختيار إرسال التسجيل للتدقيق، يُرسل الملف الصوتي عبر اتصال مشفّر (HTTPS) إلى خادم التحليل ثم <strong>يُحذف الملف الصوتي من جهازك فوراً بعد التحليل، ولا يُخزّن أو يُحفظ على الخادم بأي شكل</strong>.</li>
      <li><strong>الموقع الجغرافي (اختياري):</strong> يُستخدم فقط عند طلبك تحديث مدينتك لحساب مواقيت الصلاة واتجاه القبلة، ويمكنك اختيار مدينتك يدوياً دون منح إذن الموقع.</li>
      <li><strong>الإشعارات والمنبه الدقيق:</strong> لتنبيهك بمواقيت الصلاة وتذكيرك بورد القرآن الكريم.</li>
    </ul>
  </div>

  <div class="card">
    <h2>٢. مشاركة البيانات والأمان</h2>
    <p>نحن لا نبيع بيانات المستخدمين ولا نشارك تسجيلاتك الصوتية مع أي طرف إعلاني. تتم جميع الاتصالات عبر بروتوكول HTTPS المشفّر وتخضع قواعد Firestore لحماية صارمة تمنع أي مستخدم آخر من الاطلاع على بياناتك.</p>
  </div>

  <div class="card">
    <h2>٣. حذف الحساب والبيانات</h2>
    <p>يمكنك حذف حسابك وجميع بياناتك السحابية والمحلية نهائياً في أي وقت من داخل التطبيق عبر: <strong>ملفي &larr; حذف الحساب والبيانات نهائياً</strong>، أو بمراسلتنا على <a href="mailto:${supportEmail}">${supportEmail}</a>. راجع أيضاً صفحة <a href="/delete-account">تعليمات حذف الحساب</a>.</p>
  </div>

  <div class="card en">
    <h2>English Summary (Google Play Policy)</h2>
    <p>Imaniyat (<code>com.immanyat.com</code>) collects optional Google Sign-In profile data (name, email, UID) solely for cloud achievement sync via Firebase Firestore, optional device location for local prayer/Qibla calculation, and on-demand microphone audio only during Quran Tasmee' with explicit user consent. Audio clips are transmitted over HTTPS, processed statelessly, and deleted immediately without server retention. You may permanently delete your account and all cloud/local data inside the app or via <a href="/delete-account">/delete-account</a>.</p>
  </div>
</body>
</html>`;
}

function renderDeleteAccountHtml(env) {
  const supportEmail = env?.SUPPORT_EMAIL || "support@immanyat.com";
  return `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>حذف الحساب والبيانات — تطبيق إيمانيات (Imaniyat)</title>
  <style>
    body { font-family: system-ui, -apple-system, sans-serif; background:#091512; color:#f4f7f5; max-width:860px; margin:0 auto; padding:24px; line-height:1.8; }
    h1,h2 { color:#e2b857; }
    .card { background:#10221d; border:1px solid #1f4037; border-radius:14px; padding:20px; margin:16px 0; }
    .en { direction:ltr; text-align:left; color:#cfe0da; font-size:0.95rem; }
  </style>
</head>
<body>
  <h1>طلب حذف الحساب والبيانات — تطبيق إيمانيات</h1>

  <div class="card">
    <h2>أولاً: الحذف الفوري من داخل التطبيق (موصى به)</h2>
    <ol>
      <li>افتح تطبيق <strong>إيمانيات</strong> وانتقل إلى تبويب <strong>ملفي</strong>.</li>
      <li>اختر <strong>حذف الحساب والبيانات نهائياً</strong> ثم أكّد العملية.</li>
      <li>يقوم التطبيق فوراً بحذف مستندك السحابي في Firestore، وحذف حساب المصادقة في Firebase Auth، ومسح جميع الجداول المحلية في جهازك.</li>
    </ol>
  </div>

  <div class="card">
    <h2>ثانياً: طلب الحذف عبر البريد الإلكتروني (في حال إزالة التطبيق)</h2>
    <p>أرسل رسالة من نفس بريد Google المستخدم إلى: <a href="mailto:${supportEmail}">${supportEmail}</a> بعنوان <code>Delete Account - Imaniat</code>، وسيتم حذف كافة بيانات الحساب السحابية خلال 7 أيام عمل.</p>
  </div>

  <div class="card en">
    <h2>Account & Data Deletion (English Summary)</h2>
    <p>Users can permanently delete their account, Firestore cloud sync profile, Firebase Auth credentials, and local Room/SharedPreferences data directly in the Imaniat app under <strong>Profile &rarr; Delete Account & Data</strong>, or request manual deletion by emailing <strong>${supportEmail}</strong> from the registered email address.</p>
  </div>
</body>
</html>`;
}

export async function handleRequest(request, env = {}, fetcher = fetch) {
  if (request.method === "OPTIONS") {
    const origin = request.headers.get("Origin") || "";
    return new Response(null, { status: 204, headers: securityHeaders(origin, env) });
  }

  const url = new URL(request.url);
  const path = url.pathname;

  try {
    if (request.method === "GET" && (path === "/" || path === "/health")) {
      return jsonResponse({
        ok: true,
        service: "emaniat-recitation-api",
        purpose: "quran-recitation-correction-only",
        storagePolicy: "stateless-no-audio-retention",
        endpoints: [
          "/api/gemini/recitation-text",
          "/api/gemini/recitation-audio"
        ]
      }, 200, request, env);
    }

    if (request.method === "GET" && path === "/privacy") {
      return htmlResponse(renderPrivacyHtml(env), 200, request, env);
    }

    if (request.method === "GET" && path === "/delete-account") {
      return htmlResponse(renderDeleteAccountHtml(env), 200, request, env);
    }

    if (path.startsWith("/api/")) {
      if (request.method !== "POST") {
        return jsonResponse({ error: "Method Not Allowed" }, 405, request, env);
      }
      if (!isClientAuthorized(request, env)) {
        return jsonResponse({ error: "طلب غير مصرح به." }, 401, request, env);
      }

      if (path === "/api/gemini/recitation-text") {
        return await handleRecitationText(request, env, fetcher);
      }
      if (path === "/api/gemini/recitation-audio") {
        return await handleRecitationAudio(request, env, fetcher);
      }
    }

    return jsonResponse({ error: "المسار غير موجود." }, 404, request, env);
  } catch (err) {
    if (err instanceof HttpError) {
      return jsonResponse({ error: err.message }, err.status, request, env);
    }
    return jsonResponse({ error: "حدث خطأ غير متوقع في الخادم." }, 500, request, env);
  }
}

export default {
  async fetch(request, env) {
    return handleRequest(request, env, fetch);
  }
};
