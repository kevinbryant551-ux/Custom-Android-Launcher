# Bear Bones icon pack: ChatGPT prompts

No coding needed on your end. Paste the style message into ChatGPT first, then each sheet message one at a
time. You'll get 4 images with 8 icons each. Save them as `sheet1.png` … `sheet4.png` and send them over;
importing them is one command:

```bash
python3 tools/iconpack/import.py sheet1.png sheet2.png sheet3.png sheet4.png
```

It finds the round icons on each sheet by shape (they don't need to line up perfectly), cuts them out with
transparent corners, and wires them to the right apps. If a sheet comes back with the wrong number of icons,
it says which one to redo.

The icons are an easter egg in the app: tap the date under the clock five times quickly to turn bear
icons on or off.

## 1. Style (send first)

```text
I'm making a matching icon set for a minimalist Android launcher called "Bear Bones". I'll ask for
4 sheets of 8 icons. Every sheet must follow these rules exactly:

Layout
- One image, landscape, 4 icons across and 2 rows down, evenly spaced, with clear empty space between
  icons so none of them touch.
- Plain, flat, pure white background. No text, labels, numbers, borders, shadows or frames anywhere.

Each icon
- A perfect filled circle in dark espresso brown (#2A1F17), all circles the same size.
- Inside it, one simple symbol in bone white (#EDE3CF), centered and filling about half the circle.
- Bold, clean line art with rounded ends, like a minimal outline icon set. Flat colors only: no
  gradients, glow, texture, 3D or shading.
- Generic symbols only. Do not draw any company's logo or brand mark.
- Friendly and slightly rounded, "bare bones": as few lines as needed to read at a small size.

Reply "ready" and wait for the first sheet's list.
```

## 2. Sheets (send one at a time, after it replies)

### Sheet 1 of 4

```text
Bear Bones icon sheet 1. Same style as before. Draw these 8 icons, in this exact order, left to right,
top row first:
1. telephone handset
2. speech bubble
3. camera body with round lens
4. globe with meridian lines
5. envelope
6. folded map with a location pin
7. landscape picture (mountain and sun in a frame)
8. gear / cog
```

### Sheet 2 of 4

```text
Bear Bones icon sheet 2. Same style as before. Draw these 8 icons, in this exact order, left to right,
top row first:
1. round clock face with two hands
2. calendar page with two rings on top
3. person bust (head and shoulders)
4. folder
5. shopping bag with a play triangle
6. eighth note (musical note)
7. play triangle inside a rounded screen
8. cloud
```

### Sheet 3 of 4

```text
Bear Bones icon sheet 3. Same style as before. Draw these 8 icons, in this exact order, left to right,
top row first:
1. notepad with lines
2. calculator with plus/minus keys
3. sun partly behind a cloud
4. wallet
5. two speech bubbles, one with the letter A
6. microphone
7. four-point sparkle star
8. magnifying glass
```

### Sheet 4 of 4

```text
Bear Bones icon sheet 4. Same style as before. Draw these 8 icons, in this exact order, left to right,
top row first:
1. two overlapping speech bubbles
2. video camera
3. shield
4. location pin with a small circle (find my device)
5. shopping cart
6. game controller
7. angle brackets </>
8. speech bubble with three dots
```

If an icon comes out wrong, reply with: "Redo sheet N, same layout and order, but make icon K a simpler
<description>."
