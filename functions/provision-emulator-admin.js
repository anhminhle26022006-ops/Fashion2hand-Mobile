// Local-only bootstrap. No credential or privileged role is shipped inside the APK.
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
if (process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099" || process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080")
  throw new Error("Only the local Auth and Firestore Emulator hosts are permitted.");
const [email, password] = process.argv.slice(2);
if (!email || !password || password.length < 8) throw new Error("Usage: node provision-emulator-admin.js EMAIL PASSWORD (at least 8 characters)");
initializeApp({ projectId: "fashion2hand" });
(async () => {
  let user;
  try { user = await getAuth().getUserByEmail(email); }
  catch (error) { if (error.code !== "auth/user-not-found") throw error; user = await getAuth().createUser({ email, password }); }
  await getAuth().updateUser(user.uid, { password, disabled: false });
  await getAuth().setCustomUserClaims(user.uid, { ...user.customClaims, admin: true });
  await getFirestore().doc(`users/${user.uid}`).set({ uid: user.uid, email, name: "Quản trị viên Emulator", role: "ADMIN", accountStatus: "ACTIVE", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  console.log(`Local Emulator Admin provisioned: ${email} (${user.uid})`);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
