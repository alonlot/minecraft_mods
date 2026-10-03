"""Colloportus spell book: the Revelio book's 50x50 pixel grid, recoloured crimson, with a chained padlock.

Run from the mod root: python3 tools/gen_colloportus_book.py (needs pillow and numpy).
"""
from pathlib import Path
from PIL import Image
import numpy as np, colorsys
BOOKS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/hogwartsmagic/textures/item/magic_books"
src = np.array(Image.open(BOOKS / "magic_book_revelio.png").convert("RGBA"))
N = 50; s = 640 / N
g = np.zeros((N, N, 4), int)
for y in range(N):
  for x in range(N):
    g[y, x] = src[int(s * y + s / 2), int(s * x + s / 2)]
def hsv(p): return colorsys.rgb_to_hsv(*(c/255 for c in p[:3]))
emb=np.zeros((N,N),bool)
for y in range(N):
  for x in range(N):
    r,gg,b,a=g[y,x]
    if a<128: continue
    h,s,v=hsv(g[y,x])
    cyan = gg>170 and b>170 and r<150 and s>0.35
    gold = r>170 and gg>110 and b<120
    if (cyan or gold) and 9<=x<=37 and 9<=y<=34 and not (x>=35 and y<=21): emb[y,x]=True
# grow by 1 to catch glow pixels that are mid-bright cyan
emb2=emb.copy()
for y in range(N):
  for x in range(N):
    if emb[y,x]: continue
    h,s,v=hsv(g[y,x])
    if g[y,x][3]>128 and 0.42<h<0.55 and v>0.55 and s>0.5 and 9<=x<=37 and 9<=y<=34 and any(emb[yy,xx] for yy in range(y-1,y+2) for xx in range(x-1,x+2)):
      emb2[y,x]=True
emb=emb2
# fill emblem with nearby non-emblem cover pixels (median)
out=g.copy()
for it in range(6):
  for y in range(N):
    for x in range(N):
      if not emb[y,x]: continue
      nb=[out[yy,xx] for yy in range(y-2,y+3) for xx in range(x-2,x+3) if 0<=yy<N and 0<=xx<N and not emb[yy,xx] and out[yy,xx][3]>128 and sum(out[yy,xx][:3])>60]
      if len(nb)>=3:
        nb=np.array(nb); lum=nb[:,:3].sum(1); out[y,x]=nb[np.argsort(lum)[len(lum)//2]]; emb[y,x]=False
# recolour
def setc(p,h,s,v):
  r,gg,b=colorsys.hsv_to_rgb(h,s,v); return [int(r*255),int(gg*255),int(b*255),p[3]]
for y in range(N):
  for x in range(N):
    p=out[y,x]
    if p[3]<128: continue
    h,s,v=hsv(p)
    if 0.40<h<0.62 and s<0.5 and v>0.55:      # page edges -> parchment
      out[y,x]=setc(p,0.11,0.30,min(1,v*0.95))
    elif 0.40<h<0.62 and s>0.25:               # teal cover -> crimson
      out[y,x]=setc(p,0.985,min(1,s*1.05),min(1,v*1.05))
    elif 0.04<h<0.12 and v<0.6:                # brown trim -> dark brass
      out[y,x]=setc(p,0.11,0.65,min(1,v*1.35))
img=out.copy()
# emblem: chains crossing + padlock, centre (27,23)
cx,cy=27,23
CL=(235,235,245); CM=(160,160,180); CD=(45,30,38)
def put(x,y,c):
  if 0<=x<N and 0<=y<N and img[y,x][3]>128: img[y,x]=[*c,255]
def blend(x,y,c,k):
  if 0<=x<N and 0<=y<N and img[y,x][3]>128:
    p=img[y,x]; img[y,x]=[int(p[i]*(1-k)+c[i]*k) for i in range(3)]+[255]
# soft glow ring
for y in range(cy-11,cy+11):
  for x in range(cx-11,cx+12):
    d=((x-cx)**2+(y-cy)**2)**0.5
    if d<10: blend(x,y,(255,120,40),0.28*(1-d/10))
# chains: links of 2 pixels, dark gap, along two diagonals
for sgn in (1,-1):
  for t in range(-12,13):
    x=cx+t; y=cy+int(round(sgn*t*0.6))
    ph=(t+12)%3
    if ph<2:
      put(x,y-1,CD); put(x,y,CL if ph==0 else CM); put(x,y+1,CM if ph==0 else CD); put(x,y+2,CD)
    else:
      put(x,y,CD); put(x,y+1,CD)
A0=(150,64,10); A1=(240,160,40); A2=(255,226,140); K=(30,8,12)
# dark outline box for lock
for y in range(cy-8,cy+7):
  for x in range(cx-5,cx+6):
    if (y>=cy-2) or (cx-4<=x<=cx+4 and y>=cy-8):
      pass
# shackle arch (outline K, metal A1/A2)
arch=[(cx-3,cy-2),(cx-3,cy-3),(cx-3,cy-4),(cx-3,cy-5),(cx-2,cy-6),(cx-1,cy-7),(cx,cy-7),(cx+1,cy-7),(cx+2,cy-6),(cx+3,cy-5),(cx+3,cy-4),(cx+3,cy-3),(cx+3,cy-2)]
for (x,y) in arch:
  for dx,dy in ((-1,0),(1,0),(0,-1),(0,1),(-1,-1),(1,-1)):
    if (x+dx,y+dy) not in arch: put(x+dx,y+dy,K)
for (x,y) in arch: put(x,y,A1)
for (x,y) in arch[:5]: put(x,y,A2)
for x in range(cx-2,cx+3):
  for y in range(cy-5,cy-1): put(x,y,K) if (x in (cx-2,cx+2) or y==cy-5) else None
for x in range(cx-1,cx+2):
  for y in range(cy-5,cy-1): blend(x,y,(0,0,0),0.35)
# body 11x8 with outline
for y in range(cy-2,cy+7):
  for x in range(cx-5,cx+6):
    if y in (cy-2,cy+6) or x in (cx-5,cx+5): put(x,y,K)
    else: put(x,y,A1)
for x in range(cx-4,cx+5): put(x,cy+5,A0)
for y in range(cy-1,cy+6): put(cx+4,y,A0)
for x in range(cx-4,cx+3): put(x,cy-1,A2)
for y in range(cy-1,cy+4): put(cx-4,y,A2)
# keyhole
for (x,y) in ((cx-1,cy),(cx,cy),(cx+1,cy),(cx-1,cy+1),(cx,cy+1),(cx+1,cy+1),(cx,cy+2),(cx,cy+3)): put(x,y,K)
# sparkles
for (x,y) in ((cx-8,cy-7),(cx+8,cy-6),(cx-9,cy+5),(cx+9,cy+7),(cx+4,cy-10)):
  put(x,y,A2)
im=Image.fromarray(img.astype(np.uint8))
im.resize((640,640),Image.NEAREST).save(BOOKS / 'magic_book_colloportus.png')
