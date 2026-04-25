import json
import os
import glob
import shutil

spells = [
    "expelliarmus", "glacius", "confringo", "protego", "lumos", 
    "accio", "wingardium_leviosa", "alohomora", "incendio", "stupefy", 
    "revelio", "avada_kedavra", "crucio", "imperio", "fiendfyre", 
    "sectumsempra", "morsmordre", "dolohov_curse", "incarcerous", 
    "heal", "tenebris_flight", "expecto_patronum", "petrificus_totalus"
]

modid = "hogwartsmagic"
models_dir = f"src/main/resources/assets/{modid}/models/item"
textures_dir = f"src/main/resources/assets/{modid}/textures/item/magic_books"
os.makedirs(models_dir, exist_ok=True)
os.makedirs(textures_dir, exist_ok=True)

existing_pngs = [os.path.basename(f) for f in glob.glob(os.path.join(textures_dir, "*.png"))]
png_map = {f.lower().replace(".png", ""): f for f in existing_pngs}

overrides = []

for i, spell in enumerate(spells):
    spell_id = float(i + 1)
    
    spell_variations = [
        f"{spell}_spell",
        f"{spell.replace('_', '')}_spell",
        spell,
        spell.replace('_', ' '),
        f"magic_book_{spell}" # Default standard
    ]
    
    # Check if a variation exists and if so, RENAME it to the standard if it's not already
    standard_name = f"magic_book_{spell}.png"
    texture_name = f"magic_book_{spell}"
    
    for var in spell_variations:
        if var.lower() in png_map:
            old_name = png_map[var.lower()]
            old_path = os.path.join(textures_dir, old_name)
            new_path = os.path.join(textures_dir, standard_name)
            
            # Rename if it doesn't match the new standard
            if old_name != standard_name:
                os.rename(old_path, new_path)
                print(f"Renamed {old_name} -> {standard_name}")
            break
            
    # Always put them under magic_books/ folder in the JSON
    layer0_path = f"{modid}:item/magic_books/{texture_name}"
    
    # Create individual child model JSON
    model_path = os.path.join(models_dir, f"magic_book_{spell}.json")
    with open(model_path, "w") as f:
        json.dump({
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": layer0_path
            }
        }, f, indent=2)
        
    # Add to overrides list
    overrides.append({
        "predicate": { f"{modid}:spell_id": spell_id },
        "model": f"{modid}:item/magic_book_{spell}"
    })

# Write the main magic_book.json
main_book_path = os.path.join(models_dir, "magic_book.json")
with open(main_book_path, "w") as f:
    json.dump({
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"{modid}:item/magic_book"
        },
        "overrides": overrides
    }, f, indent=2)
    
print("Successfully renamed textures and generated JSON models!")
