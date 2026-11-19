import appVersionManifest from "../version.json" with { type: "json" };

const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
const GEMINI_MODELS = [
  "gemini-4-hydrogen",
  "gemini-3.8-flash",
  "gemini-3.7-flash",
  "gemini-3.6-flash",
  "gemini-2.5-flash"
];
const MAX_AUDIO_BASE64_LENGTH = 8 * 1024 * 1024; // ~6 MB decoded audio limit
const MIN_AUDIO_BASE64_LENGTH = 24000;
const MAX_TEXT_LENGTH = 25000;
const MAX_REQUEST_BYTES = 9 * 1024 * 1024;
const UPSTREAM_TIMEOUT_MS = 25000;

const QURAN_WHISPER_MODEL_MANIFEST = {
  modelId: "tarteel-ai/whisper-base-ar-quran-ggml-q5_0",
  fileName: "ggml-base-quran-q5_0.bin",
  expectedBytes: 55295433,
  minValidBytes: 45000000,
  primaryUrl: "https://huggingface.co/androidapps11/whisper-base-ar-quran-ggml/resolve/main/ggml-base-quran-q5_0.bin?download=true",
  mirrorUrls: [
    "https://huggingface.co/herrero1234/ayahsync-model/resolve/main/ggml-base-ar-quran-q5_0.bin?download=true"
  ],
  sampleRateHz: 16000,
  channels: 1,
  bitsPerSample: 16,
  language: "ar"
};

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

const RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    isTargetSurahRecitation: { type: "BOOLEAN" },
    heardTranscript: { type: "STRING" },
    score: { type: "NUMBER" },
    status: { type: "STRING" },
    mistakes: {
      type: "ARRAY",
      items: { type: "STRING" }
    },
    feedback: { type: "STRING" }
  },
  required: ["isTargetSurahRecitation", "heardTranscript", "score", "status", "mistakes", "feedback"]
};

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
    "Access-Control-Max-Age": "86400",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "X-Robots-Tag": "noindex, nofollow",
    "Referrer-Policy": "no-referrer",
    "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
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
    return true;
  }
  const provided = (request.headers.get("X-App-Token") || "").trim();
  return constantTimeEqual(provided, expectedToken);
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function readJsonBody(request, maxBytes = MAX_REQUEST_BYTES) {
  const contentType = (request.headers.get("Content-Type") || "").toLowerCase();
  if (contentType && !contentType.includes("application/json")) {
    throw new HttpError(415, "نوع المحتوى غير مدعوم. يُرجى إرسال application/json.");
  }
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

// =============================================================================
// SMART ARABIC QURAN RECITATION NORMALIZER & VERIFIER
// =============================================================================

export function normalizeArabicQuranText(text) {
  return String(text || "")
    .replace(/\uFEFF/g, "")
    // Strip Arabic diacritics, Quranic annotation marks, superscript alef, tatweel
    .replace(/[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640]/g, "")
    // Strip digits, ayah end signs, brackets, punctuation
    .replace(/[0-9\u0660-\u0669\u06DD\uFD3E\uFD3F]/g, " ")
    // Normalize Alef / Hamza / Ya / Ta Marbuta variants
    .replace(/[\u0622\u0623\u0625\u0671]/g, "ا")
    .replace(/\u0649/g, "ي")
    .replace(/\u0629/g, "ه")
    .replace(/\u0624/g, "و")
    .replace(/\u0626/g, "ي")
    // Remove anything outside Arabic letters and whitespace
    .replace(/[^\u0621-\u064A\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function looseOrthographicKey(word) {
  // Helps match Uthmani vs standard spelling e.g. الرحمان/الرحمن, صلوة/صلاة
  if (word.length <= 3) return word;
  return word
    .replace(/^ال/, "ال")
    .replace(/([بتثجحخدذرزسشصضطظعغفقكلمنهوي])ا([بتثجحخدذرزسشصضطظعغفقكلمنهوي])$/g, "$1$2");
}

function tokensEqual(a, b) {
  if (a === b) return true;
  if (looseOrthographicKey(a) === looseOrthographicKey(b)) return true;
  // Allow single-character tolerance on longer words (>=5 chars) for minor ASR transcription spelling differences
  if (a.length >= 5 && b.length >= 5 && Math.abs(a.length - b.length) <= 1) {
    let diffs = 0;
    let i = 0;
    let j = 0;
    while (i < a.length && j < b.length) {
      if (a[i] === b[j]) {
        i++;
        j++;
      } else {
        diffs++;
        if (diffs > 1) return false;
        if (a.length > b.length) i++;
        else if (b.length > a.length) j++;
        else {
          i++;
          j++;
        }
      }
    }
    diffs += (a.length - i) + (b.length - j);
    return diffs <= 1;
  }
  return false;
}

export function tokenizeArabicWords(text) {
  const normalized = normalizeArabicQuranText(text);
  if (!normalized) return [];
  return normalized.split(" ").filter(Boolean);
}

function stripLeadingPrefixPhrase(tokens, phraseTokens) {
  if (tokens.length < phraseTokens.length) return { tokens, stripped: false };
  for (let i = 0; i < phraseTokens.length; i++) {
    if (!tokensEqual(tokens[i], phraseTokens[i])) {
      return { tokens, stripped: false };
    }
  }
  return { tokens: tokens.slice(phraseTokens.length), stripped: true };
}

function lcsInWindow(userTokens, windowTokens) {
  const n = userTokens.length;
  const m = windowTokens.length;
  if (n === 0 || m === 0) return { lcsCount: 0, matchedExpectedIndices: [], matchedUserIndices: [] };

  const dp = Array.from({ length: n + 1 }, () => new Int32Array(m + 1));
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      if (tokensEqual(userTokens[i - 1], windowTokens[j - 1])) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = dp[i - 1][j] >= dp[i][j - 1] ? dp[i - 1][j] : dp[i][j - 1];
      }
    }
  }

  const matchedUserIndices = [];
  const matchedExpectedIndices = [];
  let i = n;
  let j = m;
  while (i > 0 && j > 0) {
    if (tokensEqual(userTokens[i - 1], windowTokens[j - 1])) {
      matchedUserIndices.push(i - 1);
      matchedExpectedIndices.push(j - 1);
      i--;
      j--;
    } else if (dp[i - 1][j] >= dp[i][j - 1]) {
      i--;
    } else {
      j--;
    }
  }
  matchedUserIndices.reverse();
  matchedExpectedIndices.reverse();

  return {
    lcsCount: dp[n][m],
    matchedExpectedIndices,
    matchedUserIndices
  };
}

function findBestAnchorWindow(userTokens, expectedTokens) {
  if (expectedTokens.length <= 180) {
    return { start: 0, end: expectedTokens.length };
  }

  // Build bigram positions map for fast candidate localization in long Surahs (e.g. Al-Baqarah)
  const wordPositions = new Map();
  for (let j = 0; j < expectedTokens.length; j++) {
    const key = looseOrthographicKey(expectedTokens[j]);
    if (!wordPositions.has(key)) wordPositions.set(key, []);
    wordPositions.get(key).push(j);
  }

  const anchorHits = [];
  for (let i = 0; i < userTokens.length - 1; i++) {
    const positions = wordPositions.get(looseOrthographicKey(userTokens[i])) || [];
    for (const pos of positions) {
      for (let gap = 1; gap <= 2; gap++) {
        if (pos + gap < expectedTokens.length && tokensEqual(userTokens[i + 1], expectedTokens[pos + gap])) {
          anchorHits.push(pos);
          break;
        }
      }
    }
  }

  if (anchorHits.length === 0) {
    return { start: 0, end: Math.min(expectedTokens.length, 220) };
  }

  anchorHits.sort((a, b) => a - b);
  const targetWindowLen = Math.max(80, Math.min(240, userTokens.length * 3));
  let bestCount = 0;
  let bestStart = anchorHits[0];
  let right = 0;
  for (let left = 0; left < anchorHits.length; left++) {
    while (right < anchorHits.length && anchorHits[right] - anchorHits[left] <= targetWindowLen) {
      right++;
    }
    const count = right - left;
    if (count > bestCount) {
      bestCount = count;
      bestStart = anchorHits[left];
    }
  }

  const start = Math.max(0, bestStart - 10);
  const end = Math.min(expectedTokens.length, bestStart + targetWindowLen + 15);
  return { start, end };
}

function roundToHalf(val) {
  return Math.round(Math.max(0, Math.min(10, val)) * 2) / 2;
}

export function analyzeArabicRecitation(expectedText, candidateText, surahName = "") {
  const expectedTokensRaw = tokenizeArabicWords(expectedText);
  let userTokens = tokenizeArabicWords(candidateText);

  const istiadha = ["اعوذ", "بالله", "من", "الشيطان", "الرجيم"];
  const basmalah = ["بسم", "الله", "الرحمن", "الرحيم"];

  const istiadhaStripped = stripLeadingPrefixPhrase(userTokens, istiadha);
  userTokens = istiadhaStripped.tokens;

  const isFatiha = normalizeArabicQuranText(surahName).includes("فاتحه");
  let onlyBasmalah = false;
  if (!isFatiha && expectedTokensRaw.length > 8) {
    const basmalahCheck = stripLeadingPrefixPhrase(userTokens, basmalah);
    if (basmalahCheck.stripped && basmalahCheck.tokens.length === 0) {
      onlyBasmalah = true;
    }
  }

  if (userTokens.length < 2) {
    return {
      expectedCount: expectedTokensRaw.length,
      userCount: userTokens.length,
      matchedCount: 0,
      orderPrecision: 0,
      bigramPrecision: 0,
      windowRecall: 0,
      surahCoverage: 0,
      onlyBasmalahOrIstiadha: istiadhaStripped.stripped || onlyBasmalah,
      isLikelyTargetSurah: false,
      maxObjectiveScore: (istiadhaStripped.stripped || onlyBasmalah) ? 0.5 : 0.0,
      missingWords: [],
      extraWords: userTokens
    };
  }

  if (onlyBasmalah) {
    return {
      expectedCount: expectedTokensRaw.length,
      userCount: userTokens.length,
      matchedCount: 0,
      orderPrecision: 0,
      bigramPrecision: 0,
      windowRecall: 0,
      surahCoverage: 0,
      onlyBasmalahOrIstiadha: true,
      isLikelyTargetSurah: false,
      maxObjectiveScore: 0.5,
      missingWords: expectedTokensRaw.slice(0, 6),
      extraWords: []
    };
  }

  const { start, end } = findBestAnchorWindow(userTokens, expectedTokensRaw);
  const windowTokens = expectedTokensRaw.slice(start, end);

  const { lcsCount, matchedExpectedIndices, matchedUserIndices } = lcsInWindow(userTokens, windowTokens);

  // Measure contiguous bigrams across matched pairs
  let bigramHits = 0;
  for (let k = 0; k < matchedExpectedIndices.length - 1; k++) {
    const expGap = matchedExpectedIndices[k + 1] - matchedExpectedIndices[k];
    const usrGap = matchedUserIndices[k + 1] - matchedUserIndices[k];
    if (expGap >= 1 && expGap <= 2 && usrGap >= 1 && usrGap <= 2) {
      bigramHits++;
    }
  }

  const orderPrecision = userTokens.length > 0 ? lcsCount / userTokens.length : 0;
  const bigramPrecision = userTokens.length > 1 ? bigramHits / (userTokens.length - 1) : 0;

  let spanRecall = 0;
  let missingWords = [];
  if (matchedExpectedIndices.length > 0) {
    const firstExp = matchedExpectedIndices[0];
    const lastExp = matchedExpectedIndices[matchedExpectedIndices.length - 1];
    const spanLen = Math.max(1, lastExp - firstExp + 1);
    spanRecall = lcsCount / spanLen;

    const matchedSet = new Set(matchedExpectedIndices);
    for (let idx = firstExp; idx <= lastExp && missingWords.length < 8; idx++) {
      if (!matchedSet.has(idx)) {
        missingWords.push(windowTokens[idx]);
      }
    }
  }

  const matchedUserSet = new Set(matchedUserIndices);
  const extraWords = userTokens.filter((_, idx) => !matchedUserSet.has(idx)).slice(0, 8);
  const surahCoverage = expectedTokensRaw.length > 0 ? lcsCount / expectedTokensRaw.length : 0;

  // Determine if the user is actually reciting this Surah (rather than random talk or a different Surah)
  let isLikelyTargetSurah = false;
  if (userTokens.length <= 3) {
    isLikelyTargetSurah = orderPrecision >= 0.66 && (bigramHits >= 1 || lcsCount === userTokens.length);
  } else {
    isLikelyTargetSurah = orderPrecision >= 0.42 && (bigramPrecision >= 0.18 || (lcsCount >= 4 && spanRecall >= 0.45));
  }

  let maxObjectiveScore = 0.0;
  if (!isLikelyTargetSurah) {
    maxObjectiveScore = (orderPrecision >= 0.25 && bigramHits >= 1) ? 1.5 : 0.0;
  } else {
    const segmentQuality = (0.50 * orderPrecision) + (0.30 * spanRecall) + (0.20 * bigramPrecision);
    let scoreEstimate = segmentQuality * 10;

    // For short/medium Surahs (<= 100 words), require reasonable coverage before awarding full marks
    if (expectedTokensRaw.length <= 100 && surahCoverage < 0.78) {
      const coverageFactor = Math.min(1, surahCoverage / 0.80);
      scoreEstimate = Math.min(scoreEstimate, Math.max(2.0, scoreEstimate * (0.45 + 0.55 * coverageFactor)));
    } else if (expectedTokensRaw.length > 100 && userTokens.length < 5) {
      scoreEstimate = Math.min(scoreEstimate, 6.0);
    }

    maxObjectiveScore = roundToHalf(scoreEstimate);
  }

  return {
    expectedCount: expectedTokensRaw.length,
    userCount: userTokens.length,
    matchedCount: lcsCount,
    orderPrecision: Number(orderPrecision.toFixed(3)),
    bigramPrecision: Number(bigramPrecision.toFixed(3)),
    windowRecall: Number(spanRecall.toFixed(3)),
    surahCoverage: Number(surahCoverage.toFixed(3)),
    onlyBasmalahOrIstiadha: false,
    isLikelyTargetSurah,
    maxObjectiveScore,
    missingWords,
    extraWords
  };
}

function scoreDiscoveredModel(modelId) {
  const id = modelId.toLowerCase();
  if (id.includes("hydrogen")) return 100;
  if (id.includes("gemini-4") && id.includes("flash")) return 95;
  if (id.includes("gemini-4")) return 90;
  if (id.includes("3.8") && id.includes("flash")) return 88;
  if (id.includes("3.7") && id.includes("flash")) return 86;
  if (id.includes("3.6") && id.includes("flash")) return 84;
  if (id.includes("flash-latest")) return 82;
  if (id.includes("flash") && !id.includes("lite")) return 80;
  if (id.includes("flash")) return 70;
  if (id.includes("pro")) return 60;
  return 50;
}

async function discoverAvailableGeminiModels(apiKey, alreadyTriedSet, fetcher = fetch) {
  try {
    const listUrl = `${GEMINI_BASE_URL}?key=${encodeURIComponent(apiKey)}&pageSize=100`;
    const fetchOptions = { method: "GET" };
    if (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") {
      fetchOptions.signal = AbortSignal.timeout(10000);
    }
    const res = await fetcher(listUrl, fetchOptions);
    if (!res.ok) return [];

    const data = await res.json();
    const models = Array.isArray(data?.models) ? data.models : [];
    return models
      .filter((m) => {
        const methods = Array.isArray(m?.supportedGenerationMethods) ? m.supportedGenerationMethods : [];
        if (!methods.includes("generateContent")) return false;
        const rawName = String(m?.name || "").replace(/^models\//, "").trim();
        const lower = rawName.toLowerCase();
        if (!lower.startsWith("gemini-")) return false;
        if (alreadyTriedSet.has(rawName)) return false;
        return !/(embedding|imagen|veo|tts|aqa|robotics|computer-use|image-generation|-image|-live|native-audio-dialog)/i.test(lower);
      })
      .map((m) => String(m.name).replace(/^models\//, "").trim())
      .sort((a, b) => scoreDiscoveredModel(b) - scoreDiscoveredModel(a))
      .slice(0, 6);
  } catch {
    return [];
  }
}

async function tryGenerateWithModel(apiKey, model, contents, fetcher, useSchema) {
  const endpoint = `${GEMINI_BASE_URL}/${model}:generateContent?key=${encodeURIComponent(apiKey)}`;
  const bodyPayload = {
    contents,
    generationConfig: useSchema
      ? {
          temperature: 0.0,
          topP: 0.1,
          responseMimeType: "application/json",
          responseSchema: RESPONSE_SCHEMA
        }
      : {
          temperature: 0.0,
          topP: 0.1,
          responseMimeType: "application/json"
        }
  };

  const fetchOptions = {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(bodyPayload)
  };
  if (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") {
    fetchOptions.signal = AbortSignal.timeout(UPSTREAM_TIMEOUT_MS);
  }

  const res = await fetcher(endpoint, fetchOptions);
  if (res.ok) {
    const json = await res.json();
    const text = json?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (text && typeof text === "string") {
      return { ok: true, text };
    }
    return { ok: false, status: 502, errorText: "Empty Gemini candidate response" };
  }

  const errorText = await res.text();
  if (res.status === 401 || res.status === 403) {
    throw new HttpError(502, "مفتاح الذكاء الاصطناعي على الخادم غير مخوّل حالياً.");
  }

  if (res.status === 400 && useSchema) {
    return await tryGenerateWithModel(apiKey, model, contents, fetcher, false);
  }

  return { ok: false, status: res.status, errorText };
}

async function callGeminiWithFallback(env, contents, fetcher = fetch, useSchema = true) {
  const apiKey = (env?.GEMINI_API_KEY || "").trim();
  if (!apiKey) {
    throw new HttpError(503, "خدمة الذكاء الاصطناعي غير مفعلة حالياً على الخادم (GEMINI_API_KEY غير مضاف في Cloudflare Secrets).");
  }

  let lastStatus = 502;
  let lastErrorText = "";
  const tried = new Set();

  for (const model of GEMINI_MODELS) {
    tried.add(model);
    try {
      const attempt = await tryGenerateWithModel(apiKey, model, contents, fetcher, useSchema);
      if (attempt.ok) return attempt.text;
      lastStatus = attempt.status;
      lastErrorText = attempt.errorText;
    } catch (err) {
      if (err instanceof HttpError) throw err;
      lastErrorText = err?.message || "Upstream network error";
    }
  }

  // Dynamic model auto-discovery: if static model IDs returned 404/deprecated/unavailable, query v1beta/models
  const discoveredModels = await discoverAvailableGeminiModels(apiKey, tried, fetcher);
  for (const model of discoveredModels) {
    tried.add(model);
    try {
      const attempt = await tryGenerateWithModel(apiKey, model, contents, fetcher, useSchema);
      if (attempt.ok) return attempt.text;
      lastStatus = attempt.status;
      lastErrorText = attempt.errorText;
    } catch (err) {
      if (err instanceof HttpError) throw err;
      lastErrorText = err?.message || "Upstream network error";
    }
  }

  throw new HttpError(
    lastStatus >= 400 && lastStatus < 600 ? 502 : 502,
    `تعذر الاتصال بخدمة الذكاء الاصطناعي مؤقتاً (${lastErrorText.slice(0, 140)}).`
  );
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
    const isTargetSurahRecitation = parsed.isTargetSurahRecitation !== false;
    const heardTranscript = typeof parsed.heardTranscript === "string" ? parsed.heardTranscript.trim() : "";
    const score = typeof parsed.score === "number" ? Math.min(10, Math.max(0, parsed.score)) : 0;
    const status = typeof parsed.status === "string" && parsed.status.trim() ? parsed.status.trim() : "نتيجة التقييم";
    const mistakes = Array.isArray(parsed.mistakes)
      ? parsed.mistakes.map((m) => String(m).trim()).filter(Boolean).slice(0, 12)
      : [];
    const feedback = typeof parsed.feedback === "string" ? parsed.feedback.trim() : "";
    return { isTargetSurahRecitation, heardTranscript, score, status, mistakes, feedback };
  } catch {
    return {
      isTargetSurahRecitation: false,
      heardTranscript: "",
      score: 0.0,
      status: "تنبيه المعالجة ⚠️",
      mistakes: [],
      feedback: cleaned || "تعذر تحليل مخرجات التقييم. يرجى إعادة المحاولة."
    };
  }
}

function buildMismatchResponse(surahName, analysis, heardTranscript = "") {
  if (analysis.onlyBasmalahOrIstiadha) {
    return {
      score: 0.5,
      status: "البسملة فقط دون آيات السورة ⚠️",
      transcript: heardTranscript,
      matchedRatio: analysis.orderPrecision,
      mistakes: [],
      feedback: `تم سماع الاستعاذة أو البسملة فقط دون البدء في آيات سورة ${surahName}. يُرجى تلاوة آيات السورة نفسها ليتم تقييم حفظك بدقة.`
    };
  }

  const heardNote = heardTranscript
    ? `\n• ما تم رصده فعلياً: "${heardTranscript.slice(0, 180)}"`
    : "";

  return {
    score: 0.0,
    status: "الكلام لا يطابق السورة ❌",
    transcript: heardTranscript,
    matchedRatio: analysis.orderPrecision,
    mistakes: analysis.extraWords.slice(0, 5),
    feedback: `الكلام المُرسل أو المسموع لا يطابق آيات سورة ${surahName} (نسبة التطابق ${(analysis.orderPrecision * 100).toFixed(0)}%).${heardNote}\n• يُرجى تلاوة أو كتابة آيات من سورة ${surahName} بوضوح للتقييم الصحيح.`
  };
}

function finalizeVerifiedResult(aiEval, analysis, surahName, heardTranscript) {
  if (!aiEval.isTargetSurahRecitation || !analysis.isLikelyTargetSurah) {
    return buildMismatchResponse(surahName, analysis, heardTranscript);
  }

  // Strictly clamp AI score so it can NEVER over-score partial or mismatched recitations
  const clampedScore = roundToHalf(Math.min(aiEval.score, analysis.maxObjectiveScore));

  let status = aiEval.status;
  if (clampedScore < 3.0) {
    status = "يحتاج لمراجعة وتثبيت ⚠️";
  } else if (clampedScore < 6.0 && !status.includes("جزئي")) {
    status = analysis.surahCoverage < 0.7 && analysis.orderPrecision >= 0.75
      ? "تسميع جزئي متقن ✨"
      : "حفظ متوسط يحتاج مراجعة 📖";
  }

  let feedback = aiEval.feedback || "";
  if (aiEval.score > analysis.maxObjectiveScore + 1.0) {
    const details = [];
    if (analysis.missingWords.length > 0) {
      details.push(`كلمات ناقصة داخل المقطع: (${analysis.missingWords.slice(0, 6).join("، ")})`);
    }
    if (analysis.extraWords.length > 0) {
      details.push(`كلمات غير مطابقة: (${analysis.extraWords.slice(0, 5).join("، ")})`);
    }
    if (analysis.expectedCount <= 100 && analysis.surahCoverage < 0.78) {
      details.push(`تم تسميع ${analysis.matchedCount} كلمة من أصل ${analysis.expectedCount} كلمة في السورة.`);
    }
    if (details.length > 0) {
      feedback = `${feedback}\n\n📌 ملاحظات التحقق الدقيق:\n• ${details.join("\n• ")}`.trim();
    }
  }

  return {
    score: clampedScore,
    status,
    transcript: heardTranscript,
    matchedRatio: analysis.orderPrecision,
    mistakes: aiEval.mistakes,
    feedback: feedback || "تم تدقيق التلاوة بنجاح."
  };
}

// =============================================================================
// ROUTE HANDLERS
// =============================================================================

async function handleRecitationText(request, env, fetcher) {
  const body = await readJsonBody(request, 256 * 1024);
  const surahName = String(body.surahName || "").trim().slice(0, 80);
  const correctText = String(body.correctText || "").trim().slice(0, 80000);
  const recitationText = String(body.recitationText || "").trim();

  if (!surahName || !correctText) {
    throw new HttpError(400, "اسم السورة والنص المرجعي مطلوبان.");
  }
  if (recitationText.length < 5) {
    return jsonResponse({
      score: 0.0,
      status: "تسميع قصير جداً ⚠️",
      transcript: recitationText,
      matchedRatio: 0,
      mistakes: [],
      feedback: "أهلاً بك يا طيب، يبدو أن النص المكتوب قصير جداً أو فارغ. يرجى كتابة ما تيسر لك من السورة الكريمة بوضوح لتدقيق حفظك."
    }, 200, request, env);
  }
  if (recitationText.length > MAX_TEXT_LENGTH) {
    throw new HttpError(400, "النص المرسل أطول من الحد المسموح به في الطلب الواحد.");
  }

  // Step 1: Deterministic pre-verification (prevents wasting AI quota & eliminates 9/10 on random chat!)
  const preAnalysis = analyzeArabicRecitation(correctText, recitationText, surahName);
  if (!preAnalysis.isLikelyTargetSurah) {
    return jsonResponse(buildMismatchResponse(surahName, preAnalysis, recitationText), 200, request, env);
  }

  const prompt = [
    'أنت مدقق حفظ قرآن كريم صارم وعادل في تطبيق "إيمانيات".',
    `السورة المطلوبة هي: سورة ${surahName}.`,
    `مقاييس المطابقة الآلية المبدئية: نسبة تطابق الكلمات المرتبة ${(preAnalysis.orderPrecision * 100).toFixed(0)}%، وتغطية السورة ${(preAnalysis.surahCoverage * 100).toFixed(0)}%.`,
    `النص الصحيح الكامل لسورة ${surahName}:\n${correctText}`,
    "",
    `نص التسميع الذي كتبه المستخدم:\n"${recitationText}"`,
    "",
    "قواعد صارمة لا تخالفها:",
    "1. إذا كان النص كلاماً عادياً أو غير قرآني أو لا يطابق السورة المطلوبة: اجعل isTargetSurahRecitation = false و score = 0.0 فوراً.",
    "2. لا تجامل بالدرجات أبداً. احسب الدرجة من 10 بدقة بناءً على الأخطاء والكلمات الناقصة والمستبدلة ومقدار التغطية.",
    "3. ضع النص المُرسل في heardTranscript، واذكر الأخطاء المحددة في mistakes، واكتب توجيهاً واضحاً في feedback."
  ].join("\n");

  const raw = await callGeminiWithFallback(
    env,
    [{ parts: [{ text: prompt }] }],
    fetcher
  );
  const aiEval = parseEvaluationJson(raw);
  const finalResult = finalizeVerifiedResult(aiEval, preAnalysis, surahName, recitationText);
  return jsonResponse(finalResult, 200, request, env);
}

async function handleRecitationAudio(request, env, fetcher) {
  // Stateless audio evaluation: audio bytes live strictly in memory for this single request and are NEVER written or logged.
  const body = await readJsonBody(request, MAX_AUDIO_BASE64_LENGTH + 256 * 1024);
  const surahName = String(body.surahName || "").trim().slice(0, 80);
  const correctText = String(body.correctText || "").trim().slice(0, 80000);
  const mimeType = String(body.mimeType || "audio/mp4").trim().toLowerCase();
  const audioBase64 = typeof body.audioBase64 === "string" ? body.audioBase64.trim() : "";

  if (!surahName || !correctText) {
    throw new HttpError(400, "اسم السورة والنص المرجعي مطلوبان.");
  }
  if (!ALLOWED_AUDIO_MIME_TYPES.has(mimeType)) {
    throw new HttpError(400, "نوع الملف الصوتي غير مدعوم.");
  }
  if (!audioBase64 || audioBase64.length < MIN_AUDIO_BASE64_LENGTH) {
    return jsonResponse({
      score: 0.0,
      status: "تسجيل صامت أو قصير جداً ⚠️",
      transcript: "",
      matchedRatio: 0,
      mistakes: [],
      feedback: "نعتذر يا طيب، يبدو أن التسجيل الصوتي صامت أو قصير جداً ولم يتضمن تلاوة واضحة. يُرجى التحدث بوضوح وقرب الميكروفون ثم إعادة المحاولة."
    }, 200, request, env);
  }
  if (audioBase64.length > MAX_AUDIO_BASE64_LENGTH) {
    throw new HttpError(413, "حجم التسجيل الصوتي كبير جداً. يُرجى تقسيم التسميع إلى مقاطع أقصر.");
  }

  const prompt = [
    'أنت مدقق حفظ وتلاوة القرآن الكريم صارم ودقيق في تطبيق "إيمانيات".',
    `السورة المفترضة للتسميع هي: سورة ${surahName}.`,
    `النص المرجعي الكامل لسورة ${surahName} (للمقارنة فقط بعد التفريغ):\n${correctText}`,
    "",
    "تحذير حاسم لمنع الأخطاء:",
    "1. استمع أولاً إلى الصوت وفرّغ الكلمات التي نطقها المتحدث حرفياً في حقل heardTranscript كما سمعتها بالضبط! يُمنع منعاً باتاً نسخ النص المرجعي إذا لم ينطق به المتحدث.",
    "2. إذا سمعت كلاماً عادياً (مثل: ألو، السلام عليكم، تجربة، حديث يومي، ضحك، غناء، أو لغة غير عربية) أو ضوضاء أو صمتاً أو سورة قرآنية أخرى غير المطلوبة: اجعل isTargetSurahRecitation = false، واجعل score = 0.0، واكتب ما سمعته في heardTranscript.",
    `3. فقط إذا تأكدت أن المتحدث يرتل فعلاً آيات من سورة ${surahName}: قيّم التلاوة بصدق وصرامة من 10 حسب الكلمات الصحيحة والمفقودة والمبدلة وأحكام النطق.`,
    "4. لا تمنح درجات مرتفعة مجاملةً أبداً. اذكر الأخطاء المحددة في mistakes والتوجيه في feedback."
  ].join("\n");

  const raw = await callGeminiWithFallback(
    env,
    [{ parts: [{ text: prompt }, { inlineData: { mimeType, data: audioBase64 } }] }],
    fetcher
  );
  const aiEval = parseEvaluationJson(raw);

  // Step 2: Independent server-side verification against Gemini's verbatim heardTranscript
  const transcriptForVerification = aiEval.heardTranscript || "";
  if (!transcriptForVerification) {
    return jsonResponse({
      score: 0.0,
      status: "لم يُرصد كلام مسموع ⚠️",
      transcript: "",
      matchedRatio: 0,
      mistakes: [],
      feedback: "لم يتمكن النظام من تمييز تلاوة قرآنية واضحة في التسجيل الصوتي. يُرجى الاقتراب من الميكروفون والتلاوة بصوت واضح بعيداً عن الضوضاء."
    }, 200, request, env);
  }

  const audioTranscriptAnalysis = analyzeArabicRecitation(correctText, transcriptForVerification, surahName);
  const finalResult = finalizeVerifiedResult(aiEval, audioTranscriptAnalysis, surahName, transcriptForVerification);
  return jsonResponse(finalResult, 200, request, env);
}

// =============================================================================
// GOOGLE PLAY COMPLIANCE STATIC PAGES
// =============================================================================

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
      <li><strong>ميزة التسميع الصوتي للقرآن الكريم (الميكروفون):</strong> لا يتم تشغيل الميكروفون إطلاقاً إلا بموافقتك الصريحة وعند الضغط على زر التسميع داخل شاشة القرآن. عند استخدام <strong>التسميع بدون إنترنت (Whisper)</strong> يُعالَج الصوت محلياً بالكامل على جهازك دون إرساله عبر الإنترنت. وعند اختيار التسميع السحابي الاختياري يُرسل المقطع عبر اتصال مشفّر (HTTPS) إلى خادم التحليل ثم يُحذف فوراً دون أي تخزين.</li>
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
    <p>Imaniyat (<code>com.immanyat.com</code>) collects optional Google Sign-In profile data (name, email, UID) solely for cloud achievement sync via Firebase Firestore, optional device location for local prayer/Qibla calculation, and on-demand microphone audio only during Quran Tasmee' with explicit user consent. Audio clips are transmitted over HTTPS, processed statelessly on Cloudflare Workers, and deleted immediately without server retention. You may permanently delete your account and all cloud/local data inside the app or via <a href="/delete-account">/delete-account</a>.</p>
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
        runtime: "cloudflare-workers",
        service: "emaniat-recitation-api",
        purpose: "quran-recitation-correction-only",
        primaryModel: GEMINI_MODELS[0],
        models: GEMINI_MODELS,
        stateless: true,
        storagePolicy: "stateless-no-audio-retention",
        smartVerification: [
          "structured-json-schema",
          "verbatim-audio-transcription",
          "arabic-lcs-alignment",
          "objective-score-clamping"
        ],
        limits: {
          maxRequestBytes: MAX_REQUEST_BYTES,
          maxAudioBase64Length: MAX_AUDIO_BASE64_LENGTH,
          minAudioBase64Length: MIN_AUDIO_BASE64_LENGTH,
          maxTextLength: MAX_TEXT_LENGTH,
          upstreamTimeoutMs: UPSTREAM_TIMEOUT_MS,
          allowedAudioMimeTypes: Array.from(ALLOWED_AUDIO_MIME_TYPES)
        },
        endpoints: [
          "/version.json",
          "/api/models/quran-whisper",
          "/api/gemini/recitation-text",
          "/api/gemini/recitation-audio"
        ]
      }, 200, request, env);
    }

    if (request.method === "GET" && (path === "/version.json" || path === "/api/app/version")) {
      return jsonResponse({
        versionCode: Number(env?.APP_VERSION_CODE || appVersionManifest.versionCode || 2),
        versionName: String(env?.APP_VERSION_NAME || appVersionManifest.versionName || "1.5"),
        forceUpdate: typeof env?.APP_FORCE_UPDATE === "string"
          ? env.APP_FORCE_UPDATE.toLowerCase() === "true"
          : Boolean(appVersionManifest.forceUpdate),
        title: String(env?.APP_UPDATE_TITLE || appVersionManifest.title || "تحديث جديد لتطبيق إيمانيات ✨"),
        message: String(env?.APP_UPDATE_MESSAGE || appVersionManifest.message || ""),
        downloadUrl: String(env?.APP_DOWNLOAD_URL || appVersionManifest.downloadUrl || "https://apkpure.com/p/com.immanyat.com"),
        minSupportedVersion: Number(env?.APP_MIN_SUPPORTED_VERSION || appVersionManifest.minSupportedVersion || 1),
        releaseNotes: String(env?.APP_RELEASE_NOTES || appVersionManifest.releaseNotes || "")
      }, 200, request, env);
    }

    if (request.method === "GET" && path === "/api/models/quran-whisper") {
      const overridePrimary = (env?.WHISPER_MODEL_URL || "").trim();
      const primaryUrl = overridePrimary.startsWith("https://")
        ? overridePrimary
        : QURAN_WHISPER_MODEL_MANIFEST.primaryUrl;
      return jsonResponse({
        ok: true,
        ...QURAN_WHISPER_MODEL_MANIFEST,
        primaryUrl
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
  async fetch(request, env, _ctx) {
    return handleRequest(request, env, fetch);
  }
};
