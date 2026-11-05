import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

/** Stay still until the platform opts into motion; live events outrank the initial query. */
export function useReducedMotion() {
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    let active = true;
    let changed = false;
    const subscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      value => {
        changed = true;
        if (active) setReduced(value);
      },
    );
    AccessibilityInfo.isReduceMotionEnabled()
      .then(value => {
        if (active && !changed) setReduced(value);
      })
      .catch(() => {});
    return () => {
      active = false;
      subscription.remove();
    };
  }, []);
  return reduced;
}
