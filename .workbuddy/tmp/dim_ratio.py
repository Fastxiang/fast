from PIL import Image
import collections


def stats(path, label):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    print("=== %s  %dx%d ===" % (label, w, h))
    cnt = collections.Counter(im.getdata())
    tot = w * h
    for c, n in cnt.most_common(8):
        print("   %-16s %8d  %5.2f%%" % (str(c), n, 100.0 * n / tot))
    return im


a = stats(r"D:\Minecraft\fast_mod\fast-forge-1.20.1-47.4.0\.workbuddy\tmp\m_card2.png", "normal")
b = stats(r"D:\Minecraft\fast_mod\fast-forge-1.20.1-47.4.0\.workbuddy\tmp\m_card2_dim.png", "dim")

print()
print("=== top-1 vs top-1 ===")
ca = collections.Counter(a.getdata()).most_common(1)[0][0]
cb = collections.Counter(b.getdata()).most_common(1)[0][0]
print("normal bg", ca, "dim bg", cb)
for i, nm in enumerate("RGB"):
    if ca[i]:
        print("  %s: dim/normal = %.4f   implied overlay alpha if black = %.3f"
              % (nm, cb[i] / float(ca[i]), 1.0 - cb[i] / float(ca[i])))
