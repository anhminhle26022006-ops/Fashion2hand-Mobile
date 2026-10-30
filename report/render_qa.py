from pathlib import Path
import json
import pypdfium2 as pdfium
from pypdf import PdfReader
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parent/'qa'
pdf=root/'FASHION2HAND_BAO_CAO.pdf'
reader=PdfReader(str(pdf))
pages=pdfium.PdfDocument(str(pdf))
info=[]
for i,page in enumerate(pages):
    bitmap=page.render(scale=1.5)
    im=bitmap.to_pil().convert('RGB')
    im.save(root/f'page-{i+1}.png')
    text=reader.pages[i].extract_text() or ''
    info.append({'page':i+1,'chars':len(text),'first':text[:130]})
for start in range(0,len(pages),4):
    ims=[Image.open(root/f'page-{j+1}.png') for j in range(start,min(start+4,len(pages)))]
    w,h=ims[0].size
    sheet=Image.new('RGB',(w*2,h*2+60),'#D0D0D0');draw=ImageDraw.Draw(sheet)
    for k,im in enumerate(ims):
        x=(k%2)*w;y=(k//2)*(h+30)
        sheet.paste(im,(x,y+25));draw.text((x+20,y+5),f'Page {start+k+1}',fill='black')
    sheet.save(root/f'review-{start//4+1}.jpg',quality=90)
(root/'pages.json').write_text(json.dumps(info,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'pages':len(pages),'sparse_pages':[p for p in info if p['chars']<350]},ensure_ascii=False))
