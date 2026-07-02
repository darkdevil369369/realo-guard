# REALO Guard — Google Play Launch Kit
_Prepared 2 Jul 2026. Everything below is copy-paste ready._

---

## ⚡ THE PLAN (important reality check)
Google's rule for **new personal developer accounts**: before a public (production) release, the app must run a **closed test with 12+ testers opted-in for 14 continuous days**. Organization accounts are exempt, but need a D-U-N-S number (takes weeks).

So the fastest honest path:
- **Day 1 (tomorrow):** create account, pay $25, upload AAB to **Closed testing**, invite 12+ testers.
- **Day 1–14:** testers keep the app installed; we polish + collect feedback.
- **Day ~15:** apply for Production access → public launch a few days later.

---

## 👤 AMIT'S STEPS (only these need you)

### Step 1 — Developer account (do TODAY, verification takes time)
1. Go to https://play.google.com/console/signup
2. Sign in with the Google account you want to own REALO forever
   (recommend: **tryrealo.official@gmail.com** — keeps brand assets together).
3. Choose **"Yourself"** (personal account) → pay **$25** (one-time).
4. Complete **identity verification** (ID/passport photo). Can take a few hours to 2 days — this is why we start today.

### Step 2 — Create the app (after verification)
Play Console → **Create app**:
- App name: `REALO Guard — AI Scam Protection`
- Default language: English (US) · **App** · **Free**
- Declarations: not a news app, no ads.

### Step 3 — Store listing (copy-paste from sections below)
Upload from this folder: `icon_512.png`, `feature_1024x500.png`, plus **4–8 phone screenshots** — take these on your phone (Guard home, Tools scan result, deepfake result, a scam alert notification, Payment Sentry nudge).

### Step 4 — Upload the app
- Release → **Testing → Closed testing** → Create track "REALO Beta" → Create release.
- Upload `app-release.aab` (I build it — grab the `realo-guard-aab` artifact from the latest GitHub Actions run, or ask me and I'll hand you the file).
- When asked about signing: accept **Play App Signing** (Google manages the release key; our keystore becomes the upload key). ✅ Say yes.
- Release notes: `First Play release — automatic scam protection, screenshot & photo checks, Pay-Safe, Family Guardian.`

### Step 5 — Testers (the 12+)
- Closed testing → Testers tab → create an email list `realo-testers`.
- Add 12–20 Gmail addresses (family, friends, nephew, his subscribers).
- Share the opt-in link Play gives you; each tester taps it + installs from Play.
- They must stay opted-in 14 days (uninstalling is OK per policy, but keeping it installed is safer + gives real feedback).

### Step 6 — After 14 days
Dashboard → **Apply for production** → answer honestly (testing learnings) → submit → public in a few days.

---

## 📝 STORE LISTING (copy-paste)

**App name (30 chars max):**
`REALO Guard: AI Scam Shield`

**Short description (80 chars max):**
`Automatic scam protection. Paste anything suspicious — know if it's real.`

**Full description:**
```
Scams got smarter. Your protection should too.

REALO Guard is an AI shield against the scams flooding your phone — fake bank alerts, phishing links, deepfake photos, payment fraud, romance scams and more.

🛡️ AUTOMATIC PROTECTION (the magic)
Turn it on once. REALO quietly watches notifications from the chat apps YOU choose (WhatsApp, Telegram, Instagram and more) and raises an instant alarm the moment a scam message lands — before you tap anything.

🔍 CHECK ANYTHING IN ONE TAP
• Message — paste any text, email or DM → scam or real, with reasons
• Screenshot — upload a chat/SMS pic, REALO reads it (even QR codes)
• Photo — is this picture a real photo or AI-generated?
• Payment — check a UPI ID, account or link BEFORE you send money

💳 PAYMENT SENTRY
Money is never fully silent: even when a payment request looks fine, REALO reminds you to verify the receiver's name first.

🌐 POWERED BY REAL THREAT DATA
Every check runs against 400,000+ confirmed scam domains (the same blocklists that protect crypto wallets), Google Safe Browsing, and REALO's own Crowd Shield — scammers reported by other users are caught automatically, even when they change their words.

👨‍👩‍👧 FAMILY GUARDIAN
Link with your parents or loved ones. If a scam reaches them, you get alerted instantly — from anywhere.

🔒 PRIVATE BY DESIGN
Scan, don't store. Your messages and photos are analyzed in real time and never stored as readable content. No ads. No trackers. No data selling.

Free. Because everyone deserves protection.

When in doubt, REALO it.
```

**Category:** Tools (or Communication) · **Tags:** security, anti-scam
**Website:** `https://api.tryrealo.com` · **Email:** `tryrealo.official@gmail.com`
**Privacy policy URL:** `https://api.tryrealo.com/privacy`

---

## 🔐 DATA SAFETY FORM (answers)

- **Does your app collect or share any of the required user data types?** → Yes
- **Is all of the user data collected by your app encrypted in transit?** → Yes
- **Do you provide a way for users to request that their data is deleted?** → Yes → URL: `https://api.tryrealo.com/delete-account`

Data types:
1. **Personal info → Email address**: Collected · Not shared · Optional (account) · Purposes: Account management. Ephemeral: No.
2. **Messages → Other in-app messages** (notification text / pasted content): Collected · Not shared · **Ephemeral: Yes** (processed transiently) · Purpose: App functionality. Required for core feature (if user enables protection).
3. **Photos** (only when user uploads for deepfake check): Collected · Not shared · Ephemeral: Yes · Purpose: App functionality · Optional.
- No location, no contacts, no financial info collected, no advertising ID.

**Notification access declaration (if asked / policy text):**
`REALO Guard's core purpose is automatic scam protection. With the user's explicit opt-in, it reads the text of incoming notifications only from apps the user selects, sends the text securely to REALO's AI engine to detect scams, and warns the user instantly. Content is processed transiently and never stored as readable content, never used for ads, never shared. Users can revoke access anytime in Android settings. Full disclosure shown in-app at signup and on the main screen; privacy policy: https://api.tryrealo.com/privacy`

---

## 🎯 CONTENT RATING questionnaire
Category: **Utility/Tools**. Violence: No. Sexual: No. Profanity: No. Drugs: No. Gambling: No. User-generated content: **No** (reviews live on the website, not in the app). Shares location: No. Personal info shared: No. → Result should be **Everyone / PEGI 3**.

**Target audience:** 18+ (safest for a security tool; avoids child-policy overhead).

---

## 📦 FILES IN THIS FOLDER
- `icon_512.png` — Play app icon (512×512)
- `feature_1024x500.png` — feature graphic
- Screenshots: take 4–8 on the phone (portrait), min 320px, 16:9–9:16.
- AAB: download from the latest release (v2.2 "Truth Engine") -> app-release.aab — this is the Play upload file.

## ✅ ALREADY DONE (by Claude, 2 Jul)
- targetSdk 35 + signed release **.aab** built in CI
- Privacy policy live: https://api.tryrealo.com/privacy
- Account deletion (self-serve) live: https://api.tryrealo.com/delete-account
- This kit + assets committed to the repo
