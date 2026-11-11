# C177 — Barrier-grid animation kit

One new source workflow, registered at166/500: Photo tools → Barrier-grid kit generates a complete interlaced carrier and matching slotted-mask PNG pair from a supplied equal-grid sprite sheet. Carrier, mask, directions, strip widths and aperture playback are parts of one kit, not separate feature counts. Existing frame extraction/playback already exists and is not counted again.

## Geometry and artifact contract

Use the full embedded8–1024pixel source before drawing crop/rotation/flip/opacity. Choose2–6equal frames in row-major order, with whole0–128pixel margins/gaps and frames≥8pixels/side. Choose1–16pixel strips along columns or rows. The corresponding frame axis must fit at least one complete cycle. A partial final cycle remains at the original pixel grid; no resizing/cropping/dropping frames.

At carrier coordinate(x,y), let q=x for columns or y for rows. Source view is floor(q/stripWidth) modulo frameCount; use the same(x,y) within that view's source rectangle. Source alpha composites per encodedRGBchannel over white, rounded to8bits; carrier is opaque. Mask pixels are black, alpha0when that view is0 and alpha255otherwise. Transparent mask RGB is canonical zero. The mask is always exported for view1.

The basic period/opaque-opening relationship agrees with [the authors' FabObscura research description](https://hcie.csail.mit.edu/research/fabobscura/fabobscura.html): the transparent band matches one frame-strip width, while the opaque portion covers the remaining frame strips. This implementation uses its own explicit integer pixel contract and code; no research code was copied. It implements straight horizontal/vertical patterns only.

The aperture canvas reveals only one phase's carrier strips through opaque black bars. It is not a reconstructed full frame. Phase/playback are guide-only and do not change either PNG. Positive one-strip displacement advances the chosen view. Preview wraps the periodic mask at edges; physical edge coverage, same-scale printing, pixel pitch, actual transparency/cut slots and alignment remain the user's fabrication requirements. Ordinary opaque paper cannot provide transparent slots. No physical motion/print/material quality guarantee or calibrated lens/pitch claim.

## Ownership, exact output and insertion

The private source buffer produces carrier/mask together, yields every32outputrows and checks cancellation through final yield. Decoder/encoder owns handlers/listeners/canvas/timer with an overall30second deadline. Both PNGs must independently fit192KiB; any failure refuses the entire kit without resizing. Stop, hidden-page, settings, Escape/unmount/source edits cancel or invalidate review; both BlobURLs revoke. No personal uploads.

Exact selected carrier/mask handoff preserves reviewed PNG bytes. Insertion verifies both images before any publication, respects existing8photo/500object/draft/storage limits and inserts both images plus labels as one4objectgroup/oneUndo. Role/dimensions/viewcount/stripaxis/width/maskphase/convention metadata supports recovery. Display placement is scaled; later image/drawing exports can resample and change physical pitch. Source bytes/geometry stay intact.

## Verification and limits

7focused tests pass: independent everycarrier/maskRGBA source-rectangle oracle across axes/grids/gaps/margins/partialcycles; everyaperturephase/allpixelunion; hiddenRGB/whitealpha/transparentmask; asyncprivatecapture/progress/first-finalcancel; max1024source/1024×512outputs bothRGBAplanes; invalidsettings/buffers; exact4objectkitplan/rolemetadata/matchedplacement. All769drawing and70Node server/project/configuration tests pass; isolatedbundle/manifest pass.

Initialbrowser confirms both encodedPNGplanes but catches a blank initial aperture canvas: it mounts after BlobURLs are ready, so the paint effect must also depend on URLs. Lifecycle dependency fixed; initialfailure evidence retained in `.context/noble-fast-web/milestones/C177/initial-failure/`. Finalbrowser rerun PASS:6fullPNGplane cases across3configs with2/4views and8aperturephases/bothaxes/partialcycles; guidephase/playback preservePNGbytes; rawmaskfalse→retry, allURLrevoke, secondencoderStopHidden/syntheticoversize andsecondverifierEscape refusewithoutpartialpublication; sourcebytesgeometry/group/UndoRedo/reload/JSONSVG/quota/8file/500object/staleedit→Undo/320/390/844px44px. Both1024×512planes/all4194304RGBAbytesmatch; exactstoredPNGs/reload pass. NaturalnoisyJPEGsource yields1213722byte1024×512carrierPNG, refusingcompletekitat192KiBwithoutresizing. No page/CSP/externalerrors. Retainedcube currentbundle browser regression also passes; logs/receipt retained with C177 proof. No perfeature fullnative/APKbuild, device or physicalprint/motion acceptance. Preserve frozen native138 drawing assets, search/ranking/credits/catalog/ledger invariants.

SourceSHA256 `87301b681b7cf9057250eb5191ac3b90e9259620e9b16843413bde2479445b6e`; immutableproof `.context/noble-fast-web/milestones/C177/`. All271frozen138assetbytes/SHAunchanged; manifestSHA256 `9a7dff9a2c8195a8c69ebd2231f968e22f5081d4b35da2cdedf15710c30c519a`. This source is excluded from native138. 1newworkflow,0separateproductfix/scaffolding. Initialaperturepaintfix was partof the unregisteredfeature. RegistrationfollowedC160scope release; fresh165baseline preserved.
