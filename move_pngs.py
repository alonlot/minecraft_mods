import os
import glob
import shutil

src_dir = r"C:\Users\alonl\.gemini\antigravity\brain\9e53aaf9-c0fe-4c39-87d4-1ab480463eb3"
dest_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\textures\item\magic_books"

os.makedirs(dest_dir, exist_ok=True)
spells = ["glacius", "confringo", "accio", "wingardium_leviosa", "revelio", 
          "dolohov_curse", "incarcerous", "heal", "tenebris_flight"]

for spell in spells:
    files = glob.glob(os.path.join(src_dir, f"magic_book_{spell}_*.png"))
    if files:
        latest = sorted(files)[-1]
        dest = os.path.join(dest_dir, f"magic_book_{spell}.png")
        shutil.copy2(latest, dest)
        print(f"Copied {os.path.basename(latest)} -> {os.path.basename(dest)}")
    else:
        print(f"Warning: No generated file for {spell} found.")
