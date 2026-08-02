---
name: make-android-widget
description: Bootstrap and develop clean Kotlin Android home-screen widgets from reproducible activity-free or launcher-activity starters, using minSdk 34 (Android 14), a strict local and CI Kotlin quality gate, one manually dispatched GitHub Actions release build, and the bundled check-github-stuff and make-remote scripts. Use when Codex must initialize or modify an Android widget repository, establish or verify its GitHub origin, create the first working widget and remotely compiled APK, or continue widget development without assembling Android APKs locally.
---

# Make Android Widget

Build Android widgets from fixed resources and use GitHub Actions as the only APK build environment. Keep the local checkout as the source of truth and run the same strict Gradle quality gate locally and in CI.

## Prerequisites

Require these local commands before running the bundled scripts:

- `git`, `gh`, and an authenticated GitHub CLI session;
- `shellcheck` for static analysis of shell scripts;
- a JDK 17 available through `JAVA_HOME`, `java` on `PATH`, or a standard user/system location such as `~/.local/share/jdks/temurin-17`;
- an Android SDK containing platform 36, Build Tools 36.0.0, and command-line tools with `apkanalyzer`, discoverable through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, an ignored `local.properties` file, or a standard user location such as `~/.local/share/android-sdk`;
- `bash`, `find`, `sed`, `sort`, `sha256sum`, `mktemp`, and the other commands checked by the scripts themselves. `adb` is optional for the final device handoff; when it is unavailable or no authorized device is connected, `make-remote` skips that handoff.

Run `shellcheck scripts/check-github-stuff scripts/check-local scripts/make-remote scripts/verify-apk gradlew` and fix every diagnostic before reporting the local script checks complete. ShellCheck validates shell-script structure; it does not replace the remote Android build or device validation.

Keep local and remote orchestration separate. `scripts/check-local` is the complete local validation entry point: it runs every applicable local check, including shell syntax, ShellCheck, the strict Gradle `qualityCheck`, Android XML parsing, workflow YAML/contract assertions, and project/script invariants. It must capture all stdout and stderr in the ignored repository-local `check-local.log`, continue through the local checks so the log contains every failure, record the tested Git worktree state, and finish with an explicit pass/fail marker and a matching exit status. `scripts/make-remote` must not rerun local quality checks. Before staging anything, it must require a successful `check-local.log` whose recorded worktree-state digest matches the current worktree. Only then may it stage, commit, and push; after that it dispatches and monitors GitHub Actions, downloads and verifies the artifact, atomically updates `build/`, and optionally performs the ADB device handoff. Keep `check-local.log` ignored and never commit it.

When code is created from `AGENTS.md`, or is subsequently modified in response to `AGENTS.md`, ask the user whether to launch `scripts/check-local` before launching it. Do not infer consent from the request to implement the `AGENTS.md` instructions. If the user declines, leave the local changes in place, do not launch `scripts/check-local` or `scripts/make-remote`, and report that validation was not run.

Treat the repository's current `AGENTS.md` as authoritative when it replaces older
instructions. Re-read it before changing the widget, scripts, manifests, resources, or
validation protocol, and update the repository documentation whenever a behavior guarantee
changes.

Do not require standalone Detekt or KtLint executables. Gradle resolves the versions pinned by the project so local and CI checks use the same binaries and configuration.

## Choose the scripting language deliberately

Keep the bundled scripts in Bash while they remain short orchestration layers around `git`, `gh`, filesystem operations, and process status. Continue to require `set -Eeuo pipefail`, ShellCheck, and syntax checks.

Propose a migration to Python when a script accumulates substantial data parsing, nested validation, retries, state management, API logic, or becomes difficult to unit-test. As a practical signal, consider migration when a script exceeds roughly 150 lines, needs several associative arrays or complex pipelines, or repeatedly requires ShellCheck suppressions. Migrate the whole responsibility behind the same CLI and preserve the existing exit codes and artifact/commit contract before removing the Bash implementation.

Prefer Go only when the tool must be distributed as a self-contained, fast, cross-platform executable or needs stronger concurrency/performance guarantees. Do not introduce Go merely to replace a small local Bash or Python helper.

For this skill, do not migrate `check-github-stuff` or `make-remote` pre-emptively: Bash is currently appropriate. Reassess after adding non-trivial GitHub API behavior, complex artifact/state handling, or reliable unit-test requirements. If migration becomes useful, implement and test the replacement first, retain a short compatibility wrapper if callers depend on the old path, and update this skill's validation commands and prerequisites together.

## Respect the permission boundary

Require the user or Codex configuration to enable the equivalent of `Approve for me` for ordinary operations in the current repository. Never attempt to select or grant a permission mode from the skill. Limit filesystem work to the current repository, skill resources, and temporary download paths. Limit network work to the associated GitHub repository and GitHub Actions through `gh`.

Treat the one setup confirmation below as user intent, not as a sandbox permission grant. After acceptance, do not ask for separate confirmation for routine edits, commits, pushes, or `gh` operations already allowed by the configured permission profile. Stop and report any permission boundary instead of bypassing it.

## Run the mandatory first bootstrap

Before modifying a new widget repository, ask for:

- a lowercase reverse-DNS application ID such as `com.example.monwidget`;
- a non-empty user-facing application/widget name such as `Mon Widget`;
- the activity-free starter (`Widget sans`, no launcher activity or launcher icon) or the activity starter (`Widget avec`, adaptive launcher icon, activity text `Widget activity`);
- only when strict ABI restriction matters, whether the user accepts the clean pure-Kotlin starter being ABI-neutral or explicitly requires native arm64-only packaging. Explain that an APK with no native libraries cannot truthfully be restricted to arm64 merely by setting `abiFilters`.

Validate the application ID before continuing. Require at least two dot-separated lowercase segments, each starting with a letter and containing only lowercase letters, digits, or underscores. Use the same value for `namespace` and `applicationId` unless the user explicitly requests otherwise.

Inspect existing files read-only when necessary to describe whether setup will create a new project or adapt an existing one. Then present the entire setup and ask for one confirmation. Include the selected names, starter, ABI policy, development signing, repository check, template installation, two commits, remote build, checksum verification, and APK download. For example:

> Puis-je lancer tout le setup initial pour `com.example.monwidget`, nommé `Mon Widget`, avec la variante `Widget sans` ? Le setup va vérifier ou établir le dépôt GitHub, installer le projet reproductible et les deux scripts, créer et pousser le commit `Batman, because he has no parent`, lancer et surveiller la compilation release distante, vérifier le checksum de l’APK dans `build/`, puis pousser le commit `Basic widget pushed and compiled successfully`. L’APK initial sera signé avec la clé Android de développement et ne constituera pas une signature de production.

If the user declines, stop without modifying the repository. After acceptance, run the following sequence without another skill-level confirmation. Questions emitted by `check-github-stuff` to choose, create, or relink the exact repository are the only expected operational exceptions.

1. Invoke the bundled [check-github-stuff](scripts/check-github-stuff) directly from the skill before copying anything into the current directory. This preserves the empty directory required when the script must clone a remote repository.
2. Stop if that script cannot establish a Git worktree rooted at the current directory with an accessible GitHub `origin` and authenticated `gh`.
3. If no Android project exists, copy [starter-project-base](assets/starter-project-base) as the common project. If a coherent Android project already exists, preserve its wrapper, version management, module layout, signing policy, and compatible dependencies; adapt it instead of overlaying the base blindly.
4. Copy exactly one variant's `src/main` into the application module: [starter-widget-without-activity](assets/starter-widget-without-activity) or [starter-widget-with-activity](assets/starter-widget-with-activity). Do not combine the manifests or resources.
5. Replace `com.example.widget` in Kotlin, Gradle, `scripts/make-remote`, and the workflow's `verify-apk` invocation, move Kotlin files into the matching package directory, set only `app_name` and `widget_description` from the user-facing name, and preserve the required starter strings `Widget sans`, `Widget avec`, and `Widget activity`.
6. Immediately after installing and substituting the first widget source, ask whether to launch `scripts/check-local`. When the source was created from `AGENTS.md`, or was subsequently modified in response to it, this question is mandatory. If accepted, run `scripts/check-local`; fix every KtLint, Detekt, and Android Lint finding in the source, resources, manifest, and Gradle Kotlin scripts. Never create a baseline, ignore failures, lower severities, or disable a rule globally merely to pass the bootstrap. Preserve the starter's reviewed `lint.xml` exceptions only while their exact external constraints still apply: pre-release API 37, AGP 9.2's required Gradle 9.4.1, and the deliberate latent arm64-only native ABI policy. A narrowly scoped source suppression is allowed only for a documented framework convention such as PascalCase Compose functions.
7. Copy the bundled scripts `check-github-stuff`, `check-local`, `make-remote`, and `verify-apk` into repository `scripts/`, preserve their filenames, and make them executable. Copy [github-actions-template.yml](references/github-actions-template.yml) to `.github/workflows/android-widget.yml`. Ensure `/build/`, module build directories, and `check-local.log` remain ignored. Verify that the workflow runs the same `qualityCheck` task before release assembly and invokes `verify-apk` before uploading the artifact.
8. Review all generated paths and substitutions. After `check-local` succeeds, invoke repository `scripts/make-remote --bootstrap` exactly once. Do not directly run `git commit`, `git push`, `gh workflow run`, run polling, or artifact download from the skill.
9. Require the script to create and push `Batman, because he has no parent`, run the strict quality gate and build that exact commit remotely, verify the downloaded checksum and APK metadata/signature, atomically replace local `build/`, and only then create and push `Basic widget pushed and compiled successfully`.
10. Treat a failed quality gate, workflow, checksum mismatch, missing APK, or missing final commit as a hard gate. Fix the cause and rerun the same script workflow before implementing other widget features.

If the remote Kotlin compile reports `Unresolved reference 'provideContent'`, verify that every copied `BasicWidget.kt` imports `androidx.glance.appwidget.provideContent`; both bundled starter variants must contain this import. Correct the starter or generated source, run the static checks again, and rerun `scripts/make-remote --bootstrap`. Once the first bootstrap commit exists, the script may create a corrective source commit before the required final success commit; do not bypass the script with direct commit, push, dispatch, polling, or artifact commands.

## Use the bundled resources as the source of truth

Keep these resources reusable and deterministic:

- `assets/starter-project-base`: common Gradle project, official wrapper, fixed tool/library versions, strict Detekt and KtLint configuration, minSdk 34 (Android 14), release shrinking, development signing, and latent arm64 ABI filter;
- `assets/starter-widget-without-activity`: widget receiver and UI with no activity or launcher entry;
- `assets/starter-widget-with-activity`: the same widget architecture plus a minimal activity and adaptive launcher icon;
- `scripts/check-github-stuff`: repository discovery, cloning, origin verification, and confirmed origin creation/relinking;
- `scripts/check-local`: the complete local validation and `check-local.log` capture path;
- `scripts/make-remote`: the sole commit, push, dispatch, polling, checksum, APK inspection, artifact-install, and optional ADB device-install path after a matching successful local check;
- `scripts/verify-apk`: deterministic release APK inspection using `aapt2`, `apkanalyzer`, and `apksigner`;
- `references/github-actions-template.yml`: manual release workflow with immutable action SHAs.

Do not rewrite these resources ad hoc in each target repository. Copy them and change only documented project-specific values.

## Synchronize reusable scripts and repository skill snapshots

Every reusable script modified in an Android widget repository must remain neutral and
widget-independent. Do not hard-code a current widget's package name, display name, target
date, artifact identity, or other product value into a reusable script. Infer a value from the
repository configuration where reliable, or expose it as a documented argument or environment
variable. Keep genuinely project-specific configuration in the project, not in the reusable
skill resource.

When a repository script changes, update the matching resource under this skill's `scripts/`
directory before reporting the work. Verify byte-for-byte equality and executable permissions
between the repository script and skill resource. If the changed script cannot be made neutral,
do not add that project-specific variant as a skill resource.

Whenever any file in this skill changes, including `SKILL.md`, scripts, starters, references,
or agent metadata, copy the complete skill directory exactly into the active widget repository
at `skill/make-android-widget/`. Preserve every file, directory, and executable permission; do
not ignore that snapshot in Git. It is a versioned record of the exact skill used to develop the
widget. Perform this synchronization before validation and before reporting the skill updated.
Then verify the complete copied tree against the source skill and verify its reusable scripts
against the repository scripts. Repository `scripts/check-local` must enforce the snapshot's
presence, Git visibility, and script equality on subsequent checks.

## Enforce the post-check repository contract

After `check-github-stuff` succeeds, require:

- the current directory to be the Git worktree root;
- `origin` to resolve to an accessible `github.com` repository;
- `gh auth status` to succeed;
- a named branch rather than detached HEAD;
- either an empty/new Android setup or a coherent Android application module that can be adapted safely.

Do not overwrite unrelated existing files. Stop with a precise conflict report if the selected starter cannot be integrated without a user decision that was not covered by the initial plan.

## Keep the Android implementation conventional

Use Kotlin, Android Gradle Plugin, the Gradle wrapper, Jetpack Glance, and AndroidX libraries only as needed. Keep `minSdk = 34` (Android 14). Prefer stable dependencies bundled in the base project; preserve compatible existing version catalogs instead of introducing a second version system.

For each starter:

- keep `GlanceAppWidget` stateless and load persistent state outside in-memory widget instances;
- import `androidx.glance.appwidget.provideContent` explicitly when calling `provideContent` from `provideGlance`;
- render localized text from Android string resources;
- fill the host bounds and use Glance composables only, not regular Compose UI composables;
- declare `initialLayout` and scalable `previewLayout` metadata;
- set `updatePeriodMillis="0"` until periodic updates are genuinely required;
- keep the receiver non-exported and expose only the launcher activity in the activity variant;
- use WorkManager only for justified deferred or periodic work, and DataStore only when state is required.

Use the modern widget sizing and feature APIs available on Android 14 directly. Add responsive/exact Glance layouts, dynamic color, generated Android 15 previews, reconfiguration, interactions, or state only when the product needs them. Do not add compatibility branches below API 34.

For the Memento Mori implementation, use a native `RemoteViews` `TextView` with the bundled
`@font/input_mono_regular` resource rather than Glance's generic font-family span. This makes
the exact Input Mono glyphs part of the APK and prevents the launcher from selecting a
proportional fallback. The real layout and static XML preview must use that same font resource,
right/end alignment, thin Unicode spacing before suffixes, and representative non-placeholder
values in the preview. Its current target and minimum widget size is 1x2; keep
`targetCellWidth="1"`, `targetCellHeight="2"`, and matching 55dp by 110dp minimum dimensions
in the provider metadata. The pure font-size calculation must use the current AppWidget option
dimensions and grow for larger widget sizes. Use the real `widget_content` layout as
`initialLayout`; never treat the static widget preview as the calculated widget output.

## Enforce one strict Kotlin quality gate

Keep `qualityCheck` as the single Gradle quality entry point used by `scripts/check-local` and GitHub Actions. It must depend on the pinned KtLint CLI check, the Android variant-aware `:app:detektRelease` task, and `:app:lintRelease`. Run `scripts/check-local` after every change to Kotlin source, resources, manifests, or Gradle Kotlin scripts and always before `make-remote`; when the change comes from `AGENTS.md`, ask the user before launching it. `ktlintFormat` may make mechanical formatting corrections, but rerun `scripts/check-local` afterward and review the log/diff. Keep Gradle warning mode set to `fail` so deprecations and build warnings are gates too.

Commit `gradle/verification-metadata.xml` and keep Gradle dependency verification in its default strict mode. Generate or update it only after reviewing the resolved dependency/plugin changes; never use `--dependency-verification lenient` or `off` to bypass a missing or changed checksum.

Pin KtLint 1.8.0 through the documented custom Gradle `JavaExec` integration so it analyzes all `*.kt` and `*.kts` files despite AGP 9 built-in Kotlin. Keep `.editorconfig` at the repository root with the official KtLint style, a 100-character line limit, experimental rules enabled, and unused-import detection enabled.

Pin the AGP 9-compatible Detekt plugin `dev.detekt` 2.0.0-alpha.5 until a tested stable Detekt 2 release replaces it. Apply it in the Android application module with type resolution, all rules enabled, default configuration retained, configuration validation enabled, configuration warnings treated as errors, and task failure starting at warning severity. Generate checkstyle, HTML, Markdown, and SARIF reports. Do not add a Detekt or KtLint baseline.

Use the maximum deterministic severity supported by the pinned tools in both environments. Fail on every KtLint finding, every Detekt warning or error, every actionable Android Lint warning or error, every invalid or deprecated Detekt configuration entry, every Gradle warning, and every nonzero tool exit. Never use `continue-on-error`, `ignoreFailures`, `--continue`, `|| true`, a baseline, a broad exclusion, or different local/CI settings to turn a failed quality result into success. Keep any Android Lint environment-policy exception in `app/lint.xml`, scoped to one issue and an exact path or message, with the external constraint explained; a changed diagnostic must fail until the exception is reviewed. If a strict rule is incompatible with a documented Android or Compose convention, require the narrowest declaration-level suppression and review it explicitly.

Prefer correcting source structure, documentation, constants, resources, imports, and formatting over suppressing findings. Any suppression must target one rule at the narrowest declaration, explain or embody a legitimate Android/Kotlin convention, and survive review. Never use file-wide or project-wide suppressions as routine bootstrap policy.

`detektRelease` may invoke `compileReleaseKotlin` to obtain type information. This local Kotlin compilation is an intentional part of the quality gate; it does not authorize local `assemble`, `bundle`, APK packaging, signing, or release artifact claims. The first APK remains built in GitHub Actions.

## Be exact about release, ABI, and signing

Build only `:app:assembleRelease` in CI and keep `isDebuggable = false`. Enable R8 and resource shrinking when compatible. Produce and upload exactly one APK, never a debug APK.

Keep `abiFilters += "arm64-v8a"` for future native dependencies. Do not claim that the pure-Kotlin starter APK is physically arm64-only: without `.so` files it is ABI-neutral. If the user requires strict arm64-only installation, first ask which justified native component should establish that restriction; never add a dummy native library solely to manufacture an ABI label.

Sign the bootstrap release with Android's development signing configuration so the first APK is installable. Describe it as a non-debuggable release signed for development, never as production-signed. Before distribution, ask for the user's production keystore/secret strategy and update CI without exposing secrets.

## Keep GitHub Actions manual and reproducible

Use `workflow_dispatch` as the only trigger. Keep explicit read-only token permissions, a job timeout, the strict `qualityCheck` task, immutable full-SHA action references, and fixed SDK/JDK setup. Require `qualityCheck` to include release Android Lint with warnings treated as errors, then run release assembly separately. Generate a checksum file beside the APK and upload both in one artifact named `android-widget-arm64-v8a`.

Do not add `push`, `pull_request`, or scheduled triggers. Let `make-remote` dispatch the workflow after pushing the intended commit.

## Delegate all remote builds to `make-remote`

Copy [make-remote](scripts/make-remote) unchanged except for its configuration variables. Require it to:

1. validate safe repository-relative paths, ignored build output, a GitHub origin, authentication, workflow, and named branch;
2. require an existing `check-local.log` whose final result is successful and whose worktree-state digest matches the current checkout; do not rerun local quality tasks;
3. stage source changes and avoid empty normal-mode commits, then resolve a usable Android SDK and JDK 17 after the push for APK inspection;
4. push the exact current commit;
5. snapshot existing workflow run IDs, dispatch a new manual run, and select only the newly created run for the pushed SHA;
6. poll every 10 seconds and print failed logs for unsuccessful conclusions;
7. download one named artifact into temporary space;
8. require one APK and one checksum file and verify them with `sha256sum -c`;
9. run `scripts/verify-apk` on the downloaded APK and require the expected package, `minSdk`, `targetSdk`, non-debuggable manifest, and valid APK signature;
10. replace `build/` on the same filesystem with rollback on interruption or failure;
11. avoid staging edits made concurrently while waiting for the bootstrap build;
12. after the remote artifact is installed into `build/`, detect authorized ADB devices; with exactly one device (or an `ADB_SERIAL` selection), uninstall the previous expected package and install the new APK;
13. verify on that device the expected package, `versionCode`, `versionName`, and the exact SHA-256 of the installed APK by pulling it back temporarily; skip when no ADB device is available and stop rather than guessing when several are connected;
14. print the APK path, verified digest, optional ADB installation result, and GitHub Actions run URL.

`scripts/check-local` may run only the local Gradle quality tasks documented above. Never run local `assemble`, `bundle`, packaging, or signing tasks. A successful workflow proves remote APK compilation, manifest/signature inspection, and artifact integrity. The optional ADB SHA-256 comparison proves that the exact artifact was installed, but it does not prove launcher rendering, widget placement, resizing, updates, or interaction; those remain explicit device tests.

## Add quality gates when widget responsibilities grow

Apply only the gates justified by the widget's actual behavior; do not add empty tests, coverage targets, or security tooling for a static starter merely to increase ceremony.

- When the widget gains state transformations, date/time logic, repositories, or non-trivial formatting, add JVM unit tests and make `testReleaseUnitTest` part of `qualityCheck`. Use Robolectric only when the test genuinely needs Android framework behavior, and use coroutine test utilities for coroutine code.
- When meaningful unit tests exist, add Kover with a reviewed minimum line/branch coverage threshold and make the threshold part of the same local/CI gate. Do not claim coverage from generated Glance code.
- When the widget adds network access, persistence, exported components, deep links, IPC, authentication, or sensitive data, add CodeQL or a focused Semgrep policy and require its CI result before release. Add runtime permission and manifest checks to `verify-apk` when those capabilities appear.
- When dependency count or transitive risk grows, keep Gradle dependency verification strict, add dependency locking, and add dependency-update review rather than silently accepting dynamic versions. Review generated verification metadata whenever dependencies or plugins change.
- When layout, resizing, periodic updates, configuration, or interactions become product requirements, add an emulator/device workflow with focused smoke tests. Keep the current static/remote gates in place; device validation supplements them.
- Before production distribution, replace development signing with a protected release keystore strategy and extend APK inspection to verify the expected certificate identity.

## Ask feature questions only after bootstrap

After the first APK gate succeeds, ask only questions that materially affect the requested feature. Typical unknowns are data source, refresh trigger/cadence, interaction behavior, target widget sizes, persistence, configuration UI, permissions, and production signing. Do not ask this entire list pre-emptively, and do not delay the basic starter build for unspecified future features.

## Validate before reporting completion

Run all required local checks before reporting completion:

- `scripts/check-local`, whose `check-local.log` records `bash -n`, ShellCheck, `qualityCheck`, XML/YAML parsing, project invariants, and every failure;
- XML parsing for every manifest/resource file;
- YAML parsing and inspection that `workflow_dispatch` is the only trigger;
- verification of executable bits, wrapper JAR presence, Gradle distribution checksum, package substitutions, `minSdk = 34`, release-only workflow task, and ignored `/build/`;
- verification that each selected starter's `BasicWidget.kt` contains the `provideContent` import matching its `provideGlance` call and passes the same quality gate;
- verification that `scripts/verify-apk` passes the downloaded release APK for package, SDK levels, non-debuggable manifest, and signature;
- verification that `gradle/verification-metadata.xml` exists and a normal Gradle quality run succeeds with dependency verification enabled;
- the skill creator's `quick_validate.py` on the skill directory;
- a final diff/file-list review for stale resources or duplicate variants.

Report remote compilation only after `make-remote` succeeds. Report runtime/device validation only when actually performed.
