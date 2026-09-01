const PREVIEWS=[
['01-home','Home','Current Bubble OS UI','IMPLEMENTED'],
['02-memories','Memories','Current Bubble OS UI','IMPLEMENTED'],
['03-storage','Storage','Current Bubble OS UI','IMPLEMENTED'],
['04-settings','Settings','Current Bubble OS UI','IMPLEMENTED'],
['05-searching','Searching','Current Bubble OS UI','IMPLEMENTED'],
['06-photo-results','Photo Results','Current Bubble OS UI','IMPLEMENTED'],
['07-video-moment','Video Moment','Current Bubble OS UI','IMPLEMENTED']
].map(([id,name,group,status])=>({id,name,group,status,file:`${id}.png`,theme:'light'}));
