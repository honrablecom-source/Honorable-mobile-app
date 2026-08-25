export const searchIntentTemplates=[
  "can't find an old photo",'cannot find photo without the date','find a picture when I forgot when it was taken','describe a photo to find it','app that finds photos by description','search camera roll by what is in photo','looking for one picture for hours','photo search by memory','find old family photo on phone','search image without filename',
  "can't find an old video",'find video by what happens in it','search inside phone videos','forgot when I recorded a video','find one clip in thousands of videos','video search by description','looking for a video from years ago','search camera roll videos by scene','app to find old video','find moment inside long video',
  "can't find old screenshot",'find screenshot by words','search receipt screenshot','lost screenshot in camera roll','find document saved as photo','search screenshot without date','too many screenshots to search','find ticket screenshot on phone','find invoice photo','search gallery for receipt',
  'too many photos on iPhone','too many photos on Android','40000 photos cannot find anything','camera roll has thousands of pictures','scrolling camera roll for an hour','massive photo library search','how to manage 20000 photos','photo organization app huge library','camera roll impossible to navigate','organize years of phone photos',
  'iPhone Photos search not working','Google Photos search cannot find picture','Apple Photos search missed photo','gallery search is bad','need better photo search app','photo search does not understand description','search tool cannot find old memory','AI photo finder private','on device photo search app','search photos locally without cloud',
  'find photo of dog at beach','find picture where someone wore blue','search photos by object','find photos by activity','find picture of people playing tennis','search camera roll by scene','find photo from remembered details','natural language photo search','find image by describing event','search gallery using a sentence'
];

export class DiscoveryQueryEngine {
  constructor({budget=5,seed=()=>new Date().getUTCDate()}={}){this.budget=Math.max(1,Math.min(10,budget));this.seed=seed;}
  select(customQuery){if(customQuery)return[customQuery];const offset=this.seed()%searchIntentTemplates.length;return Array.from({length:this.budget},(_,i)=>searchIntentTemplates[(offset+i*11)%searchIntentTemplates.length]);}
  combined(customQuery){return this.select(customQuery).map(x=>`"${x}"`).join(' OR ');}
}
