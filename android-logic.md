# Android Build Logic — LMSv5 (Master Spec)

> **Entry point** for building the Android client of the LMS exam platform with **100% functional parity** to the PHP web app.
> **Audience:** an AI (or engineer) in Android Studio implementing from scratch.
> **Companion docs:** [`android-api-contract.md`](android-api-contract.md) (all 89 v1 endpoints) · [`android-screens.md`](android-screens.md) (all 40 screens) · [`logic.md`](logic.md) (PHP source of truth, 20 sections) · [`lms-gap-spec.md`](lms-gap-spec.md) (verified gap rules G1–G14)

---

## Table of Contents

1. [Purpose & scope](#1-purpose--scope)
2. [Parity guarantee](#2-parity-guarantee)
3. [System architecture](#3-system-architecture)
4. [What changes vs the web (and why)](#4-what-changes-vs-the-web-and-why)
5. [Authentication model](#5-authentication-model)
6. [Client architecture (recommended)](#6-client-architecture-recommended)
7. [API summary](#7-api-summary)
8. [Design system parity (Fluent 2 → Material 3)](#8-design-system-parity-fluent-2--material-3)
9. [Business rules reference (with file:line)](#9-business-rules-reference-with-fileline)
10. [Data model reference](#10-data-model-reference)
11. [HTTP status & error parity](#11-http-status--error-parity)
12. [Offline strategy & autosave queue](#12-offline-strategy--autosave-queue)
13. [Anti-cheat lifecycle mapping](#13-anti-cheat-lifecycle-mapping)
14. [Analytics & Python charts](#14-analytics--python-charts)
15. [Parity matrix](#15-parity-matrix)
16. [Known open gaps & deferred items](#16-known-open-gaps--deferred-items)
17. [Source references & reading order](#17-source-references--reading-order)

---

## 1. Purpose & scope

### 1.1 What this document set delivers

Three docs that together are sufficient to build a feature-complete Android client:

| Doc | Contents |
|---|---|
| **`android-logic.md`** (this file) | architecture, auth, business rules with `file:line` sources, data model, parity matrix, gaps |
| **[`android-api-contract.md`](android-api-contract.md)** | the 89 `/api/v1/*` endpoints to add to the PHP server (auth, hierarchy, students, tests, submissions, analytics, notifications, faculty, geo), each with request/response shapes and parity notes |
| **[`android-screens.md`](android-screens.md)** | per-screen specs for all 40 PHP pages: role, navigation, APIs, UI states, edge cases |

### 1.2 Scope decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| UI framework | **Jetpack Compose + Kotlin** | modern, declarive, single-activity |
| Transport | **Android app ⇄ PHP server over a new bearer-token `/api/v1/*` layer** | additive to the PHP codebase; web sessions/CSRF untouched |
| Backend rewrite | **none** — the PHP app remains the single source of truth for all grading, ranking, and validation logic | parity is enforced by construction |
| Deliverable of *this* task | **documentation only** — no PHP or Kotlin code written | you (the Android AI) write the client; a follow-up task implements the PHP v1 layer per the contract doc |

### 1.3 What is deliberately out of scope (v1)

- **PCI (Payment Card Industry) card capture** — the web defers it (`G8`); see §16.
- **Push notifications** — the web uses in-page polling only.
- **Offline test-taking** — answers are never queued across app restarts; only in-session retry (§12).
- **Multi-window / split-screen on the test screen** — explicitly blocked (`resizeableActivity="false"`).
- **A native "Settings" or "Activity Logs" screen** — both are "Coming Soon" placeholders on the web.

---

## 2. Parity guarantee

**Definition of "100% functional parity":** every action a user can perform in the PHP web app, every validation message they can receive, every grade/rank/score they can see, and every state transition (including anti-cheat and timer behavior) is reproducible in the Android app — with the **same numbers**, the **same rules**, and the **same server-side enforcement**.

How this is enforced:

1. **No business logic in the client that the server does not also own.** The server validates deadlines, grading, ranking, ownership, and rate limits. The client computes only *display* values (countdown, derived badges) and always re-syncs from the server on every write.
2. **The PHP functions listed in Appendix A of the API contract are reused verbatim** inside the new `/v1` handlers (`api_auth_*`, `api_issue_tokens`, `student_find`, `submission_effective_deadline`, `submission_time_remaining`, `submission_evaluated`, `result_render_context`, `batch_semester_label`, `csv_import_parse_questions`, …). This means the client cannot drift from web behavior, because both paths call the same PHP functions.
3. **Every business rule below is pinned to a `file:line` source** so the implementing AI can verify rather than guess.
4. **The parity matrix (§15)** enumerates 40/40 screens, 20/20 `logic.md` sections, and 8/8 legacy endpoints, and the contract doc's Appendix A maps every v1 endpoint back to the PHP file that implements it.

### 2.1 Known, documented divergences (the only allowed ones)

| # | Divergence | Why it is allowed |
|---|---|---|
| D1 | Password reset uses a stateless `reset_grant` token instead of `$_SESSION` | `$_SESSION` does not exist for a bearer-token client; the grant is short-lived, single-use, and stored in `password_reset_tokens` exactly like the web's row |
| D2 | QR codes are generated **on-device** with ZXing instead of calling `api.qrserver.com` | removes a third-party dependency and a network hop; the QR payload (the guest URL) is identical |
| D3 | PCI capture is deferred to post-submit (G8) | the web already defers it; documented as an open gap for both platforms |
| D4 | The multi-step college wizard drafts are held client-side instead of in `$_SESSION` | no session on the client; submission is one atomic `POST /colleges/wizard` |
| D5 | Ranking uses the **corrected** rule (evaluated-only, `COALESCE` bug fixed, competition rank 1,1,3) | the web's current behavior is a **verified bug** (G10); v1 must fix it server-side, so both web and Android converge |
| D6 | Anti-cheat events are sourced from Android lifecycle callbacks instead of DOM events | different platform; the *rule* (count a switch, log with IP/UA, show 3-second banner, enforce `max_tab_switches`) is identical |
| D7 | `FLAG_SECURE`, `resizeableActivity="false"`, and screenshot-blocking exist only on Android | the web cannot do these; they are **strictly stronger** and do not change grading outcomes |

Anything not in this table must behave identically to the web.

---

## 3. System architecture

```
┌─────────────────────────────┐          ┌──────────────────────────────────┐
│  Android app (this spec)    │          │  PHP 8 server (existing codebase) │
│                             │ HTTPS    │                                  │
│  Compose UI  ←→  ViewModel  │ ───────► │  /api/v1/*   (NEW bearer layer)   │
│       ↑            ↑        │  JSON    │      │ reuses                    │
│  Keystore    Retrofit/OkHttp│          │      ▼                           │
│  (tokens)    (auth interceptor)        │  includes/auth.php, helpers.php, │
│                             │          │  session.php, api/* (8 legacy)   │
│  WorkManager (autosave      │          │      │                           │
│   retry only — §12)         │          │      ▼                           │
│                             │          │  MySQL (26 tables, schema.sql +   │
│  ZXing (QR), MPAndroidChart │          │  migrations)                     │
│   or Vico (charts)          │          │  python_svc (optional, charts)   │
└─────────────────────────────┘          └──────────────────────────────────┘
```

Key points:

- **Single source of truth remains PHP.** The Android app never grades, never ranks, never decides deadline expiry — it *asks*.
- **The 8 legacy `/api/*.php` endpoints are reimplemented under `/v1` with the same rules** and are listed in the contract doc (§ legacy reimplementation). The old endpoints stay for the web's AJAX; Android uses only `/v1`.
- **CORS is irrelevant**: native Android HTTP clients are not subject to CORS, and the PHP app sends no CORS headers today. No server change needed for that reason.
- **The optional Python service** (`python_svc`, charts/analytics) is probed via `python_api_available`; when down, both web and Android fall back to numeric tables (§14).

---

## 4. What changes vs the web (and why)

| Concern | Web today | Android (this spec) | Why |
|---|---|---|---|
| Session | PHP session cookie, regenerated every 1800 s (`session.php:8-21`) | Bearer access token (15 min) + refresh token (30 d) in **Android Keystore** | cookies + CSRF do not apply to a native client; Keystore prevents extraction |
| CSRF | `requireCsrf()` on every POST (`session.php:50-72`) | **not needed** — bearer tokens are not ambient credentials | the `/v1` layer skips CSRF by design |
| Test start | submission row created as a side effect of `GET student/test.php` (`test.php:245-257`) | explicit `POST /submissions` (start) before rendering the paper | a GET must not have side effects for a REST client; the server keeps the web path intact |
| Tab switch | `visibilitychange` / `blur` / `resize` DOM events | `onStop`/`onPause`/`onWindowFocusChanged` + screen-off; `resizeableActivity="false"` blocks split-screen | platform lifecycle replaces DOM lifecycle (D6) |
| Screenshot | not preventable | `FLAG_SECURE` on the test screen | strictly stronger (D7) |
| QR generation | server-side call to `api.qrserver.com` | on-device ZXing | removes third-party dependency (D2) |
| Wizard draft | `$_SESSION['college_draft']` | ViewModel state (SavedStateHandle) | no session (D4) |
| Autosave throttle | server returns 429 every 60 s per student (`submit_answer.php`) | identical server rule + client debounces 2 s, sends only dirty questions, max 3 retries | server unchanged; client is well-behaved |
| Ranking | bug: `COALESCE(total_marks_obtained, 0)` includes pending as 0 (`reports.php:41,55,641`) | **fixed** rule implemented in `/v1` (D5) | G10; fixes both platforms at once |

**Nothing else changes.** Deadlines, grace periods, grading, PCI rules, promotions, guest ownership, rate limits, error codes, and every validation string are identical.

---

## 5. Authentication model

### 5.1 Token lifecycle

```
login (email+password [+college_id for faculty])
  └─► POST /auth/login | /auth/faculty-login
        └─► { access_token (15 min, bearer), refresh_token (30 d, single-use rotation) }
              └─► stored in Android Keystore (EncryptedSharedPreferences / MasterKey)

every API call:
  Authorization: Bearer <access_token>
    ├─► 200 → proceed
    ├─► 401 token_expired → silent refresh via POST /auth/refresh
    │        ├─► success → retry original request once
    │        └─► failure → clear Keystore → AuthGraph
    └─► 401 token_invalid → same as refresh failure

logout / password reset → POST /auth/logout → server revokes ALL the user's tokens
```

### 5.2 Roles & gating

| Role | Resolved from | Graph |
|---|---|---|
| `student` | `auth.role` on `/auth/session` | StudentGraph |
| `faculty` | same | FacultyGraph |
| `admin` (+ `admins.sub_role` ∈ `super_admin\|platform_admin\|admin`) | same | AdminGraph |

- **Role is never supplied by the client for authorization.** The token's claims decide; `G1` forbids trusting client-supplied identity. This is why `POST /auth/faculty-login` takes `college_id` as a *credential hint* only, and the server resolves the actual user from the `faculty` table.
- Admin **sub-role** gates only destructive/mutating actions (see `android-screens.md` §F0); it does not hide screens.

### 5.3 Why not reuse the PHP session

The web's session is a cookie with a CSRF token and an 1800 s regeneration window (`session.php:8-21`). A native app cannot reliably participate in that flow (no cookie jar semantics users expect, no CSRF surface, no third-party cookie lifetime). The `/v1` layer therefore authenticates **only** via bearer tokens and **never** calls `session_start()` in a way that the client can observe.

---

## 6. Client architecture (recommended)

```
app/
 ├─ di/                     Hilt modules
 ├─ data/
 │   ├─ remote/             Retrofit services (one per contract section: AuthService,
 │   │                      TestService, SubmissionService, …)
 │   ├─ remote/dto/         kotlinx.serialization DTOs mirroring the contract exactly
 │   ├─ token/              TokenStore (Keystore) + AuthInterceptor + TokenRefreshInterceptor
 │   └─ repo/               *Repository — the only layer ViewModels talk to
 ├─ domain/
 │   ├─ model/              pure Kotlin models (Test, Submission, Question, …)
 │   └─ usecase/            optional; for multi-repo flows (StartTest, SubmitTest, PromoteBatch)
 ├─ ui/
 │   ├─ nav/                NavGraph per role (android-screens.md §B)
 │   ├─ components/         the shared components in android-screens.md §H
 │   └─ <feature>/          one package per screen: Screen + ViewModel + UiState
 └─ util/                   countdown clock, debounce, retry policy, chart fallback
```

Rules the implementing AI must follow:

1. **ViewModels hold a single `StateFlow<UiState>`** per screen; composables are stateless renderers of `UiState`. Business rules (§9) live in the repository or use-case, **never** in a composable.
2. **The repository is the only network boundary.** ViewModels never see Retrofit types.
3. **Every write returns the server's authoritative object** and the ViewModel replaces local state with it (this is how `saved_at`, ranks, and grades stay correct without local computation).
4. **One `OkHttpClient`** with: auth interceptor (attach bearer), a refresh interceptor (single-flight refresh with a `Mutex`, queue concurrent 401s), 10 s call timeout, and no response caching for `/v1`.
5. **`SavedStateHandle`** for anything that must survive process death mid-test (submission id, test id). Not for tokens.
6. **No offline database of questions or answers** — the paper is fetched fresh, answers live in memory + the server, and process death resumes via `GET /submissions/{id}`.

---

## 7. API summary

Full details in [`android-api-contract.md`](android-api-contract.md). Grouped counts:

| Group | Range | Count | Highlights |
|---|---|---|---|
| Auth & session | 1–12 | 12 | login, faculty-login, register, otp verify/resend, password forgot/verify/reset, refresh, logout, session |
| Hierarchy (geo/college/course/batch/section) | 13–33 | 21 | cascading dropdowns, wizard create, archive/restore/permanent-delete, promote |
| Students & guest | 34–42 | 9 | list/filter, patch, delete, guest links + QR, unverified-students |
| Tests & questions | 43–63 | 21 | CRUD, publish/schedule/pause/resume/force-end, questions CRUD + reorder + CSV import, extend-timer |
| Submissions & grading | 64–74 | 11 | **start**, answers autosave, submit, result, live, grades, tab-switches, PCI |
| Analytics & charts | 75–80 | 6 | student analytics, faculty overview, admin stats, per-test results, charts |
| Misc (notifications, faculty, failed-logins) | 81–89 | 9 | notification CRUD, faculty CRUD, failed-login audit, help |

Conventions every endpoint follows:

- **Base path** `/api/v1`, `Accept: application/json`, bearer auth unless marked public.
- **Success** `200` (or `201` on create) with the resource object.
- **Errors** `{ "error": { "code": "...", "message": "...", "details"?: { "field": "..." } } }` with the HTTP statuses in §11. Codes are stable strings (`token_expired`, `test_not_found`, `deadline_passed`, `duplicate`, `rate_limited`, `forbidden`, …).
- **Pagination** `?page=&per_page=` returning `{ data: [...], meta: { page, per_page, total } }`.
- **The 8 legacy endpoints are reimplemented** with identical semantics: `submit_answer`, `tab_switch`, `delete_college`, `promote_students`, `get_courses`, `get_batches`, `get_course_college`, `get_sections`.

---

## 8. Design system parity (Fluent 2 → Material 3)

The web is **Fluent 2** (`logic.md` §16: admin theme with data-dense tables, 4 px grid, `#0078D4` accent). Android uses **Material 3** with tokens mapped as follows.

### 8.1 Color mapping

| Fluent token (web value) | Material 3 role |
|---|---|
| accent `#0078D4` | `primary`, `onPrimary = #FFFFFF` |
| green `#0B6A0B` (PASS / success) | `tertiary` (or a custom `success` color in the scheme) |
| red `#BC2F32` (FAIL / error) | `error`, `onError = #FFFFFF` |
| yellow `#8A6D00` (warning) | `tertiaryContainer` / custom `warning` |
| orange `#C85A00` (coding-question accent) | custom `codingAccent` |
| gray-5 `#F5F5F5` (page background) | `background` / `surfaceContainerLowest` |
| gray-30 `#E1DFDD` (borders, gridlines) | `outlineVariant` |
| gray-90 `#292827` (text) | `onSurface` |

Dark theme: derive with `isDark = true`; the web's `dark` query flags are a **client-only** concern — no server change.

### 8.2 Spacing, type, radius

| Fluent | Material 3 |
|---|---|
| 4 px base grid | `4.dp` unit; standard insets `8/12/16/24.dp` |
| radius 2 / 4 / 8 | `Shapes.small / medium / large` |
| body 14 px / caption 12 px / subtitle 16 px / title 20–24 px | `Typography.bodyMedium / labelSmall / titleMedium / headlineSmall` |

### 8.3 Component mapping

| Fluent 2 control | Material 3 equivalent | Used in |
|---|---|---|
| Command bar | `TopAppBar` + `IconButton`s | all screens |
| Data grid (striped, paginated) | `LazyColumn` + custom `TableRow` (shared `DataTable` component) | admin tables |
| Dialog / teaching bubble | `AlertDialog` / `ModalBottomSheet` | confirms, forms |
| Dropdown / combo box | `ExposedDropdownMenuBox` | cascading pickers |
| Progress bar | `LinearProgressIndicator` / `CircularProgressIndicator` | loading states |
| Info bar (toast) | `SnackbarHost` | save status, errors |
| Pivot / tabs | `TabRow` / `PrimaryTabRow` | filters, wizard steps |

Full shared-component list: [`android-screens.md` §H](android-screens.md#h-shared-ui-components).

---

## 9. Business rules reference (with file:line)

**This is the most important table in the document.** Every rule is enforced by the server; the client must present it faithfully and must never diverge.

### 9.1 Auth & identity

| # | Rule | Source |
|---|---|---|
| A1 | Never trust client-supplied identity — role and user id come from the token / session | `lms-gap-spec.md` G1 |
| A2 | Faculty login requires a `college_id`; the server resolves the user from `faculty` + `faculty_colleges`, not from the posted email alone | `auth.php:659-690` |
| A3 | Brute-force: 5 failures per email per 900 s → `429 account_locked` | `auth.php:30-33` |
| A4 | Signup creates the row in `unverified_students`, **not** `students`; the account cannot authenticate until OTP-verified | `auth.php:360-400` |
| A5 | Password-reset OTP: 6 digits, 10-minute expiry, single-use, newest-wins (requesting a new OTP invalidates prior) | `auth.php:455-490` |
| A6 | Successful reset revokes **all** of the student's API tokens | `auth.php:520-530` |
| A7 | Probing faculty-login with a student/admin email logs `invalid_email` and must not lock that other account | `auth.php:92-99, 686-690` |
| A8 | Login rate limit: 10 requests / 60 s per student across both login endpoints | `auth.php` throttle |

### 9.2 Test-taking (the core)

| # | Rule | Source |
|---|---|---|
| T1 | `effective_deadline = earliest(tests.end_time, submissions.started_at + tests.duration_minutes)` | `submit_answer.php:167-189`, G4 |
| T2 | **Grace period is 0 seconds.** A submission at deadline+1 is refused (`deadline_passed`) | G4 |
| T3 | Server time is authoritative; client countdown is display-only | G4, `submit_answer.php` |
| T4 | Autosave server throttle: max **1 write per 60 s per student per question** → `429` | G5, `submit_answer.php:100-140` |
| T5 | The response's `saved_at` (server clock) drives the "Saved at HH:MM:SS" display | G7 |
| T6 | Submit is idempotent: a lost-race submit returns `duplicate: true` and is **success-equivalent** | `submit_answer.php:194-204, 258` |
| T7 | Question scoping: only ids returned by the paper may be written; foreign ids → `400` | G3 |
| T8 | MCQ answers are stored as `{"selected":"A"}`; coding `{"code":"..."}`; explanation `{"text":"..."}` | `submit_answer.php:239-244` |
| T9 | Unanswered MCQ scores **0**; unanswered coding/explanation scores 0 and counts as ungraded | G11 |
| T10 | Timer expiry → the client **must** auto-submit with the same retry/backoff (web leaves this OPEN — see §16) | G9 |
| T11 | Existing submission (in_progress/submitted/evaluated) short-circuits the start gates → resume or result | `test.php:44-72` |
| T12 | Take gates, in order: completed → "Test Stopped"; paused & no progress → "Test Paused"; not active or outside window → "Test is not available" | `test.php:218-276` |

### 9.3 Anti-cheat

| # | Rule | Source |
|---|---|---|
| C1 | Each counted switch → one row in `tab_switch_logs` with `submission_id`, `switch_count`, IP, UA, `occurred_at` (DATETIME(3)) | `tab_switch.php:1-78` |
| C2 | Show a 3-second warning banner on every counted switch | `logic.md:745` |
| C3 | `tests.max_tab_switches` is set-or-null; when set and exceeded, apply the admin-configured consequence (the web records only — do not invent auto-submit) | `schema.sql` `tests.max_tab_switches` |
| C4 | Config changes / Activity recreation must **not** log a switch | spec (D6) |
| C5 | `timer_extend` events are logged distinctly with `metadata.extended_by` | `tab_switcher.php` |

### 9.4 Grading & ranking

| # | Rule | Source |
|---|---|---|
| R1 | Per-question awarded marks are clamped to `questions.marks` | `grading.php:14-26` |
| R2 | `ungraded` = non-MCQ answers with NULL marks; the submission becomes `evaluated` only when `ungraded == 0` | `grading.php:31-38` |
| R3 | `total_score = COALESCE(auto_score,0) + COALESCE(manual_score,0)`, DECIMAL(8,2) | `submit_answer.php` |
| R4 | **Ranking (corrected, v1):** evaluated submissions only; **no** `COALESCE(...,0)` for pending; order by `total_score DESC`; **competition rank** `1,1,3` | G10; web bug at `reports.php:41,55,641`, `faculty/dashboard.php:134,189,215` |
| R5 | PASS threshold = 40 % (percent ≥ 40) | `test.php` result branch, `logic.md` |
| R6 | `evaluation_status` ∈ `pending_manual_review | evaluated`; per-question review shows correct option, your option, marks, remarks | `test.php:76-212` |

### 9.5 Hierarchy & lifecycle

| # | Rule | Source |
|---|---|---|
| H1 | Cascade integrity: college→course→batch→section→student; deleting a parent is blocked while children are active | `schema.sql` FKs, `ON DELETE RESTRICT` on `students.batch_id` |
| H2 | **Archive ≠ delete.** `DELETE /colleges|courses|batches/{id}` sets `status='archived'`; permanent delete is a separate, guarded, ordered cascade that requires the row to already be archived | `delete_college.php:1-312` |
| H3 | Permanent-delete cascade order: PCI → submissions/answers → tests → students → faculty links → streams → courses → batch → college, each writing `audit_log` | `delete_college.php` |
| H4 | Promote is **dry-run first** (`preview=true`), then atomic; validates: target semester has students, won't exceed `duration_years × 2`, no duplicate batch at target | `promote_students.php:1-140` |
| H5 | `college_streams` carry `category UGC|PGC`; wizard batches must have internally consistent `duration_years` + `ending_year` + `academic_year` | `migration_college_wizard.sql` |
| H6 | `batch_nick_name` is globally unique | `migration_college_wizard.sql` |
| H7 | Guest ownership: a guest token is bound to `guest_entries.id`; guest answers go to `guest_entries.temp_data`; submission id = string `guest_<entryId>` | G2, `auth.php:600-630`, `submit_answer.php` |
| H8 | `submissions.evaluation_status` starts `pending_manual_review` for coding/explanation tests, `evaluated` when all auto-gradable | `submit_answer.php`, G12/G13 |

### 9.6 Rate limits & quotas

| # | Rule | Source |
|---|---|---|
| Q1 | Login: 10 / 60 s per student | `auth.php` |
| Q2 | Autosave: 1 write / 60 s per student per question | G5 |
| Q3 | OTP resend: cooldown + `429` | `auth.php` |
| Q4 | `autosave_throttle_log` records enforcement | `schema.sql` |

---

## 10. Data model reference

Authoritative source: `LMSv5/sql/schema.sql` plus the 17 `sql/migration_*.sql` files. **26 tables:**

| Group | Tables |
|---|---|
| Identity | `students`, `unverified_students`, `admins`, `faculty`, `faculty_colleges`, `failed_login_log`, `password_reset_tokens`, `api_tokens`, `faculty_login_log` |
| Hierarchy | `colleges`, `courses`, `batches`, `sections`, `college_streams`, `college_batches`, `geo_cities` |
| Assessments | `tests`, `test_sections`, `questions`, `options_json` (embedded), `test_questions` |
| Submissions | `submissions`, `answers`, `autosave_throttle_log`, `tab_switch_logs`, `pci_records` |
| System | `notifications`, `admin_notifications`, `audit_log`, `system_settings` |

Client-relevant notes:

- `colleges.status`, `courses.status`, `batches.status` ∈ `active|archived` (soft delete). `students.status` ∈ `active|inactive`.
- `tests.test_type` ∈ `mcq|coding|explanation|mixed`; `tests.status` ∈ `draft|scheduled|active|paused|completed`.
- `submissions.evaluation_status` ∈ `pending_manual_review|evaluated`; `auto_score`, `manual_score`, `total_score` are `DECIMAL(8,2)`.
- `questions.marks` drives grading caps; `questions.type` ∈ `mcq|coding|explanation`.
- `tab_switch_logs.occurred_at` is `DATETIME(3)` (millisecond precision).
- The client **never** writes `audit_log`, `system_settings`, or `admin_notifications` directly — those are server-side consequences.

DTOs must mirror these names exactly so the contract doc, the SQL, and the Kotlin models stay aligned.

---

## 11. HTTP status & error parity

| Status | Meaning | Typical codes |
|---|---|---|
| `200` | success | — |
| `201` | created (register, start submission, create entity) | — |
| `400` | malformed / rule violation | `invalid_payload`, `invalid_question_id`, `deadline_passed`, `test_not_active`, `test_paused`, `test_completed`, `csv_invalid` |
| `401` | auth | `token_missing`, `token_invalid`, `token_expired` (refreshable) |
| `403` | authorization / ownership | `forbidden`, `not_verified`, `account_deactivated`, `guest_forbidden`, `admin_required`, `super_admin_required`, `invalid_credentials` |
| `404` | not found | `test_not_found`, `submission_not_found`, `question_not_found`, `college_not_found`, `student_not_found` |
| `409` | conflict | `duplicate` (email, code, nick_name), `already_submitted` |
| `422` | validation | `validation_failed` + `details.field` |
| `429` | rate limit | `rate_limited`, `account_locked`, `autosave_throttled` |
| `500` | server | `server_error` (render the designed 500 state, never raw JSON) |
| `503` | dependency down | `python_unavailable` (charts degrade to tables, §14) |

Rules:
- **Never render raw JSON** to the user. Map `error.code` → designed full-screen or inline message. The web already distinguishes HTML vs JSON via `requireCsrf()`/`redirectReasonQuery()`; Android is always the JSON consumer.
- `403` carries an optional `reason` ∈ `forbidden|expired|required` mirroring `redirectReasonQuery()` (`auth.php:583-588`) so the client can say *why*.
- `429` carries `retry_after` seconds where applicable; the client shows a countdown and **never** auto-retries `429` before the window.

---

## 12. Offline strategy & autosave queue

**Principle:** the exam is not an offline activity. There is no persistent answer queue across process death — correctness beats convenience (G8/G12).

### 12.1 In-session autosave (the only queue)

```
user edits answer
  → mark question id dirty
  → debounce 2 s after last keystroke
  → if a request is in flight, set "pending retry" flag (no overlap)
  → POST /submissions/{id}/answers { answers: {qid: value}, auto_save: true }
       ├─ 200 → clear dirty flags for sent qids; display server saved_at
       ├─ 429 → schedule retry in retry_after seconds (do NOT hammer)
       ├─ deadline_passed → stop autosaving; show deadline state; attempt submit
       └─ timeout / 5xx → retry up to 3 times, backoff 1000·2^attempt ms
            └─ exhausted → "Unable to save" + explicit Retry button (gap G6)
```

### 12.2 What is cached (read-only)

- The fetched paper (questions + metadata) for the current session only — evicted on submit, on test switch, or on logout.
- The student's hierarchy for dropdown rendering (colleges/courses/batches) — TTL ~5 min, invalidated on pull-to-refresh.

### 12.3 What is never cached

- Tokens are in Keystore only (never logged, never in `Log`, never in analytics).
- Answers are never written to disk or `SharedPreferences`.
- No Room database in v1 (YAGNI — the server is always the store).

### 12.4 Process death recovery

On recreation: read `submission_id` + `test_id` from `SavedStateHandle` → `GET /submissions/{id}` → restore saved answers + `remaining_seconds` → resume. Never re-POST start (idempotent but wasteful), never restart the countdown locally.

---

## 13. Anti-cheat lifecycle mapping

| Web event | Android event | Notes |
|---|---|---|
| `document.visibilitychange` → hidden | `onStop()` / `onPause()` of the test screen | counts |
| switch to another tab / site | `onWindowFocusChanged(false)` | counts |
| window `blur` | blocked — `resizeableActivity="false"` | no split-screen |
| window `resize` | blocked — same flag | |
| (none) | screen-off (`onPause` + `KEYCODE_POWER` path) | counts (stronger, D7) |
| (none) | `FLAG_SECURE` blocks screenshots & screen recording | does **not** count as a switch |
| (n/a) | configuration change / Activity recreation | **must not** count |

Every counted event → `POST /tab-switches` with `switch_count: 1`; then show the 3-second banner (C2). Respect `tests.max_tab_switches` (C3) — the web only *records* exceedances, so do not invent an auto-submit.

---

## 14. Analytics & Python charts

- Endpoints: `GET /student/analytics`, `GET /faculty/overview`, `GET /admin/stats`, `GET /tests/{id}/results`, `GET /pci-records`, `GET /charts/test/{id}`.
- `GET /charts/*` returns rendered chart data (or base64 images) from the optional Python service. Every response includes `python_api_available`.
- **Degrade gracefully:** when `503 python_unavailable` or the flag is false, the client renders **numeric tables** instead of charts. This mirrors the web's fallback and is a parity requirement, not a nicety.
- Chart types used: bar (PCI per student), doughnut (category averages), stacked bar (per type per student), histogram (score distribution), radar (individual breakdown).
- Recommended client libs: **Vico** (Compose-native) or **MPAndroidChart**; QR via **ZXing** (`com.google.zxing:core`).
- **Never** show individual peer scores to a student — only batch-level aggregates (privacy rule from `logic.md`).

---

## 15. Parity matrix

### 15.1 Screens (40 PHP pages → 36 Android destination screens)

| Bucket | PHP pages | Android destination screens | Where specified |
|---|---|---|---|
| Public / auth | 10 (index, login, bgs, signup, verify-otp, forgot-password, faculty-login, guest, logout, `__error_probe_500`) | 7 (Splash, Login[*bgs* merged], FacultyLogin, Signup, VerifyOtp, ForgotPassword, GuestEntry) + Logout action + shared error states | `android-screens.md` §C |
| Student | 7 | 6 (Dashboard, MyTests[*test-list* merged], TestTaking, Results, Analytics, Profile) + post-submit Result state | §D |
| Faculty | 1 | 1 (FacultyDashboard) | §E |
| Admin | 22 | 22 (incl. 2 "Coming Soon" placeholders) | §F |
| **Total** | **40** | **36** destinations (7+6+1+22) | §G (collapse rules) |

### 15.2 `logic.md` sections (20/20 mapped)

| `logic.md` § | Topic | Android counterpart |
|---|---|---|
| 1 | Schema overview | §10 + `schema.sql` |
| 2 | Admin theme | §8 + screens §H |
| 3 | Admin dashboard | screens F1 |
| 4 | College lifecycle | screens F2–F4, rules H1–H3 |
| 5 | Courses & batches | screens F5–F6, rules H4–H6 |
| 6 | Students | screens F7–F8 |
| 7 | Faculty control | screens E1, F18 |
| 8 | Tests & builder | screens F10–F11, rules T1–T12 |
| 9 | Questions & library | screens F9–F10 |
| 10 | Test taking | screens D3, rules T1–T12, §12–13 |
| 11 | Grading | screens F15, rules R1–R3 |
| 12 | Reports & ranking | screens F16, rules R4–R6 |
| 13 | Analytics | screens D6, §14 |
| 14 | Notifications | screens F17 |
| 15 | Guest & QR | screens C7, F7, rules H7 |
| 16 | Design system | §8 |
| 17 | Anti-cheat | §13, rules C1–C5 |
| 18 | Failed logins | screens F19 |
| 19 | Status taxonomy | screens §H `StatusBadge` |
| 20 | Misc & placeholders | screens F20–F22, §G |

### 15.3 Legacy endpoints (8/8 reimplemented)

| Legacy | New v1 | Parity note |
|---|---|---|
| `api/submit_answer.php` (380 L) | `POST /submissions/{id}/answers`, `POST /submissions/{id}/submit` | same throttle, same `saved_at`, same idempotent submit |
| `api/tab_switch.php` (78 L) | `POST /tab-switches` | same logging, same banner rule |
| `api/delete_college.php` (312 L) | `DELETE /colleges/{id}/permanent` | same ordered cascade + audit |
| `api/promote_students.php` (140 L) | `POST /batches/{id}/promote` | same dry-run + 3 validations |
| `api/get_courses.php` | `GET /courses` | same filters |
| `api/get_batches.php` | `GET /batches` | same filters |
| `api/get_course_college.php` | folded into `GET /colleges/{id}` / `GET /courses` | same data |
| `api/get_sections.php` | `GET /sections` | same filters |

---

## 16. Known open gaps & deferred items

| # | Gap | Status | Impact on Android |
|---|---|---|---|
| G8 | PCI card capture is deferred to post-submit on the web | **open on both platforms** | render the deferred capture screen after submit; do not invent a different timing |
| G9 | Web does **not** auto-submit on timer expiry | open on web | Android **must** auto-submit with retry (T10) — a deliberate improvement; server still enforces the deadline |
| G10 | Ranking bug: `COALESCE(...,0)` includes pending as 0; row-position rank instead of competition rank | **fixed in v1 `/v1`** (D5) | client just renders the corrected values |
| — | Push notifications | not built | use in-page polling (screens F17) |
| — | Offline test-taking | not built | §12 |
| — | Settings / Activity Logs screens | "Coming Soon" on web | render the same placeholder (screens F21–F22) |
| — | Self-service faculty registration | does not exist | do not add a signup path for faculty |

---

## 17. Source references & reading order

### 17.1 Reading order for the implementing AI

1. **This file** (§1–§5 for decisions, §9 for the rules you must not break).
2. **[`android-api-contract.md`](android-api-contract.md)** — implement the Retrofit layer first; every screen depends on it.
3. **[`android-screens.md`](android-screens.md)** — implement screens role-group by role-group (Auth → Student → Faculty → Admin).
4. **[`logic.md`](logic.md)** — consult when a rule in §9 needs more prose context.
5. **[`lms-gap-spec.md`](lms-gap-spec.md)** — G1–G14 are the verified, non-negotiable rules.

### 17.2 PHP files pinned in this document

| File | Why it matters |
|---|---|
| `src/php/includes/auth.php` | login/register/OTP/reset/guest — all auth rules (§9.1) |
| `src/php/includes/helpers.php` | shared render helpers, notification types |
| `src/php/includes/session.php` | the session/CSRF model being replaced |
| `src/php/public/student/test.php` | test-taking gates, result rendering (§9.2) |
| `src/php/api/submit_answer.php` | autosave throttle, deadline, idempotent submit |
| `src/php/api/tab_switch.php` | anti-cheat logging |
| `src/php/api/delete_college.php` | archive vs permanent-delete cascade |
| `src/php/api/promote_students.php` | dry-run + validation |
| `src/php/public/admin/reports.php` | ranking (bug + fix) |
| `src/php/public/admin/grading.php` | manual grading rules |
| `sql/schema.sql` + `sql/migration_*.sql` | the 26-table model (§10) |

### 17.3 Scope reminder

This document set is **documentation only**. Implementing it requires:
1. A follow-up task to add the `/api/v1/*` layer to the PHP server per [`android-api-contract.md`](android-api-contract.md) (reusing the functions in its Appendix A).
2. The Android Studio project built from [`android-screens.md`](android-screens.md) + this file.

No PHP, SQL, or Kotlin source was modified in producing these three documents.
