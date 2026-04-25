from PIL import Image
import os

def check_create_image(path, size=(64, 64)):
    try:
        img = Image.open(path).convert("RGBA")
        if img.size != size:
            img = img.resize(size, Image.Resampling.LANCZOS)
        return img
    except FileNotFoundError:
        print(f"Error: {path} not found.")
        return Image.new("RGBA", size, (0, 0, 0, 0))

def remap_dementor(source_path, dest_path):
    print("Remapping Dementor...")
    src = check_create_image(source_path, (256, 256)) # Source is usually larger
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    
    # We sample different neat-looking areas of the 256x256 abstract texture
    
    # Head (8x8x8) -> UV: 0, 0. Needs 32x16 space.
    head_tex = src.crop((0, 0, 32, 16))
    out.paste(head_tex, (0, 0))
    
    # Body (8x16x4) -> UV: 16, 16. Needs 24x16 space.
    body_tex = src.crop((32, 32, 56, 48))
    out.paste(body_tex, (16, 16))
    
    # Arms (3x18x3) -> UV: 40, 16. Needs 12x18 space.
    arm_tex = src.crop((64, 64, 76, 82))
    out.paste(arm_tex, (40, 16))
    
    # Lower Cloak (10x16x6) -> UV: 0, 36. Needs 32x16 space.
    cloak_tex = src.crop((128, 128, 160, 144))
    out.paste(cloak_tex, (0, 36))
    
    out.save(dest_path)
    print("Dementor UV mapped!")

def remap_patronus(source_path, dest_path):
    print("Remapping Patronus...")
    src = check_create_image(source_path, (256, 256))
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    
    # Body (8x10x18) -> UV: 0, 0. Needs 52x28 space. 
    body_tex = src.crop((0, 0, 52, 28))
    out.paste(body_tex, (0, 0))
    
    # Head (6x7x10) -> UV: 0, 28. Needs 32x17 space.
    head_tex = src.crop((64, 64, 96, 81))
    out.paste(head_tex, (0, 28))
    
    # Antlers (16x14x0) -> UV: 32, 28. Needs 16x14 space.
    antler_tex = src.crop((128, 128, 144, 142))
    out.paste(antler_tex, (32, 28))
    
    # Front Legs (8x10x4) -> UV: 0, 45. Needs 24x10 space.
    fleg_tex = src.crop((160, 160, 184, 170))
    out.paste(fleg_tex, (0, 45))
    
    # Back Legs (8x10x4) -> UV: 24, 45. Needs 24x10 space.
    bleg_tex = src.crop((200, 200, 224, 210))
    out.paste(bleg_tex, (24, 45))
    
    out.save(dest_path)
    print("Patronus UV mapped!")

# Execute
base_dir = "src/main/resources/assets/examplemod/textures/entity/"
remap_dementor(os.path.join(base_dir, "dementor.png"), os.path.join(base_dir, "dementor.png"))
remap_patronus(os.path.join(base_dir, "patronus.png"), os.path.join(base_dir, "patronus.png"))
