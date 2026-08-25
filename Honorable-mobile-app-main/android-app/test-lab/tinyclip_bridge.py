#!/usr/bin/env python3
import json, sys
from pathlib import Path
import numpy as np
from PIL import Image
from tokenizers import Tokenizer
import onnxruntime as ort

root = Path(__file__).resolve().parent / "models" / "tinyclip"
session = ort.InferenceSession(str(root / "model_int8.onnx"), providers=["CPUExecutionProvider"])
tokenizer = Tokenizer.from_file(str(root / "tokenizer.json"))
MEAN = np.array([.48145466,.4578275,.40821073],dtype=np.float32)[:,None,None]
STD = np.array([.26862954,.26130258,.27577711],dtype=np.float32)[:,None,None]

def pixels(path=None):
    image = Image.open(path).convert("RGB") if path else Image.new("RGB",(224,224))
    # Match Android Bitmap.createScaledBitmap(..., filter=true): floor dimensions and bilinear filtering.
    w,h=image.size; scale=224/min(w,h); image=image.resize((int(w*scale),int(h*scale)),Image.Resampling.BILINEAR)
    left=(image.width-224)//2; top=(image.height-224)//2; image=image.crop((left,top,left+224,top+224))
    return ((np.asarray(image,dtype=np.float32).transpose(2,0,1)/255-MEAN)/STD)[None]

def tokens(text):
    ids=tokenizer.encode(text).ids[:77]; ids += [49407]*(77-len(ids)); last_content=max((i for i,v in enumerate(ids) if v != 49407),default=-1); mask=[1 if i <= last_content else 0 for i in range(77)]
    return np.array([ids],dtype=np.int64),np.array([mask],dtype=np.int64)

def infer(kind,value):
    ids,mask=tokens(value if kind=="text" else "a photo")
    out=session.run(None,{"input_ids":ids,"attention_mask":mask,"pixel_values":pixels(value if kind=="image" else None)})
    vector=out[2 if kind=="text" else 3][0].astype(float); vector/=np.linalg.norm(vector)
    return vector.tolist()

print(json.dumps({"ready":True,"dimension":512}),flush=True)
for line in sys.stdin:
    try:
        request=json.loads(line); print(json.dumps({"vector":infer(request["kind"],request["value"])}),flush=True)
    except Exception as error: print(json.dumps({"error":str(error)}),flush=True)
