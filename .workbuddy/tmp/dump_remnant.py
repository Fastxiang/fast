import zipfile, re, sys, subprocess, os, tempfile

JAR = r"D:\Minecraft\异界随行诗\.minecraft\versions\otherworld_traveling_poems\mods\[灾变] L_Enders_Cataclysm-3.31.jar"
CLS = "com/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Ancient_Remnant/Ancient_Remnant_Entity.class"
JAVAP = r"C:\Users\ZhuanZ（无密码）\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\javap.exe"

z = zipfile.ZipFile(JAR)
out_dir = r"D:\Minecraft\fast_mod\fast-forge-1.20.1-47.4.0\build\catdump"
os.makedirs(out_dir, exist_ok=True)
p = os.path.join(out_dir, CLS.replace("/", os.sep))
os.makedirs(os.path.dirname(p), exist_ok=True)
with open(p, "wb") as f:
    f.write(z.read(CLS))

r = subprocess.run([JAVAP, "-p", "-c", "-cp", out_dir,
                    CLS.replace("/", ".").removesuffix(".class")],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
text = r.stdout
# only the hurt method body
i = text.find("public boolean m_6469_")
if i < 0:
    for name in ("m_6469_", "hurt"):
        i = text.find(name)
        if i >= 0:
            break
print(text[i:i+4000] if i >= 0 else text[:4000])
