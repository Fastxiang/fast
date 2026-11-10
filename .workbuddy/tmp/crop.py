from PIL import Image

base = r'C:\Users\ZhuanZ（无密码）\Downloads'
S = 1920 / 1080.0


def crop(im, box, scale, out):
    x0, y0, x1, y1 = [int(v * S) for v in box]
    im.crop((x0, y0, x1, y1)).resize(
        (int((x1 - x0) * scale), int((y1 - y0) * scale)), Image.NEAREST
    ).save(out)
    print(out, (x0, y0, x1, y1))


im2 = Image.open(base + r'\QQ20261012-201541.png').convert('RGB')
crop(im2, (375, 250, 715, 460), 2.5, 'crop_grid.png')
crop(im2, (190, 445, 300, 505), 6, 'crop_hotbar.png')

im1 = Image.open(base + r'\QQ20261012-201534.png').convert('RGB')
crop(im1, (425, 150, 665, 420), 3, 'crop_dialog.png')
print('ok')
