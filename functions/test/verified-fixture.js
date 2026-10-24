const { getAuth } = require("firebase-admin/auth");
// Trusted Emulator test setup only. App users must consume email/SMS actions.
async function verifyFixture(app, user, { phone = true } = {}) {
  const auth = getAuth(app);
  const record = await auth.getUser(user.uid);
  const update = { emailVerified: true };
  if (phone) update.phoneNumber = "+1" + String(BigInt("0x" + require("node:crypto").randomBytes(5).toString("hex")) % 9000000000n + 1000000000n);
  await auth.updateUser(user.uid, update);
  const response = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: record.email, password: "Test-only-123!", returnSecureToken: true })
  });
  const result = await response.json();
  if (!response.ok) throw new Error(JSON.stringify(result));
  user.token = result.idToken;
  return user;
}
module.exports = { verifyFixture };
