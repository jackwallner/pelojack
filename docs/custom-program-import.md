# Custom programs — v1

On the tablet open **Programs → Import plan**, choose a JSON or ZIP document with Android's file picker, review every week's workouts and intensity previews, then **Import plan**. Cancel writes nothing. Success and validation/provider failures have separate messages. Import never joins a program, changes the active program/selected workout, starts a ride, or replaces an existing ID. Joining remains the separate **Start program** action.

The picker accepts any MIME type because document providers sometimes label JSON/ZIP as `application/octet-stream`. Content, not the document's display name, determines JSON versus ZIP. Only the selected document is opened; no broad storage permission, ADB, persistent URI grant, server, or network download is needed.

## Program JSON

Strict UTF-8 JSON object, either selected directly or named `program.json` at the ZIP root. Program and workout JSON require double-quoted keys/strings and standard JSON values; comments, single quotes, unquoted keys, trailing commas/content and nonstandard numbers are rejected before the platform parser runs:

```json
{
  "formatVersion": 1,
  "id": "my-two-week-plan",
  "name": "My two-week plan",
  "description": "Optional description",
  "weeks": [["endurance-20"], ["my-new-workout"]]
}
```

- `formatVersion`: integer `1`, required. Built-in assets retain their existing format; this version applies to custom imports.
- `id`: unique lowercase ASCII `[a-z0-9][a-z0-9_-]{0,79}`. No spaces, dots, slashes or normalization.
- `name`: nonblank string, up to 200 characters.
- `description`: optional string, up to 10,000 characters.
- `weeks`: ordered 1–52 nonempty arrays, each with 1–14 string workout IDs. IDs use the same safe syntax. Every reference must resolve to an existing workout or a new workout in the bundle. Duplicate references anywhere in a plan are rejected in v1; reuse requires a separately named workout. Progress remains the existing week/ride ordering.
- Unknown program fields are ignored; no scheduling, rest-day syntax, replacement directives, URLs or download instructions are executed.

Direct JSON can only reference workouts already in the library. It cannot bring in additional workout definitions.

## ZIP bundle

```text
program.json
workouts/my-new-workout.json
workouts/example-intervals.zwo
```

The optional `workouts/` directory entry is accepted; no other directories or entries are allowed. Workout filenames are lowercase ASCII `[a-z0-9][a-z0-9_-]{0,74}.json` or `.zwo`. Filenames, ZIP entries, new workout IDs, and program references must be unique. Every bundled workout must be referenced by the plan (no hidden extra imports).

### JSON workout

Uses the existing Pelojack timed-segment format; its `id`, not its filename, is referenced by `weeks`:

```json
{
  "id": "my-new-workout",
  "name": "10 min easy spin — DEMO",
  "description": "Self-authored demo, not a coached prescription",
  "category": "custom",
  "segments": [{"name": "Easy", "seconds": 600, "ftp": 0.5}]
}
```

Require nonblank workout and segment names (up to 200 chars), 1–4096 segments, positive integer seconds, at most six hours total. Optional targets: integer power `zone` 1–7; finite `ftp` share 0–3; integer `resistance` 0–100 or ordered two-integer range; integer `cadence` 0–250 or ordered range. Existing `cue`/`test` fields are supported. Video imports are explicitly unsupported in v1. Category uses the existing reader's category mapping (unknown maps to Custom). Invalid targets are rejected rather than silently clamped/dropped.

### ZWO workout

A file `workouts/example-intervals.zwo` becomes workout ID **`zwo-example-intervals`**. Reference that exact ID. UTF-8 XML with one `<workout_file>` / `<workout>`. Supported direct steps: `SteadyState`, `Warmup`, `Cooldown`, `Ramp`, `FreeRide`, `MaxEffort`, `IntervalsT`. Unsupported steps cause the entire import to fail, even if other steps are valid. All required durations are positive whole seconds; power targets finite 0–3; `Repeat` 1–100; cadence 0–245. Zero means no cadence target; positive cadence uses the existing ±5 rpm band, with the generated lower edge clipped to zero (for example, 1 becomes 0–6 rpm). Normalized targets are validated before preview and use the same JSON reader at confirmation/reload. Expanded output is limited to 4096 segments and six hours. Ramps retain the existing one-minute approximation. DTDs, entity declarations and NUL/alternative-encoding tricks are forbidden. Coaching metadata is not a substitute for a licensed video; video is not downloaded.

## Safety, persistence and reload

- Add-only: built-in program IDs, loaded local workout IDs, existing imported IDs and the currently active program ID are reserved. Collisions fail, including changes between preview and confirmation. There is no replacement approval path in v1; use a genuinely new ID. No overwrites happen automatically.
- Bounded input: JSON container nesting ≤32, counting the root object and including ignored fields; JSON/each ZIP entry and each normalized workout ≤1 MiB; ZIP file ≤4 MiB; aggregate expanded ZIP data ≤8 MiB; 1–128 ZIP entries; per-entry compression ratio ≤200:1. ZIP64, split/encrypted/unsupported-method archives, unsafe names, duplicate local/central records, mismatched records, symbolic links and special Unix file types are rejected. Archives are never extracted using their paths.
- Validate everything in memory first. Normalize all workouts to JSON in one hidden `.pending-<uuid>` directory under the tablet's app-specific external `programs/` directory; publish the complete plan and its workouts with a same-filesystem rename. A failed publish removes staging; a crash before publish leaves an invisible pending directory. It does not leave a partially visible plan or add workouts to the separate `workouts/` directory.
- Committed structure: `programs/<plan-id>/program.json` and `programs/<plan-id>/workouts/<workout-id>.json`. Standalone `programs/*.json` is also loaded for existing-workout-only plans. Linked files/directories and malformed/missing-reference plans are skipped on reload. Duplicate external program IDs are suppressed; built-ins always win over external programs. Existing loose-workout precedence is unchanged.
- Plan-owned or program-referenced workouts cannot be deleted from class details, including loose edited overrides of bundled workouts. Editing remains available; deleting an override is not offered as a silent revert. Deletion rechecks ownership and only removes bookmarks/stack membership after confirmed removal. Plan deletion/reference editing is outside v1.
- Programs and workouts publish StateFlows. The Programs list observes both, reloads on resume, and includes **Reload** for manual external-file edits. Long plan details scroll horizontally with fixed-width week cards; the list and preview scroll vertically.

## Original example

Choose [`examples/custom-plans/example-two-week-plan.zip`](examples/custom-plans/example-two-week-plan.zip). It is a self-contained two-week import fixture, not a training recommendation. Its editable sources are in [`examples/custom-plans/example-two-week-plan/`](examples/custom-plans/example-two-week-plan/):

1. **Example intervals**, a 10-minute ZWO workout, imported as `zwo-example-intervals`.
2. **Example easy spin**, a 7-minute JSON workout, ID `example-easy-spin`.

All program text, timings and targets in this example were authored for this contribution and are distributed under the repository's MIT License. They are not copied from a third-party class or workout library. No video, coaching content, account data or external download is included.

The JVM importer test checks ZIP members against the editable sources, previews with no existing workout IDs, commits into temporary storage, and verifies full program/workout equality after reload. Keep the ZIP and sources in sync when changing the example. The archive contains only `program.json` and the two files under `workouts/`, with no enclosing directory or host metadata.

## Verification and limits

Run from `android/` with Java 17 / Android SDK configured:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Tests use temporary files and real ZIP/JSON/XML parsers. They cover preview-without-writes, JSON reference resolution, bundle normalization/reload, malformed/unsupported workouts, ID/reference collisions, unsafe ZIPs/size bombs/Unix symlinks, stale confirmation, loose-file reload and missing refs, symlink storage, and the documented example bundle. UI workflow logic runs on the JVM; source-wiring guards check Android picker/preview and scrolling integration. These checks do not validate Android file providers or rendered Compose layouts. Report actual automated results separately from any manual device checks; a successful build is not device verification.

For a manual check on an authorized test tablet, preview the example with both week cards visible, cancel and confirm that nothing was added, then import and reload. Confirm that the plan appears with both workouts but the active program and selected workout are unchanged. Re-importing the same bundle should fail without replacing anything. Record the Android version and file provider used, without publishing account details or private screenshots.

Limits: no plan edit/delete/export UI, media import, replacement approval, recurring workout references, scheduled calendar, ZIP64 or password-protected ZIP support. Reload is on resume/manual/import, not a continuous filesystem watcher. Existing progress semantics remain unchanged. Same-process import commits are serialized; app-specific storage is assumed not to be concurrently manipulated by another privileged process. Rename provides atomic visibility, not a guarantee against filesystem/device failure or sudden power loss. Pending crash directories are ignored rather than automatically deleted.
