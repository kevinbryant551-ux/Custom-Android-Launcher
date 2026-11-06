# Bear Bones icon pack: ChatGPT prompt

Paste everything inside the box below into ChatGPT (a model that can run Python). It draws the icons with
code, so they come out consistent and named exactly as the app expects. Then import them:

```bash
python3 tools/iconpack/import.py ~/Downloads/bear-bones-icons.zip
```

Rebuild the app. It's an easter egg: tap the date under the clock five times quickly to turn bear icons on or off.

---

```text
Make an Android icon pack for a minimalist launcher called "Bear Bones". Use Python (Pillow, or
cairosvg/svgwrite rendered to PNG) to DRAW every icon with code so the set is perfectly consistent. Do not
use an image model and do not copy any company's logo: each icon is a simple, generic symbol.

Style, identical for every icon:
- Canvas 432x432 px, transparent outside a filled circle of diameter 432 in espresso brown #2A1F17.
- One symbol per icon, drawn in bone white #EDE3CF, centered, fitting inside a 200x200 px box.
- Line-art: strokes 22 px wide, round caps and round joins; at most one small solid fill for emphasis
  (for example a dot or a lens). No gradients, shadows, text or extra decoration (the "translate" icon may
  use a single letter A).
- Friendly, slightly rounded geometry, the "bare bones" look: as few lines as it takes to read clearly
  at 48 px. Optional: give each symbol one tiny bear-ear bump only if it still reads at small size.
- Export PNG, then downscale with high-quality resampling to 192x192 px.

Make exactly these files (names must match):
- `bb_phone.png`: telephone handset
- `bb_messages.png`: speech bubble
- `bb_camera.png`: camera body with round lens
- `bb_browser.png`: globe with meridian lines
- `bb_mail.png`: envelope
- `bb_maps.png`: folded map with a location pin
- `bb_photos.png`: landscape picture (mountain and sun in a frame)
- `bb_settings.png`: gear / cog
- `bb_clock.png`: round clock face with two hands
- `bb_calendar.png`: calendar page with two rings on top
- `bb_contacts.png`: person bust (head and shoulders)
- `bb_files.png`: folder
- `bb_store.png`: shopping bag with a play triangle
- `bb_music.png`: eighth note (musical note)
- `bb_video.png`: play triangle inside a rounded screen
- `bb_cloud.png`: cloud
- `bb_notes.png`: notepad with lines
- `bb_calculator.png`: calculator with plus/minus keys
- `bb_weather.png`: sun partly behind a cloud
- `bb_wallet.png`: wallet
- `bb_translate.png`: two speech bubbles, one with the letter A
- `bb_recorder.png`: microphone
- `bb_assistant.png`: four-point sparkle star
- `bb_search.png`: magnifying glass
- `bb_chat.png`: two overlapping speech bubbles
- `bb_videocall.png`: video camera
- `bb_shield.png`: shield
- `bb_locate.png`: location pin with a small circle (find my device)
- `bb_shopping.png`: shopping cart
- `bb_games.png`: game controller
- `bb_code.png`: angle brackets </>
- `bb_forum.png`: speech bubble with three dots

When done: show a contact sheet of all icons in a 6-column grid on a mid-grey background so I can check
them, then give me a single zip named bear-bones-icons.zip containing only the PNG files listed above.
If any icon is unclear at small size, redraw it simpler before zipping.
```
