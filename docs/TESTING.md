# Verification matrix

`qualityCheck` runs KtLint, Detekt, Android Lint, JVM tests, native-graphics Robolectric tests,
Kover for the pure rules, and the isolated Python delivery tests. `assembleRelease` depends
on this gate and the manual GitHub workflow runs it before signing. No APK is assembled locally.

| Area | Automated regression coverage |
| --- | --- |
| Date arithmetic | Independent intervals, boundaries, leap days and month ends |
| Daily alarm | Before/after 01:00 and real spring/autumn UTC offset transitions |
| Typography | Measured fit, invalid inputs, equal Noto glyph advances, three painted lines, padding at multiple sizes and font scales |
| Host layout | Exact host sizes, portrait/landscape fallback, aspect-ratio-preserving image |
| Settings | Default date/colors, invalid and expired persisted dates, opaque writes, reload and appearance selection |
| Dialog lifecycle | Activity recreation while calendar or RGB picker is open, retained selection, working confirmation |
| Accessibility | Color-button contrast and contextual labels |
| Widget events | Creation, touch refresh, boot, time/time-zone changes, package replacement and alarm cancellation |
| Resources | Preview and icon colors in both system modes |
| Delivery | Each script mismatch/permission failure, all Bash files parsed, stale PASS invalidation, log capture, stable source digest after commit, wrong signer rejection |
| GitHub discovery | Matching origin is read-only; origin changes require confirmation; API errors never create/delete a repository |
| Signing backup | Real GnuPG round trip without a public keyring, exact archive contents, optional transcript, wrong passphrase, cancellation, concurrent changes, overwrite/symlink/path rejection, terminal-only prompts |
| Conversation export | Only visible messages, no tools/instructions/reasoning, no mixed projects, ignored private output, incomplete tail handling |

Tests use API 34 with Robolectric native graphics and an explicitly verified SDK JAR.
They exercise Android code without packaging/installing an APK. Pure coverage thresholds do
not claim coverage of Android UI classes or all possible launcher behavior.

Before distribution, also verify the actual release on Android 14+ and a recent Android version:
add a 2x2 widget, resize, rotate, switch system theme with dialogs open, use TalkBack, reboot,
change time zone, and check updates and saved settings. Test a real 01:00 delivery allowing for
Android's inexact alarm policy. CI success alone is not evidence that these device checks passed.

The delivery tests create isolated temporary repositories and fake inspection commands;
they never push, dispatch workflows, install applications or use real signing secrets.
Signing backup tests additionally require GnuPG and gpgconf, using an isolated temporary
keyring and public fixture passphrases. They never read the real `.signing/` directory.
