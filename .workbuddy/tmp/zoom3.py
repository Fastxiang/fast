from PIL import Image
base = r'C:/Users/ZhuanZ（无密码）/Downloads'
im2 = Image.open(base + r'\QQ20261012-201541.png').convert('RGB')
S = 1920 / 1080.0
def crop(b, sc, out):
    x0, y0, x1, y1 = [int(v * S) for v in b]
    im2.crop((x0, y0, x1, y1)).resize((int((x1-x0)*sc), int((y1-y0)*sc)), Image.NEAREST).save(out)
# 副标题 FOREST GOODS · OPEN DAILY + 标题
crop((430, 55, 660, 95), 7, 't_title.png')
# 一排价格数字（拉丁数字，最能看出像素/矢量）
crop((385, 250, 715, 275), 7, 't_prices.png')
