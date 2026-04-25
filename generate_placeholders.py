import os
from PIL import Image, ImageDraw
import random

def create_item(dest, color_main, color_highlight, shape="dust"):
    img = Image.new("RGBA", (16, 16), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    if shape == "dust":
        # Draw small scattered pixels
        for _ in range(30):
            x, y = random.randint(2, 13), random.randint(2, 13)
            col = color_main if random.random() > 0.3 else color_highlight
            d.point((x, y), fill=col)
    elif shape == "core":
        # Draw a glowing orb
        d.ellipse([4, 4, 11, 11], fill=color_main, outline=color_highlight)
        d.point((6, 6), fill=(255,255,255,255))
    elif shape == "feather":
        # Draw a feather shape
        d.line([(4,12), (12,4)], fill=color_main, width=2)
        d.line([(5,11), (10,3)], fill=color_highlight, width=1)
        d.line([(6,11), (13,6)], fill=color_highlight, width=1)
    elif shape == "hair":
        # Draw a curved strand
        d.arc([4, 4, 12, 12], 90, 270, fill=color_main, width=1)
        d.arc([5, 5, 11, 11], 90, 270, fill=color_highlight, width=1)
    elif shape == "string":
        # Draw a thick string
        d.line([(3,10), (12,6)], fill=color_main, width=2)
        d.line([(4,11), (13,7)], fill=color_highlight, width=1)
    elif shape == "table":
        # Draw a small table
        d.rectangle([3, 8, 12, 11], fill=color_main)
        d.rectangle([4, 11, 5, 15], fill=color_highlight)
        d.rectangle([10, 11, 11, 15], fill=color_highlight)
        # Add some random pixels for tools on top
        for _ in range(5):
            x = random.randint(4, 11)
            y = random.randint(6, 7)
            d.point((x,y), fill=(200,200,200,255))
    img.save(dest)

def create_block(dest, color_main, color_highlight, pattern="wood"):
    img = Image.new("RGBA", (16, 16), color_main)
    d = ImageDraw.Draw(img)
    if pattern == "wood":
        for y in range(16):
            if y % 4 == 0 or random.random() > 0.7:
                d.line([(0, y), (15, y)], fill=color_highlight, width=1)
    elif pattern == "wood_top":
        for y in range(16):
            if y % 4 == 0:
                d.line([(0, y), (15, y)], fill=color_highlight, width=1)
        # scatter some tools
        for _ in range(8):
            x, y = random.randint(1, 14), random.randint(1, 14)
            d.point((x, y), fill=(100,100,100,255))
    img.save(dest)

item_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\textures\item"
block_dir = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\examplemod\textures\block"
os.makedirs(item_dir, exist_ok=True)
os.makedirs(block_dir, exist_ok=True)

# Items
create_item(os.path.join(item_dir, "arcane_dust.png"), (150, 50, 200, 255), (200, 100, 255, 255), "dust")
create_item(os.path.join(item_dir, "arcane_core.png"), (100, 50, 200, 255), (150, 100, 255, 255), "core")
create_item(os.path.join(item_dir, "phoenix_feather_core.png"), (220, 50, 30, 255), (255, 150, 50, 255), "feather")
create_item(os.path.join(item_dir, "unicorn_hair_core.png"), (200, 200, 220, 255), (255, 255, 255, 255), "hair")
create_item(os.path.join(item_dir, "dragon_heartstring_core.png"), (100, 20, 20, 255), (150, 50, 50, 255), "string")

create_item(os.path.join(item_dir, "wand_workbench.png"), (120, 80, 40, 255), (80, 50, 20, 255), "table")
create_item(os.path.join(item_dir, "broom_workbench.png"), (140, 100, 60, 255), (100, 70, 30, 255), "table")

# Blocks
create_block(os.path.join(block_dir, "wand_workbench_top.png"), (120, 80, 40, 255), (100, 60, 30, 255), "wood_top")
create_block(os.path.join(block_dir, "wand_workbench_side.png"), (100, 60, 30, 255), (80, 40, 20, 255), "wood")
create_block(os.path.join(block_dir, "wand_workbench_bottom.png"), (80, 40, 20, 255), (60, 30, 10, 255), "wood")

create_block(os.path.join(block_dir, "broom_workbench_top.png"), (140, 100, 60, 255), (120, 80, 40, 255), "wood_top")
create_block(os.path.join(block_dir, "broom_workbench_side.png"), (120, 80, 40, 255), (100, 60, 30, 255), "wood")
create_block(os.path.join(block_dir, "broom_workbench_bottom.png"), (100, 60, 30, 255), (80, 40, 20, 255), "wood")

print("Placeholder generation complete.")
