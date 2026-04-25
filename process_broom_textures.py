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

def process_item_image(img_path, dest_path, size=(64, 64)):
    try:
        img = Image.open(img_path).convert("RGBA")
        img = img.resize(size, Image.Resampling.LANCZOS)
        
        # Make very light background pixels transparent
        data = img.getdata()
        new_data = []
        for item in data:
            if item[0] > 230 and item[1] > 230 and item[2] > 230:
                new_data.append((255, 255, 255, 0))
            else:
                new_data.append(item)
        img.putdata(new_data)
        img.save(dest_path)
        print(f"Saved item texture to {dest_path}")
    except Exception as e:
        print(f"Error processing {img_path}: {e}")

top_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\broom_workbench_top_1775297494545.png"
side_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\broom_workbench_side_1775297936441.png"
bottom_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\broom_workbench_bottom_1775297984312.png"
item_src = r"C:\Users\alonl\.gemini\antigravity\brain\7229a1f8-ea42-4e07-ac7c-56fb78f1f4fd\broom_workbench_item_1775297996861.png"

block_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\block"
item_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\item"

os.makedirs(block_dir, exist_ok=True)
os.makedirs(item_dir, exist_ok=True)

process_block_image(top_src, os.path.join(block_dir, "broom_workbench_top.png"))
process_block_image(side_src, os.path.join(block_dir, "broom_workbench_side.png"))
process_block_image(bottom_src, os.path.join(block_dir, "broom_workbench_bottom.png"))

process_item_image(item_src, os.path.join(item_dir, "broom_workbench.png"))
