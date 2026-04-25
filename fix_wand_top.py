import os
import random
from PIL import Image, ImageDraw

def create_top_from_side(side_path, dest_path):
    # Open the side texture to use as our consistent base
    try:
        base_img = Image.open(side_path).convert("RGBA")
        
        # It's currently 64x64. Let's create a new image for the top
        # We can just rotate the side texture to make it look like planks from the top
        top_img = base_img.rotate(90)
        
        # Now let's draw some pixel-art style tools on top so it looks like a workbench!
        # We want to match the 64x64 scale. 
        draw = ImageDraw.Draw(top_img)
        
        # Draw a glowing core (like a small orb or magic string)
        # We'll make it a light blue/magenta glow
        glow_color = (200, 100, 255, 200)
        center_color = (255, 200, 255, 255)
        
        # A magic core in the center
        draw.ellipse([26, 26, 38, 38], fill=glow_color)
        draw.ellipse([30, 30, 34, 34], fill=center_color)
        
        # Draw a wand shaft (a fine wood stick)
        shaft_color = (120, 60, 20, 255)
        draw.line([15, 50, 48, 15], fill=shaft_color, width=4)
        
        # Draw a carving tool
        blade_color = (200, 200, 200, 255)
        handle_color = (60, 30, 10, 255)
        draw.line([48, 50, 56, 58], fill=handle_color, width=3)
        draw.polygon([(44, 46), (48, 50), (42, 52)], fill=blade_color)

        # Draw some wood chips
        chip_color = (200, 160, 100, 255)
        for _ in range(12):
            x = random.randint(10, 50)
            y = random.randint(10, 50)
            # Avoid the exact center where the core is
            if not (20 < x < 44 and 20 < y < 44):
                draw.rectangle([x, y, x+2, y+2], fill=chip_color)

        top_img.save(dest_path)
        print(f"Successfully generated new matching top texture at {dest_path}")
        
    except Exception as e:
        print(f"Error: {e}")

side_tex = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\block\wand_workbench_side.png"
top_tex = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\block\wand_workbench_top.png"

create_top_from_side(side_tex, top_tex)
