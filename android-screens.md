# Android Screen Specification — `LMSv5`

> **Purpose:** per-screen behavioral spec so an Android (Jetpack Compose + Kotlin) client reaches 100% functional parity with the PHP web app.
> **Companion docs:** [`android-logic.md`](android-logic.md) (architecture, business rules, parity matrix) · [`android-api-contract.md`](android-api-contract.md) (the 89 endpoints these screens call) · [`logic.md`](logic.md) (PHP source of truth)
> **Screen count:** 40 PHP pages → 36 Android destination screens (4 pages collapse: `bgs.php`→Login, `test-list.php`→My Tests, `logout`→action, `__error_probe_500.php`→no client surface)

---

## Table of Contents

- [A. How to read a screen entry](#a-how-to-read-a-screen-entry)
- [B. Navigation graph](#b-navigation-graph)
- [C. Public / Auth screens (9)](#c-public--auth-screens)
- [D. Student screens (7)](#d-student-screens)
- [E. Faculty screens (1)](#e-faculty-screens)
- [F. Admin screens (22)](#f-admin-screens)
- [G. Screens with no Android equivalent (4)](#g-screens-with-no-android-equivalent)
- [H. Shared UI components](#h-shared-ui-components)

---

## A. How to read a screen entry

| Field | Meaning |
|---|---|
| **Source** | the PHP file this screen replaces |
| **Role** | who may see it (nav-graph gate) |
| **Entry** | how the user reaches it |
| **APIs** | v1 endpoints consumed (links into the API contract) |
| **States** | loading / empty / error — every screen must implement all three |
| **Edge cases** | parity-critical behaviors that are easy to miss |

Compose convention: each screen = one `@Composable fun XxxScreen(viewModel: XxxViewModel)` + one `XxxViewModel` exposing a `StateFlow<UiState>`. Business rules live in the ViewModel or a use-case — **never** in the composable. See [`android-logic.md` §6](android-logic.md#6-client-architecture-recommended).

---

## B. Navigation graph

```
                        ┌──────────────┐
                        │  SplashScreen │  (restores token → role)
                        └──────┬───────┘
         ┌─────────────────────┼──────────────────────┬──────────────┐
         ▼                     ▼                      ▼              ▼
   ┌──────────┐        ┌────────────┐          ┌───────────┐   ┌──────────┐
   │AuthGraph │        │StudentGraph│          │FacultyGraph│  │ AdminGraph│
   └──────────┘        └────────────┘          └───────────┘   └──────────┘
   Login (role sel)     Dashboard               Dashboard      Dashboard
   FacultyLogin         MyTests/TestList                        Colleges
   Signup               TestTaking (⚠FLAG_SECURE)               CollegeCreate
   VerifyOtp            Result                                    CollegeDashboard
   ForgotPassword       Results                                   Courses
   GuestEntry           Analytics                                 Batches
                        Profile                                   Students
                                                                PendingVerifications
                                                                QuestionLibrary
                                                                TestBuilder
                                                                AssessmentStudio
                                                                AssessmentManagement
                                                                LiveMonitor
                                                                TabSwitcher
                                                                Grading
                                                                Reports
                                                                Notifications
                                                                FacultyControl
                                                                FailedLogins
                                                                Help
                                                                Settings
```

Rules:
- **Role is resolved once** at cold start from `GET /auth/session`; the graph is chosen by role and a user cannot navigate across graphs.
- `admin` sub-role (`super_admin|platform_admin|admin`) only gates **destructive** actions (see §F gate table), not screen visibility.
- The **test-taking** screen is a single-Activity fullscreen destination with `FLAG_SECURE`, `resizeableActivity=false`, and no system-back exit without confirmation.
- `logout` is reachable from every graph's top bar and clears the Keystore token + returns to `AuthGraph`.

---

## C. Public / Auth screens

### C1. Splash / Boot
| | |
|---|---|
| **Source** | `index.php` (role-based redirect) |
| **Role** | any / none |
| **Entry** | app launch |
| **APIs** | `GET /auth/session` |
| **Behavior** | read cached token from Keystore → call `/auth/session` → route to the matching graph. No token → `AuthGraph`. Network error with a valid cached token → **offline-tolerant**: route by cached role and show a non-blocking "offline" banner (do not bounce a logged-in user to login on a flaky connection). |
| **Edge** | `index.php` itself is a pure redirect with no data (`index.php:13-20`); the Android equivalent must not flash the auth graph before deciding. |

### C2. Login
| | |
|---|---|
| **Source** | `login.php`, `bgs.php` (both are login pages; `bgs.php` is a high-concurrency variant with identical logic) |
| **Role** | guest |
| **Entry** | AuthGraph root |
| **APIs** | `POST /auth/login` |
| **UI** | role selector (Student / Admin) · email · password · show/hide · Sign in · link to Signup · link to Forgot Password |
| **Behavior** | submit → on success store tokens → route by `role`. On `403 not_verified` → navigate to **VerifyOtp** passing `student_id`. On `429 account_locked` → disable submit for the remaining window and show the server message. |
| **Edge cases** | • Role selector is a **hint** only — the server decides (`auth.php:121-128, 306-318`).<br>• Brute-force: 5 failures / 900 s per email (`auth.php:30-33`); show the exact server message, never invent one.<br>• `bgs.php`'s concurrency design (release session lock before redirect) is a **server** concern; the client just fires one request.<br>• Password never logged, never persisted outside the field. |

### C3. Faculty Login
| | |
|---|---|
| **Source** | `faculty-login.php` |
| **Role** | guest |
| **Entry** | Login screen role selector → "Faculty", or a dedicated entry |
| **APIs** | `GET /colleges?has_faculty=1` (dropdown, **no token**), `GET /geo/cities` (optional location typeahead), `POST /auth/faculty-login` |
| **UI** | college dropdown (required) · email · password · optional location field |
| **Behavior** | college is mandatory — empty → inline "Please select your college." (`auth.php:659-662`). Success → FacultyGraph. `403` deactivated account → show message, do **not** retry. |
| **Edge cases** | • A faculty row can belong to **many** colleges via `faculty_colleges`; the posted `college_id` is what scopes the lookup (`auth.php:674-683`).<br>• Geolocation is **optional** and best-effort; denial must not block login (`latitude/longitude/accuracy_m` stay NULL).<br>• Probing with a student/admin email logs `invalid_email` and must **not** throttle-lock that other account (`auth.php:686-690`, `:92-99`). |

### C4. Signup
| | |
|---|---|
| **Source** | `signup.php` |
| **Role** | guest |
| **Entry** | Login → "Create account" |
| **APIs** | `GET /colleges?status=active`, `GET /courses?college_id=`, `GET /batches?course_id=&active=1`, `GET /sections?batch_id=`, `POST /auth/register` |
| **UI** | name · email · phone · gender · college ▾ → course ▾ → batch ▾ → section ▾ · roll number · year of joining · password · confirm password |
| **Behavior** | **cascading dropdowns** — selecting a college clears + refills courses; selecting a course clears + refills batches (the exact AJAX the web does via `get_courses.php` / `get_batches.php`). Submit → `POST /auth/register` → navigate to **VerifyOtp** with `student_id`. |
| **Edge cases** | • Duplicate email → `409` with two distinct messages: verified duplicate vs "already registered, check your email for the OTP" (`auth.php:374-388`).<br>• Invalid hierarchy produces **three different** server messages (no courses in college / no batches in course / invalid batch) — render the server's message verbatim (`auth.php:399-414`).<br>• The student row is created in `unverified_students`, **not** `students` — the account cannot log in until OTP verified.<br>• OTP email failure still creates the account; the server raises an admin notification. |

### C5. Verify OTP
| | |
|---|---|
| **Source** | `verify-otp.php` |
| **Role** | guest (holds a pending `student_id`) |
| **Entry** | Signup success, or Login `403 not_verified` |
| **APIs** | `POST /auth/otp/verify`, `POST /auth/otp/resend` |
| **UI** | 6-digit OTP input (auto-advance) · Verify · Resend (cooldown timer) · email display |
| **Behavior** | verify success → tokens issued → **StudentGraph** (parity: web auto-logs-in after verification, `logic.md:492`). |
| **Edge cases** | • `otp_expired` → prompt resend (`auth.php:208-214`).<br>• `otp_invalid` → clear the field, keep focus (`:217-222`).<br>• Resend starts a **new** 10-minute window and invalidates the old OTP implicitly (new `otp_hash` overwrites).<br>• Resend is rate-limited; surface `429` with a cooldown.<br>• Every failure writes `failed_login_log` **and** an admin notification — the client must not spam resend. |

### C6. Forgot Password
| | |
|---|---|
| **Source** | `forgot-password.php` |
| **Role** | guest |
| **Entry** | Login → "Forgot password" |
| **APIs** | `POST /auth/password/forgot`, `POST /auth/password/verify-otp`, `POST /auth/password/reset` |
| **UI** | 3-step flow: (1) email → (2) OTP → (3) new password + confirm |
| **Behavior** | step 1 **always** returns success (no email enumeration, `auth.php:478-481`) — the UI must say "If that email is registered, an OTP has been sent." Steps 2→3 are gated by the `reset_grant` returned at step 2. |
| **Edge cases** | • Only **verified students** can reset (lookup is `students` only, `:474-476`).<br>• Requesting a new OTP invalidates all prior unused ones (`:489`).<br>• Success revokes **all** the student's API tokens.<br>• The web stores the intermediate state in `$_SESSION`; Android uses the stateless `reset_grant` (see API contract §4.6–4.8). |

### C7. Guest Entry
| | |
|---|---|
| **Source** | `guest.php` |
| **Role** | guest |
| **Entry** | deep link / QR scan → app opens with the token; or a manual "Enter guest code" screen |
| **APIs** | `POST /guest/session` |
| **UI** | token input (prefilled from the deep link) · Continue; or an auto-processing state if launched from a link |
| **Behavior** | success → guest token stored → if `test_id` present go straight to **TestTaking**, else to the student dashboard in guest mode. |
| **Edge cases** | • Invalid/expired → `400` + the server's "Invalid or expired link." and an admin notification fires (`auth.php:620-623`).<br>• The guest token is **bound to `guest_entries.id`** — this is what lets the server enforce the G2 ownership rule on answer writes.<br>• Guests never create a `submissions` row; their answers go to `guest_entries.temp_data` and the submission id is the string `guest_<entryId>`.<br>• **Deep-link handling:** register an `intent-filter` for the guest URL scheme/host; extract `?token=`. Do **not** log the token. |

### C8. Logout (action, not a screen)
Reachable from every authenticated top bar. Calls `POST /auth/logout`, clears Keystore tokens + cached role, cancels any in-flight autosave, and returns to `AuthGraph`. Must be idempotent.

### C9. Error screens (403 / 404 / 429 / 500)
| | |
|---|---|
| **Source** | `includes/error_pages.php`, `session.php:51-73` |
| **Role** | any |
| **Entry** | any API returning a non-2xx that the caller cannot handle inline |
| **Behavior** | map `error.code` → a designed full-screen state: `403 forbidden` (with the "why you were redirected" reason: `forbidden|expired|required`, mirroring `redirectReasonQuery()` at `auth.php:583-588`) · `404 not_found` · `429 rate_limited` (with retry countdown) · `5xx` retryable. |
| **Rule** | **never** render raw JSON. The web's `requireCsrf()` already distinguishes HTML-vs-JSON responses; Android is always the JSON consumer. |

---

## D. Student screens

### D1. Student Dashboard
| | |
|---|---|
| **Source** | `student/dashboard.php` |
| **Role** | student |
| **Entry** | StudentGraph root |
| **APIs** | `GET /tests?filter=`, `GET /students/me` |
| **UI** | stat cards (tests available / in progress / submitted / evaluated) · test list with status badges · pull-to-refresh |
| **Behavior** | the web embeds the test list in an **iframe** pointing at `test-list.php` (`dashboard.php`); Android collapses dashboard + test-list into one scrollable screen with a filter row. |
| **Edge cases** | • Badge derivation must match `logic.md:19.2` exactly (see API contract §7.1).<br>• `remaining_seconds` is server-computed; never derive the countdown locally as the source of truth.<br>• A test with `status='active'` but outside `[start_time, end_time]` still shows as unavailable. |

### D2. My Tests / Test List
| | |
|---|---|
| **Source** | `student/my_tests.php`, `student/test-list.php` |
| **Role** | student |
| **Entry** | Dashboard filter tabs / bottom nav |
| **APIs** | `GET /tests?filter=&search=` |
| **Filters** | `all, active, upcoming, completed, submitted, pending, missed, paused, scheduled` (these exact filter keys appear in both PHP files) |
| **Edge** | `my_tests.php` additionally surfaces **switch counts** from `tab_switch_logs` — expose it as a subtle "tab switches: N" chip when > 0. |

### D3. Test Taking ⚠ (highest-integrity screen)
| | |
|---|---|
| **Source** | `student/test.php` |
| **Role** | student **or** guest |
| **Entry** | Dashboard "Start/Resume" · guest redirect |
| **APIs** | `POST /submissions` (start) · `GET /tests/{id}` (paper) · `GET /submissions/{id}` (resume) · `POST /submissions/{id}/answers` (autosave) · `POST /submissions/{id}/submit` · `POST /tab-switches` |

**Layout (parity with `logic.md:10.2`):** sticky timer bar (title + `HH:MM:SS` + question count) · question navigator dot-grid (○ unanswered / ● answered / ring = current) · one question per page with type-specific input · Save-status indicator · Submit button.

**State machine:**
```
Loading → PaperLoaded → Answering ⇄ Autosaving → Saved(at HH:MM:SS)
                              │                        │
                              │                   SaveFailed → Retrying → (cap) UnableToSave + Retry
                              ▼
                        Submitting → Submitted(evaluated | pending_manual_review)
                              │
                        TimerExpired → AutoSubmitting → (failure) SubmitFailed + Retry
```

**Critical rules (all source-verified):**

| # | Rule | Source |
|---|---|---|
| 1 | **Start is explicit.** Call `POST /submissions` before rendering the paper. The web creates the row as a side effect of the GET — Android must not assume that. | `test.php:245-257` |
| 2 | **Existing submission wins.** If a submission exists (in_progress/submitted/evaluated), skip the taking-gates and go to resume or result. | `test.php:44-72` |
| 3 | **Gate order** (server enforces; client shows the mapped state): completed → "Test Stopped"; paused & no progress → "Test Paused"; not active or outside window → "Test is not available". | `test.php:218-276` |
| 4 | **Server clock is authoritative.** Countdown is display-only; every write is re-validated server-side against `effective_deadline = earliest(end_time, started_at + duration)`, 0 s grace. | `submit_answer.php:167-189`, gap-spec G4 |
| 5 | **Autosave debounce 2 s** after the last change, **dirty-tracking** (send only changed question ids), and an **overlap guard** (never two in-flight saves). | gap-spec G5/G6, `test.php:905-955` |
| 6 | **Retry:** timeout 10 s (AbortController equivalent), max 3 attempts, exponential backoff `1000·2^attempt`; `429` is retryable, other `4xx` (deadline) is **never** retried. | gap-spec G6 |
| 7 | **Save status keys off the response**, using the returned `saved_at` (server clock). States: `Saving… / Saved at HH:MM:SS / Save failed — retrying / Unable to save`. | gap-spec G7 |
| 8 | **Submit** sends all answers, `auto_save=false`. A lost race (`duplicate:true`) is **success-equivalent**. | `submit_answer.php:194-204, 258` |
| 9 | **Timer expiry auto-submits** with the same retry/backoff; never leave the UI permanently locked. | gap-spec G9 (currently OPEN on web) |
| 10 | **Question scoping:** the client may only send question ids returned by the paper; foreign ids → server `400`. | gap-spec G3 |

**Android anti-cheat mapping (divergence — mandatory):**

| Web | Android |
|---|---|
| `visibilitychange` → `document.hidden` | `onStop()`/`onPause()` of the test screen |
| switch to another tab/site | `onWindowFocusChanged(false)` + `onPause` |
| window blur / resize | **block** multi-window via `resizeableActivity="false"` |
| (none) | screen-off counts as a switch |
| (none) | `window FLAG_SECURE` blocks screenshots + screen recording |
| (none) | **must not** log a switch on config change / Activity recreation (`isChangingConfigurations`) |

Every counted event → `POST /tab-switches` with `switch_count: 1`, then show the 3-second warning banner (`logic.md:745`). Respect `tests.max_tab_switches`: when set and exceeded, show the server's/admin's configured consequence (web records only; no auto-submit is implemented — do **not** invent one).

**Input types:** MCQ = radio list keyed A/B/C/D (`options_json`) · coding = monospace editor · explanation = multiline text. Answers map to `{"selected":"A"}` / `{"code":"..."}` / `{"text":"..."}` (`submit_answer.php:239-244`).

**Process death:** on recreation, `GET /submissions/{id}` restores saved answers + remaining time; do **not** restart the test or re-POST start.

### D4. Result (immediate, post-submit)
| | |
|---|---|
| **Source** | `student/test.php` result branch (`:76-212`) |
| **Role** | student |
| **Entry** | automatic after submit; also "View Result" from lists |
| **APIs** | `GET /submissions/{id}/result` |
| **Three renderings** | **evaluated** → score `X / Y`, percent, PASS/FAIL (PASS = ≥ 40 %), submitted date, full per-question review (correct option tagged, your option tagged, marks, evaluator remarks; **unanswered listed too**). **pending_manual_review** → "Result Not Yet Announced / Under Evaluation" with the explanation that written/coding answers are being reviewed. **submitted-only** → "Test Submitted — results once evaluated". |
| **Edge** | DECIMAL formatting must match `test.php:88-93` (trim trailing zeros). |

### D5. Results (history)
| | |
|---|---|
| **Source** | `student/results.php` |
| **Role** | student |
| **Entry** | bottom nav |
| **APIs** | `GET /student/results` |
| **UI** | list of all submissions with score, percent, status chip (`evaluated` / `pending_manual_review` / `submitted` / `in_progress`), test title, date; tap → D4. |

### D6. Analytics
| | |
|---|---|
| **Source** | `student/analytics.php` |
| **Role** | student |
| **Entry** | bottom nav |
| **APIs** | `GET /student/analytics`, `GET /pci-records?student_id=`, `GET /charts/test/{id}` (per-test drill-down) |
| **UI** | trend of own scores over time · per-subject/type breakdown · **batch peer aggregates** (never individual peers) · PCI history |
| **Edge** | if charts return `503 python_unavailable`, show numeric tables instead — never a hard failure. |

### D7. Profile
| | |
|---|---|
| **Source** | `student/profile.php` |
| **Role** | student |
| **Entry** | top-bar avatar / settings |
| **APIs** | `GET /students/me` |
| **UI** | read-only identity (name, email, phone, gender, roll, year, semester, section) + hierarchy (college, course, batch) + submission stats |
| **Note** | the web profile is **view-only** (no password change) — do **not** add an edit form (YAGNI / parity). |

---

## E. Faculty screens

### E1. Faculty Dashboard
| | |
|---|---|
| **Source** | `faculty/dashboard.php` |
| **Role** | faculty |
| **Entry** | FacultyGraph root |
| **APIs** | `GET /faculty/overview?batch_id=&semester=&year=&test_id=` |
| **UI** | filter row (batch, semester, year, test) · stat boxes · per-test submission/eval summary · top performers |
| **Edge cases** | • Scoped to the faculty's **college** via the token's `college_id` — the server enforces this; the client never sends a college filter.<br>• **Ranking rule:** the web's `COALESCE(total_marks_obtained, 0)` ordering (`faculty/dashboard.php:134,189,215`) is a **known bug**; v1 must use the corrected rule — pending excluded, competition rank 1,1,3. Do not replicate the bug. |

---

## F. Admin screens

### F0. Admin gate table (sub-role permissions)

| Action class | Required `admins.role` | Screens affected |
|---|---|---|
| View everything | `admin` | all |
| CRUD colleges/courses/batches (add/edit/archive/restore) | `super_admin`, `platform_admin` | Colleges, Courses, Batches |
| Permanent delete (college) | `super_admin`, `platform_admin` | Colleges |
| Promote batch | `super_admin`, `platform_admin` | Batches |
| Tests / questions / grading / reports | `admin` (any) | TestBuilder, Studio, Management, Grading, Reports |
| Faculty control | `admin` (any) | FacultyControl |

This mirrors `promote_students.php:48-57` and `delete_college.php`'s role gate. The client hides/disables the action **and** the server re-checks (never rely on client gating alone).

### F1. Admin Dashboard
| | |
|---|---|
| **Source** | `admin/dashboard.php` |
| **Role** | admin |
| **Entry** | AdminGraph root |
| **APIs** | `GET /admin/stats` |
| **UI** | stat boxes (colleges, courses, batches, students, tests, submissions) · recent activity · `python_api_available` indicator |
| **Edge** | dark-mode toggle in the web (`dark` filter) is a **client** concern only. |

### F2. Colleges
| | |
|---|---|
| **Source** | `admin/colleges.php` |
| **Role** | admin (mutate: super/platform) |
| **Entry** | sidebar |
| **APIs** | `GET /colleges?status=`, `POST /colleges`, `PATCH /colleges/{id}`, `DELETE /colleges/{id}` (archive), `POST /colleges/{id}/restore`, `DELETE /colleges/{id}/permanent` |
| **Actions** | `add`, `edit`, `delete` (= archive), `restore`, permanent-delete (only on archived rows) |
| **UI** | filter (active/archived) · searchable table · row actions · add/edit modal · archive confirm · "permanently delete" only visible on archived rows |
| **Edge cases** | • **Archive ≠ delete.** `delete` archives (`status='archived'`); permanent delete is a separate, guarded action that requires the row to already be archived.<br>• Permanent delete cascade is destructive and ordered — show a typed-confirmation dialog listing what will be removed (students, tests, submissions, PCI…).<br>• `nick_name` and `college_code` are UNIQUE → `409`. |

### F3. College Create (4-step wizard)
| | |
|---|---|
| **Source** | `admin/college_create.php` |
| **Role** | admin |
| **Entry** | Colleges → "Add College (wizard)" |
| **APIs** | `GET /geo/cities` · `POST /colleges/wizard` |
| **Steps** | 1 identity+contact → 2 accreditation+location → 3 streams (`category UGC/PGC`) → 4 courses + batches (academic year, joining year/month, duration, ending year, batch nick name, section, semester order) |
| **Actions (web)** | `wizard_action` ∈ `save_draft, next, prev, create` — draft held in `$_SESSION['college_draft']` |
| **Android mapping** | hold the draft in the ViewModel ( Survives config change via `rememberSaveable` / SavedStateHandle). Only persist a server draft if cross-device resume is required. Submit once via `POST /colleges/wizard` (atomic bulk create). |
| **Edge cases** | • `batch_nick_name` is globally UNIQUE (`migration_college_wizard.sql`) → surface `409` on the specific field.<br>• `course_duration` and `ending_year` must be internally consistent with joining year + duration — validate client-side **and** server-side.<br>• `semester_order` should default to 1 for the first cohort; promote later depends on it being set. |

### F4. College Dashboard
| | |
|---|---|
| **Source** | `admin/college_dashboard.php` |
| **Role** | admin |
| **Entry** | Colleges → row tap "View" |
| **APIs** | `GET /colleges/{id}` |
| **UI** | college header · streams · wizard batches · faculty list · faculty login activity (from `faculty_login_log`: time, IP, resolved city/region/country, device) |

### F5. Courses
| | |
|---|---|
| **Source** | `admin/courses.php` |
| **Role** | admin (mutate: super/platform) |
| **APIs** | `GET /colleges`, `GET /courses?college_id=&active=`, `POST /courses`, `PATCH /courses/{id}`, `DELETE /courses/{id}`, `POST /courses/{id}/restore` |
| **Actions** | `add`, `edit`, `delete` (archive), `restore` |
| **Edge** | unique per college (`uk_college_course`) → `409`. `duration_years` drives the promote ceiling (`duration_years × 2` semesters). |

### F6. Batches
| | |
|---|---|
| **Source** | `admin/batches.php` |
| **Role** | admin (mutate: super/platform) |
| **APIs** | `GET /colleges`, `GET /courses?college_id=`, `GET /batches?course_id=&active=`, `POST /batches`, `PATCH /batches/{id}`, `DELETE /batches/{id}`, `POST /batches/{id}/restore`, `POST /batches/{id}/promote` |
| **Actions** | `add`, `edit`, `delete`, `restore`, **promote** |
| **Promote UI** | dry-run first (`preview:true`) showing from→to semester, student count, course, max semesters; then confirm. All three validation failures (no semester assigned / would exceed max / duplicate batch at target semester) are distinct server messages — render verbatim. |
| **Edge** | `students.batch_id` is `ON DELETE RESTRICT` — archiving is the safe path; hard-deleting a batch with students fails. |

### F7. Students
| | |
|---|---|
| **Source** | `admin/students.php` |
| **Role** | admin |
| **APIs** | `GET /colleges`, `GET /courses`, `GET /batches`, `GET /sections`, `GET /students?...`, `PATCH /students/{id}`, `DELETE /students/{id}`, `POST /guest-links`, `GET /guest-links` |
| **Actions** | `generate_guest`, `generate_qr`, `delete`, `update`; sorts `latest | modified | old` |
| **UI** | cascading college→course→batch→section filters · search · sort · paginated table · row actions · "Generate Guest Link / QR" modal |
| **Guest modal** | select hierarchy → optional test link → type (`guest`/`qr`) → create → show the URL with copy + a **locally rendered** QR (ZXing). Do **not** call `api.qrserver.com`. |
| **Edge** | student edit cannot collide on email; delete cascades that student's submissions/answers. |

### F8. Pending Verifications
| | |
|---|---|
| **Source** | `admin/pending_verifications.php` |
| **Role** | admin |
| **APIs** | `GET /unverified-students`, `DELETE /unverified-students/{id}`, `DELETE /unverified-students?all=1` |
| **Actions** | `delete`, `delete_all` |
| **Edge** | these rows are pre-OTP registrations; deleting one is irreversible and does **not** touch `students`. Confirm `delete_all` with a count. |

### F9. Question Library
| | |
|---|---|
| **Source** | `admin/question_library.php` |
| **Role** | admin |
| **APIs** | `GET /questions?search=&type=` |
| **UI** | search · type filter (`mcq/coding/explanation`) · read-only browse (the web has no create/edit here — it's a viewer). Do **not** add editing (parity). |

### F10. Test Builder
| | |
|---|---|
| **Source** | `admin/test_builder.php` |
| **Role** | admin |
| **APIs** | `GET /tests`, `POST /tests`, `PATCH /tests/{id}`, `DELETE /tests/{id}`, `POST /tests/{id}/publish`, `POST /tests/{id}/schedule`, `POST /tests/{id}/questions`, `PATCH /questions/{id}`, `DELETE /questions/{id}`, `PUT /tests/{id}/questions/order`, `POST /tests/{id}/questions/import`, `POST /submissions/{id}/extend-timer` |
| **Actions** | `save_test`, `add_question`, `delete_question`, `import_csv`, `publish_now`, `schedule_publish`, `reorder_question`, `extend_timer`, `adjust_end_time`, `delete_test` |
| **UI** | test list → open a test → question list (drag-reorder) → add/edit question sheet (type switch mcq/coding/explanation) · CSV import sheet · publish/schedule controls · "Active Submissions" panel with per-student timer extension (+5/+10/+15/+30) |
| **Edge cases** | • `total_marks` is recomputed from questions (or manually set).<br>• CSV import validation: header detection `/question/i`, ≥2 options, `correct_answer ∈ ABCD`, per-row warnings returned.<br>• `adjust_end_time` changes `tests.end_time`, which **tightens or loosens the effective deadline for everyone** — confirm dialog required.<br>• Multi-batch assignment writes `test_sections` rows; keep `tests.batch_id` as the primary. |

### F11. Assessment Studio
| | |
|---|---|
| **Source** | `admin/assessment_studio.php` |
| **Role** | admin |
| **APIs** | same tests/questions endpoints as F10 |
| **Actions** | `create_draft`, `update_draft`, `add_question`, `delete_question`, `reorder`, `reorder_question`, `import_csv`, `publish_now`, `schedule_publish`, `delete_draft` |
| **Behavior** | a draft-first authoring surface over the same `tests` resource with `status` acting as the draft/published discriminator (`drafts` filter). Share the question-editor and CSV-import components with F10 — do not duplicate them. |

### F12. Assessment Management
| | |
|---|---|
| **Source** | `admin/assessment_management.php` |
| **Role** | admin |
| **APIs** | `POST /tests/{id}/pause`, `/resume`, `/force-end`, `/publish`, `/schedule`, `/cancel-schedule`, `PATCH /tests/{id}`, `DELETE /tests/{id}`, `GET /submissions?test_id=` |
| **Actions** | `pause`, `resume`, `force_end`, `extend_time`, `delete`, `unpublish`, `publish_now`, `cancel_schedule`, `schedule_publish` |
| **Status chips** | `upcoming, scheduled, active, paused, completed, published` |
| **Edge** | **force_end auto-submits every in-progress submission** — this is destructive and irreversible; require typed confirmation showing the in-progress count. `pause` blocks new starts but lets in-progress students resume. |

### F13. Live Monitor
| | |
|---|---|
| **Source** | `admin/live_monitor.php` |
| **Role** | admin |
| **APIs** | `GET /tests/{id}/live` |
| **Behavior** | poll every 15 s (the web self-fetches `window.location.href` every 15 s). Per student: status, remaining time, switch count, last save, answers saved. Stop polling when the screen is backgrounded. |

### F14. Tab Switcher
| | |
|---|---|
| **Source** | `admin/tab_switcher.php` |
| **Role** | admin |
| **APIs** | `GET /tab-switches?test_id=&submission_id=`, `POST /submissions/{id}/extend-timer` |
| **UI** | test selector · per-student switch totals · expandable event list with millisecond timestamps (`DATETIME(3)`) · `timer_extend` events shown distinctly with `metadata.extended_by` |
| **Action** | `extend_timer` (+5/+10/+15/+30) |

### F15. Grading
| | |
|---|---|
| **Source** | `admin/grading.php` |
| **Role** | admin |
| **APIs** | `GET /submissions?test_id=&evaluation_status=`, `GET /submissions/{id}/answers`, `PATCH /submissions/{id}/grades` |
| **UI** | test → student → per-question card: prompt, student answer (code block / text / MCQ selected+correct), max marks, marks input, remarks input · Save (individual) · Save & finalize (bulk) |
| **Rules** | • awarded marks clamped to `q.marks` per type (`grading.php:14-26`).<br>• `ungraded` = count of non-MCQ answers with NULL marks; only when `ungraded == 0` does the submission become `evaluated` (`:31-38`).<br>• MCQ rows are already auto-graded at submit; the admin confirms rather than re-enters.<br>• Finalize sets `total_score = auto_score + manual_score` and triggers PCI refresh. |

### F16. Reports
| | |
|---|---|
| **Source** | `admin/reports.php` |
| **Role** | admin |
| **APIs** | `GET /tests/{id}/results`, `POST /tests/{id}/pci`, `GET /pci-records?test_id=&student_id=`, `GET /charts/test/{id}` |
| **UI (all students)** | bar (PCI per student) · doughnut (category averages) · stacked bar (MCQ/Coding/Explanation per student) · histogram (distribution) · ranked table with drill-down |
| **UI (individual)** | stat boxes (PCI, MCQ %, Coding %, Explanation %) · radar chart · back |
| **Rules** | • "Generate PCI" recomputes all evaluated submissions for the test.<br>• **Ranking must implement the corrected rule**: evaluated only, no `COALESCE(...,0)`, competition rank 1,1,3. The web currently prints row-position rank (`reports.php:641`) and coerces pending to 0 (`:41,55`) — **known bug, do not replicate** (gap-spec G10).<br>• Charts degrade to tables if Python is down. |

### F17. Notifications
| | |
|---|---|
| **Source** | `admin/notifications.php` |
| **Role** | admin |
| **APIs** | `GET /notifications`, `POST /notifications/read-all`, `POST /notifications/{id}/read` |
| **Actions** | `mark_all`, `mark_one`, `poll` |
| **Types** | `student_account`, `guest_link`, `qr`, `test`, `system` — each with its own color/icon mapping (`helpers.php:334-347`) |
| **Behavior** | a bell in the top bar with an unread dot; tapping a notification with a `link` deep-links into the relevant admin screen. Poll on foreground + interval. |

### F18. Faculty Control
| | |
|---|---|
| **Source** | `admin/faculty_control.php` |
| **Role** | admin |
| **APIs** | `GET /colleges`, `GET /faculty`, `POST /faculty`, `PATCH /faculty/{id}`, `DELETE /faculty/{id}` |
| **Actions** | `save_faculty`, `delete_faculty` |
| **UI** | per-college faculty list · add/edit sheet (email, name, password, assigned colleges multi-select, is_active) · delete with confirm |
| **Edge** | a faculty can serve **1..n** colleges via `faculty_colleges`; `faculty_email` is globally unique. No self-registration path exists. |

### F19. Failed Logins
| | |
|---|---|
| **Source** | `admin/failed_logins.php` |
| **Role** | admin |
| **APIs** | `GET /failed-logins?email=&type=&page=` |
| **Types** | `signup_unverified, wrong_password, invalid_email, expired_otp, wrong_otp, duplicate_email` |
| **UI** | filters + paginated audit table (email, student name, IP, type, reason, timestamp) |

### F20. Help
| | |
|---|---|
| **Source** | `admin/help.php` |
| **Role** | admin |
| **APIs** | `GET /admin/stats` (4 header counts only) |
| **Behavior** | static documentation content. Ship the copy as a bundled Composable/Markdown asset — no need to serve it from the API. |

### F21. Settings — **placeholder**
| | |
|---|---|
| **Source** | `admin/settings.php` ("Coming Soon") |
| **Behavior** | render the same placeholder. **Do not invent settings** (parity + YAGNI). |

### F22. Activity Logs — **placeholder**
| | |
|---|---|
| **Source** | `admin/activity_logs.php` ("Coming Soon") |
| **Behavior** | same placeholder. Note: `audit_log` rows **are** written by the permanent-delete cascade, but the web has no UI for them — do not expose one. |

---

## G. Pages that do not map 1:1 to a new destination screen

These explain why 40 PHP pages yield 36 destinations:

| PHP file | Mapping |
|---|---|
| `index.php` | pure role-based redirect → replaced by **Splash/Boot (C1)**, not a page of its own |
| `bgs.php` | high-concurrency variant of `login.php` with identical logic → **merged into Login (C2)** |
| `student/test-list.php` | the iframe target of the dashboard → **merged into My Tests / Test List (D2)** |
| `logout` | an action in every top bar, not a destination → **C8** |
| `admin/activity_logs.php` | "Coming Soon" placeholder → rendered as placeholder **F22** |
| `admin/settings.php` | "Coming Soon" placeholder → rendered as placeholder **F21** |
| `__error_probe_500.php` | dev-only 500 probe (hard-404 outside `php -S`); no client surface |

Net: **40 PHP pages → 36 Android destination screens.** Count: C = 7 destinations (C1–C7; C8 Logout is an action, C9 error views are shared states) · D = 6 destinations (D1–D3, D5–D7; D4 Result is a post-submit state of D3) · E = 1 · F = 22 (incl. 2 placeholders) → **7 + 6 + 1 + 22 = 36**.

---

## H. Shared UI components

Build these once and reuse — they are the highest-leverage pieces:

| Component | Used by | Notes |
|---|---|---|
| `CascadingHierarchyPicker` (college→course→batch→section) | Signup, Students, Batches, Guest modal | each level refills the next and clears downstream; disabled until parent chosen |
| `StatusBadge` | everywhere | map `status`/`evaluation_status` → color+label per `logic.md:19.2` and the admin chip sets |
| `DataTable` (paginated, sortable, striped) | Colleges, Courses, Batches, Students, FailedLogins, Notifications, Reports | matches the admin theme's data-dense tables (`logic.md:16.3`) |
| `ModalSheet` (form) | all add/edit flows | Fluent 2 form controls; field-level error from `error.details.field` |
| `ConfirmDialog` (destructive) | delete/archive/force-end/permanent-delete | typed confirmation for irreversible ops |
| `SaveStatusBar` | Test Taking | `Saving / Saved at HH:MM:SS / Retrying / Unable to save + Retry` (G7) |
| `QuestionNavigator` (dot grid) | Test Taking | answered/unanswered/current states |
| `TimerBar` | Test Taking | `HH:MM:SS`; warning < 5 min, danger < 1 min (`test.php:805-806`) |
| `ChartCanvas` (bar/doughnut/stacked/radar/histogram) | Reports, Analytics | with a **table fallback** when charts are unavailable |
| `NotificationBell` | all admin screens | unread count + dropdown panel |

### Design token mapping (Fluent 2 → Material 3)

Full table in [`android-logic.md` §8](android-logic.md#8-design-system-parity). Core palette: accent `#0078D4` → `primary` · green `#0B6A0B` → `tertiary`/success · red `#BC2F32` → `error` · yellow `#8A6D00` → `tertiary`/warning · orange `#C85A00` → coding-question accent · gray-5 `#F5F5F5` → `background` · gray-90 `#292827` → `onSurface`. Spacing 4 px grid → `8.dp` Material scale. Radius 2/4/8 → small/medium/large.
