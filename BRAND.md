# LinkBridge — brand system

## Positioning

**LinkBridge** is a calm, human way to move an Internet connection between two nearby phones without asking users to configure a classic Wi‑Fi hotspot.

## Tagline

> Un pont, pas un hotspot.

## Brand idea

The mark is two small bridge pillars that become one path when placed together. It represents proximity, consent and a connection that is visible rather than secret.

## Voice

- Clear: explain one action at a time.
- Reassuring: always show what Android is doing.
- Practical: prefer useful status messages over technical jargon.
- Respectful: never imply that the VPN or the link is invisible to the device owner.

## Palette

| Token | Hex | Use |
|---|---|---|
| Ink | `#251A46` | primary text, hero surfaces, logo base |
| Bridge Purple | `#6D55D9` | primary action and focus |
| Coral Signal | `#FF7657` | active state and attention |
| Bridge Mint | `#C7F7E8` | positive state, paired link |
| Warm Butter | `#FFE7A8` | pairing code card |
| Soft Lavender | `#EAE3FF` | receive mode surfaces |
| Canvas | `#FFFBFF` | app background |

## Typography

One typeface, embedded in the product: **Inter** (SIL Open Font License 1.1). It is shipped
inside the APK and inside the Windows application, so the rendering is identical on Android
and on Windows and nothing is fetched at runtime.

- Weights: Regular 400, Medium 500, SemiBold 600, Bold 700 (`app/src/main/res/font/inter_*.ttf`,
  `desktop/src/main/resources/fonts/inter-*.ttf`).
- Every Material 3 text style is overridden with Inter, on both platforms.
- French coverage is required and verified: é è ê à ç œ « » plus the narrow no-break space used
  before : ; ! ? and inside guillemets.
- Desktop sizing is mouse-adapted: titles 18–20 sp, body 13–14 sp, buttons 36–40 dp high.
  Android keeps the mobile scale (body 14–16 sp, targets 48 dp).

Clarity City was considered and is a valid OFL typeface with correct French coverage, but it is
not distributed through Google Fonts and its upstream repository is archived, so Inter was chosen
instead. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/INTER_OFL.txt`.

## UI direction

The product uses Material 3 Expressive principles without decorative AI artwork:

- contained hero surface with a clear status dot;
- intentionally oversized role cards;
- asymmetric but predictable color pairing;
- rounded shapes with expressive motion;
- one primary action per screen;
- explicit VPN and nearby-device consent.

The source logo is hand-authored vector artwork in `branding/linkbridge-mark.svg` and the Android launcher artwork is in `app/src/main/res/drawable/ic_linkbridge.xml`.
