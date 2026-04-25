import os
import glob
from PIL import Image

def process_image(img_path, dest_path):
    try:
        img = Image.open(img_path).convert("RGBA")
        img = img.resize((32, 32), Image.Resampling.LANCZOS)
        
        # Make white background transparent
        data = img.getdata()
        new_data = []
        for item in data:
            if item[0] > 230 and item[1] > 230 and item[2] > 230:
                new_data.append((255, 255, 255, 0))
            else:
                new_data.append(item)
        img.putdata(new_data)
        img.save(dest_path)
        print(f"Processed {os.path.basename(img_path)} -> {dest_path}")
    except Exception as e:
        print(f"Failed to process {img_path}: {e}")

src_dir = r"C:\Users\alonl\.gemini\antigravity\brain\5bffee5e-2500-475d-b4db-84ac5ea87058"
dest_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\textures\item"
os.makedirs(dest_dir, exist_ok=True)

# Mapping generated files to actual texture names
for file in glob.glob(os.path.join(src_dir, "*.png")):
    basename = os.path.basename(file)
    if "wand_harry_potter" in basename:
        process_image(file, os.path.join(dest_dir, "harry_potter_wand.png"))
    elif "wand_phoenix" in basename:
        process_image(file, os.path.join(dest_dir, "phoenix_core_wand.png"))
    elif "wand_unicorn" in basename:
        process_image(file, os.path.join(dest_dir, "unicorn_core_wand.png"))
    elif "wand_dragon" in basename:
        process_image(file, os.path.join(dest_dir, "dragon_core_wand.png"))
    elif "fine_wood_stick" in basename:
        process_image(file, os.path.join(dest_dir, "fine_wood_stick.png"))
