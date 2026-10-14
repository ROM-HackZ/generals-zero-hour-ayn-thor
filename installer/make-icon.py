# GeneralsX @build Codex 14/10/2026 Draw the original two-screen handheld installer icon.
# Optional build tool: pip install Pillow. Generated icons are checked in.
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).parent
image = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
d = ImageDraw.Draw(image)
d.rounded_rectangle((24, 24, 1000, 1000), radius=220, fill='#172b37')
d.rounded_rectangle((235, 145, 789, 880), radius=80, fill='#78bfdc')
d.rounded_rectangle((320, 215, 704, 485), radius=25, fill='#11202a')
d.rounded_rectangle((320, 540, 704, 790), radius=25, fill='#11202a')
d.line((390, 435, 475, 310, 535, 380, 622, 260), fill='#a8dd82', width=22)
for x, y in [(379, 594), (480, 594), (581, 594), (379, 691), (480, 691), (581, 691)]:
    d.rounded_rectangle((x, y, x+60, y+60), radius=12, fill='#628ba2')
d.rounded_rectangle((172, 544, 274, 580), radius=7, fill='#e5f4fa')
d.rounded_rectangle((205, 511, 241, 613), radius=7, fill='#e5f4fa')
d.ellipse((795, 518, 841, 564), fill='#e5f4fa')
d.ellipse((848, 565, 894, 611), fill='#e5f4fa')
image.save(root / 'icon.png')
image.save(root / 'icon.ico', sizes=[(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)])
image.save(root / 'icon.icns')
