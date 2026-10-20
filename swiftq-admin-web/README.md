# SwiftQ Admin web app

The admin web app for IT admins and clinic managers. It is a Next.js app that exports to plain static files, so it hosts on Cloudflare Pages. It talks to the shared SwiftQ Admin API (see the SwiftQ Admin API Reference).

## Run it

```
npm install
cp .env.example .env.local     # then set the API address
npm run dev                    # http://localhost:3000
npm test                       # 45 tests, no network needed
npm run build                  # writes the site to the "out" folder
```

On Windows use `copy .env.example .env.local`.

The only setting is `NEXT_PUBLIC_API_BASE_URL`, the address of the shared API with no slash at the end. Restart `npm run dev` after changing it.

## How sign in works

There is no Firebase in this app. The login page sends the email and password to `POST /v1/auth/login`. The API sends back a JWT and the person's details (id, email, role, name, clinic). The app keeps the token in the browser tab (sessionStorage) and sends it as `Authorization: Bearer ...` on every call. On a 401 it calls `POST /v1/auth/refresh` once and retries. If that fails the person is sent back to the login page. Closing the tab signs you out.

The API checks the role on every request. The app only decides what to show.

## Trying it without the shared API

```
npm run mock                   # fake API on http://localhost:4010, signed in as an IT admin
npm run mock -- manager        # the same, as a manager of the first clinic
```

Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:4010`, run `npm run dev` in a second terminal, and sign in with any email and password. The fake API forgets everything when you stop it. Take final screenshots and run user testing against the real shared API, because the data here is made up.

## Which role sees what

Both roles can open every screen. The difference is the scope:

| | IT admin | Manager |
|---|---|---|
| Clinics | all clinics | own clinic only |
| Staff accounts | any role, any clinic | receptionists and nurses in own clinic |
| Queue settings, sync log, analytics | all clinics | own clinic only |

## What is where

| Plan task | What it does | Where |
|---|---|---|
| S8 shared web module | Token aware API client, typed endpoints, auth provider, role helper | `src/lib/api`, `src/lib/auth`, `src/lib/hooks.ts` |
| D1 login and guard | Login through the API, role check, route guard, sign out | `src/app/login`, `src/app/(admin)/layout.tsx`, `src/lib/auth` |
| D2 staff accounts | List, filter, add, edit, deactivate, reactivate | `src/features/staff` |
| D3 clinics and queue settings | Add clinics and service points, queue configuration | `src/features/clinics`, `src/features/queue-config` |
| D4 sync log | Conflict review and every sync attempt | `src/features/sync` |
| D5 analytics and CSV | Totals, charts, date range, CSV download | `src/features/analytics` |
| D6 audit log | Shows a "not available yet" message | `src/features/audit` |

Everything visual uses the group's design tokens (`src/styles/tokens.css`). Do not edit that file by hand.

## Not built on the server yet

The API reference says these do not exist yet, so the app does not offer them: the audit log, the error log, and editing or deactivating a clinic or service point. When the server team adds them, the endpoints go in `src/lib/api/endpoints.ts` and the screens can be switched back on.

## Deploying

Cloudflare Pages settings: build command `npm run build`, output folder `out`. Set `NEXT_PUBLIC_API_BASE_URL` in the Pages environment settings and set the Node version to 20 or higher. The API must allow your Pages address in its CORS settings.

## Questions for the backend team

1. **CORS.** Please allow `http://localhost:3000` and the Cloudflare Pages address.
2. **Sync log paging.** The sync log returns at most 200 entries, newest first, with no paging. That is fine for now.
3. **Conflict choices.** What is the difference between `keep_server` and `discard`? Resolving currently only records the decision and does not change the ticket.
4. **Icon names.** The service point form takes free text because the icon set is not listed.
5. **Staff names in analytics.** The app looks names up from the staff list, so `calls_per_staff` does not need `display_name`.
