import test from "node:test";
import assert from "node:assert/strict";
import { handleRequest, analyzeArabicRecitation } from "../src/index.js";

test("GET /health returns Cloudflare Worker specs and recitation-only stateless status", async () => {
  const req = new Request("https://api.immanyat.example/health", { method: "GET" });
  const res = await handleRequest(req, {});
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.ok, true);
  assert.equal(json.runtime, "cloudflare-workers");
  assert.equal(json.stateless, true);
  assert.equal(json.purpose, "quran-recitation-correction-only");
  assert.equal(json.primaryModel, "gemini-4-hydrogen");
  assert.deepEqual(json.models, [
    "gemini-4-hydrogen",
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-2.5-flash"
  ]);
  assert.equal(json.storagePolicy, "stateless-no-audio-retention");
  assert.ok(Array.isArray(json.smartVerification));
  assert.ok(json.limits.maxAudioBase64Length === 8 * 1024 * 1024);
});

test("GET /privacy and /delete-account serve Arabic & English HTML pages", async () => {
  const privRes = await handleRequest(new Request("https://api.immanyat.example/privacy"), {});
  assert.equal(privRes.status, 200);
  const privHtml = await privRes.text();
  assert.match(privHtml, /سياسة الخصوصية/);
  assert.match(privHtml, /com\.immanyat\.com/);

  const delRes = await handleRequest(new Request("https://api.immanyat.example/delete-account"), {});
  assert.equal(delRes.status, 200);
  const delHtml = await delRes.text();
  assert.match(delHtml, /حذف الحساب والبيانات/);
});

test("POST /api/gemini/recitation-text rejects random non-Quranic speech with 0.0 instead of 9/10", async () => {
  let geminiCalled = false;
  const mockFetch = async () => {
    geminiCalled = true;
    return new Response("{}", { status: 200 });
  };

  const req = new Request("https://api.immanyat.example/api/gemini/recitation-text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ الرَّحْمَٰنِ الرَّحِيمِ مَالِكِ يَوْمِ الدِّينِ",
      recitationText: "ألو السلام عليكم كيف حالك اليوم هذا كلام عادي للتجربة"
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "test-key" }, mockFetch);
  assert.equal(res.status, 200);
  assert.equal(geminiCalled, false, "Should reject non-Quranic chatter locally before wasting Gemini call");
  const json = await res.json();
  assert.equal(json.score, 0);
  assert.match(json.status, /لا يطابق/);
});

test("POST /api/gemini/recitation-text rejects wrong Surah (Al-Ikhlas recited while Al-Fatiha expected)", async () => {
  const req = new Request("https://api.immanyat.example/api/gemini/recitation-text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ مَالِكِ يَوْمِ الدِّينِ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
      recitationText: "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد"
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "test-key" });
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.score, 0);
  assert.match(json.status, /لا يطابق/);
});

test("POST /api/gemini/recitation-text evaluates valid Surah recitation cleanly (handles leading Isti'adha)", async () => {
  const mockFetch = async () => {
    return new Response(JSON.stringify({
      candidates: [
        {
          content: {
            parts: [{
              text: JSON.stringify({
                isTargetSurahRecitation: true,
                heardTranscript: "أعوذ بالله من الشيطان الرجيم قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد",
                score: 10,
                status: "حفظ متقن",
                mistakes: [],
                feedback: "أحسنت، التلاوة مطابقة تماماً."
              })
            }]
          }
        }
      ]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  const req = new Request("https://api.immanyat.example/api/gemini/recitation-text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الإخلاص",
      correctText: "قُلْ هُوَ اللَّهُ أَحَدٌ اللَّهُ الصَّمَدُ لَمْ يَلِدْ وَلَمْ يُولَدْ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
      recitationText: "اعوذ بالله من الشيطان الرجيم قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد"
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "test-key" }, mockFetch);
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.score, 10);
  assert.equal(json.status, "حفظ متقن");
});

test("POST /api/gemini/recitation-audio clamps score to 0 if AI hallucinates 9/10 on non-Quranic transcript", async () => {
  const mockFetch = async () => {
    return new Response(JSON.stringify({
      candidates: [
        {
          content: {
            parts: [{
              text: JSON.stringify({
                isTargetSurahRecitation: true, // even if model misclassifies
                heardTranscript: "ألو تجربة واحد اثنين ثلاثة السلام عليكم",
                score: 9.0, // inflated score that used to leak through!
                status: "ممتاز",
                mistakes: [],
                feedback: "تلاوة جيدة"
              })
            }]
          }
        }
      ]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  const req = new Request("https://api.immanyat.example/api/gemini/recitation-audio", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ الرَّحْمَٰنِ الرَّحِيمِ مَالِكِ يَوْمِ الدِّينِ",
      mimeType: "audio/mp4",
      audioBase64: "A".repeat(25000)
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "test-key" }, mockFetch);
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.score, 0);
  assert.match(json.status, /لا يطابق/);
  assert.match(json.feedback, /ألو تجربة/);
});

test("POST /api/gemini/recitation-audio rejects unsupported mime type and silent short clip", async () => {
  const badMimeReq = new Request("https://api.immanyat.example/api/gemini/recitation-audio", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      mimeType: "application/exe",
      audioBase64: "A".repeat(25000)
    })
  });
  const badMimeRes = await handleRequest(badMimeReq, { GEMINI_API_KEY: "test-key" });
  assert.equal(badMimeRes.status, 400);

  const shortAudioReq = new Request("https://api.immanyat.example/api/gemini/recitation-audio", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      mimeType: "audio/mp4",
      audioBase64: "shortclip"
    })
  });
  const shortRes = await handleRequest(shortAudioReq, { GEMINI_API_KEY: "test-key" });
  assert.equal(shortRes.status, 200);
  const shortJson = await shortRes.json();
  assert.equal(shortJson.score, 0);
  assert.match(shortJson.status, /صامت|قصير/);
});

test("POST /api/gemini/recitation-audio proxies valid recitation statelessly without leaking API key", async () => {
  const secretKey = "SUPER_SECRET_KEY_NEVER_LEAK";
  let capturedUrl = "";
  const mockFetch = async (url) => {
    capturedUrl = String(url);
    return new Response(JSON.stringify({
      candidates: [
        {
          content: {
            parts: [{
              text: JSON.stringify({
                isTargetSurahRecitation: true,
                heardTranscript: "بسم الله الرحمن الرحيم الحمد لله رب العالمين الرحمن الرحيم مالك يوم الدين",
                score: 9.5,
                status: "ممتاز",
                mistakes: [],
                feedback: "تلاوة متقنة ومخارج سليمة."
              })
            }]
          }
        }
      ]
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  const req = new Request("https://api.immanyat.example/api/gemini/recitation-audio", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الفاتحة",
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ الرَّحْمَٰنِ الرَّحِيمِ مَالِكِ يَوْمِ الدِّينِ",
      mimeType: "audio/mp4",
      audioBase64: "A".repeat(25000)
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: secretKey }, mockFetch);
  assert.equal(res.status, 200);
  assert.ok(capturedUrl.includes("generativelanguage.googleapis.com"));
  assert.ok(capturedUrl.includes("/models/gemini-4-hydrogen:generateContent"), `Expected gemini-4-hydrogen endpoint, got: ${capturedUrl}`);
  const bodyText = await res.text();
  assert.ok(!bodyText.includes(secretKey));
  const json = JSON.parse(bodyText);
  assert.equal(json.score, 9.5);
  assert.equal(json.status, "ممتاز");
  assert.ok(json.matchedRatio >= 0.9);
});

test("analyzeArabicRecitation localizes segments accurately in long Surahs and detects Basmalah-only", () => {
  const longSurah = Array.from({ length: 60 }, (_, i) => `آية رقم ${i}`).join(" ") +
    " ذلك الكتاب لا ريب فيه هدى للمتقين الذين يؤمنون بالغيب ويقيمون الصلاة";
  const segRes = analyzeArabicRecitation(longSurah, "ذلك الكتاب لا ريب فيه هدى للمتقين", "البقرة");
  assert.equal(segRes.isLikelyTargetSurah, true);
  assert.ok(segRes.maxObjectiveScore >= 8.0);

  const basmalahOnly = analyzeArabicRecitation(longSurah, "بسم الله الرحمن الرحيم", "البقرة");
  assert.equal(basmalahOnly.isLikelyTargetSurah, false);
  assert.equal(basmalahOnly.onlyBasmalahOrIstiadha, true);
  assert.equal(basmalahOnly.maxObjectiveScore, 0.5);
});

test("APP_CLIENT_TOKEN blocks unauthorized callers when configured", async () => {
  const req = new Request("https://api.immanyat.example/api/gemini/recitation-text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ surahName: "الإخلاص", correctText: "قل هو الله أحد", recitationText: "قل هو الله أحد" })
  });
  const res = await handleRequest(req, { APP_CLIENT_TOKEN: "secret-client-token", GEMINI_API_KEY: "test" });
  assert.equal(res.status, 401);
});

test("Removed PayPal and advice endpoints return 404", async () => {
  const paypalReq = new Request("https://api.immanyat.example/api/paypal/create-order", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ amountUsd: 5 })
  });
  const paypalRes = await handleRequest(paypalReq, {});
  assert.equal(paypalRes.status, 404);

  const adviceReq = new Request("https://api.immanyat.example/api/gemini/advice", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ timeOfDay: "صباح" })
  });
  const adviceRes = await handleRequest(adviceReq, {});
  assert.equal(adviceRes.status, 404);
});

test("callGeminiWithFallback auto-discovers active model from /v1beta/models when static models return 404", async () => {
  const calledUrls = [];
  const mockFetch = async (url) => {
    const urlStr = String(url);
    calledUrls.push(urlStr);

    if (urlStr.includes("/v1beta/models?key=")) {
      return new Response(JSON.stringify({
        models: [
          {
            name: "models/gemini-embedding-001",
            supportedGenerationMethods: ["embedContent"]
          },
          {
            name: "models/gemini-4-hydrogen-preview",
            supportedGenerationMethods: ["generateContent"]
          }
        ]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }

    if (urlStr.includes("/models/gemini-4-hydrogen-preview:generateContent")) {
      return new Response(JSON.stringify({
        candidates: [
          {
            content: {
              parts: [{
                text: JSON.stringify({
                  isTargetSurahRecitation: true,
                  heardTranscript: "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد",
                  score: 10,
                  status: "حفظ متقن",
                  mistakes: [],
                  feedback: "ممتاز"
                })
              }]
            }
          }
        ]
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }

    return new Response(JSON.stringify({
      error: { code: 404, message: "This model is no longer available to new users." }
    }), { status: 404, headers: { "Content-Type": "application/json" } });
  };

  const req = new Request("https://api.immanyat.example/api/gemini/recitation-text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      surahName: "الإخلاص",
      correctText: "قُلْ هُوَ اللَّهُ أَحَدٌ اللَّهُ الصَّمَدُ لَمْ يَلِدْ وَلَمْ يُولَدْ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ",
      recitationText: "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد"
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "new-user-key" }, mockFetch);
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.score, 10);
  assert.ok(calledUrls.some((u) => u.includes("/v1beta/models?key=")));
  assert.ok(calledUrls.some((u) => u.includes("/models/gemini-4-hydrogen-preview:generateContent")));
});
