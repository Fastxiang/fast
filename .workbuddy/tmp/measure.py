from PIL import Image

base = r'C:\Users\ZhuanZ（无密码）\Downloads'
im2 = Image.open(base + r'\QQ20261012-201541.png').convert('RGB')   # 货架（无弹窗）
im1 = Image.open(base + r'\QQ20261012-201534.png').convert('RGB')   # 详情弹窗开着

# 1080 显示坐标 -> 1920 真实坐标
S = 1920 / 1080.0


def box(b):
    return tuple(int(v * S) for v in b)


# --- 1) 热栏槽位间距（确定原版 GUI scale）---
hot = im2.crop(box((205, 455, 295, 500)))
hot.resize((hot.width * 6, hot.height * 6), Image.NEAREST).save('m_hotbar.png')
print('hotbar crop', hot.size)

# --- 2) 货架上第 2 行第 1 列卡片（价格 100,000，深蓝方块）---
card2 = im2.crop(box((378, 325, 420, 400)))
print('card2 size', card2.size)
card2.resize((card2.width * 8, card2.height * 8), Image.NEAREST).save('m_card2.png')

# 卡片内找图标 bbox：与卡片底色不同的像素
bg = card2.getpixel((3, 3))
print('card bg', bg)
minx, miny, maxx, maxy = 999, 999, -1, -1
for y in range(card2.height):
    for x in range(card2.width):
        r, g, b = card2.getpixel((x, y))
        if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > 40:
            minx = min(minx, x); miny = min(miny, y)
            maxx = max(maxx, x); maxy = max(maxy, y)
print('card2 非底色区域 bbox', (minx, miny, maxx, maxy), 'w', maxx - minx + 1, 'h', maxy - miny + 1)

# --- 3) 同一件物品：弹窗背后的那张卡（左边缘，价格 100,000）---
print()
print('--- 对照：弹窗背后的同名卡片 ---')
# 在 im1 里找左边缘的卡片区域
cand = im1.crop(box((368, 320, 425, 400)))
cand.resize((cand.width * 8, cand.height * 8), Image.NEAREST).save('m_card2_dim.png')
print('cand size', cand.size, 'corner', cand.getpixel((2, 2)))

# 统计两张图里"深色方块"区域的平均色
def darkest(img):
    px = list(img.getdata())
    return sorted(px, key=lambda p: sum(p))[:40]

print('card2(无弹窗) 最暗 40 像素均值', tuple(sum(c[i] for c in darkest(card2)) // 40 for i in range(3)))
print('card2(弹窗下) 最暗 40 像素均值', tuple(sum(c[i] for c in darkest(cand)) // 40 for i in range(3)))

# --- 4) 卡片底色对比（判断弹窗遮罩有没有压暗 DOM）---
print()
print('card2 左上底色(无弹窗)', card2.getpixel((3, 3)))
print('cand  左上底色(弹窗下)', cand.getpixel((3, 3)))
