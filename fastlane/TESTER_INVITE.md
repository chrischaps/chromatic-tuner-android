# Tester invite: Chaps Tuner closed test

Messages for recruiting the 12 or more testers that Play requires for 14 days before production access. See `PLAY_CONSOLE.md` for the release path.

## One-time setup

1. Create a Google Group at groups.google.com, for example "Chaps Tuner Testers", and set **Who can join** to **Anyone can join**.
2. In Play Console, open the closed track, go to **Testers**, and add the group's email address.
3. Copy the **opt-in link** from that page.
4. In the messages below, fill in `[GROUP LINK]`, `[OPT-IN LINK]` and `[END DATE]` (the start date + 14 days, plus a couple of days of buffer).

## The invite (works as a text or an email)

> Hi! I've made a small tuner app for Android, for guitar, bass, ukulele and more, and I'm about to put it on the Google Play Store. Before Google lets a new developer publish, 12 people have to test the app for 14 days, so I'm hoping you'd be one of them. It takes about two minutes to set up.
>
> **You'll need an Android phone.** Use the same Google account that's on your phone for all of these steps.
>
> 1. **Join the tester group:** [GROUP LINK]. Tap "Join group".
> 2. **Become a tester:** [OPT-IN LINK]. Tap "Become a tester".
> 3. On that same page, tap **"download it on Google Play"** and install **Chaps Tuner**.
> 4. Open it and let it use the microphone. It only listens while it's on screen, and nothing is recorded or saved.
>
> **Then, over the next two weeks:**
> - Open it a few times. If you play, tune your instrument with it. If you don't, hum a note at it. Google checks that testers actually use the app.
> - Please keep it installed and stay in the group until [END DATE]. If people drop out, the 14 days start over.
> - If anything feels confusing, broken or nice, tell me. Even one sentence helps, and Google asks me what feedback I got.
>
> Thank you, it really helps!
> Chris

## Halfway nudge (around day 7)

> Quick tester update: we're halfway there. Thank you! I just sent out an update, so Play may offer you a new version. If you have a moment, open the tuner once or twice this week and let me know how it's going. Seven days to go.

## Thank-you (after launch)

> We made it! Chaps Tuner is now live on Google Play: [PLAY LINK]. Thank you for testing. You can leave the tester group now if you want, and the app will keep working. If you liked it, a rating or review would mean a lot.

## Notes

- **Steps 1 and 2 must use the same Google account as the phone.** If someone says "it says I'm not a tester", they joined the group with a different account.
- **Save the feedback replies.** Quoting a few in the production application is the best evidence that real testing happened.
- **The halfway update can be small:** a copy tweak, or something a tester mentioned. Bump `versionCode`, add `metadata/android/en-US/changelogs/<versionCode>.txt`, build and upload. See "Each later update" in `PLAY_CONSOLE.md`.
- Testers need Android. iPhone users can't join.
