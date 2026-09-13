#!/usr/bin/env python3
"""Encode paced, captioned walkthroughs from unaltered browser screenshots.

Run from any directory: python3 docs/demo/build-gifs.py
Requires Pillow. Source screenshots are checked in beside this script.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT_PATHS = [
    Path('/System/Library/Fonts/Supplemental/Arial.ttf'),
    Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'),
]
font_path = next((p for p in FONT_PATHS if p.exists()), None)
font = ImageFont.truetype(str(font_path), 25) if font_path else ImageFont.load_default(size=25)
small = ImageFont.truetype(str(font_path), 18) if font_path else ImageFont.load_default(size=18)

walkthroughs = {
    'prepare-stock.gif': [
        ('frames/01-inventory.png', 'Start with the cards you plan to bring.', 5000),
        ('frames/01-upload.png', 'Upload a CSV and choose the convention for this stock.', 5000),
        ('frames/01-resolve.png', 'An abbreviated set name? Choose the correct Charizard printing.', 6000),
        ('frames/01-preview.png', 'Review the cards, condition, quantities, and asking prices.', 6000),
        ('frames/01-complete.png', 'Commit the reviewed rows. Your inventory is ready.', 4000),
        ('assets/buy-list.png', 'Your buy list tells other vendors what you want and your budget.', 6000),
    ],
    'find-matches.gif': [
        ('frames/02-event.png', 'Start with the convention you are attending.', 5000),
        ('frames/02-dallas.png', 'Dallas: sell Pikachu for $8 or buy Rayquaza for $140.', 7000),
        ('frames/02-portland.png', 'Switch to Portland: different vendors, different matches.', 6000),
        ('frames/02-saved.png', 'Back in Dallas, save the Pikachu opportunity to your plan.', 6000),
        ('frames/02-dashboard.png', 'The dashboard tracks your saved matches and event preparation.', 6000),
    ],
}

for output, scenes in walkthroughs.items():
    frames = []
    durations = []
    for index, (source, caption, duration) in enumerate(scenes, 1):
        with Image.open(ROOT / source) as screenshot:
            screenshot = screenshot.convert('RGB')
            if screenshot.size != (1280, 720):
                raise ValueError(f'{source}: expected 1280x720, got {screenshot.size}')
            frame = Image.new('RGB', (1280, 818), '#26344f')
            frame.paste(screenshot, (0, 0))
        draw = ImageDraw.Draw(frame)
        draw.text((30, 734), f'VENDEX  /  {index:02d} OF {len(scenes):02d}', fill='#c4d2e8', font=small)
        draw.text((30, 766), caption, fill='white', font=font)
        # Keep subtle UI gradients while reserving colors for small card artwork.
        palette = Image.new('P', (1, 1))
        dominant = frame.quantize(colors=224).getpalette()[:224 * 3]
        accents = frame.quantize(colors=32, method=Image.Quantize.MAXCOVERAGE).getpalette()[:32 * 3]
        palette.putpalette(dominant + accents)
        frames.append(frame.quantize(palette=palette))
        durations.append(duration)
    target = ROOT / 'assets' / output
    frames[0].save(target, save_all=True, append_images=frames[1:], duration=durations,
                   loop=0, optimize=True, disposal=2)
    print(f'{output}: {sum(durations) / 1000:g}s, {target.stat().st_size / 1024:.0f} KiB')
