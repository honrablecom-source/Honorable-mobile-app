const PREVIEWS=[
['01-home','Home','Current Bubble OS UI','IMPLEMENTED'],
['02-memories','Memories','Current Bubble OS UI','IMPLEMENTED'],
['03-storage','Usage & Indexing','Current Bubble OS UI','IMPLEMENTED'],
['04-settings','Settings','Current Bubble OS UI','IMPLEMENTED'],
['05-searching','Searching','Current Bubble OS UI','IMPLEMENTED'],
['06-photo-results','Photo Results','Current Bubble OS UI','IMPLEMENTED'],
['07-video-moment','Video Moment','Current Bubble OS UI','IMPLEMENTED']
,
['08-models','Models','Current Bubble OS UI','IMPLEMENTED'],
['09-privacy-data','Privacy & Data','Current Bubble OS UI','IMPLEMENTED'],
['10-seran-consent','Seran Consent','Current Bubble OS UI','IMPLEMENTED'],
['11-memory-pass','Memory Pass','Current Bubble OS UI','IMPLEMENTED']
].map(([id,name,group,status])=>({id,name,group,status,file:`${id}.png`,theme:'light'}));
