import json
import os
import glob
import shutil

textures_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\textures\item\magic_books"
models_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\models\item"

spells = [
    "expelliarmus", "glacius", "confringo", "protego", "lumos", 
    "accio", "wingardium_leviosa", "alohomora", "incendio", "stupefy", 
    "revelio", "avada_kedavra", "crucio", "imperio", "fiendfyre", 
    "sectumsempra", "morsmordre", "dolohov_curse", "incarcerous", 
    "heal", "tenebris_flight", "expecto_patronum"
]

existing_pngs = [os.path.basename(f) for f in glob.glob(os.path.join(textures_dir, "*.png"))]

# Just force everything that matches our list to standard format
for spell in spells:
    standard_name = f"magic_book_{spell}.png"
    
    # Try to find this spell in the existing pngs, ignoring case and ignoring "_spell"
    found = False
    for png in existing_pngs:
        lower_png = png.lower()
        if spell in lower_png.replace("_spell", "").replace(" ", "_"):
            if png != standard_name:
                os.rename(os.path.join(textures_dir, png), os.path.join(textures_dir, standard_name))
                print(f"Renamed: {png} -> {standard_name}")
            found = True
            break
            
print("Standardized all spellbook texture file names.")
