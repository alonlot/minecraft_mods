import os
from PIL import Image, ImageDraw

def create_venom(dest):
    img = Image.new("RGBA", (16, 16), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    # Glass vial outline
    d.line([(6, 14), (9, 14)], fill=(150, 150, 150, 255))
    d.line([(5, 13), (5, 8)], fill=(150, 150, 150, 255))
    d.line([(10, 13), (10, 8)], fill=(150, 150, 150, 255))
    d.line([(6, 7), (6, 5)], fill=(150, 150, 150, 255))
    d.line([(9, 7), (9, 5)], fill=(150, 150, 150, 255))
    d.line([(7, 4), (8, 4)], fill=(100, 50, 20, 255)) # cork
    # Venom liquid
    d.rectangle([6, 9, 9, 13], fill=(0, 255, 0, 200))
    d.point((6, 10), fill=(100, 255, 100, 255))
    img.save(dest)

def create_villager_profession(dest):
    img = Image.new("RGBA", (128, 64), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    # A simple brown apron for wandmaker
    d.rectangle([16, 20, 32, 40], fill=(120, 80, 40, 255))
    d.rectangle([20, 24, 28, 36], fill=(100, 60, 30, 255))
    # Hat or goggles
    d.rectangle([0, 0, 16, 8], fill=(60, 40, 20, 255))
    img.save(dest)

os.makedirs(r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\item", exist_ok=True)
os.makedirs(r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\entity\villager\profession", exist_ok=True)

create_venom(r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\item\acromantula_venom.png")
create_villager_profession(r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures\entity\villager\profession\wandmaker.png")
print("Done")
