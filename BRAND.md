# LinkBridge — brand system

## Positioning

**LinkBridge** is a calm, human way to move an Internet connection between two nearby phones without asking users to configure a classic Wi‑Fi hotspot.

## Tagline

> Un pont, pas un hotspot.

## Brand idea

The mark is two small bridge pillars that become one path when placed together. It represents proximity, consent and a connection that is visible rather than secret.

The new mark keeps the same idea but in a restrained, professional look: deep violet background, white arcs, a single turquoise accent and one amber dot. No childish rainbow of colors.

## Voice

- Clear: explain one action at a time.
- Reassuring: always show what Android is doing.
- Practical: prefer useful status messages over technical jargon.
- Respectful: never imply that the VPN or the link is invisible to the device owner.

## Palette

| Token | Hex | Use |
|---|---|---|
| Ink | `#1E1B4B` | primary text, hero surfaces, logo base |
| Canvas | `#FFFFFF` | app background, card surfaces |
| Primary | `#6D55D9` | primary action and focus (violet from the icon) |
| Accent | `#14B8A6` | active/positive state only, used sparingly (turquoise from the icon) |
| OnSurface | `#1E1B4B` | text on white surfaces |
| OnSurfaceVariant | `#6B7280` | secondary text on white surfaces |

### Deprecated (temporary aliases, being removed)

| Old token | Hex | Replaced by |
|---|---|---|
| Purple | `#6D55D9` | Primary |
| Coral | `#FF7657` | Accent |
| Mint | `#C7F7E8` | Primary (transparent) |
| Butter | `#FFE7A8` | Primary (transparent) |
| Lavender | `#EAE3FF` | Primary (transparent) |
| Background | `#FFFFFBFF` | Canvas |

## Typography

Use Inter, embedded in `res/font` at four weights (Regular, Medium, SemiBold, Bold). Nothing is downloaded and nothing depends on Google Play Services: the files are shipped in the APK, which works offline and gives the same rendering as the Windows version, which embeds the exact same files.

## UI direction

The product uses Material 3 principles with a restrained, professional look:

- white or near-white background throughout;
- deep violet hero surface for the status card;
- subtle borders instead of heavy color blocks;
- one primary action per screen;
- rounded shapes with subtle motion;
- explicit VPN and nearby-device consent;
- no decorative AI artwork, no flashy colors.

The source logo is hand-authored vector artwork in `branding/linkbridge-mark.svg`. The Android launcher artwork is in `app/src/main/res/drawable/ic_linkbridge.xml` and `ic_launcher_foreground.xml`. The new PNG icon provided by the user is in `uploads/linkbridge-icone.png`.
