'use strict';
// Password arrives only through redirected stdin. Never log raw SDK errors.
const path = require('node:path');
const {initializeApp, refreshToken, deleteApp} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const projectId = 'fashion2hand';
const email = 'admin@fashion2hand.test';
let stage = 'configuration';
async function main() {
  if (Object.keys(process.env).some(k => k.endsWith('_EMULATOR_HOST') && process.env[k])) {
    throw new Error('EMULATOR_ENV_PRESENT');
  }
  const root = path.resolve(__dirname, '../..');
  if (require(path.join(root, 'app/google-services.json')).project_info.project_id !== projectId ||
      JSON.parse(require('node:fs').readFileSync(path.join(root, '.firebaserc'), 'utf8')).projects.default !== projectId) {
    throw new Error('PROJECT_MISMATCH');
  }
  // Reuse the selected, legitimately authenticated Firebase CLI operator.
  // This credential still requires Google IAM permission to update Auth users.
  const cli = process.env.FASHION2HAND_FIREBASE_CLI;
  if (!cli) throw new Error('FIREBASE_CLI_PATH_REQUIRED');
  stage = 'CLI credential';
  const account = require(path.join(cli, 'lib/auth')).getProjectDefaultAccount(root);
  const api = require(path.join(cli, 'lib/api'));
  if (!account?.tokens?.refresh_token) throw new Error('FIREBASE_LOGIN_REQUIRED');
  const operatorCredential = refreshToken({
    type: 'authorized_user', client_id: api.clientId(), client_secret: api.clientSecret(),
    refresh_token: account.tokens.refresh_token,
  });
  // Explicit Credential adapter lets Firestore use the same IAM-checked operator token.
  const app = initializeApp({projectId, credential: {
    getAccessToken: () => operatorCredential.getAccessToken(),
  }});
  try {
    const auth = getAuth(app);
    stage = 'Auth lookup';
    const before = await auth.getUserByEmail(email);
    console.log('Existing email/password account found in Cloud Auth.');
    stage = 'Firestore profile read';
    // Read-only REST lookup avoids Firestore SDK restrictions on CLI user credentials.
    async function readProfile() {
      const token = await operatorCredential.getAccessToken();
      const response = await fetch(`https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/users/${encodeURIComponent(before.uid)}`, {
        headers: {Authorization: `Bearer ${token.access_token}`}, signal: AbortSignal.timeout(20000),
      });
      if (!response.ok) throw new Error(`PROFILE_HTTP_${response.status}`);
      return response.json();
    }
    const profile = await readProfile();
    if (before.disabled || !before.providerData.some(p => p.providerId === 'password') ||
        profile.fields?.role?.stringValue !== 'ADMIN') throw new Error('ADMIN_ACCOUNT_CHECK_FAILED');
    console.log(`Project: ${projectId} | Target: Cloud | Existing Admin found: true`);
    console.log(`Admin claim: ${before.customClaims?.admin === true} | Firestore role: ADMIN`);
    if (process.argv.includes('--check')) return;
    if (process.stdin.isTTY) throw new Error('USE_POWERSHELL_WRAPPER');
    let input = '';
    for await (const chunk of process.stdin) input += chunk.toString('utf8');
    let payload;
    try { payload = JSON.parse(input); } catch { throw new Error('INVALID_INPUT'); }
    input = '';
    if (typeof payload.password !== 'string' || payload.password.length < 6) throw new Error('PASSWORD_TOO_SHORT');
    // The sole mutation: preserve UID, claims, email and Firestore document.
    stage = 'password update';
    await auth.updateUser(before.uid, {password: payload.password});
    payload.password = null;
    const after = await auth.getUser(before.uid);
    const profileAfter = await readProfile();
    if (after.uid !== before.uid || after.email !== before.email ||
        JSON.stringify(after.customClaims) !== JSON.stringify(before.customClaims) ||
        profile.updateTime !== profileAfter.updateTime) {
      throw new Error('PASSWORD_UPDATED_POSTCHECK_CHANGED');
    }
    console.log('Password updated. UID, Admin claims and Firestore profile preserved.');
  } finally { await deleteApp(app); }
}
main().catch(error => {
  const allowed = ['EMULATOR_ENV_PRESENT','PROJECT_MISMATCH','FIREBASE_CLI_PATH_REQUIRED',
    'FIREBASE_LOGIN_REQUIRED','ADMIN_ACCOUNT_CHECK_FAILED','USE_POWERSHELL_WRAPPER',
    'INVALID_INPUT','PASSWORD_TOO_SHORT','PASSWORD_UPDATED_POSTCHECK_CHANGED'];
  const code = allowed.includes(error.message) || /^PROFILE_HTTP_[0-9]{3}$/.test(error.message) ? error.message :
    (/^(auth|app)\/[a-z-]+$/.test(error.code || '') ? error.code :
      (Number.isInteger(error.code) ? `RPC_${error.code}` : 'ADMIN_SDK_ACCESS_FAILED'));
  console.error(`Admin password operation failed at ${stage}: ${code}. No account was created or deleted.`);
  process.exitCode = 1;
});
