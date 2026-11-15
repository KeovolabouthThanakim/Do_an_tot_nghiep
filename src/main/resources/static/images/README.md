# KOSIS image library

Folders follow the project brief: `logo/ hero/ products/ collections/ editorial/ brands/ banners/`.

| Folder | Contents |
|---|---|
| `hero/` | Homepage campaign photo (`hero-fashion-campaign`) |
| `products/` | 24 product cut-outs on white (4:5) + `placeholder.svg` |
| `collections/` | Model / lifestyle photography used in grids, banners and avatars |
| `editorial/` | Texture and campaign detail photos |
| `logo/` | `kosis-logo.svg` (the header uses live text so it stays crisp) |

Every photo exists as `.webp` (used by the site) and `.jpg` (fallback).

**Important – resolution.** The supplied source images are small crops (the hero was 504 px wide,
most products ~130-190 px). They were cleaned (slivers removed, backgrounds whitened, text removed
from the hero) and upscaled 3-4x, so they look soft on large/retina screens. Drop in real high-resolution
photography with the SAME file names (hero >= 2400 px wide, products >= 1200 x 1500) and nothing else
needs to change.

**Update (2026-11-04).** hero/, collections/ (except `model-womens-bag-look`) and editorial/ were replaced with the
images from `kosis_non_product_high_quality_jpg.zip`, upscaled 4x with ESPCN super-resolution + light sharpening
(hero capped at 2400 px wide). The source crops were still small (134-706 px), so these are sharper than before but are
not true high-resolution photos; replace them with real photography using the same file names when available.
