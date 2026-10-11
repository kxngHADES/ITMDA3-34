# SwiftQ design tokens

One set of colours, type, spacing and shapes shared by all four SwiftQ apps, taken from the ITMDA Figma kit.

| App | Owner | Use |
|---|---|---|
| Patient app (Kotlin) | Ndaedzo | `dist/android/` |
| Staff mobile (Kotlin) | Sibusiso | `dist/android/` |
| Staff web (Next.js) | Kgomotso | `dist/web/` |
| Admin web (Next.js) | Thabiso | `dist/web/` |

## Rule: change tokens in one place

1. Edit `tokens.json`. Never edit the files in `dist/`.
2. Run `python build.py` (Python 3, no packages needed).
3. Commit `tokens.json` and `dist/` together.

If a colour changes in Figma, change it in `tokens.json` too so the apps and the designs stay the same.

## Use semantic tokens, not raw colours

`semantic` says what a colour is *for* (`text.primary`, `action.primary`, `ticket.waiting.bg`). Use these in screens. Only reach for the raw palette (`color.blue.system`) when nothing semantic fits.

Ticket statuses are the same everywhere: `waiting`, `almost`, `called`, `in-progress`, `completed`, `cancelled`, `no-show`. Match these names to the API's ticket status values.

## Next.js (staff web, admin web)

```tsx
// app/layout.tsx
import "@/styles/tokens.css";
```

```tsx
<button style={{ background: "var(--sq-action-primary)", borderRadius: "var(--sq-radius-md)",
                 height: "var(--sq-size-button-lg)" }} className="sq-text-title-3">Call next</button>

<span style={{ background: "var(--sq-ticket-waiting-bg)", color: "var(--sq-ticket-waiting-fg)" }}>Waiting</span>
```

Need the values in JS (charts, dynamic status colours)? Import `tokens.ts`:

```ts
import { tokens, type TicketStatus } from "@/styles/tokens";
const chip = (s: TicketStatus) => tokens.semantic.ticket[s];
```

Fonts: load Inter with `next/font/google` and the CSS variable `--sq-font-family` picks it up.

## Kotlin / Jetpack Compose (patient app, staff mobile)

Copy `SwiftQTokens.kt` into your app (change the `package` line to yours) and `colors.xml` into `res/values/`.

```kotlin
val type = SwiftQType(inter)   // see the comment in the file for loading Inter from res/font

Button(
    onClick = { joinQueue() },
    shape = SwiftQShapes.Md,
    colors = ButtonDefaults.buttonColors(containerColor = SwiftQSemantic.Action.Primary),
    modifier = Modifier.fillMaxWidth().height(SwiftQSize.ButtonLg)
) { Text("Join a queue", style = type.title3, color = SwiftQSemantic.Text.OnPrimary) }
```

`colors.xml` is for things outside Compose: splash screen, launcher icon background, notification accent (`@color/sq_blue_system`).

## Accessibility rules baked in

- Patient-facing text is never below 16 (`callout` and up). `footnote` and `caption` are for staff screens only.
- Every tap target is at least `size.touch-min` (48). Primary buttons are `button-lg` (64).
- Use the `*.text` colours for coloured text on white. The `*.fill` colours are for backgrounds and icons; they fail contrast as small text.
- `neutral.tertiary` is for placeholders and icons, never body text.
