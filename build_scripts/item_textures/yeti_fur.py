"""Yeti Fur: a hank of thick white fur, lit from the top-left, strands trailing to ragged points.

    python3 build_scripts/item_textures/yeti_fur.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..', '.claude', 'skills', 'texturing-minecraft-items', 'scripts'))
from pixkit import sprite, save  # noqa: E402

OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures',
                   'item', 'yeti_fur.png')

# o outline (darkest of the fur's own ramp), d shadow, m mid, l light, w highlight.
PALETTE = {'o': '#343d4c', 'e': '#6c7b92', 'd': '#7f8fa6', 'm': '#aebccd', 'l': '#d6e0ea',
           'w': '#f6f9fc'}
# e is the outline on the lit top-left, o the darkest outline on the bottom-right.
GRID = [
    '................',
    '.....eeee.......',
    '...eelwwleo.....',
    '..elwwlwwllo....',
    '..elwlwllwmlo...',
    '.elwlwllmlmmo...',
    '.elwlllmllmmdo..',
    '.elwllmlmmdmdo..',
    '..eelwlmmdmddo..',
    '..eolmlmmdmdmdo.',
    '...oolmmdmdmddo.',
    '....oomdmddmdo..',
    '.....oodmdodmo..',
    '......oodo.odmo.',
    '........o...oo..',
    '................',
]

if __name__ == '__main__':
    save(sprite("\n".join(GRID), PALETTE), OUT)
    print('wrote', os.path.relpath(OUT))
