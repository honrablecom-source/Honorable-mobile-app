#!/usr/bin/env python3
import base64,json,sys,time,urllib.request
from io import BytesIO
from PIL import Image

MODEL="moondream:1.8b"; VERSION=2
path=sys.argv[1]
image=Image.open(path).convert("RGB");image.thumbnail((768,768));buffer=BytesIO();image.save(buffer,"JPEG",quality=88)
schema={"type":"object","properties":{
 "caption":{"type":"string"},"objects":{"type":"array","items":{"type":"string"}},
 "activities":{"type":"array","items":{"type":"string"}},"scene":{"type":"array","items":{"type":"string"}},
 "colors":{"type":"array","items":{"type":"string"}},"clothing":{"type":"array","items":{"type":"string"}},
 "environment":{"type":"array","items":{"type":"string"}},"textual_context":{"type":"array","items":{"type":"string"}},
 "attributes":{"type":"array","items":{"type":"string"}}},
 "required":["caption","objects","activities","scene","colors","clothing","environment","textual_context","attributes"]}
request={"model":MODEL,"prompt":"Describe this image.","images":[base64.b64encode(buffer.getvalue()).decode()],"stream":False,"options":{"temperature":0,"num_predict":180},"keep_alive":"2m"}
start=time.time();response=json.load(urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:11434/api/generate",json.dumps(request).encode(),{"Content-Type":"application/json"}),timeout=300))
result={key:("" if key=="caption" else []) for key in schema["required"]};result["caption"]=response.get("response","").strip()
import re
vocab={"objects":["person","people","dog","cat","car","vehicle","shoe","shoes","cake","table","shirt","ball","racket","computer","screen","phone","tree","building","boat","ocean","water","snow","grass","kite"],"activities":["running","walking","playing","dancing","driving","swimming","sitting","standing","holding","eating","flying"],"scene":["indoor","outdoor","beach","ocean","street","court","restaurant","park","snow","grass","room"],"colors":["black","white","red","blue","green","yellow","orange","purple","pink","brown","gray"],"clothing":["shirt","dress","jacket","shoes","hat","pants"],"environment":["daytime","night","sunny","cloudy","wooden floor","water","snow","grass"],"textual_context":[],"attributes":["athletic","sports","viewed from above","close-up"]}
lower=result["caption"].lower()
for key,terms in vocab.items(): result[key]=[term for term in terms if re.search(r"\b"+re.escape(term)+r"\b",lower)]
if not result["caption"]: raise RuntimeError("vision model returned an empty caption")
result.update({"model_id":MODEL,"analysis_version":VERSION,"analysis_time_ms":round((time.time()-start)*1000),"load_duration_ms":round(response.get("load_duration",0)/1e6)})
print(json.dumps(result))
