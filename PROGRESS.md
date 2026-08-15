# VOR Concierge — Progress & Roadmap

Enterprise Knowledge Concierge: an on-premise RAG (retrieval-augmented generation) chat
platform that lets a company's staff ask questions against its own internal documents,
with strict role- and department-based access control over what each person can see.
Built by Vigilant Ordnance Risks (VOR). This is real commercial software intended for
paying enterprise customers — nothing in this codebase is a demo, a prototype, or filled
with placeholder data. Every credential, account, and data path is real.

This document is the single source of truth for what has been built, how it works, and
everything still planned. It should be kept up to date as work proceeds.

---

## 1. What This Product Is

A company signs up as an **organization** (tenant). The organization's owner/executives
set up **departments** (their own org chart, can nest arbitrarily) and **roles** (named
however they like — "Owner", "Director", "Employee", whatever fits that specific
company — each with a seniority level and a set of platform capabilities). They invite
staff, assign each person a department and a role, and upload the company's internal
documents (policies, contracts, handbooks, anything) scoped to whichever department/
seniority level should be able to see them. Staff then use the **Chat** page to ask
natural-language questions; the system retrieves only the document chunks that person is
actually allowed to see, and an LLM answers strictly from that retrieved context — it is
explicitly instructed to say "Insufficient secure local data" rather than hallucinate an
answer from outside what was retrieved.

The product is sold on a subscription (tiered plans, see §6.2), and a plan's tier caps
which capabilities an organization can use regardless of what a role is configured to
grant — this is what will drive "upgrade to unlock" UI once billing exists.

---

## 2. Architecture & Stack

**Backend** — `backend/`, Spring Boot 3.4 on Java 21, package `com.vorconcierge.ragcore`.
Deliberately plain JDBC (`JdbcTemplate`, hand-written `RowMapper`s) — no JPA/Hibernate.
Postgres 16 with the `pgvector` extension for embeddings (HNSW cosine-distance index).
Apache Tika for extracting text out of uploaded documents (PDF, DOCX, TXT, etc.). A
hand-rolled sliding-window token chunker (300 tokens per chunk, 50-token overlap).
Ollama for both embeddings (`mxbai-embed-large`) and generation (`llama3.1` locally —
the original `llama3` default was never pulled on the dev machine, so this was changed).
Auth is JWT (RS256), carried in an HttpOnly/Secure/SameSite=Strict cookie. Ingestion
progress and chat responses both stream over Server-Sent Events (SSE).

**Frontend** — `frontend/`, React 19 + Vite + Tailwind CSS, plain `fetch`-based API
client (no axios/react-query). Dark theme throughout; brand accent color is the exact
hex `#004515` (Tailwind token `brand-600` in `tailwind.config.js` — deep forest green,
not a bright default). Branding uses a real logo asset (`frontend/public/VOR Updated
Logo.png`, a metallic shield/eagle-eye mark) via a shared `Logo` component — never a
placeholder icon. Top navbar (not a sidebar) for primary navigation.

**Local dev environment** — Postgres and Ollama both run outside Docker's default
compose-managed Ollama service; Ollama runs **natively** on the dev machine (already
installed with models pulled) while Postgres runs via `backend/compose.yaml`
(`pgvector/pgvector:pg16` image). Backend started with `./mvnw spring-boot:run`;
frontend with `npm run dev`. JDK 21 (Temurin) path: `C:\Program Files\Eclipse
Adoptium\jdk-21.0.12.8-hotspot` (needs to be on `PATH`/`JAVA_HOME` per shell).

---

## 3. Data Model (Multi-Tenant Core)

- **`organizations`** — the tenant boundary. Includes a reserved, non-billable
  `_platform` organization that holds VOR's own operator accounts, so `org_id` can stay
  `NOT NULL` everywhere with no scattered null-tenant special-casing.
- **`departments`** — per-organization, self-referencing (`parent_department_id`) so
  they nest arbitrarily. Rendered in the UI as a real collapsible tree.
- **`roles`** — per-organization, each with a `hierarchy_level` (higher = more senior;
  seeded with gaps like 10/20/30/40 so an org can insert intermediate levels later) and
  a `capability_mask` — a bitmask over a **fixed, platform-defined** set of capabilities
  (see `security/Capability.java`: `UPLOAD_DOCUMENTS`, `MANAGE_USERS`,
  `MANAGE_DEPARTMENTS`, `MANAGE_BILLING`, `VIEW_ALL_ORG_CHATS`, `MANAGE_ROLES`,
  `INVITE_USERS`, `VIEW_ANALYTICS`). Organizations cannot invent new capabilities — only
  choose which of the fixed set a given role gets — because every capability bit
  corresponds to a real enforced code path; a fully dynamic permission system would add
  real complexity for no practical benefit.
- **`subscription_plans`** — caps what capabilities an org's plan allows, independent of
  what a role is configured to grant. Effective access for any check is
  `role.capability_mask & plan.capability_mask`. This is the mechanism that will drive
  "locked, upgrade to unlock" UI for features outside the org's current tier.
- **`users`** — login is by **email** (globally unique), not username (which is now
  just an org-scoped display name, since usernames are no longer globally unique once
  multiple tenants exist).
- **`documents` / `chunks`** — visibility replaced the old flat 6-bit permission mask
  with `minimum_role_level` (an integer floor) plus an optional `department_id` scope
  (null = org-wide). This is enforced directly in the vector-search SQL query itself —
  not just a UI-level check — so a user's role level and department are part of the
  actual `WHERE` clause on the hot retrieval path.

Full schema: `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

---

## 4. What's Built and Verified (Phases 1–3)

Everything below has been compiled, started against a real Postgres + Ollama, and
exercised with real HTTP calls — not just written and assumed correct.

### Phase 1 — Multi-tenant foundation
- Full schema above, JWT claims carrying org/department/role/hierarchy-level/capability-
  mask, `PlanCapabilityService` (short-TTL cache so a plan downgrade/cancellation cuts
  off access promptly rather than waiting for JWT expiry, without a DB hit on every
  request), `SecurityUtils` capability/org-ownership guards.
- Bootstrap: on an empty database, one platform-operator account is created (random
  password, logged once), and — controlled by `CREATE_TEST_ORG` (on by default during
  development, meant to be set `false` before a real launch) — one real test
  organization on the Enterprise plan with a full-capability Owner account, so every
  real feature can be exercised without Stripe wired up yet. This is a genuine seeded
  account with a real bcrypt password, not an authentication bypass — a backdoor would
  be an actual security liability in software sold to enterprise customers.

### Phase 2 — Organization management backend
- `DepartmentController`, `RoleController`, `UserManagementController` — full CRUD for
  departments, roles, and org membership, all capability-gated.
- Privilege-escalation guard: a user with `MANAGE_ROLES` cannot create or assign a role
  with more capabilities or more seniority than they hold themselves — otherwise
  `MANAGE_ROLES` itself would be an escalation path.
- Inviting a user creates a real account immediately with a random temporary password
  handed back once to the inviting admin (no email provider is wired up yet — that's
  Phase 5 territory).

### Phase 3 — Frontend catch-up + executive/employee experience
- **Dashboard** — org overview landing page: real member/department/document/chunk
  counts, plan name and limits, tokens used today.
- **Chat** — threads grouped by date (Today / Yesterday / Previous 7 Days / Older); a
  hover-revealed ⋮ menu per thread for rename/delete (both backed by real endpoints);
  a right-side inspector panel for the selected thread (start date, last activity,
  message count, every document referenced across the conversation). No share/export
  feature by deliberate choice — see §7.
- **Sources** — upload flow scoped by department + minimum-visibility level (replacing
  the old flat permission checkboxes); click-to-inspect detail panel; List/Grid view
  toggle.
- **Organization** — departments render as a real collapsible tree (expand/collapse,
  nesting); role builder with a capability-checkbox picker.
- **Members** — roster with inline department/role reassignment, invite flow, enable/
  disable accounts.
- **Admin** — platform-operator-only system page (unchanged surface from the original
  single-tenant build, now correctly gated to VOR's own ops accounts rather than tenant
  admins).

---

## 5. Real Bugs Found — and the Lesson Each One Taught

Compiling and reading code is not the same as running it. Every one of these was missed
by review and only surfaced by actually executing the system end to end:

1. **Postgres auth failure on first real boot.** A stale Docker volume from an earlier
   attempt had initialized with different credentials than the current `compose.yaml`
   — Postgres only applies `POSTGRES_USER`/`PASSWORD` on first init of an *empty* data
   directory. Fix: wipe and recreate.
2. **Every repository's `insert()` was broken.** All used
   `Statement.RETURN_GENERATED_KEYS`, but Postgres's JDBC driver returns the *entire
   inserted row* for that flag (MySQL only returns the actual key) — so
   `GeneratedKeyHolder.getKey()` threw on multiple columns the very first time any
   insert path actually ran. Fixed across all 7 repositories by passing
   `new String[]{"id"}` instead of the generic flag.
3. **The SSE chat stream ran words together with no spaces.** First fix attempt assumed
   Spring's `SseEmitter` pads `data:` with a space per SSE convention, and stripped one
   leading space to compensate — but Spring writes `"data:" + rawContent` with **no**
   padding of its own, so that "fix" was still eating a real leading space off every
   streamed word-token, silently reproducing the identical bug. Confirmed via raw byte
   inspection (`curl | cat -A`) before landing the real fix: take the SSE data line
   completely verbatim, no trimming at all.
4. **CORS silently blocked the whole frontend after Vite picked a different dev port.**
   `localhost:5173` was already held by a stray process, so Vite bound to `5174`; the
   backend's CORS allow-list only had `5173` configured, and Spring Security's CORS
   processor rejects a disallowed origin with a bare `403 Forbidden` — which looked
   identical to a real auth failure until the origin header was inspected directly.
5. **`postgresql` JDBC driver was `runtime`-scope in `pom.xml`**, but `DatabaseConfig`
   referenced its classes at compile time — caught the first time the module was
   actually compiled (it had never been compiled before that point in the project,
   despite reading as complete).
6. **`PGvector.registerTypes()` was called with a `DataSource` instead of a
   `Connection`** — removed entirely once it became clear the app never reads vectors
   back as `PGvector` objects, only writes them, so the registration wasn't needed.

---

## 6. Remaining Roadmap

### 6.1 Phase 4 — Public marketing / pricing site
A real, genuinely polished landing site — explicitly not something to phone in. Needs:
- A homepage explaining the product (what it does, who it's for, why the security/
  access-control model matters for enterprise buyers).
- A pricing page with the tier structure and the multi-year prepay calculator (§6.2).
- A signup flow that leads into Phase 5's Stripe checkout.
- Still needs a decision on whether this lives in the same frontend app (new public
  routes ahead of the login-gated app) or as a fully separate site/deploy.

### 6.2 Phase 5 — Billing (Stripe)
Pricing model as described by the user (2026-08-15), reflected back and confirmed —
not yet built:
- Tiered plans (naming not finalized), differentiated along two axes: **employee count
  allowed** and **monthly token allowance**.
- Beyond standard monthly billing, a **multi-year prepay option**: a quoted per-year
  rate (an example figure of $200/yr was given during planning, not a final price) with
  a live on-site calculator — the buyer enters a number of years, the page shows the
  total upfront cost for that term.
- When a prepaid term expires, the organization's access should be **automatically
  restricted** until renewed.
- Stripe is the intended processor. Needs the user's real Stripe account/keys
  (test-mode keys to start) — this can't be faked or stubbed for a product that's
  actually going to charge real customers; the integration code can be built ahead of
  time, but it needs real credentials to ever actually function.
- Webhook-driven organization provisioning: a successful subscription should create the
  `organizations` row, the owner account, and default role/department — this is the
  real-world entry point that the manually-seeded test org (§4, Phase 1) currently
  stands in for.
- Inviting staff by email (Phase 2, currently returns a one-time temporary password
  directly to the inviting admin) should eventually go through a real transactional
  email provider (e.g. Resend/Postmark/SendGrid) once one is chosen — needs a real API
  key the same way Stripe does.

### 6.3 Phase 6 — Tacitus Dave cross-promotion
The user owns both VOR Concierge and a separate product/site, Tacitus Dave, and wants
cross-promotion between them once both are live. Explicitly **not** to be framed as if
two independent parties "collaborated" — that would be a factual misrepresentation to
enterprise buyers who do vendor/ownership due diligence. Agreed framing instead:
something like "from the same studio that built Tacitus Dave," or a plain footer link —
honest about common ownership while still getting the cross-promotion benefit. Exact
copy still to be finalized with the user when this phase starts.

---

## 7. Deliberate Design Decisions Worth Remembering

- **No demo/fake data anywhere.** Bootstrap accounts use randomly generated passwords
  logged once (Jenkins-style), never hardcoded weak credentials; the login page never
  displays credentials in the UI.
- **No chat share/export feature**, on purpose. The user's reasoning: since the whole
  point of the product is protecting classified/sensitive enterprise data, a feature
  that helps that data leave the app would undermine the product's core value
  proposition. Thread actions are limited to rename and delete.
- **Persistent JWT signing key, not ephemeral.** If no externally managed key is
  configured, the backend generates one *once* and persists it to disk
  (`backend/data/jwt/`) rather than regenerating a new one on every restart (which
  would silently invalidate every session each time the app restarted). Deployments
  running more than one backend instance need a real centrally managed key via
  `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` — this local generate-and-persist behavior is a
  single-instance convenience, not a production key management strategy.
- **UI patterns borrowed deliberately from Scrivener's binder/inspector model** (a
  writing-software reference the user shared), translated to what's actually useful
  for a concierge app rather than copied wholesale: real nested tree navigation for
  departments (Scrivener's binder), click-to-inspect detail panels for Sources and
  Chat threads (Scrivener's inspector + synopsis/notes), a card/grid view alternative
  for Sources (Scrivener's corkboard). Explicitly *not* borrowed: the breadcrumb/back-
  forward navigation strip — solves a folder-depth problem this app doesn't have, and
  the browser's own back button already covers it.

---

## 8. Open Questions for the User (revisit before building the relevant phase)

- Phase 4: same-app public routes vs. a fully separate marketing site/deploy?
- Phase 4/5: final plan tier names and exact pricing figures (the $200/yr example was
  illustrative, not confirmed final).
- Phase 5: which transactional email provider for real staff invitations?
- Phase 6: final approved copy for the Tacitus Dave cross-promotion mention.
