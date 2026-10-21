"""Generate original cube UVs and editable Blockbench model from Echo's public face design.
Reference: https://github.com/Ty0x7/EchoSpeak/blob/main/apps/web/src/components/EchoFace.tsx
No EchoSpeak code or bitmap is bundled. Pillow is needed only to regenerate assets/previews.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import base64
import io
import json
import uuid

root = Path(__file__).resolve().parents[1]
assets = root / "src/main/resources/assets/echo/textures/entity"
assets.mkdir(parents=True, exist_ok=True)
body = (244, 244, 242, 255)
eye = (7, 7, 7, 255)


def face(size, blink=False):
    image = Image.new("RGBA", (size, size), body)
    draw = ImageDraw.Draw(image)
    w, h = round(size * .115), round(size * (.025 if blink else .2))
    gap = round(size * .15)
    for x in [(size - 2*w - gap)//2, (size + gap)//2]:
        y = (size - h)//2
        draw.rounded_rectangle((x,y,x+w-1,y+h-1), radius=w//2, fill=eye)
    return image


front = face(64)
shell = Image.new("RGBA", (64,64), body)
shell.save(assets / "cube_shell.png")
front.save(assets / "cube_face.png")
for blink in [False, True]:
    # Standard Minecraft cuboid UV layout, nominal texture 64x32 at 4x density.
    atlas = Image.new("RGBA", (256,128), body)
    atlas.paste(face(40,blink), (40,40))
    atlas.save(assets / ("echo_blink.png" if blink else "echo.png"))

model_dir = root / "models"
model_dir.mkdir(exist_ok=True)
element_id = str(uuid.uuid5(uuid.NAMESPACE_URL,"echo-minecraft:cube"))
textures = []
for index, (name, bitmap) in enumerate([("cube_shell",shell),("cube_face",front)]):
    buffer=io.BytesIO();bitmap.save(buffer,format="PNG")
    textures.append({"name":name+".png","id":str(index),"uuid":str(uuid.uuid5(uuid.NAMESPACE_URL,name)),"width":64,"height":64,"uv_width":16,"uv_height":16,"source":"data:image/png;base64,"+base64.b64encode(buffer.getvalue()).decode()})
model={"meta":{"format_version":"4.10","model_format":"java_block","box_uv":False},"name":"Echo — white cube","resolution":{"width":16,"height":16},
       "elements":[{"name":"Echo cube","uuid":element_id,"from":[5.5,5.5,5.5],"to":[10.5,10.5,10.5],"origin":[8,8,8],"faces":{side:{"uv":[0,0,16,16],"texture":1 if side=="north" else 0} for side in ["north","south","east","west","up","down"]}}],
       "outliner":[element_id],"textures":textures}
(model_dir/"echo-cube.bbmodel").write_text(json.dumps(model,indent=2),encoding="utf-8")

# Orthographic model-design preview, not a Minecraft screenshot.
preview=Image.new("RGB",(800,640),(25,29,35));draw=ImageDraw.Draw(preview)
draw.ellipse((245,475,615,535),fill=(15,18,23))
draw.polygon([(235,205),(425,110),(620,205),(430,300)],fill=(254,254,252))
draw.polygon([(430,300),(620,205),(620,410),(430,510)],fill=(214,215,213))
projected=front.transform((800,640),Image.Transform.AFFINE,(64/195,0,-235*64/195,-.5*64/210,64/210,(-205+.5*235)*64/210),resample=Image.Resampling.NEAREST)
mask=Image.new("L",(800,640));ImageDraw.Draw(mask).polygon([(235,205),(430,300),(430,510),(235,415)],fill=255)
preview.paste(projected,(0,0),mask)
draw=ImageDraw.Draw(preview)
draw.text((30,28),"ECHO / MINECRAFT CUBE MODEL",fill=(244,244,242))
draw.text((30,590),"Off-white shell / black pill eyes / no mouth / no limbs",fill=(180,186,195))
preview.save(model_dir/"echo-cube-preview.png")
