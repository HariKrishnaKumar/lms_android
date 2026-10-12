# Android API Contract — `LMSv5` `/api/v1/*`

> **Purpose:** the complete REST contract an Android client (Jetpack Compose + Kotlin) calls to achieve 100% functional parity with the PHP web app.
> **Companion docs:** [`android-logic.md`](android-logic.md) (architecture, business rules, parity matrix) · [`android-screens.md`](android-screens.md) (per-screen behavior) · [`logic.md`](logic.md) (PHP source of truth)
> **Status:** design spec. None of these endpoints exist yet except the 8 legacy AJAX files listed in §10.

---

## Table of Contents

1. [Conventions](#1-conventions)
2. [Authentication & Token Layer](#2-authentication--token-layer)
3. [Endpoint Catalog](#3-endpoint-catalog)
4. [Auth Endpoints](#4-auth-endpoints)
5. [Hierarchy Endpoints (Colleges / Courses / Batches / Sections)](#5-hierarchy-endpoints)
6. [Students & Guest Links](#6-students--guest-links)
7. [Tests](#7-tests)
8. [Questions](#8-questions)
9. [Submissions, Answers, Grading, Anti-Cheat](#9-submissions-answers-grading-anti-cheat)
10. [Analytics, Reports, PCI](#10-analytics-reports-pci)
11. [Notifications, Audit, Faculty, Misc](#11-notifications-audit-faculty-misc)
12. [Legacy Endpoints (the 8 existing files)](#12-legacy-endpoints)

---

## 1. Conventions

### 1.1 Base URL & transport

| Item | Value |
|---|---|
| Dev base | `http://10.0.2.2:8000` (Android emulator → host `localhost:8000`) |
| Prod base | configurable BuildConfig field, e.g. `https://<host>/test-platform` |
| Prefix | all v1 routes live under `/api/v1/` |
| Content-Type | `application/json; charset=utf-8` (request **and** response) |
| Exception | CSV import uses `multipart/form-data`; file upload endpoints noted inline |
| Cleartext | dev only — AndroidManifest `usesCleartextTraffic` gated on a debug build-type |

> The PHP dev server is started with `php -S localhost:8000 router.php`. `router.php` maps `/api/*` → `src/php/api/*`. The new `/api/v1/*` routes are added to `router.php` (or a single `src/php/api/v1/index.php` front controller). See [`android-logic.md` §3](android-logic.md#3-architecture).

### 1.2 Response envelope

Every v1 response uses a **uniform envelope**. This is the single biggest divergence from the legacy 8 endpoints, which return bare arrays / ad-hoc `{success,message}` shapes.

**Success**
```json
{
  "data": { ... },          // payload — object or array
  "meta": {                 // optional
    "server_time": "2026-10-12T10:30:00+05:30",
    "page": 1, "per_page": 50, "total": 120
  }
}
```

**Error**
```json
{
  "error": {
    "code": "deadline_exceeded",         // machine-readable, snake_case
    "message": "Test time is over. Your answers could not be saved.",  // human-readable, safe to show
    "details": { "field": "otp" }        // optional, for form validation
  }
}
```

Rules:
- `error.message` is **always safe to render to the user** — no raw JSON, no stack traces, no SQL (parity with the web's designed error pages: `includes/error_pages.php`, `requireCsrf()` JSON branch at `session.php:69-73`).
- `error.code` is what the Android client switches on. Canonical codes are listed in §1.5.
- On success, `data` is never `null` unless the endpoint documents an empty-collection case; empty lists return `"data": []`.

### 1.3 HTTP status codes

| Code | Meaning | Android handling |
|---|---|---|
| 200 | OK | — |
| 201 | Created | resource created; body has new `id` |
| 400 | Bad request / validation | show `error.message`; do **not** retry |
| 401 | No / expired / revoked token | clear token, navigate to login |
| 403 | Authenticated but forbidden (wrong role, deadline, CSRF-legacy) | show `error.message`; do **not** retry |
| 404 | Not found | show empty/not-found state |
| 409 | Conflict (duplicate email, already submitted) | show `error.message` |
| 413 | Payload too large (CSV > limit) | show message |
| 422 | Unprocessable (semantically invalid) | show `error.message` |
| 429 | Rate limited | back off per `Retry-After`; surface "try again shortly" |
| 500 | Server error | generic retryable error state |
| 503 | Python analytics service down (only on proxied analytics) | degrade — charts unavailable, rest of app works |

### 1.4 Canonical error codes

| Code | HTTP | Where used |
|---|---|---|
| `validation_failed` | 400 | any field validation |
| `invalid_credentials` | 401 | login / faculty-login |
| `account_locked` | 429 | brute-force throttle hit |
| `not_verified` | 403 | student login when account is in `unverified_students` |
| `authentication_required` | 401 | missing/bad token |
| `forbidden` | 403 | wrong role |
| `not_found` | 404 | resource missing |
| `already_submitted` | 409 | final submit race lost |
| `deadline_exceeded` | 403 | G4 deadline (submit **and** autosave) |
| `rate_limited` | 429 | autosave throttle (G5) |
| `unknown_question_for_test` | 400 | G3 question scoping |
| `csrf_invalid` | 403 | only if a v1 route ever falls back to session+CSRF |
| `duplicate_email` | 409 | signup |
| `otp_expired` | 400 | OTP verify |
| `otp_invalid` | 400 | OTP verify |
| `token_expired` | 401 | guest link / password-reset OTP |
| `batch_not_promotable` | 400 | promote (semester ceiling / duplicate) |
| `not_archived` | 400 | hard-delete before archive |
| `python_unavailable` | 503 | PCI/chart proxy |

### 1.5 Pagination, filtering, sorting

List endpoints accept:
- `page` (1-based, default 1), `per_page` (default 50, max 200)
- `sort` = field name, `order` = `asc|desc`
- resource-specific filters as query params (documented per endpoint)

`meta` always returns `page`, `per_page`, `total` for list endpoints.

### 1.6 Timestamps

All timestamps are **ISO-8601 with offset**, e.g. `2026-10-12T10:30:00+05:30` (server runs `Asia/Kolkata`, `config/db.php`). The client must never assume UTC. The server's clock is authoritative for deadlines and `saved_at` (see §9.4).

### 1.7 Decimal / score formatting

`DECIMAL(10,2)` DB columns arrive as JSON **strings** (`"4.00"`). The Android layer must parse to `BigDecimal` and format like the web does (`student/test.php:88-93`): trim trailing zeros → `4`, `4.5`, `16`; never show `4.00`.

### 1.8 Idempotency

- Answer upserts are idempotent per `(submission_id, question_id)` via `UNIQUE KEY` + `ON DUPLICATE KEY UPDATE` (`schema.sql:188`, `submit_answer.php:224-230`).
- Final submit is a **conditional** `UPDATE ... WHERE status='in_progress'`; a duplicate caller gets `already_submitted` / `duplicate:true` (G13). The client must treat a lost race as **success-equivalent**, not an error.

---

## 2. Authentication & Token Layer

### 2.1 Why a token layer exists

The web uses PHP session cookies + a CSRF token stored in `$_SESSION` (`session.php:8-21`, `447-452` of `logic.md`). Android cannot hold a browser session naturally, and session IDs regenerate every 30 min (`session.php:13-16`). Per the locked decision, v1 introduces a **bearer-token** layer so that:

- CSRF becomes unnecessary for `/v1` routes (bearer tokens are not auto-attached by a browser, so there is no ambient-authority CSRF risk). Legacy routes keep CSRF.
- The existing PHP auth functions (`adminLogin`, `studentLogin`, `facultyLogin`, `guestLogin`, OTP/reset in `includes/auth.php`) are **reused verbatim** — only the session-write step is replaced by token issuance.
- The web app is **untouched**; this is purely additive.

### 2.2 New table: `api_tokens`

```sql
CREATE TABLE api_tokens (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_type     ENUM('admin','student','faculty','guest') NOT NULL,
    user_id       INT NOT NULL,              -- admins.id / students.id / faculty.id / guest_entries.id
    access_hash   CHAR(64) NOT NULL UNIQUE,  -- sha256(access_token)
    refresh_hash  CHAR(64) NOT NULL UNIQUE,  -- sha256(refresh_token)
    role          VARCHAR(50) NOT NULL,      -- mirrors session role + sub-role (admin: super_admin|platform_admin|admin)
    college_id    INT NULL,                  -- faculty: home college; guest: batch's college
    batch_id      INT NULL,                  -- student/guest scoping
    expires_at    DATETIME NOT NULL,         -- access token expiry
    refresh_expires_at DATETIME NOT NULL,
    last_used_at  DATETIME NULL,
    revoked_at    DATETIME NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_at_user (user_type, user_id),
    INDEX idx_at_expiry (expires_at)
) ENGINE=InnoDB;
```

Only **hashes** are stored. Plaintext tokens are returned exactly once, at issue time.

### 2.3 Token lifecycle

| Event | Behavior |
|---|---|
| Login success | issue `access_token` (15 min TTL) + `refresh_token` (30 day TTL); return both once |
| Every `/v1` request | `Authorization: Bearer <access_token>`; server sha256-hashes and looks up `access_hash`; updates `last_used_at` (throttled to ≥60s writes) |
| Access token expired | `401 {code:"authentication_required"}` → client silently calls `POST /auth/refresh` |
| Refresh | `POST /auth/refresh` with `refresh_token` → **rotates** both tokens (old refresh marked used); returns new pair |
| Refresh token expired/revoked | `401` → client clears Keystore and routes to login |
| Logout | `POST /auth/logout` revokes the current token row (`revoked_at=NOW()`) |
| Password change (reset) | revoke **all** tokens for that `user_type,user_id` |

### 2.4 Client-side storage (Android)

- `access_token` + `refresh_token` in **EncryptedSharedPreferences** / Android Keystore-backed storage — never SharedPreferences plaintext, never logged.
- On process death, restore tokens; if access expired → silent refresh before first API call.
- `role` cached locally to route the nav graph without a network round-trip (revalidated by `GET /auth/session` on cold start).

### 2.5 Role model (mirrors web)

| Role | Session equivalent | Sub-role / scoping | Guard |
|---|---|---|---|
| `admin` | `$_SESSION['role']='admin'` + `admin_id` | `admins.role` ∈ {`super_admin`,`platform_admin`,`admin`} | v1 admin routes require `role=admin` |
| `student` | `$_SESSION['role']='student'` + `student_id` | `batch_id` from token | v1 student routes require `role=student` |
| `faculty` | `$_SESSION['role']='faculty'` + `faculty_id` | `college_id` from token | v1 faculty routes require `role=faculty` |
| `guest` | `$_SESSION['role']='guest'` + `guest_token`/`guest_entry_id` | `batch_id`, optional `test_id` | guest routes require `role=guest` |

`super_admin` / `platform_admin` gating (mirror of `promote_students.php:48-57`, `delete_college.php` role gate): destructive hierarchy ops (promote, permanent delete, archive of colleges/courses/batches) require `role ∈ {super_admin, platform_admin}`; plain `admin` gets `403 forbidden`.

### 2.6 Rate limits (parity with web)

| Limit | Value | Source |
|---|---|---|
| Login brute-force | 5 failed attempts / 900 s per email → `account_locked` | `auth.php:30-33`, `89-109` |
| Autosave / submit | 60 requests / 60 s per `sub:<id>` **and** per token bucket → `429 rate_limited` | `submit_answer.php:28-29`, gap-spec §6.1 |
| OTP resend | reuse login-throttle pattern; recommend 3 / 600 s per email | new, mirrors `auth.php:116` |

Brute-force check **excludes** `attempt_type='invalid_email'` so probing a student form with an admin email can never lock the admin (`auth.php:92-99`, `686-690`).

### 2.7 What v1 does NOT do

- Does **not** return `csrf_token` to Android (bearer is immune to CSRF).
- Does **not** expose `password_hash`, `otp_hash`, `token` (guest), or any secret in any response.
- Does **not** accept `student_id` from the client for authorization-sensitive reads/writes — identity always derives from the token (G1 rule, gap-spec §5 G1.4).

---

## 3. Endpoint Catalog

Legend: **Auth** = required token role · **NEW** = net-new v1 route · **LEGACY** = reimplements one of the 8 existing files under a consistent envelope.

### 3.1 Summary table

| # | Method | Path | Auth | Kind |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/login` | — | NEW |
| 2 | POST | `/api/v1/auth/faculty-login` | — | NEW |
| 3 | POST | `/api/v1/auth/register` | — | NEW |
| 4 | POST | `/api/v1/auth/otp/verify` | — | NEW |
| 5 | POST | `/api/v1/auth/otp/resend` | — | NEW |
| 6 | POST | `/api/v1/auth/password/forgot` | — | NEW |
| 7 | POST | `/api/v1/auth/password/verify-otp` | — | NEW |
| 8 | POST | `/api/v1/auth/password/reset` | — | NEW |
| 9 | POST | `/api/v1/auth/refresh` | refresh token | NEW |
| 10 | POST | `/api/v1/auth/logout` | any | NEW |
| 11 | GET | `/api/v1/auth/session` | any | NEW |
| 12 | POST | `/api/v1/guest/session` | — | NEW |
| 13 | GET | `/api/v1/colleges` | admin/faculty/student | NEW |
| 14 | GET | `/api/v1/colleges/{id}` | admin | NEW |
| 15 | POST | `/api/v1/colleges` | admin (super/platform) | NEW |
| 16 | PATCH | `/api/v1/colleges/{id}` | admin (super/platform) | NEW |
| 17 | DELETE | `/api/v1/colleges/{id}` | admin (super/platform) | NEW (archive) |
| 18 | POST | `/api/v1/colleges/{id}/restore` | admin (super/platform) | NEW |
| 19 | DELETE | `/api/v1/colleges/{id}/permanent` | admin (super/platform) | LEGACY `delete_college` |
| 20 | POST | `/api/v1/colleges/wizard` | admin | NEW |
| 21 | GET | `/api/v1/courses` | admin/faculty/student | LEGACY `get_courses` |
| 22 | GET | `/api/v1/courses/{id}` | admin | LEGACY `get_course_college` |
| 23 | POST | `/api/v1/courses` | admin (super/platform) | NEW |
| 24 | PATCH | `/api/v1/courses/{id}` | admin (super/platform) | NEW |
| 25 | DELETE | `/api/v1/courses/{id}` | admin (super/platform) | NEW (archive) |
| 26 | POST | `/api/v1/courses/{id}/restore` | admin (super/platform) | NEW |
| 27 | GET | `/api/v1/batches` | admin/faculty/student | LEGACY `get_batches` |
| 28 | POST | `/api/v1/batches` | admin (super/platform) | NEW |
| 29 | PATCH | `/api/v1/batches/{id}` | admin (super/platform) | NEW |
| 30 | DELETE | `/api/v1/batches/{id}` | admin (super/platform) | NEW (archive) |
| 31 | POST | `/api/v1/batches/{id}/restore` | admin (super/platform) | NEW |
| 32 | POST | `/api/v1/batches/{id}/promote` | admin (super/platform) | LEGACY `promote_students` |
| 33 | GET | `/api/v1/sections` | admin/faculty/student | LEGACY `get_sections` |
| 34 | GET | `/api/v1/students` | admin | NEW |
| 35 | GET | `/api/v1/students/me` | student | NEW |
| 36 | PATCH | `/api/v1/students/{id}` | admin | NEW |
| 37 | DELETE | `/api/v1/students/{id}` | admin | NEW |
| 38 | GET | `/api/v1/unverified-students` | admin | NEW |
| 39 | DELETE | `/api/v1/unverified-students/{id}` | admin | NEW |
| 40 | DELETE | `/api/v1/unverified-students` | admin | NEW (`?all=1`) |
| 41 | POST | `/api/v1/guest-links` | admin | NEW |
| 42 | GET | `/api/v1/guest-links` | admin | NEW |
| 43 | GET | `/api/v1/tests` | student/admin/faculty | NEW |
| 44 | GET | `/api/v1/tests/{id}` | student/admin/faculty/guest | NEW |
| 45 | POST | `/api/v1/tests` | admin | NEW |
| 46 | PATCH | `/api/v1/tests/{id}` | admin | NEW |
| 47 | DELETE | `/api/v1/tests/{id}` | admin | NEW |
| 48 | POST | `/api/v1/tests/{id}/publish` | admin | NEW |
| 49 | POST | `/api/v1/tests/{id}/schedule` | admin | NEW |
| 50 | POST | `/api/v1/tests/{id}/cancel-schedule` | admin | NEW |
| 51 | POST | `/api/v1/tests/{id}/pause` | admin | NEW |
| 52 | POST | `/api/v1/tests/{id}/resume` | admin | NEW |
| 53 | POST | `/api/v1/tests/{id}/force-end` | admin | NEW |
| 54 | GET | `/api/v1/tests/{id}/live` | admin | NEW |
| 55 | GET | `/api/v1/tests/{id}/results` | admin/faculty | NEW |
| 56 | POST | `/api/v1/tests/{id}/pci` | admin | NEW |
| 57 | GET | `/api/v1/tests/{id}/questions` | admin/student/guest | NEW |
| 58 | GET | `/api/v1/questions` | admin | NEW |
| 59 | POST | `/api/v1/tests/{id}/questions` | admin | NEW |
| 60 | PATCH | `/api/v1/questions/{id}` | admin | NEW |
| 61 | DELETE | `/api/v1/questions/{id}` | admin | NEW |
| 62 | PUT | `/api/v1/tests/{id}/questions/order` | admin | NEW |
| 63 | POST | `/api/v1/tests/{id}/questions/import` | admin | NEW (multipart CSV) |
| 64 | POST | `/api/v1/submissions` | student | NEW **(start)** |
| 65 | GET | `/api/v1/submissions/{id}` | student/admin | NEW |
| 66 | GET | `/api/v1/submissions/{id}/result` | student | NEW |
| 67 | POST | `/api/v1/submissions/{id}/answers` | student/guest | LEGACY `submit_answer` (autosave) |
| 68 | POST | `/api/v1/submissions/{id}/submit` | student/guest | LEGACY `submit_answer` (final) |
| 69 | GET | `/api/v1/submissions/{id}/answers` | admin | NEW (grading view) |
| 70 | PATCH | `/api/v1/submissions/{id}/grades` | admin | NEW (save grades) |
| 71 | POST | `/api/v1/submissions/{id}/extend-timer` | admin | NEW |
| 72 | GET | `/api/v1/submissions` | admin | NEW (grading/monitor list) |
| 73 | POST | `/api/v1/tab-switches` | student/guest | LEGACY `tab_switch` |
| 74 | GET | `/api/v1/tab-switches` | admin | NEW |
| 75 | GET | `/api/v1/admin/stats` | admin | NEW |
| 76 | GET | `/api/v1/faculty/overview` | faculty | NEW |
| 77 | GET | `/api/v1/student/results` | student | NEW |
| 78 | GET | `/api/v1/student/analytics` | student | NEW |
| 79 | GET | `/api/v1/pci-records` | admin/faculty/student | NEW |
| 80 | GET | `/api/v1/charts/test/{id}` | admin/faculty/student | NEW (Python proxy) |
| 81 | GET | `/api/v1/notifications` | admin | NEW |
| 82 | POST | `/api/v1/notifications/read-all` | admin | NEW |
| 83 | POST | `/api/v1/notifications/{id}/read` | admin | NEW |
| 84 | GET | `/api/v1/failed-logins` | admin | NEW |
| 85 | GET | `/api/v1/faculty` | admin | NEW |
| 86 | POST | `/api/v1/faculty` | admin | NEW |
| 87 | PATCH | `/api/v1/faculty/{id}` | admin | NEW |
| 88 | DELETE | `/api/v1/faculty/{id}` | admin | NEW |
| 89 | GET | `/api/v1/geo/cities` | — (faculty-login) | NEW |

**Totals:** 89 contract entries · 8 legacy reimplementations · 81 net-new · covers 40 screens (4 screens need no endpoint: `index`, `admin/activity_logs`, `admin/settings`, `__error_probe_500`).

### 3.2 Resource groups → screens

| Group | Endpoints | Screens served |
|---|---|---|
| auth | 1–12 | login, bgs, faculty-login, signup, verify-otp, forgot-password, guest, index, logout |
| colleges | 13–20 | colleges, college_create, college_dashboard, signup (cascade), students (cascade) |
| courses | 21–26 | courses, batches (cascade), students (cascade), signup (cascade) |
| batches | 27–33 | batches, students (cascade), signup (cascade), sections |
| students | 34–42 | students, pending_verifications, profile, signup result |
| tests | 43–57 | test_builder, assessment_studio, assessment_management, live_monitor, dashboard, my_tests, test-list, test, reports |
| questions | 57–63 | test_builder, assessment_studio, question_library, grading, test |
| submissions | 64–74 | test, grading, tab_switcher, live_monitor, results |
| analytics | 75–80 | dashboard, faculty/dashboard, reports, student/analytics, student/results |
| misc | 81–89 | notifications, failed_logins, faculty_control, faculty-login |

---

## 4. Auth Endpoints

### 4.1 `POST /api/v1/auth/login`

Admin **or** student login (mirror of `login.php` + `auth.php:113-146, 295-361`). `bgs.php` uses the same contract.

**Request**
```json
{ "email": "a@b.com", "password": "secret", "role": "student" }
```
`role` is a **hint** (`student|admin`) matching the web's role selector; the server still resolves the account the same way and rejects mismatches.

**Logic (parity):**
1. `isBruteForceLocked(email)` → `429 account_locked` (`auth.php:117-119`).
2. For `role=admin`: `SELECT ... FROM admins WHERE email=?` + `password_verify` (`auth.php:121-128`).
3. For `role=student`: single `UNION ALL` across `students` + `unverified_students` (`auth.php:306-318`).
   - not found → `401 invalid_credentials` (+ `failed_login_log` `invalid_email`).
   - wrong password → `401 invalid_credentials` (+ `wrong_password`).
   - found but `account_status='unverified'` → `403 not_verified` + `data.student_id` (client routes to OTP screen, `auth.php:333-346`).
4. On success: issue token pair (§2.3) instead of `session_regenerate_id`.

**Success 200**
```json
{
  "data": {
    "token": "<access_token>",
    "refresh_token": "<refresh_token>",
    "expires_in": 900,
    "role": "student",
    "user": { "id": 7, "name": "A B", "email": "a@b.com", "batch_id": 3 }
  }
}
```
Admin `user` adds `"admin_role": "super_admin"`.

**Errors:** `401 invalid_credentials` · `403 not_verified` (+`data.student_id`) · `429 account_locked` · `400 validation_failed`

---

### 4.2 `POST /api/v1/auth/faculty-login`

Mirror of `auth.php:656-718` (+ geo capture from `faculty-login.php`).

**Request**
```json
{ "college_id": 2, "email": "f@x.com", "password": "pw",
  "geo": { "latitude": 12.97, "longitude": 77.59, "accuracy_m": 20 } }
```

**Logic (parity):**
1. `college_id` must be a positive integer else `400 "Please select your college."` (`auth.php:659-662`).
2. Brute-force lock → `429`.
3. Single query joining `faculty` × `faculty_colleges` × `colleges` on the **posted** college (`auth.php:674-683`) — `faculty.college_id` is only the "home" college.
4. No row → `401 invalid_credentials` + log `invalid_email` (`auth.php:686-691`).
5. Wrong password → `401` + `wrong_password`.
6. `is_active != 1` → `403 "This faculty account is deactivated..."` (`auth.php:700-702`).
7. Success → write `faculty_login_log` (incl. optional geo → `latitude/longitude/accuracy_m`, resolved `city/region/country` via `geo_resolver.php`) and issue tokens.

**Success 200:** same envelope as §4.1 with `"role":"faculty"` and `user.faculty_college_id`.

---

### 4.3 `POST /api/v1/auth/register`

Mirror of `studentRegister()` (`auth.php:367-462`). Creates an **unverified** row and emails an OTP.

**Request**
```json
{
  "name": "A B", "email": "a@b.com", "phone": "9876543210",
  "gender": "male", "college_id": 1, "course_id": 4, "batch_id": 9,
  "section": "A", "roll_number": "21CS001", "year_of_joining": 2021,
  "password": "pw"
}
```

**Logic (parity):**
1. Duplicate in `students` → `409 duplicate_email` (`auth.php:371-378`).
2. Duplicate in `unverified_students` → `409` with "check your email for the OTP" (`:380-388`).
3. Validate `batch ∈ course ∈ college` in one join (`:391-396`); specific errors when college has no courses / course has no batches / invalid batch (`:399-414`).
4. Insert into `unverified_students` with bcrypt hash (cost 10) (`:427-442`).
5. `generateStudentOtp()` — 6-digit OTP, bcrypt cost 8, 10-min expiry, email via SMTP (`:154-183`).

**Success 201**
```json
{ "data": { "student_id": 55, "otp_sent": true } }
```
`otp_dev` is returned **only** when `MAIL_DEV_MODE=true` (never in prod). Client navigates to OTP verify with `student_id`.

**Errors:** `409 duplicate_email` · `400 validation_failed` (invalid hierarchy) · `500` (DB/OTP failure; also surfaces as `notifyAdmin` `student_account`)

---

### 4.4 `POST /api/v1/auth/otp/verify`

Mirror of `verifyStudentOtp()` (`auth.php:188-262`).

**Request:** `{ "student_id": 55, "otp": "123456" }`

**Logic (parity):**
1. Row not found → `400 "Student not found or already verified."`
2. `otp_hash` empty → `400 "No OTP requested..."` (+ log `signup_unverified` + admin notify)
3. `otp_expires_at < now` → `400 otp_expired` (+ log `expired_otp` + admin notify → link to pending verifications)
4. `password_verify` fails → `400 otp_invalid` (+ log `wrong_otp` + admin notify)
5. Success → **one transaction**: `INSERT INTO students (...)` then `DELETE FROM unverified_students` (`:225-251`). New student gets its own `id`.

**Success 200**
```json
{ "data": { "student_id": 7, "token": "...", "refresh_token": "..." } }
```
Auto-login on success (parity with the web's post-verify redirect, `logic.md:492`).

---

### 4.5 `POST /api/v1/auth/otp/resend`

Mirror of `resendStudentOtp()` (`auth.php:267-278`). **Request:** `{ "student_id": 55 }` · regenerates OTP (new 10-min window) and re-sends. Rate-limited per §2.6.

---

### 4.6–4.8 Password reset (OTP-based)

Mirror of `auth.php:470-562`. **Note:** the web's reset is student-only (looks up `students` table).

| Endpoint | Request | Behavior |
|---|---|---|
| `POST /auth/password/forgot` | `{email}` | look up `students`; **always** return success (no email enumeration, `:478-481`); invalidate prior unused OTPs (`:489`); insert hashed OTP (10 min); email |
| `POST /auth/password/verify-otp` | `{email, otp}` | latest unused token; expiry → `otp_expired`; verify → mark used; issue a **short-lived reset grant** (stateless, not a session) so step 3 is stateless on Android |
| `POST /auth/password/reset` | `{reset_grant, new_password}` | bcrypt the new hash, `UPDATE students SET password_hash`, revoke all tokens for that student |

> **Divergence note:** the web stores `reset_email`/`reset_otp_verified` in `$_SESSION` (`:538-539`). Android cannot rely on that, so v1 returns an opaque `reset_grant` (HMAC over email + expiry) instead. This is an intentional, documented divergence — see [`android-logic.md` §4.4](android-logic.md#44-intentional-divergences).

---

### 4.9 `POST /api/v1/auth/refresh`

**Request:** `{ "refresh_token": "..." }` · **Success:** new access+refresh pair (old refresh rotated). **Errors:** `401 authentication_required` (expired/revoked/unknown).

### 4.10 `POST /api/v1/auth/logout`

Revokes the current token row. Idempotent (200 even if already revoked).

### 4.11 `GET /api/v1/auth/session`

Cold-start bootstrap. Returns `{ role, admin_role?, user{...}, college_id?, batch_id? }` for the presented token so the app can route the nav graph. `401` → login.

### 4.12 `POST /api/v1/guest/session`

Mirror of `guestLogin()` (`auth.php:614-634`).

**Request:** `{ "token": "<64-char guest token>" }`

**Logic (parity):**
1. `SELECT * FROM guest_entries WHERE token=? AND status='pending' AND expires_at > NOW()` (`:616`) — miss → `400 "Invalid or expired link."` + `notifyAdmin('guest_link', ...)`.
2. Issue a **guest-scoped** token carrying `guest_entry_id`, `batch_id`, optional `test_id` (parity with the session vars set at `:627-631`).

**Success 200**
```json
{ "data": { "token": "...", "refresh_token": "...", "role": "guest",
            "guest_entry_id": 12, "batch_id": 3, "test_id": 41 } }
```

> The guest token **must** be bound to `guest_entries.id` so `POST /submissions/{id}/answers` can enforce the G2 ownership rule (gap-spec §5 G2). The submission id on the guest path is the string `guest_<entryId>` (`submit_answer.php:114-115`).

---

## 5. Hierarchy Endpoints

### 5.1 `GET /api/v1/colleges`

**Auth:** admin (full list) / faculty (their colleges) / student (their own college, for signup cascade pre-auth it is public — see note).
**Query:** `status=active|archived` (default `active`) · `has_faculty=1` (for faculty-login dropdown) · `q` (name search) · pagination.

**Data shape (per college)** — extended registration fields from `migration_college_wizard.sql`:
```json
{ "id": 1, "college_code": "COL000123", "nick_name": "TechU", "name": "...",
  "address": "...", "established_year": 1960, "website": "...", "email": "...",
  "phone": "...", "country": "India", "state": "KA", "district": "BBMP",
  "city": "Bengaluru", "pincode": "560001", "logo": null, "description": "...",
  "recognized_university": "...", "affiliated_university": "...",
  "autonomous": "Yes", "accreditation_naac": 1, "naac_grade": "A",
  "accreditation_nba": 0, "accreditation_aicte": 1, "accreditation_ugc": 1,
  "status": "active", "course_count": 4, "batch_count": 12, "student_count": 340 }
```

> **Pre-auth usage:** `signup.php` needs the active-college list **before** login. v1 exposes `GET /api/v1/colleges` (and courses/batches/sections cascades) with **no token** but `status='active'` enforced and a narrow field projection (id, name, nick_name only). Documented as an intentional, minimal public surface.

### 5.2 `GET /api/v1/colleges/{id}`

Admin-only. Returns the college row **plus** `streams[]`, `wizard_batches[]` (from `college_batches`), `faculty[]`, and `login_activity[]` (from `faculty_login_log`) — this is the `college_dashboard.php` payload.

### 5.3 `POST /api/v1/colleges`

Create a single college (the simple `colleges.php` "Add" flow). Auto-generates `college_code` (`COL######`). Requires unique `nick_name` → `409` on conflict.

### 5.4 `PATCH /api/v1/colleges/{id}`

Partial update of the extended fields. Same validation as create.

### 5.5 `DELETE /api/v1/colleges/{id}` → **archive (soft delete)**

Sets `status='archived'` (`migration_college_wizard.sql`, `colleges.status` ENUM `active|archived`). This is the safe "delete" in the UI; the destructive hard delete is separate (§5.6).

### 5.6 `DELETE /api/v1/colleges/{id}/permanent`

Reimplements `api/delete_college.php` (312 lines) under the v1 envelope. **Safety contract (must be preserved verbatim):**
- admin session + `super_admin|platform_admin` only (`401` / `403`)
- college **must already be `archived`** else `400 not_archived` (double-guarded inside the transaction under `FOR UPDATE`)
- single InnoDB transaction, cascade in **dependency order** (children→parents) so no FK aborts midway: `student_answers` → `tab_switch_logs` → `pci_records` → `submissions` → `guest_entries` → `questions` → `tests` → `unverified_students` → `students` → `college_batches` → `batches` → `college_streams` → `courses` → `colleges`
- audit row in `audit_log` with full breakdown (best-effort, inside the transaction)
- response: `{ deleted: { college_id, name, rows_removed } }`

### 5.7 `POST /api/v1/colleges/wizard`

Reimplements the 4-step `college_create.php` wizard (`wizard_action` = `save_draft|next|prev|create`, draft held in `$_SESSION['college_draft']`) as **one atomic bulk-create**. This intentionally **removes** the server-side wizard session — Android holds the draft client-side (or in a short-lived `draft_id` if a resume-across-sessions is wanted).

**Request**
```json
{
  "college": { ...full registration fields... },
  "streams": [ { "stream_name": "Bachelor of Technology (BTech)", "category": "UGC" } ],
  "courses": [ { "stream_index": 0, "name": "CSE", "duration_years": 4 } ],
  "batches": [ { "course_index": 0, "name": "2024-2028", "section": "A",
                 "academic_year": "2024-2025", "joining_year": 2024,
                 "joining_month": 8, "course_duration": 4, "ending_year": 2028,
                 "batch_nick_name": "CSE-2024-A", "semester_order": 1 } ]
}
```
Server validates every FK link, then creates in one transaction. Response: `{ college_id, streams:[...ids], courses:[...ids], batches:[...ids] }`.

> **Draft resume (optional):** `POST /api/v1/colleges/wizard/draft` persists a `college_drafts` row keyed by admin; `GET .../draft/{id}` restores. Only add if multi-device resume is required — otherwise client-side state suffices (YAGNI).

### 5.8 `GET /api/v1/courses` (LEGACY `get_courses.php`)

**Query:** `college_id` (required), `active=1` to exclude archived. Returns `[{id, name, duration_years}]` — `duration_years` feeds the N×2 semester ceiling the client clamps (`get_courses.php:22-25`).

### 5.9 `GET /api/v1/courses/{id}` (LEGACY `get_course_college.php`)

Returns `{id, college_id, name}` for batch-edit cascade. `404` if missing.

### 5.10–5.13 Courses CRUD

`POST /courses` (name + `college_id` + `duration_years`) · `PATCH /courses/{id}` · `DELETE /courses/{id}` (archive → `status='archived'`, `migration_softdelete.sql`) · `POST /courses/{id}/restore`. Unique per college (`uk_college_course`) → `409`.

### 5.14 `GET /api/v1/batches` (LEGACY `get_batches.php`)

**Query:** `course_id` (required), `active=1`. Returns `[{id, name, section, display_name}]` where `display_name = "name - Section X"` (`get_batches.php:22-26`).

### 5.15–5.18 Batches CRUD + restore

Same pattern as courses, plus `section` and `semester_order` (`migration_batch_sections.sql`, `migration_promote.sql`).

### 5.19 `POST /api/v1/batches/{id}/promote` (LEGACY `promote_students.php`)

In-place promotion: advances `batches.semester_order` and every `students.semester` in the batch; batch is **not** renamed, **no** new batch created, **no** students move (`promote_students.php` header). Super/platform admin only.

**Request:** `{ "batch_id": 9, "preview": true }` for dry-run, `"preview": false` to execute.

**Validation (parity, all `400` with the exact messages):**
- no `semester_order` → "This batch has no semester number assigned..."
- would exceed `duration_years × 2` → "Cannot promote beyond semester N..."
- target semester already has a batch in this course (`duplicate_conflict`) → "Cannot promote: Batch ... already exists for semester N..."

**Success:** `{ from_semester, next_semester, student_count, promoted_students, message }`.

### 5.20 `GET /api/v1/sections` (LEGACY `get_sections.php`)

**Query:** `batch_id` · returns `{ sections: ["A","B"] }` (distinct, non-empty, sorted).

---

## 6. Students & Guest Links

### 6.1 `GET /api/v1/students`

**Auth:** admin. **Query:** `college_id`, `course_id`, `batch_id`, `section`, `search`, `sort`, `order`, pagination (mirrors `students.php` filters).
**Data:** `{ id, name, email, phone, gender, roll_number, year_of_joining, semester, section, batch_id, batch_name, course_name, college_name, updated_at, test_count, ... }`.

### 6.2 `GET /api/v1/students/me`

Student self-profile (mirrors `student/profile.php`): identity + hierarchy + submission counts + recent results.

### 6.3 `PATCH /api/v1/students/{id}`

Admin edit (the `students.php` `update` action). Cannot change email to an existing one.

### 6.4 `DELETE /api/v1/students/{id}`

Admin delete (`students.php` `delete`). Note `students.batch_id` is `ON DELETE RESTRICT` — deleting a batch with students is blocked; deleting a student cascades its submissions/answers (`schema.sql:83,169,190`).

### 6.5–5.7 Unverified students

`GET /unverified-students` (list, `pending_verifications.php`) · `DELETE /unverified-students/{id}` (single) · `DELETE /unverified-students?all=1` (purge all, the `delete_all` action).

### 5.8 `POST /api/v1/guest-links`

Generate a guest link **or** QR (`students.php` `generate_guest` / `generate_qr`).

**Request**
```json
{ "batch_id": 3, "test_id": 41, "type": "qr" }
```
`type` ∈ `guest|qr`. Server (`logic.md:8.1`):
1. `token = bin2hex(random_bytes(32))` → 64-char hex
2. `expires_at` = now + 30 days, **or** the linked test's `end_time` if earlier
3. insert `guest_entries` (`status='pending'`)

**Success 201**
```json
{ "data": { "id": 12, "token": "...", "type": "qr",
            "url": "<BASE_URL>/guest.php?token=...",
            "qr_image_url": "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=<url>",
            "expires_at": "..." } }
```
> **Divergence:** the web renders the QR via the third-party `api.qrserver.com` (`logic.md:8.2`). On Android, **generate the QR locally** (ZXing / `com.google.zxing`) from `url` — no third-party network call, no PII leaving the device. `qr_image_url` is kept in the contract for parity/debugging but the client should ignore it.

### 5.9 `GET /api/v1/guest-links`

List generated guest entries for a batch/test (admin management view): `{ id, token_prefix, type, status, test_id, batch_id, expires_at, created_at, used }`.

---

## 7. Tests

### 7.1 `GET /api/v1/tests`

Role-scoped.

| Role | Behavior |
|---|---|
| `student` | reuses `getStudentTests()` (`helpers.php:270-297`): tests visible via `test_sections` OR (no `test_sections` rows AND `t.batch_id = student.batch_id)`; LEFT JOIN own submission; ordered `start_time DESC` |
| `admin` | all tests (filterable by batch/course/college/status/test_type) |
| `faculty` | college-scoped (all tests under batches of the faculty's college) |
| `guest` | the single test bound to the guest entry |

**Query (student):** `filter=all|active|upcoming|completed|submitted|pending` · `search`.
**Data (per test)** — `logic.md:19.2` badge mapping drives the client:
```json
{ "id": 41, "title": "...", "description": "...", "duration_minutes": 60,
  "start_time": "...", "end_time": "...", "status": "active", "test_type": "general",
  "total_marks": 50, "max_tab_switches": null, "shuffle_questions": 0,
  "batch_id": 3, "batch_name": "2024-2028", "total_questions": 25,
  "submission": { "id": 801, "status": "in_progress",
                  "evaluation_status": "pending_manual_review",
                  "started_at": "...", "submitted_at": null,
                  "timer_extended_minutes": 0,
                  "total_marks_obtained": null, "total_marks": 50,
                  "remaining_seconds": 1800, "switch_count": 2 } }
```
`remaining_seconds` is computed **server-side** (§9.5) and is `null` when no submission exists.

**Badge derivation (client must match `logic.md:1093-1104`):** `Start Test` (active, no submission, in window) · `Resume` (in_progress) · `Resume (Paused)` (paused + in_progress) · `Paused by Admin` (paused, no submission) · `Starts in X min` (scheduled/upcoming, future start) · `Upcoming` · `View Result` (evaluated) · `Submitted` (submitted, pending) · `Expired` (past end_time).

### 7.2 `GET /api/v1/tests/{id}`

- `student`/`guest`: the **paper** — test meta + questions **with `correct_answer` stripped** + own saved answers + `submission` + `remaining_seconds`. Stripping `correct_answer` for non-admin callers is mandatory (it lives in `questions.correct_answer`, `schema.sql:139`).
- `admin`: full row incl. counts (questions, submissions, in-progress, tab switches).

### 7.3 `POST /api/v1/tests` / `4.4 PATCH` / `4.7 DELETE`

- **Create** (`test_builder.php` `save_test`, `assessment_studio.php` `create_draft`): `{batch_id|batch_ids[], title, description, duration_minutes, start_time, end_time, status, max_tab_switches, shuffle_questions, test_type, passing_marks, total_marks}`.
- **PATCH**: partial update; also the `adjust_end_time` / `extend_timer` (bulk) admin actions when expressed as field updates.
- **DELETE**: hard-delete a draft/test (`test_builder.php` `delete_test`, `assessment_management.php` `delete`). Cascades questions (`schema.sql:143`).

> `test_sections` (multi-batch assignment, `migration_batch_sections.sql`) means a test can target **several batches**. Create/update accept `batch_ids[]`; the server writes `test_sections` rows and keeps `tests.batch_id` as the primary batch for legacy readers (`helpers.php:286-292`).

### 7.4–5.6 Lifecycle & results endpoints

| Endpoint | Body / Query | Effect (parity) |
|---|---|---|
| `POST /tests/{id}/publish` | `{}` | `upcoming|scheduled → active` (Activate Now). Students can start. |
| `POST /tests/{id}/schedule` | `{start_time, end_time}` | sets schedule + `status='scheduled'` (`migration_test_builder_wizard.sql`) |
| `POST /tests/{id}/cancel-schedule` | `{}` | back to `upcoming` (`assessment_management.php` `cancel_schedule`) |
| `POST /tests/{id}/pause` | `{}` | `active → paused`: **blocks new starts**, in-progress can still resume (`logic.md:552`, `test.php:230`) |
| `POST /tests/{id}/resume` | `{}` | `paused → active` |
| `POST /tests/{id}/force-end` | `{}` | `active|paused → completed`: **auto-submits** all `in_progress` submissions (set `status='submitted'`, `submitted_at=NOW()`), blocks all access (`logic.md:557`) |
| `GET /tests/{id}/live` | — | live monitor: per-student `{student, status, remaining_seconds, switch_count, last_save_at, answers_saved}` (`live_monitor.php`, currently self-fetch every 15s) |
| `GET /tests/{id}/results` | `student_id?` | ranked results for reports (`reports.php`): evaluated only, competition rank (§10.2) |
| `POST /tests/{id}/pci` | `{}` | generate/refresh `pci_records` for all evaluated submissions of the test (§10.3) |

### 7.7 `GET /api/v1/tests/{id}/questions`

- `admin`: every field incl. `correct_answer`, `marks`, `sort_order`.
- `student`/`guest`: **`correct_answer` omitted**; `options_json` preserved (keys + text only).

---

## 8. Questions

### 8.1 `GET /api/v1/questions`

**Auth:** admin. **Query:** `test_id`, `type=mcq|coding|explanation`, `search`. Backs `question_library.php` and the test-builder list.

### 8.2 `POST /api/v1/tests/{id}/questions`

Add one question (`test_builder.php` `add_question`).
```json
{ "type": "mcq", "question_text": "...",
  "options": [{"key":"A","text":"..."},{"key":"B","text":"..."}],
  "correct_answer": "A", "marks": 1, "sort_order": 10 }
```
For `coding`/`explanation`, `options` is `null` and `correct_answer` holds the rubric reference. Server validates: text non-empty; MCQ needs ≥2 options and `correct_answer ∈ keys`.

### 8.3–8.4 `PATCH` / `DELETE /api/v1/questions/{id}`

Edit / delete a question (`delete_question`). Deleting all questions of a test leaves it with `total_questions=0` → the student sees "No questions in this test yet" (`test.php:283-291`).

### 8.5 `PUT /api/v1/tests/{id}/questions/order`

Reorder (`reorder_question`). Body: `{ "order": [qid3, qid1, qid2] }` — server rewrites `sort_order` sequentially in one transaction. Client sends the full new order (drag-and-drop result), not a delta.

### 8.6 `POST /api/v1/tests/{id}/questions/import` (multipart)

CSV import (`test_builder.php` `import_csv`, `logic.md:14`). **File field:** `file`. **Format:** 7 columns
`question_text, option_a, option_b, option_c, option_d, correct_answer, marks`.

**Server logic (parity):**
1. `fgetcsv()` per line; skip header if it matches `/question/i` (`logic.md:799`).
2. Validate each row: text non-empty; ≥2 options (A,B minimum); `correct_answer ∈ {A,B,C,D}`.
3. Build `options_json` `[{"key":"A","text":"..."},...]`; insert with `type='mcq'`.
4. Return counts + per-row warnings.

**Success 200:** `{ "imported": 10, "skipped": 0, "warnings": [{"line": 4, "reason": "..."}] }`.
**Errors:** `413` file too large · `422` unparseable · `400 validation_failed`.

---

## 9. Submissions, Answers, Grading, Anti-Cheat

This is the integrity-critical core. Every rule here is source-verified in `submit_answer.php` + `docs/lms-gap-spec.md`.

### 9.1 `POST /api/v1/submissions` — **START (new)**

> **This is the critical structural fix.** On the web, `student/test.php:245-257` **INSERTs the submission row as a side effect of the GET** that renders the page. An Android client cannot do that — it must start a test explicitly.

**Auth:** `student` (guests do **not** create a row; they use the `guest_<entryId>` pseudo-id, §9.4).

**Request:** `{ "test_id": 41 }`

**Server logic (parity with `test.php:38-276` gates, in this order):**
1. Load test + batch. Not found → `404`.
2. **Existing submission wins**: if one exists, return it (do **not** re-check "taking" gates; the result branch already won on the web, `test.php:44-72, 80`).
3. `status='completed'` → `409` `{code:"forbidden", message:"This test has been ended by the admin..."}` (Test Stopped gate, `test.php:218-228`).
4. `status='paused'` && no in-progress submission → `403` "Test Paused" (`test.php:230-240`).
5. `status != 'active'` → `403` "Test is not available right now" (`test.php:265`).
6. Outside `[start_time, end_time]` window → `403` same message (`test.php:265`).
7. Zero questions → `409` "No questions in this test yet."
8. `INSERT INTO submissions (student_id, test_id, status) VALUES (?,?, 'in_progress')` — rely on `UNIQUE(student_id,test_id)` for the race (G13).

**Success 201**
```json
{ "data": { "submission_id": 801, "started_at": "...",
            "remaining_seconds": 3600, "duration_minutes": 60,
            "timer_extended_minutes": 0 } }
```

### 9.2 `GET /api/v1/submissions/{id}`

Returns `{ submission, saved_answers: {qid: answerJson}, remaining_seconds }` — powers **resume** (the web re-reads saved answers at `test.php:294-301`). `403` if the token's student doesn't own it (G1).

### 9.3 `GET /api/v1/submissions/{id}/result`

Reimplements the result view (`test.php:76-212`) as JSON:
- `evaluation_status='evaluated'`: `{ score, total, percent, passed(≥40%), submitted_at, review:[{question_id, type, question_text, options, correct_answer, your_answer, marks_obtained, marks, remarks, answered}] }` — the review LEFT JOINs questions so **unanswered questions are listed too** (`test.php:131-138`).
- `evaluation_status='pending_manual_review'`: `{ status:"submitted", message:"Result Not Yet Announced" }` (`test.php:193-199`).
- `status='submitted'` only: `{ status:"submitted", message:"...once evaluated" }` (`test.php:200-206`).

### 9.4 `POST /api/v1/submissions/{id}/answers` — autosave (LEGACY `submit_answer.php`, autosave branch)

Body mirrors the web's dirty-tracked payload (`test.php:905-945`, gap-spec G5):
```json
{ "test_id": 41, "auto_save": true,
  "answers": { "12": {"selected":"A"}, "13": {"code":"..."} },
  "question_ids": [12, 13] }
```
> The path now carries `submission_id`; the body's `submission_id` (web shape) is accepted but ignored — the path is authoritative.

**Server order of checks (must be preserved exactly):**
1. **Method** POST · **auth** (bearer) → guest path branches on `guest_<entryId>` and requires `guest_entry_id` match (G2, `submit_answer.php:114-124`); student path requires `isStudent()` + ownership (`:160-165`).
2. **Rate limit** (§2.6) → `429 rate_limited` (`:73-111`).
3. **Ownership** load `WHERE id=? AND student_id=? AND test_id=?` (G1).
4. **Deadline** `effective_deadline = earliest(tests.end_time, started_at + duration_minutes)`; `now >= deadline` → `403 deadline_exceeded` for **both** autosave and submit (G4, `:167-189`, gap-spec decision 4/4a/5).
5. **Already submitted** → `400 already_submitted` (`:192-196`).
6. **Question scoping**: every `answers` key and `question_ids[]` entry must resolve via `WHERE test_id=? AND id IN (...)`; any unknown → `400 unknown_question_for_test` (G3, `:198-219`).
7. Upsert `ON DUPLICATE KEY UPDATE` inside a transaction (`:224-251`).

**Success 200**
```json
{ "data": { "saved_at": "2026-10-12T10:30:00+05:30" } }
```
`saved_at` is the **server clock, post-write** (G7). The client's save-status state machine keys off this response, never off "request dispatched" (G7).

### 9.5 `POST /api/v1/submissions/{id}/submit` — final submit (LEGACY `submit_answer.php`, submit branch)

Same checks as §9.4, then the **hybrid evaluation pipeline**, all inside one transaction (G12 — do not restructure):
1. Detect composition: `subjective_count` (coding+explanation) and `max_total` (`:258-265`).
2. Instant MCQ auto-grade: NULL-safe `<=> q.correct_answer` compare; blank → 0 marks (G11) (`:269-279`).
3. Sum MCQ `auto_score`; compute `total_marks` from **all** questions of the test (`:282-294`).
4. Conditional status flip `WHERE status='in_progress'`; `rowCount()>0` = winner (G13) (`:298-304`).
5. Winner only:
   - **pure MCQ** (`subjective_count==0`) → `status='evaluated'`, `evaluation_status='evaluated'`, all scores set (`:309-323`).
   - **hybrid** → keep `status='submitted'`, `evaluation_status='pending_manual_review'`, `auto_score` set, totals NULL (`:324-337`).
6. `commit()` then (pure-MCQ only) `recalculatePciForSubmission()` (`:341-348`).

**Success 200**
```json
{ "data": { "submitted": true, "duplicate": false,
            "evaluation_status": "evaluated",
            "message": "Test submitted and evaluated" } }
```
A lost race returns `duplicate:true` with the same 200 — the client must treat it as success (§1.8).

> **G8 divergence:** on the web, PCI still runs **synchronously inside the submit request** after `commit()` (up to ~9s, gap-spec §5 G8, `helpers.php:141`). For v1, PCI is **deferred** — respond immediately, compute PCI out-of-band. Documented in [`android-logic.md` §4.4](android-logic.md#44-intentional-divergences).

### 9.6 `GET /api/v1/submissions/{id}/answers` (admin grading view)

`grading.php` payload: test + student + every question LEFT JOINed with its `student_answers` row (answer, `marks_obtained`, `evaluation_remarks`, `is_auto_graded`), plus `q.marks` as the max and `q.type` — used to clamp input (`grading.php:14-26`).

### 9.7 `PATCH /api/v1/submissions/{id}/grades` (save grades)

Reimplements `grading.php` `save_grades` / `save_individual`:
**Body:** `{ "grades": { "<answer_id>": {"marks": 4, "remarks": "..."} } }`

**Server logic (parity):**
1. Validate each awarded `marks` against `q.marks` max for that answer's type (`grading.php:14-26`) → over-max → `400`.
2. Update `student_answers.marks_obtained`, `evaluated_at`, `evaluation_remarks`; set `is_auto_graded=0` for manually graded rows.
3. Recompute aggregates **straight from the DB** (`grading.php:31-38`): `auto_score` = SUM over `mcq`; `manual_score` = SUM over non-mcq; `ungraded` = count of non-mcq with NULL marks; `max_total` = SUM of all `questions.marks`.
4. When `ungraded == 0` → finalize: `submission.status='evaluated'`, `evaluation_status='evaluated'`, `total_score = auto+manual`, `total_marks_obtained` set, `evaluated_at`, `evaluator_id` = admin. Else leave `pending_manual_review`.
5. Refresh PCI for the submission (`helpers.php:198-265`).

**Success 200:** `{ ungraded, total_score, total_marks, evaluation_status }`.

### 9.8 `POST /api/v1/submissions/{id}/extend-timer`

Admin extends a student's timer (`test_builder.php` `extend_timer`, `tab_switcher.php` `extend_timer`): `{ "minutes": 5 }` (allowed values 5/10/15/30 per `logic.md:9.3`).
- `UPDATE submissions SET timer_extended_minutes = timer_extended_minutes + ?`
- insert a `tab_switch_logs` row with `type='timer_extend'`, `metadata={"extended_by":5}` (`schema.sql:200-201`).
- remaining time recomputes: `(duration + extended)×60 − elapsed` (`helpers.php:302-308`).

### 9.9 `GET /api/v1/submissions`

Admin list for grading / monitor / tab-switcher: `?test_id=&student_id=&status=&evaluation_status=`. Returns per-submission `{id, student, status, evaluation_status, auto_score, manual_score, total_score, total_marks, switch_count, started_at, submitted_at, remaining_seconds}`.

### 9.10 `POST /api/v1/tab-switches` (LEGACY `tab_switch.php`)

**This is the Android anti-cheat mapping point.**

**Request:** `{ "test_id": 41, "submission_id": "801", "switch_count": 1 }`

**Server logic (parity, `tab_switch.php`):**
- guest `guest_*` id → ack only, **not persisted** (`:45-48`).
- submission must exist and **not** be `submitted|evaluated` (late logs rejected, `:63-67`).
- insert row `type='switch'`, `timestamp NOW(3)` (ms precision) (`:70-74`).

**Success 200:** `{ "success": true }`. (Enveloped as `{data:{...}}` in v1.)

> **Anti-cheat divergence — MUST be implemented on Android:** the web detects `document.hidden` via `visibilitychange` (`logic.md:739-748`). Android equivalents, all of which must POST to this endpoint:
> | Web signal | Android signal |
> |---|---|
> | `visibilitychange` → `document.hidden` | `onStop()` / `onPause()` of the test Activity (app backgrounded, home button, app switch) |
> | tab switch to another site | another app brought to foreground (detect via `onWindowFocusChanged(false)` + `onPause`) |
> | window blur | split-screen / multi-window entry — **block** via `resizeableActivity=false` on the test Activity |
> | (none) | screen-off → also count as a switch |
> | (none) | incoming call / notification shade — optional, product decision |
> Additionally set `window FLAG_SECURE` on the test Activity to block screenshots/screen-recording (`logic.md` §13 has no web equivalent — intentional hardening).
> The client must **not** log a switch when the user merely rotates the device or when the Activity is recreated for a config change (distinguish via `isChangingConfigurations`).

### 9.11 `GET /api/v1/tab-switches`

Admin view (`tab_switcher.php`): `?test_id=&submission_id=` → `[{id, submission_id, student, switch_count, type, metadata, timestamp}]` + per-student totals.

---

## 10. Analytics, Reports, PCI

### 10.1 `GET /api/v1/admin/stats`

`admin/dashboard.php` (+ the 4 header COUNTs on `admin/help.php`): `{ colleges, courses, batches, students, tests, submissions, ... }`. Also returns `python_api_available` (the web checks a `PYTHON_API_URL` const, `dashboard.php`).

### 10.2 `GET /api/v1/tests/{id}/results` — ranking

**Locked product rules (gap-spec §2 decisions 2, 3, and G10):**
- Rank over **evaluated** submissions only. Pending evaluations are **excluded from ranking entirely** — no rank shown, never `COALESCE(...,0)` (this is the **current web bug** at `reports.php:41,55,641` and `faculty/dashboard.php:134,189,215`; **v1 must implement the correct rule, not copy the bug**).
- Ties use **competition ranking: 1, 1, 3** (NOT dense 1,1,2).
- Deterministic display tie-break for equal scores: `submitted_at ASC` (display only; the rank value itself is shared).

**Response:** `{ ranked:[{rank, student_id, name, total_score, total_marks, percent, submitted_at}], pending:[{student_id, name, status}] }`.

### 10.3 `POST /api/v1/tests/{id}/pci`

Generate/refresh `pci_records` for all evaluated submissions of a test (`reports.php` `generate_pci`). Uses `recalculatePciForSubmission()` per submission: Python service first (`/api/pci/calculate`), PHP fallback when Python is down (`helpers.php:198-265`).

**PCI formula (parity, `logic.md:12.1`, `helpers.php:245`):**
```
category% = obtained / total × 100
PCI = mcq% × 0.40 + coding% × 0.30 + explanation% × 0.30
```
Weights stored per record (`mcq_weight=40, coding_weight=30, explanation_weight=30`) for auditability; historical scores are **not** retroactively changed (`logic.md` Appendix 5).

### 10.4 `GET /api/v1/pci-records`

`?test_id=` or `?student_id=`. Each: `{student_id, test_id, pci_score, mcq_score, coding_score, explanation_score, weights, generated_at}`.

### 10.5 `GET /api/v1/charts/test/{id}`

Proxies the Python service `/api/charts/test/<id>` (`src/python/app.py`, `analysis/charts.py`) and returns chart-ready series for the reports screen: bar (PCI per student), doughnut (category averages), stacked bar (per-student breakdown), histogram (distribution), and for `?student_id=X` the individual radar + stats boxes (`logic.md:12.4`).

> **Availability:** if Python is down, this returns `503 python_unavailable` **but** the rest of the app must keep working — PCI can still be computed by the PHP fallback. The client shows a degraded "charts unavailable" state, never a hard failure.

### 10.6 `GET /api/v1/student/results` / `GET /api/v1/student/analytics`

`student/results.php` and `student/analytics.php`: the student's own submissions with scores, percentages, per-test breakdown, and **batch peer stats** (analytics.php computes batch-level aggregates). Peer stats must never expose another student's identity beyond an aggregate.

### 10.7 `GET /api/v1/faculty/overview`

`faculty/dashboard.php` filtered by `?batch_id=&semester=&year=&test_id=`: college-scoped counts, per-test submission/eval stats. Same ranking rule as §10.2 (the web's `COALESCE` bug at `:134,189,215` must **not** be replicated).

---

## 11. Notifications, Audit, Faculty, Misc

### 11.1–11.3 Notifications (`admin_notifications`, `migration_notifications.sql`)

| Endpoint | Notes |
|---|---|
| `GET /api/v1/notifications` | `?unread=1` + pagination. Types: `student_account`, `guest_link`, `qr`, `test`, `system` (`helpers.php:316`). Currently `admin/notifications.php?action=poll` self-polls — v1 makes it a real endpoint. |
| `POST /api/v1/notifications/read-all` | `mark_all` |
| `POST /api/v1/notifications/{id}/read` | `mark_one` |

Client should poll on foreground + a configurable interval (web uses the bell panel; no websocket exists).

### 11.4 `GET /api/v1/failed-logins`

`failed_logins.php`: `?email=&type=&page=`. Returns `{id, email, student_name, ip_address, attempt_type, reason, attempted_at}`. `attempt_type` ∈ `signup_unverified|wrong_password|invalid_email|expired_otp|wrong_otp|duplicate_email` (`schema`/`migration_unverified.sql`).

### 11.5–11.8 Faculty control (`faculty_control.php`, `migration_faculty_control.sql`)

| Endpoint | Notes |
|---|---|
| `GET /api/v1/faculty` | list with `faculty_colleges` expansion (a faculty can serve 1..n colleges) |
| `POST /api/v1/faculty` | create `{email, name, password, college_ids[]}` — **no self-registration**; admin-only (`migration_faculty.sql` header) |
| `PATCH /api/v1/faculty/{id}` | update name/password/college assignment/`is_active` |
| `DELETE /api/v1/faculty/{id}` | remove + cascade `faculty_colleges`, `faculty_permissions` |

`faculty_permissions` is a stub today (unread by app code) — do not expose writes to it.

### 11.9 `GET /api/v1/geo/cities`

Typeahead for the faculty-login location field. Backed by `geo_cities` (GeoNames import, `src/php/includes/geo_resolver.php`, `sql/data/cities1000.tsv`). **Query:** `?q=benga&limit=10` → `[{city, region, country, latitude, longitude}]`. Used to enrich `faculty_login_log` with resolved `city/region/country` (`migration_faculty_login_activity.sql`).

---

## 12. Legacy Endpoints (the 8 existing files)

These stay **unchanged** for the web's AJAX. Android calls the `/v1` equivalents (which reuse the same PHP logic behind the uniform envelope). Documented here so the parity mapping is explicit.

| Legacy file | Lines | Legacy contract | v1 equivalent | Notes / gotchas |
|---|---|---|---|---|
| `api/get_courses.php` | 30 | GET, returns **bare array** `[{id,name,duration_years}]`; `?college_id=&active=1`; 400/405 | §5.8 | v1 wraps in `data` + envelope |
| `api/get_batches.php` | 29 | GET, bare array; `?course_id=&active=1`; adds `display_name` | §5.14 | |
| `api/get_course_college.php` | 22 | GET, `{id,college_id,name}` or 404 | §5.9 | |
| `api/get_sections.php` | 20 | GET, `{sections:[...]}` — **orphaned**, no public page calls it | §5.20 | keep for Android signup section field |
| `api/promote_students.php` | 140 | POST JSON; admin session + super/platform role; `preview` flag; 400/401/403/404/500 with `{status:'error'|'ok', message}` | §5.19 | v1 normalizes the ad-hoc `{status,message}` into the envelope |
| `api/delete_college.php` | 312 | POST; admin + super/platform + CSRF + **archived-only** guard; 14-step cascade; audit_log; `{success, message, deleted}` | §5.6 | hardest one to port — preserve the archived guard + dependency-ordered cascade **exactly** |
| `api/submit_answer.php` | 380 | POST; **session + CSRF**; guest branch (`guest_*`); autosave + final submit; rate limit; G1–G7 guards; returns `{success,message,saved_at}` or `{success,submitted,duplicate,evaluation_status,message}`; optional `redirect` for browser form posts | §9.4, §9.5 | v1 drops CSRF (bearer), keeps every G-guard; `redirect` branch is web-only and must **not** be returned to Android |
| `api/tab_switch.php` | 78 | POST; session + CSRF; guest ack-only; rejects late logs; `{success:true}` | §9.10 | |

### 12.1 CSRF on legacy routes

Legacy routes keep `validateCsrfToken()` (`submit_answer.php:39-43`, `tab_switch.php:28-32`). **v1 routes do not** — bearer auth replaces it. If any v1 route accidentally falls through to a legacy CSRF gate, it must return `403 csrf_invalid`, not a designed HTML error page (the `errorPageWantsHtml()` branch at `session.php:55-62` must be suppressed for `/api/v1/*`).

### 12.2 `parseRequestBody()` compatibility

`helpers.php:121-130` already accepts **both** JSON (`Content-Type: application/json`) and form bodies. v1 always sends JSON, so this works unchanged. Malformed JSON returns `[]` → the caller 400s.

---

## Appendix A — Endpoint ↔ source-file traceability

Every v1 route must reuse, not rewrite, the underlying PHP function:

| v1 route | Reuse |
|---|---|
| auth login/faculty-login/register/otp/reset | `includes/auth.php` (`adminLogin`, `studentLogin`, `facultyLogin`, `guestLogin`, `studentRegister`, `generateStudentOtp`, `verifyStudentOtp`, `resendStudentOtp`, `generatePasswordResetOtp`, `verifyPasswordResetOtp`, `setNewPassword`, `isBruteForceLocked`, `logFailedLogin`) |
| student test list | `includes/helpers.php:270-297` `getStudentTests()` |
| remaining time | `includes/helpers.php:302-308` `getRemainingSeconds()` |
| autosave/submit | `api/submit_answer.php` (extract the transaction into a shared include) |
| tab switch | `api/tab_switch.php` |
| PCI | `includes/helpers.php:198-265` `recalculatePciForSubmission()` + `src/python/analysis/pci.py` `calculate_pci()` |
| python proxy | `includes/helpers.php:141-185` `pythonApiRequest()` |
| notifications | `includes/helpers.php:318-327` `notifyAdmin()` |
| promote | `includes/promote_helper.php` (`getPromotionPreview`, `promoteCohort`) |
| geo | `includes/geo_resolver.php` |
| hard-delete college | `api/delete_college.php` (extract cascade into a shared include) |

## Appendix B — Server clock is authoritative

The client **never** computes a deadline locally as the source of truth. The server sends `remaining_seconds` at start/resume (§9.1, §9.2) and re-validates every write against `effective_deadline` (§9.4 step 4). The client countdown is display-only (parity with `logic.md` Appendix 3 — "Server-side timer tracking ... Client JS is just a display"). On any `403 deadline_exceeded`, the client must stop autosaving, show the server's message verbatim, and transition to the result/pending screen.
