## Summary

<!-- What does this PR change and why? -->

Closes #

## Area

<!-- Tick everything this PR touches. Path labels are added automatically, and the code owners of each area are requested for review (see .github/CODEOWNERS). -->

- [ ] API (`api/`)
- [ ] Admin web (`admin-web/`)
- [ ] Staff web (`staff-web/`)
- [ ] Mobile app (`SwiftQ/`)
- [ ] Staff mobile app (`SwiftQ-Staff/`)
- [ ] Infrastructure / CI (`docker-compose.yml`, `.github/`)
- [ ] Documentation

## Type of change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Chore / dependencies
- [ ] Docs
- [ ] Breaking change (describe what breaks and for which app below)

## How was this tested?

<!-- Commands run, test cases added, manual steps, device/emulator or browser used. -->

## Checklist

### General

- [ ] Branch is up to date with `main`
- [ ] PR is focused on one change (unrelated work is in a separate PR)
- [ ] Docs updated where needed
- [ ] No secrets or credentials committed (`.env`, `local.properties`, keystores, API keys)

### Per area

<!-- Only complete the items for the areas ticked above; delete the rest. -->

- [ ] **API:** starts locally and tests pass (`cd api && uv run --with pytest pytest`), if any exist
- [ ] **API:** new/changed env vars are added to `api/.env.example`
- [ ] **API:** endpoint or response changes are reflected in the apps that call them
- [ ] **Admin web:** lints and builds (`cd admin-web && npm run lint && npm run build`)
- [ ] **Staff web:** lints and builds (`cd staff-web && npm run lint && npm run build`)
- [ ] **Mobile app:** builds and unit tests pass (`cd SwiftQ && ./gradlew assembleDebug testDebugUnitTest`)
- [ ] **Staff mobile app:** builds and unit tests pass (`cd SwiftQ-Staff && ./gradlew assembleDebug testDebugUnitTest`)

## Screenshots / recordings

<!-- For UI changes (web or mobile), add before/after screenshots or a short recording. -->

## Notes for reviewers

<!-- Anything reviewers should focus on, known gaps, or follow-up work. Delete if not needed. -->
