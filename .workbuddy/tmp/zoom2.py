from PIL import Image
base = r'C:/Users/ZhuanZ（无密码）/Downloads'
im2 = Image.open(base + r'\QQ20261012-201541.png').convert('RGB')
S = 1920 / 1080.0
def crop(b, sc, out):
    x0, y0, x1, y1 = [int(v * S) for v in b]
    im2.crop((x0, y0, x1, y1)).resize((int((x1-x0)*sc), int((y1-y0)*sc)), Image.NEAREST).save(out)
    print(out, (x1-x0, y1-y0))
crop((378, 358, 600, 400), 9, 'z2_items.png')
