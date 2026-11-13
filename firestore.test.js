const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid, ts = new Date(Date.now() - 2000)) {
  return {
    uid,
    displayName: "عبد الله",
    email: `${uid}@example.com`,
    userTitle: "عابد مخلص",
    points: 250,
    level: 2,
    currentStreak: 3,
    highestStreak: 5,
    khatmaProgressPages: 142,
    completedChallenges: ["c1", "c2"],
    tasbihCounts: { "1": 33, "2": 99 },
    lastSyncTime: Date.now(),
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
});

test("Authenticated user: can create, read, and update their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const createdTs = new Date(Date.now() - 5000);
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, createdTs)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      points: 300,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ points: 999, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID)));
});

test("Shadow update & schema validation: rejects ghost fields, invalid khatma pages, and createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const createdTs = new Date(Date.now() - 5000);

  // Ghost field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile(ALICE_UID, createdTs),
      isAdmin: true,
    })
  );

  // Out-of-bounds khatmaProgressPages (> 604)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile(ALICE_UID, createdTs),
      khatmaProgressPages: 700,
    })
  );

  // Valid create
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, createdTs)));

  // CreatedAt tampering on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Query trust: unfiltered list on users fails; filtered query on own uid succeeds", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertSucceeds(aliceDb.collection("users").where("uid", "==", ALICE_UID).get());
});
