# Play Console walkthrough: Chaps Tuner

Everything Play Console asks for, in order, with the answer for this app. Listing text and images live beside this file in `metadata/android/en-US/`.

## Before you start

- [ ] **Back up the upload key.** Copy `~/.keystores/tuner-upload.jks` and `~/.gradle/gradle.properties` (it holds the password) into your password manager. The bundle can't be updated without them, though Play support can reset the upload key.
- [x] **Contact email**: chris@chaps.dev (Porkbun forwarding to Gmail). It's also the address on the privacy page.
- [ ] **Privacy page is live**: https://chaps.dev/tuner/privacy loads.
- [ ] **Build the bundle:** `./gradlew app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`.

## Create the app

All apps → Create app:

| Field | Answer |
|---|---|
| App name | Chaps Tuner: Guitar & Ukulele |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free (this can't be changed to paid later) |
| Declarations | Accept both |

Developer name (Account settings): **chaps.dev**

## App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | `https://chaps.dev/tuner/privacy` |
| App access | All functionality is available without special access |
| Ads | No, my app does not contain ads |
| Content rating | See the next section |
| Target audience | **13–15, 16–17, 18 and over** (leave the under-13 groups unchecked, which keeps it out of the Families program) |
| News app | No |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health | No health features |
| Data safety | See below |

### Content rating (IARC questionnaire)

- Email: chris@chaps.dev
- Category: **All Other App Types**
- Answer **No** to every question: violence, sexuality, language, controlled substances, gambling, user interaction or sharing content, sharing location, digital purchases.
- Expected result: Everyone / PEGI 3 / USK 0.

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

That's the whole form. It's accurate because:
- **Microphone audio** is processed only on the device, in memory, and never leaves it. Play's definition of "collected" means transmitted off the device, so this isn't collected.
- **Settings** (tuning and A4) live only in local DataStore. Android Auto Backup is the user's own Google backup, not data your app collects.
- **No INTERNET permission** is declared, so the app can't send anything.

If Play pushes back on the mic, the fallback answer is: collected = No; processed ephemerally on-device.

## Store listing (Grow → Store presence → Main store listing)

| Field | Source |
|---|---|
| App name | `title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon (512×512) | `images/icon.png` |
| Feature graphic (1024×500) | `images/featureGraphic.png` |
| Phone screenshots (2–8) | `images/phoneScreenshots/` |
| Category | Music & Audio |
| Tags | Tuner, Music tools (pick the closest offered) |
| Contact email | chris@chaps.dev |
| Website | `https://chaps.dev/projects/chromatic-tuner` |

## Release path (personal account, so a closed test is required)

1. **Internal testing** (Test and release → Testing → Internal testing):
   - Create a release and upload the `.aab`. Accept **Play App Signing** when asked.
   - Release notes: `changelogs/2.txt`.
   - Add yourself as a tester, install it from the opt-in link, and check the tuner works.
   - Check the **pre-launch report** a few hours later.
2. **Closed testing**:
   - Create a track (for example "Friends") and promote the same release.
   - Add testers as an email list or a Google Group. **Aim for 15+** so that dropping below 12 doesn't reset the clock.
   - Send each tester the opt-in link. They must accept *and* install, and stay opted in.
   - Countries: all.
3. **During the 14 days**:
   - Ask testers to actually tune something and send a line of feedback.
   - Ship one small update: bump `versionCode` in `app/build.gradle.kts`, add `changelogs/<code>.txt`, and upload.
   - Keep notes. The production application asks about this.
4. **Day 15+, apply for production** (Dashboard → Apply for production). It asks:
   - How you recruited testers (friends, family, musicians you know).
   - How engaged they were and what feedback you got.
   - What you changed in response.
   - Who the app is for (guitar and ukulele players, beginners through gigging musicians) and why it's ready.
5. **Production**: create a release from the tested bundle. Countries: all. Optionally do a staged rollout (20% → 100%). Review usually takes a few days.

## Each later update

1. Bump `versionCode` (and `versionName` if it's user-visible).
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Run `./gradlew test app:bundleRelease`.
4. Upload to internal testing, then promote it.
