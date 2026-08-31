import{isSearchModeAvailable,searchModes}from'../src/search/searchModes';
import type{EntitlementState}from'../src/native/HonorableNative';

const entitlement=(features:string[]):EntitlementState=>({tier:'FREE',verified:true,storageLimitBytes:0,videoMinutesPerWindow:0,videosPerWindow:0,familyMembersTotal:1,features});

test('keeps quick search available without an entitlement feature',()=>{
  expect(isSearchModeAvailable(searchModes[0],undefined)).toBe(true);
});

test('unlocks capability modes only from verified entitlement features',()=>{
  expect(isSearchModeAvailable(searchModes[1],entitlement([]))).toBe(false);
  expect(isSearchModeAvailable(searchModes[1],entitlement(['smart_search']))).toBe(true);
  expect(isSearchModeAvailable(searchModes[2],entitlement(['deep_search']))).toBe(true);
});

test('does not expose implementation model names in product copy',()=>{
  expect(JSON.stringify(searchModes)).not.toMatch(/tinyclip|onnx|vit|embedding|model id/i);
});
