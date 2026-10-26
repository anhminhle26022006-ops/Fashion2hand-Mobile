# Existing Cloud Admin password reset

Run from the repository root on branch MINH-ANH, using the Firebase CLI operator already logged in with authorized project IAM access. No GHN API token is needed.

```powershell
.\functions\scripts\reset-admin-password.ps1 -CheckOnly
.\functions\scripts\reset-admin-password.ps1
```

The second command prompts twice using SecureString. Type the NEW Firebase Auth password privately in your local terminal. Do not paste it into chat, command arguments, environment variables or files. Passwords are not trimmed; JSON and UTF-8 preserve special characters. The only write is Firebase Admin SDK `updateUser(existingUid, {password})` for `admin@fashion2hand.test` in project `fashion2hand`. UID, email, claims and Firestore data are not written. The script refuses emulator environments, missing accounts, disabled accounts, missing password providers and non-ADMIN profiles. It checks UID, claims and profile update time after the write. A postcheck failure does not roll back a successful password update; inspect the reported stage before retrying.

If Windows blocks local scripts, use a process-only policy override:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\functions\scripts\reset-admin-password.ps1
```

Then sign in normally with the same Admin email and your new password:

```powershell
.\functions\scripts\ghn-admin.ps1 -Action ghnAdminShops -Target Cloud
```

The reset helper reuses the selected npm-installed Firebase CLI account in memory and requires genuine Google IAM access. It does not grant IAM or Admin claims. If access is denied, use an authorized project operator; do not run emulator seed/provision scripts on Cloud. `seed-emulator.js` assigns a fixture password, and `provision-emulator-admin.js` takes a password argument; both also change claims/profiles and must not be used for this password-only Cloud operation.
