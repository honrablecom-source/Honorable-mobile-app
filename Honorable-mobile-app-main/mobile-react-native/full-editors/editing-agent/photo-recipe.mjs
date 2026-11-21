const adjustments = {
  brightness: ['Brightness', -40, 40],
  contrast: ['Contrast', -35, 40],
  saturation: ['Saturation', -45, 45],
  warmth: ['Warmth', -40, 40],
  monochrome: ['Black and white', 100, 100],
};

export const recipeSchema = {
  type:'object', additionalProperties:false, required:['edits'],
  properties:{edits:{type:'array',maxItems:4,items:{
    type:'object',additionalProperties:false,required:['kind','amount'],
    properties:{kind:{type:'string',enum:Object.keys(adjustments)},amount:{type:'integer',minimum:-45,maximum:100}},
  }}},
};

export function validatePhotoRecipe(value) {
  if (!Array.isArray(value?.edits) || value.edits.length > 4) throw Error('The model did not return a photo adjustment plan.');
  const seen = new Set();
  return value.edits.map(edit => {
    const {kind,amount} = edit ?? {};
    const entry = typeof kind === 'string' && Object.hasOwn(adjustments,kind) ? adjustments[kind] : null;
    if (!entry || seen.has(kind) || !Number.isInteger(amount) || !amount || amount < entry[1] || amount > entry[2]) {
      throw Error('The model proposed an adjustment outside the supported range.');
    }
    seen.add(kind);
    return {kind,amount};
  });
}

export function describePhotoEdit({kind,amount}) {
  if (kind === 'monochrome') return adjustments[kind][0];
  return adjustments[kind][0] + ' ' + (amount > 0 ? '+' : '−') + Math.abs(amount);
}

// The model emits recipe values. Only this mapping can select an editor command.
export function photoAdjustmentLayers(edits,strength = 100) {
  if (!Number.isInteger(strength) || strength < 0 || strength > 100) throw Error('Choose an adjustment strength from 0 to 100%.');
  return validatePhotoRecipe({edits}).map(edit => {
    const {kind,amount} = edit;
    const name = 'AI · ' + describePhotoEdit(edit);
    const base = strength === 100 ? {name} : {name,opacity:strength/100};
    switch (kind) {
      case 'brightness':
      case 'contrast':
        return {kind:'brightnessContrast',params:{...base,[kind]:amount}};
      case 'saturation':
        return {kind:'hueSaturation',params:{...base,saturation:amount}};
      case 'warmth':
        return {kind:'photoFilter',params:{...base,filter:amount > 0 ? 'warming85' : 'cooling82',density:Math.abs(amount)}};
      case 'monochrome':
        return {kind:'blackWhite',params:base};
    }
  });
}

export function photoFinish(edits, measurements, finish = 'natural') {
  if (finish === 'creative') return edits;
  if (finish !== 'natural') throw Error('Choose a natural or creative finish.');
  const bright = measurements.highlightFraction >= 0.18;
  const shadows = measurements.shadowFraction >= 0.35;
  const color = measurements.meanColorSpread;
  const limits = {
    brightness:[-16, bright ? 8 : 20],
    contrast:[-12, bright || shadows ? 10 : 16],
    saturation:[-16, color >= 0.26 ? 5 : 12],
    warmth:[-14,14],
    monochrome:[100,100],
  };
  return edits.map(({kind,amount}) => {
    const [min,max] = limits[kind];
    return {kind,amount:Math.max(min,Math.min(max,amount))};
  });
}

export function photoMeasurements({width,height}, pixels) {
  const samples = pixels.filter(pixel => Array.isArray(pixel) && pixel.length === 4 &&
    pixel.every(Number.isFinite) && pixel[3] >= 0.5);
  if (!samples.length || samples.length > 64) throw Error('No visible photo pixels could be measured.');
  const clamp = value => Math.max(0,Math.min(1,value));
  const round = value => Math.round(1000 * value) / 1000;
  const mean = values => values.reduce((total,value) => total + value,0) / values.length;
  const rgb = samples.map(pixel => pixel.slice(0,3).map(clamp));
  const luma = rgb.map(([r,g,b]) => 0.2126 * r + 0.7152 * g + 0.0722 * b);
  return {
    width,height,sampleCount:samples.length,meanLuma:round(mean(luma)),
    shadowFraction:round(luma.filter(value => value < 0.2).length / luma.length),
    highlightFraction:round(luma.filter(value => value > 0.8).length / luma.length),
    meanColorSpread:round(mean(rgb.map(rgb => Math.max(...rgb)-Math.min(...rgb)))),
  };
}

export function validatePhotoMeasurements(value) {
  const {width,height,sampleCount,meanLuma,shadowFraction,highlightFraction,meanColorSpread} = value ?? {};
  if (![width,height].every(size => Number.isInteger(size) && size >= 1 && size <= 300000) ||
      !Number.isInteger(sampleCount) || sampleCount < 1 || sampleCount > 64 ||
      ![meanLuma,shadowFraction,highlightFraction,meanColorSpread].every(measure => Number.isFinite(measure) && measure >= 0 && measure <= 1)) {
    throw Error('The current photo measurements are unavailable.');
  }
  return {width,height,sampleCount,meanLuma,shadowFraction,highlightFraction,meanColorSpread};
}
