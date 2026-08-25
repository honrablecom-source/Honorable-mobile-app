import {migrationFlags} from '../src/config/migrationFlags';
import {planPresentation} from '../src/subscriptions/planPresentation';

test('presents canonical tiers without invented pricing',()=>{
  expect(planPresentation.map(plan=>plan.tier)).toEqual(['FREE','PLUS','PRO','SUPER','ULTIMATE']);
  expect(planPresentation.map(plan=>plan.storage)).toEqual(['15 GB','100 GB','350 GB','700 GB','1 TB']);
  expect(JSON.stringify(planPresentation)).not.toMatch(/\$|per month|per year/i);
});

test('keeps old Android UI as cutover fallback while enabling the native bridge',()=>{
  expect(migrationFlags.useReactNativeUi).toBe(false);
  expect(migrationFlags.searchBridgeEnabled).toBe(true);
});
