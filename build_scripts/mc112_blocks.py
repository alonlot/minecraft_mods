"""Maps Minecraft 1.12 numeric (id, meta) block pairs to modern blockstate strings.

Only covers the states that actually occur in the imported Gringotts build -
this is not a general flattening table. Unknown pairs raise, so a silent hole
turns into a loud failure at conversion time rather than a wall of air in game.
"""

# 1.12 stair metadata: low two bits are the facing, bit 2 flips to the top half.
_STAIR_FACING = {0: "east", 1: "west", 2: "south", 3: "north"}
# 1.12 torch/button metadata share this wall-facing encoding.
_WALL_FACING = {1: "east", 2: "west", 3: "south", 4: "north"}
_TRAPDOOR_FACING = {0: "south", 1: "north", 2: "east", 3: "west"}
_DOOR_FACING = {0: "east", 1: "south", 2: "west", 3: "north"}
_FENCE_GATE_FACING = {0: "south", 1: "west", 2: "north", 3: "east"}
_ANVIL_FACING = {0: "south", 1: "west", 2: "north", 3: "east"}
_REPEATER_FACING = {0: "north", 1: "east", 2: "south", 3: "west"}
_END_ROD_FACING = {0: "down", 1: "up", 2: "north", 3: "south", 4: "west", 5: "east"}
_COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
           "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
_SLAB_TYPES = {0: "smooth_stone", 1: "sandstone", 2: "petrified_oak", 3: "cobblestone",
               4: "brick", 5: "stone_brick", 6: "nether_brick", 7: "quartz"}
_WOOD = {0: "oak", 1: "spruce", 2: "birch", 3: "jungle", 4: "acacia", 5: "dark_oak"}


def _stairs(block, meta):
    return "minecraft:%s[facing=%s,half=%s,shape=straight]" % (
        block, _STAIR_FACING[meta & 3], "top" if meta & 4 else "bottom")


def to_modern(bid, meta):
    """Returns a modern blockstate string, or raises KeyError if unmapped."""
    if bid == 0:
        return "minecraft:air"
    if bid == 1:
        return {1: "minecraft:granite", 6: "minecraft:polished_andesite"}[meta]
    if bid == 5:
        return "minecraft:%s_planks" % _WOOD[meta & 7]
    if bid == 17:
        return "minecraft:%s_log[axis=y]" % _WOOD[meta & 3]
    if bid == 24:
        return {1: "minecraft:chiseled_sandstone", 2: "minecraft:cut_sandstone"}[meta]
    if bid == 41:
        return "minecraft:gold_block"
    if bid == 43:
        # Double slab: the flattening turned the stone variant into smooth stone.
        return {0: "minecraft:smooth_stone", 7: "minecraft:quartz_block"}[meta]
    if bid == 44:
        return "minecraft:%s_slab[type=%s]" % (
            _SLAB_TYPES[meta & 7], "top" if meta & 8 else "bottom")
    if bid == 50:
        return "minecraft:torch" if meta == 5 else \
            "minecraft:wall_torch[facing=%s]" % _WALL_FACING[meta]
    if bid == 54:
        return "minecraft:chest[facing=%s,type=single]" % {2: "north", 3: "south", 4: "west", 5: "east"}[meta]
    if bid == 55:
        return "minecraft:redstone_wire"
    if bid == 67:
        return _stairs("cobblestone_stairs", meta)
    if bid == 77:
        if meta == 0:
            return "minecraft:stone_button[face=ceiling,facing=north]"
        if meta == 5:
            return "minecraft:stone_button[face=floor,facing=north]"
        return "minecraft:stone_button[face=wall,facing=%s]" % _WALL_FACING[meta]
    if bid == 93:
        return "minecraft:repeater[facing=%s,delay=%d]" % (
            _REPEATER_FACING[meta & 3], (meta >> 2) + 1)
    if bid == 95:
        return "minecraft:%s_stained_glass" % _COLORS[meta]
    if bid == 98:
        return {0: "minecraft:stone_bricks", 1: "minecraft:mossy_stone_bricks",
                2: "minecraft:cracked_stone_bricks", 3: "minecraft:chiseled_stone_bricks"}[meta]
    if bid == 101:
        return "minecraft:iron_bars"
    if bid == 109:
        return _stairs("stone_brick_stairs", meta)
    if bid == 123:
        return "minecraft:redstone_lamp[lit=false]"
    if bid == 124:
        return "minecraft:redstone_lamp[lit=true]"
    if bid == 126:
        return "minecraft:%s_slab[type=%s]" % (
            _WOOD[meta & 7], "top" if meta & 8 else "bottom")
    if bid == 139:
        return "minecraft:cobblestone_wall" if meta == 0 else "minecraft:mossy_cobblestone_wall"
    if bid == 143:
        if meta == 0:
            return "minecraft:oak_button[face=ceiling,facing=north]"
        if meta == 5:
            return "minecraft:oak_button[face=floor,facing=north]"
        return "minecraft:oak_button[face=wall,facing=%s]" % _WALL_FACING[meta]
    if bid == 144:
        # The skull variant lives in the tile entity, which we do not carry over;
        # a plain skeleton skull is the closest neutral stand-in.
        return "minecraft:skeleton_skull[rotation=0]"
    if bid == 145:
        return "minecraft:anvil[facing=%s]" % _ANVIL_FACING[meta & 3]
    if bid == 147:
        return "minecraft:light_weighted_pressure_plate"
    if bid == 155:
        return {0: "minecraft:quartz_block", 1: "minecraft:chiseled_quartz_block",
                2: "minecraft:quartz_pillar[axis=y]", 3: "minecraft:quartz_pillar[axis=x]",
                4: "minecraft:quartz_pillar[axis=z]"}[meta]
    if bid == 156:
        return _stairs("quartz_stairs", meta)
    if bid == 159:
        return "minecraft:%s_terracotta" % _COLORS[meta]
    if bid == 160:
        return "minecraft:%s_stained_glass_pane" % _COLORS[meta]
    if bid == 162:
        return "minecraft:%s_log[axis=y]" % {0: "acacia", 1: "dark_oak"}[meta & 3]
    if bid == 164:
        return _stairs("dark_oak_stairs", meta)
    if bid == 167:
        return "minecraft:iron_trapdoor[facing=%s,half=%s,open=%s]" % (
            _TRAPDOOR_FACING[meta & 3],
            "top" if meta & 8 else "bottom",
            "true" if meta & 4 else "false")
    if bid == 168:
        return {0: "minecraft:prismarine", 1: "minecraft:prismarine_bricks",
                2: "minecraft:dark_prismarine"}[meta]
    if bid == 171:
        return "minecraft:%s_carpet" % _COLORS[meta]
    if bid == 173:
        return "minecraft:coal_block"
    if bid == 178:
        return "minecraft:daylight_detector[inverted=true]"
    if bid == 186:
        return "minecraft:dark_oak_fence_gate[facing=%s]" % _FENCE_GATE_FACING[meta & 3]
    if bid == 189:
        return "minecraft:birch_fence"
    if bid == 191:
        return "minecraft:dark_oak_fence"
    if bid == 197:
        if meta & 8:
            return "minecraft:dark_oak_door[half=upper,hinge=%s,facing=north]" % (
                "right" if meta & 1 else "left")
        return "minecraft:dark_oak_door[half=lower,facing=%s,open=%s]" % (
            _DOOR_FACING[meta & 3], "true" if meta & 4 else "false")
    if bid == 198:
        return "minecraft:end_rod[facing=%s]" % _END_ROD_FACING[meta]
    if bid == 216:
        return "minecraft:bone_block[axis=%s]" % {0: "y", 4: "x", 8: "z"}[meta & 12]
    if bid == 251:
        return "minecraft:%s_concrete" % _COLORS[meta]
    raise KeyError("unmapped 1.12 block id=%d meta=%d" % (bid, meta))
