# Membuat ikon peluncur dari art/logo.jpg (5.0.1). Jalankan dari folder proyek:
#   python3 art/make_icons.py art/logo.jpg app/src/main/res /tmp
# Butuh Pillow. Koordinat potong di bawah sesuai logo 2048x2048 (kotak biru membulat).
import sys
from PIL import Image, ImageDraw, ImageFilter
src, res, out = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert('RGB')
x0, y0, x1, y1 = 114, 112, 1934, 1932          # inside the navy rounded square
art = im.crop((x0, y0, x1, y1))
N = art.size[0]
NAVY = (16, 31, 79)
def rmask(n, r, ss=4):
    m = Image.new('L', (n*ss, n*ss), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, n*ss-1, n*ss-1), r*ss, fill=255)
    return m.resize((n, n), Image.LANCZOS)
mask = rmask(N, 350)
# Legacy icon (Android < 8): rounded logo on transparent background.
legacy = Image.new('RGBA', (N, N), (0, 0, 0, 0)); legacy.paste(art, (0, 0), mask)
# Adaptive foreground: logo with corners filled navy, soft edge so it blends into the navy background.
filled = Image.new('RGB', (N, N), NAVY); filled.paste(art, (0, 0), mask)
edge = Image.new('L', (N, N), 0)
ImageDraw.Draw(edge).rectangle((60, 60, N-61, N-61), fill=255)
edge = edge.filter(ImageFilter.GaussianBlur(40))
fg_art = filled.convert('RGBA'); fg_art.putalpha(edge)
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for d, k in dens.items():
    s = round(48*k)
    legacy.resize((s, s), Image.LANCZOS).save(f'{res}/mipmap-{d}/icon.png', optimize=True)
    c = round(108*k); a = round(66*k)       # 108dp canvas, logo 66dp: text stays inside circle masks
    fg = Image.new('RGBA', (c, c), (0, 0, 0, 0))
    fg.alpha_composite(fg_art.resize((a, a), Image.LANCZOS), ((c-a)//2, (c-a)//2))
    fg.save(f'{res}/drawable-{d}/icon_fg.png', optimize=True)
# Previews: circle & squircle masked adaptive icon, plus legacy.
c = 432
for name, shape in [('circle', 'circle'), ('squircle', 'squircle')]:
    bg = Image.new('RGBA', (c, c), NAVY + (255,))
    bg.alpha_composite(Image.open(f'{res}/drawable-xxxhdpi/icon_fg.png'))
    m = Image.new('L', (c, c), 0); dr = ImageDraw.Draw(m)
    inset = (c - round(72*4)) // 2                 # launchers show the inner 72dp
    if shape == 'circle': dr.ellipse((inset, inset, c-inset, c-inset), fill=255)
    else: dr.rounded_rectangle((inset, inset, c-inset, c-inset), 90, fill=255)
    o = Image.new('RGBA', (c, c), (255, 255, 255, 255)); o.paste(bg, (0, 0), m); o.save(f'{out}/preview_{name}.png')
