import sys, os
sys.argv=['x']
import paint_robes as pr
from PIL import Image
data = pr.load()
D = pr.OUT_DIR
steve = Image.open(os.path.join(pr.HERE,'out/assets/minecraft/textures/entity/player/wide/steve.png'))
player = pr.quads(data,'player',lambda p: p not in ('/cloak','/ear'))
tiles=[]
for s,de in (('auror',False),('death_eater',True)):
    tx={k:Image.open(os.path.join(D,'%s_robes_%s.png'%(s,k))) for k in ('hood','coat','trousers','boots')}
    rk=lambda p,de=de:(p=='/body' or p in('/right_arm','/left_arm') or p.startswith('/body/')) and ((not p.endswith('cloak')) if not de else not(p.endswith('mantle') or p.endswith('collar')))
    hk=lambda p,de=de:p.startswith('/head') and (de or not(p.endswith('mask') or p.endswith('nose')))
    L=[(player,pr.arr(steve)),(pr.quads(data,'inner',lambda p:p in('/body',)+pr.LEGS),pr.arr(tx['trousers'])),(pr.quads(data,'outer',lambda p:p in pr.LEGS),pr.arr(tx['boots'])),(pr.quads(data,'robe',rk),pr.arr(tx['coat'])),(pr.quads(data,'hood',hk),pr.arr(tx['hood']))]
    for az,el in ((25,10),(200,15)):
        tiles.append(pr.render_scene(L,az,el,size=560))
sheet=Image.new('RGB',(560*4,560))
for i,t in enumerate(tiles): sheet.paste(t,(i*560,0))
sheet.save('out/zoom.png')
