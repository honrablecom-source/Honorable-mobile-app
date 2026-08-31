import React from 'react';
import {Pressable,StyleSheet,View} from 'react-native';
import {Search,X} from 'lucide-react-native';
import {Input} from '@/components/ui/input';
import {colors,radii} from '../design-system/tokens';
export function HonorableSearchBar({value,onChangeText,onSubmit,compact=false}:{value:string;onChangeText:(value:string)=>void;onSubmit:()=>void;compact?:boolean}){return <View style={[styles.shell,compact&&styles.compact]}><Search color={value?colors.accent:colors.iconMuted} size={20}/><Input accessibilityLabel="Describe a photo or video" returnKeyType="search" blurOnSubmit={false} onSubmitEditing={onSubmit} placeholder="Describe a memory…" placeholderTextColor={colors.textMuted} value={value} onChangeText={onChangeText} className="h-12 flex-1 border-0 bg-transparent px-0 text-[16px] text-foreground shadow-none"/>{!!value&&<Pressable accessibilityRole="button" accessibilityLabel="Clear search" hitSlop={10} onPress={()=>onChangeText('')}><X color={colors.iconMuted} size={19}/></Pressable>}</View>}
const styles=StyleSheet.create({shell:{minHeight:58,marginHorizontal:16,paddingHorizontal:17,flexDirection:'row',alignItems:'center',gap:11,borderRadius:radii.lg,backgroundColor:'rgba(25,29,35,.94)',borderWidth:1,borderColor:'rgba(117,167,255,.22)'},compact:{minHeight:50,borderRadius:radii.md}});
