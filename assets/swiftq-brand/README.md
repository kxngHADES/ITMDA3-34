# Swift Q — brand assets

All logos are pure vector: the wordmark and tagline are converted to outlines (Inter ExtraBold / Bold), so no font needs to be installed anywhere.

## `svg/` — master files
| File | Use |
|---|---|
| `swiftq-app-icon.svg` | App icon, rounded square |
| `swiftq-app-icon-round.svg` | Circular icon |
| `swiftq-app-icon-fullbleed.svg` | Square, no corners (for platforms that apply their own mask) |
| `swiftq-mark.svg` | Cross + dots only, transparent |
| `swiftq-mark-mono.svg` | One-colour silhouette, uses `currentColor` |
| `swiftq-wordmark*.svg` | "Swift Q" text logo: default, on white, on blue, white (for dark backgrounds) |
| `swiftq-logo-horizontal*.svg` | Icon + wordmark |
| `swiftq-lockup-tagline*.svg` | Icon + wordmark + "SMARTER PATIENT FLOW": transparent, light card, blue, dark |

## `nextjs/` — App Router
Copy `nextjs/app/*` into your `app/` folder and `nextjs/public/*` into `public/`.
Next.js picks up `favicon.ico`, `icon.svg`, `icon.png`, `apple-icon.png` and `opengraph-image.png` automatically — no `<link>` tags needed.

Add the manifest and theme colour in `app/layout.tsx`:

```tsx
import type { Metadata, Viewport } from "next";

export const metadata: Metadata = {
  title: "Swift Q",
  description: "Smarter patient flow",
  manifest: "/manifest.webmanifest",
};

export const viewport: Viewport = { themeColor: "#007AFF" };
```

Use logos in the UI:

```tsx
import Image from "next/image";
<Image src="/brand/swiftq-logo-horizontal.svg" alt="Swift Q" width={180} height={44} priority />
```

`favicon.ico` contains 16, 32, 48, 64, 128 and 256 px.

## `android/` — Android Studio (Kotlin)
Merge `android/res/` into `app/src/main/res/` (it uses the default `ic_launcher` names, so it replaces the template icons; delete any old `ic_launcher.png` files in the mipmap folders first to avoid duplicate-resource errors).

- **Launcher**: adaptive icon (`mipmap-anydpi-v26`) with vector background, foreground and monochrome layer (Android 13 themed icons). Legacy `.webp` mipmaps for API < 26. The manifest's default `android:icon="@mipmap/ic_launcher"` / `android:roundIcon="@mipmap/ic_launcher_round"` work unchanged.
- **Notification**: `@drawable/ic_stat_swiftq` (white silhouette, 24dp)
  ```kotlin
  NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_swiftq)
      .setColor(ContextCompat.getColor(context, R.color.swiftq_blue))
  ```
- **Splash screen** (`androidx.core:core-splashscreen`):
  ```xml
  <item name="windowSplashScreenBackground">@color/swiftq_blue</item>
  <item name="windowSplashScreenAnimatedIcon">@drawable/ic_swiftq_mark</item>
  ```
- **In-app**: `ic_swiftq_app_icon`, `ic_swiftq_mark`, `logo_swiftq_wordmark`, `logo_swiftq_wordmark_dark`
  ```kotlin
  Image(painterResource(R.drawable.logo_swiftq_wordmark), contentDescription = "Swift Q")
  ```
- **Colours**: `values/swiftq_colors.xml`
- **Play Store**: `play-store/ic_launcher-playstore-512.png` (icon) and `feature-graphic-1024x500.png`.

## Palette
| Token | Hex |
|---|---|
| System Blue | `#007AFF` |
| Light blue (dots, dark-mode accent) | `#6AAEF5` |
| Deep blue (gradient end) | `#0040CC` |
| Navy (centre dot) | `#0F2E6E` |
| Ink | `#1C1C1E` |
| Dark background | `#11151F` |
