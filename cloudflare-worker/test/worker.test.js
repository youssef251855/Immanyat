import test from "node:test";
import assert from "node:assert/strict";
import { handleRequest } from "../src/index.js";

test("GET /health returns recitation-only stateless policy status", async () => {
  const req = new Request("https://api.immanyat.example/health", { method: "GET" });
  const res = await handleRequest(req, {});
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.ok, true);
  assert.equal(json.purpose, "quran-recitation-correction-only");
  assert.equal(json.storagePolicy, "stateless-no-audio-retention");
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

test("POST /api/gemini/recitation-text evaluates written Quran memorization cleanly", async () => {
  const mockFetch = async () => {
    return new Response(JSON.stringify({
      candidates: [
        {
          content: {
            parts: [{ text: '{"score": 10, "status": "حفظ متقن", "feedback": "أحسنت، التلاوة مطابقة تماماً."}' }]
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
      correctText: "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد",
      recitationText: "قل هو الله أحد الله الصمد لم يلد ولم يولد ولم يكن له كفوا أحد"
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: "test-key" }, mockFetch);
  assert.equal(res.status, 200);
  const json = await res.json();
  assert.equal(json.score, 10);
  assert.equal(json.status, "حفظ متقن");
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

test("POST /api/gemini/recitation-audio proxies statelessly without leaking API key", async () => {
  const secretKey = "SUPER_SECRET_KEY_NEVER_LEAK";
  let capturedUrl = "";
  const mockFetch = async (url) => {
    capturedUrl = String(url);
    return new Response(JSON.stringify({
      candidates: [
        {
          content: {
            parts: [{ text: '{"score": 9.5, "status": "ممتاز", "feedback": "تلاوة متقنة ومخارج سليمة."}' }]
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
      correctText: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      mimeType: "audio/mp4",
      audioBase64: "A".repeat(25000)
    })
  });

  const res = await handleRequest(req, { GEMINI_API_KEY: secretKey }, mockFetch);
  assert.equal(res.status, 200);
  assert.ok(capturedUrl.includes("generativelanguage.googleapis.com"));
  const bodyText = await res.text();
  assert.ok(!bodyText.includes(secretKey));
  const json = JSON.parse(bodyText);
  assert.equal(json.score, 9.5);
  assert.equal(json.status, "ممتاز");
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
