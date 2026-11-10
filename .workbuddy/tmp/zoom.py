from PIL import Image

base = r'C:\Users\ZhuanZ（无密码）\Downloads'
im2 = Image.open(base + r'\QQ20261012-201541.png').convert('RGB')
im1 = Image.open(base + r'\QQ20261012-201534.png').convert('RGB')
S = 1920 / 1080.0


def crop(im, b, sc, out):
    x0, y0, x1, y1 = [int(v * S) for v in b]
    im.crop((x0, y0, x1, y1)).resize((int((x1 - x0) * sc), int((y1 - y0) * sc)), Image.NEAREST).save(out)


# 原版热栏（左下角）
crop(im2, (196, 452, 300, 502), 9, 'z_hotbar.png')
# 货架第 2 行（含锭类 3D 物品）
crop(im2, (420, 325, 600, 385), 9, 'z_row2.png')
# 详情弹窗预览图标
crop(im1, (438, 168, 480, 208), 12, 'z_preview.png')

# 直方图：热栏物品区域 vs 货架物品区域的平均亮度
def mean_lum(img):
    d = list(img.convert('L').getdata())
    return round(sum(d) / len(d), 1)


crop(im2, (196, 452, 300, 502), 1, 'z_hotbar_raw.png')
crop(im2, (420, 325, 600, 385), 1, 'z_row2_raw.png')
print('hotbar mean lum', mean_lum(Image.open('z_hotbar_raw.png')))
print('row2   mean lum', mean_lum(Image.open('z_row2_raw.png')))
print('ok')
