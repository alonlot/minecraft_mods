import os
from PIL import Image

def process_block_image(img_path, dest_path, size=(64, 64)):
    try:
        img = Image.open(img_path).convert("RGBA")
        img = img.resize(size, Image.Resampling.LANCZOS)
        img.save(dest_path)
        print(f"Saved block texture to {dest_path}")
    except Exception as e:
        print(f"Error processing {img_path}: {e}")

top_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\wand_workbench_top_1775303458144.png"
side_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\wand_workbench_side_1775303645023.png"

block_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\block"
os.makedirs(block_dir, exist_ok=True)

process_block_image(top_src, os.path.join(block_dir, "wand_workbench_top.png"))
process_block_image(side_src, os.path.join(block_dir, "wand_workbench_side.png"))
# Use side texture for bottom as well since it wasn't generated
process_block_image(side_src, os.path.join(block_dir, "wand_workbench_bottom.png"))
