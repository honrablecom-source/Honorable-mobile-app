"""Reproduce the pinned Apache-2.0 adaptive-color inference assets.

Classifier excerpt and pretrained weights: Hui Zeng et al., Image-Adaptive-3DLUT.
Only the SHA-pinned Classifier/discriminator_block AST nodes are executed, with
torch.nn supplied explicitly. Original code/license are preserved under
color-assets/upstream. No training, custom CUDA extension or arbitrary model input.
Requires torch2.6.0+cpu, onnx1.17.0, onnxruntime1.20.1, numpy2.2.6.
"""
import ast
import hashlib
import json
from pathlib import Path
import sys
import numpy as np
import torch
import torch.nn as nn
import onnx
import onnxruntime as ort

ROOT=Path(__file__).resolve().parent
UPSTREAM=ROOT/'color-assets/upstream'
PINS={'models.py':'3f39c49aebd2b85787638bc9d402d21d5d664b844edd5fb8d5e4ce1fdb1d9f3f',
      'classifier.pth':'bae9865395625ecae58cfe86147e521093bb1e29e7b2544e02adb238b8035021',
      'LUTs.pth':'c1bb2bc4b7239c1a7e96159f5923123ba796b1fceb0b8c3132b423ea825b821a'}
def main():
    if len(sys.argv)!=2:
        raise SystemExit('Usage: python full-editors/export-color.py FRESH_OUTPUT_DIRECTORY')
    out=Path(sys.argv[1]).resolve()
    if out.exists():
        raise SystemExit('Choose fresh output. Existing source/artifacts are never overwritten.')
    if not (torch.__version__=='2.6.0+cpu' and onnx.__version__=='1.17.0' and ort.__version__=='1.20.1' and np.__version__=='2.2.6'):
        raise SystemExit('Use the documented exact CPU conversion toolchain.')
    for name,pin in PINS.items():
        if hashlib.sha256((UPSTREAM/name).read_bytes()).hexdigest()!=pin:
            raise SystemExit('A pinned conversion input changed.')
    tree=ast.parse((UPSTREAM/'models.py').read_text(encoding='utf-8'))
    nodes=[node for node in tree.body if isinstance(node,(ast.FunctionDef,ast.ClassDef)) and node.name in ('discriminator_block','Classifier')]
    if len(nodes)!=2:
        raise SystemExit('Unexpected classifier source.')
    namespace={'nn':nn}
    exec(compile(ast.Module(body=nodes,type_ignores=[]),'pinned-classifier-excerpt','exec'),namespace)
    model=namespace['Classifier']()
    model.load_state_dict(torch.load(UPSTREAM/'classifier.pth',map_location='cpu',weights_only=True),strict=True)
    model.eval();torch.set_num_threads(2)
    weights=torch.load(UPSTREAM/'LUTs.pth',map_location='cpu',weights_only=True)
    if set(weights)!={'0','1','2'} or any(set(weights[str(i)])!={'LUT'} for i in range(3)):
        raise SystemExit('Unexpected LUT parameter structure.')
    bases=torch.stack([weights[str(index)]['LUT'] for index in range(3)])
    if list(bases.shape)!=[3,3,33,33,33] or bases.dtype!=torch.float32 or not bool(torch.isfinite(bases).all()):
        raise SystemExit('Invalid learned color tables.')
    out.mkdir(parents=True)
    bases.numpy().astype('<f4').tofile(out/'lut-bases.f32')
    rng=np.random.default_rng(7)
    image=rng.random((1,3,256,256),dtype=np.float32)
    with torch.no_grad():
        reference=model(torch.from_numpy(image)).numpy()
    torch.onnx.export(model,torch.from_numpy(image),out/'adaptive-color.onnx',opset_version=17,input_names=['rgb'],output_names=['weights'],dynamo=False)
    graph=onnx.load(out/'adaptive-color.onnx');onnx.checker.check_model(graph)
    if any(item.domain for item in graph.opset_import):
        raise SystemExit('Only standard ONNX operators are allowed.')
    session=ort.InferenceSession(str(out/'adaptive-color.onnx'),providers=['CPUExecutionProvider'])
    converted=session.run(None,{'rgb':image})[0]
    error=float(np.max(np.abs(converted-reference)))
    if error>=2e-5:
        raise SystemExit('Converted predictions differ from the source model.')
    source=Path(__file__).read_text(encoding='utf-8').replace('\r\n','\n').encode()
    record={'version':1,'sourceRepository':'https://github.com/HuiZeng/Image-Adaptive-3DLUT',
            'sourceRevision':'b491f6df64a588864739a157db271e5c848e1805','license':'Apache-2.0',
            'inputPins':PINS,'converterCanonicalLfSha256':hashlib.sha256(source).hexdigest(),
            'torch':torch.__version__,'onnx':onnx.__version__,'onnxruntime':ort.__version__,'numpy':np.__version__,
            'opset':17,'input':'RGB float32 in0..1, NCHW [1,3,256,256]',
            'output':'Three signed content-dependent LUT weights [1,3,1,1]; not class probabilities',
            'tables':'Little-endian float32 [3bases,3channels,33blue,33green,33red]; red varies fastest',
            'maximumTorchOrtDifference':error,'deviceTested':False}
    for name in ('adaptive-color.onnx','lut-bases.f32'):
        data=(out/name).read_bytes();record[name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
    (out/'provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(record,indent=2))
if __name__=='__main__':
    main()
