import zipfile

JAR = r"D:\Minecraft\异界随行诗\.minecraft\versions\otherworld_traveling_poems\mods\[灾变] L_Enders_Cataclysm-3.31.jar"

z = zipfile.ZipFile(JAR)
for n in z.namelist():
    if "Ancient_Remnant" in n and n.endswith(".class") and "client" not in n:
        print(n)
