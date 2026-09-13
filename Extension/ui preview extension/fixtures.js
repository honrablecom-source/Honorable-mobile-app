const previewEntries=[
{id:'Q170583',name:'Elizabeth Bennet',aliases:[],kind:'character',description:'The protagonist of Pride and Prejudice, known for her wit and independent judgment.',source:'https://www.wikidata.org/wiki/Q170583',wikipedia:'https://en.wikipedia.org/wiki/Elizabeth_Bennet'},
{id:'Q336723',name:'Fitzwilliam Darcy',aliases:['Mr. Darcy'],kind:'character',description:'A wealthy gentleman whose relationship with Elizabeth Bennet is central to the novel.',source:null,wikipedia:'https://en.wikipedia.org/wiki/Fitzwilliam_Darcy'},
{id:'Q21',name:'England',aliases:[],kind:'place',description:'The country in which the events of Pride and Prejudice are set.',source:'https://www.wikidata.org/wiki/Q21',wikipedia:'https://en.wikipedia.org/wiki/England'}];
const previewRoleStore={'nc-roles:unknown:Q170583':['mc','fl']};
window.chrome={storage:{local:{get:async key=>({[key]:previewRoleStore[key]}),set:async value=>Object.assign(previewRoleStore,value),remove:async key=>{delete previewRoleStore[key];}}}};
