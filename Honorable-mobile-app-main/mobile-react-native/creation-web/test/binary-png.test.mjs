import test from'node:test';import assert from'node:assert/strict';import{spawnSync}from'node:child_process';import{binaryPng,binaryPngAsync,binaryPngSize}from'../binary-png.mjs';
test('independent Python PNG/CRC/zlib/Adler decoder verifies all bits, row padding and stored-block boundaries',()=>{const cases=[[1,1],[7,9],[8,16],[9,17],[672,771],[1016,512],[1024,1024]].map(([width,height])=>{const bits=Uint8Array.from({length:width*height},(_,i)=>(Math.imul(i+1,1664525)>>>27)&1),before=bits.slice(),png=binaryPng(bits,width,height);assert.deepEqual(bits,before);assert.equal(png.length,binaryPngSize(width,height));return{width,height,png:Buffer.from(png).toString('base64'),bits:Buffer.from(bits).toString('base64')};});const script=`import sys,json,base64,struct,zlib,binascii,hashlib
proof=[]
for case in json.load(sys.stdin):
 b=base64.b64decode(case['png']); expected=base64.b64decode(case['bits']); w=case['width'];h=case['height']
 assert b[:8]==bytes([137,80,78,71,13,10,26,10]);p=8;chunks=[]
 while p<len(b):
  n=struct.unpack('>I',b[p:p+4])[0];kind=b[p+4:p+8];data=b[p+8:p+8+n];crc=struct.unpack('>I',b[p+8+n:p+12+n])[0]
  assert binascii.crc32(kind+data)==crc;chunks.append((kind,data));p+=n+12
 assert p==len(b) and [c[0] for c in chunks]==[b'IHDR',b'IDAT',b'IEND']
 assert struct.unpack('>IIBBBBB',chunks[0][1])==(w,h,1,0,0,0,0)
 stream=chunks[1][1];raw=zlib.decompress(stream);stride=(w+7)//8+1;assert len(raw)==stride*h
 decoded=bytearray()
 for y in range(h):
  assert raw[y*stride]==0
  for x in range(w):decoded.append((raw[y*stride+1+x//8]>>(7-x%8))&1)
  if w%8:assert raw[(y+1)*stride-1]&((1<<(8-w%8))-1)==0
 assert bytes(decoded)==expected
 q=2;stored=bytearray();blocks=0
 while True:
  flag=stream[q];assert flag in (0,1);length,inverse=struct.unpack('<HH',stream[q+1:q+5]);assert length^inverse==65535
  stored.extend(stream[q+5:q+5+length]);q+=length+5;blocks+=1
  if flag:break
 assert q+4==len(stream) and bytes(stored)==raw and zlib.adler32(raw)==struct.unpack('>I',stream[-4:])[0]
 proof.append({'width':w,'height':h,'bytes':len(b),'raw':len(raw),'blocks':blocks,'pixels':len(decoded),'sha256':hashlib.sha256(decoded).hexdigest()})
print(json.dumps(proof))`;
const result=spawnSync('python',['-S','-c',script],{input:JSON.stringify(cases),encoding:'utf8',maxBuffer:4*1024*1024,timeout:20000});assert.equal(result.status,0,result.stderr||String(result.error));const proof=JSON.parse(result.stdout);assert.equal(proof[4].raw,65535);assert.equal(proof[4].blocks,1);assert.equal(proof[5].raw,65536);assert.equal(proof[5].blocks,2);assert.equal(proof[6].blocks,3);assert.equal(proof[6].bytes,132174);assert(proof[6].bytes<192*1024);console.log('Independent Python binary PNG receipts:',JSON.stringify(proof));});
test('private asynchronous bit capture agrees with synchronous bytes and progress remains monotone',async()=>{const bits=Uint8Array.from({length:65*33},(_,i)=>i%2),expected=binaryPng(bits,65,33),progress=[];const actual=await binaryPngAsync(bits,65,33,()=>{},p=>progress.push(p),async()=>bits.fill(0));assert.deepEqual(actual,expected);assert.equal(progress.at(-1),1);for(let i=1;i<progress.length;i++)assert(progress[i]>=progress[i-1]);});
test('initial, middle and final-yield cancellation refuse rather than return partial PNG bytes',async()=>{const bits=new Uint8Array(64*65);await assert.rejects(binaryPngAsync(bits,64,65,()=>{throw Error('initial');}),/initial/);for(const boundary of[.4,1]){let stop=false;await assert.rejects(binaryPngAsync(bits,64,65,()=>{if(stop)throw Error('stale');},p=>{if(p>=boundary)stop=true;},async()=>{}),/stale/);}});
test('invalid binary samples, dimensions, buffers and concurrent shared memory are rejected',()=>{assert.throws(()=>binaryPng(new Uint8Array([2]),1,1));for(const dimensions of[[0,1],[1.5,2],[1025,1]])assert.throws(()=>binaryPngSize(...dimensions));assert.throws(()=>binaryPng(new Uint8Array(1),8,8));if(typeof SharedArrayBuffer==='function')assert.throws(()=>binaryPng(new Uint8Array(new SharedArrayBuffer(64)),8,8));});
