import React from 'react';
import {View} from 'react-native';
// Browser frame has no Android system bars. Native implementation remains untouched.
export const SafeAreaView=({children,...props})=><View {...props}>{children}</View>;
