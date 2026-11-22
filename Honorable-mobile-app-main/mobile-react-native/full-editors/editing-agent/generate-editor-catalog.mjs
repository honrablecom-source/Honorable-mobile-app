// Input files are the results of engine.commands and (where supported) ui.menu.list.
// Store catalog metadata only, excluding document data, locale-specific key bindings and enablement.
import {readFileSync,writeFileSync} from 'node:fs';
const catalog={};
for(const [i,studio] of ['photo','video'].entries()) {
  const {commands,menus=[]}=JSON.parse(readFileSync(process.argv[i+2],'utf8'));
  const rows=new Map(commands.map(c=>[c.id,[c.id,c.label,c.params||'{}','engine']]));
  for(const m of menus) {
    if (!/^[a-z][\w.-]*$/.test(m.id) || !m.label || /^(?:window\.document\.|file\.openRecent\.)/.test(m.id)) continue;
    if (!rows.has(m.id)) rows.set(m.id,[m.id,m.label,'{}','menu']);
    else if (rows.get(m.id)[3]==='engine') rows.get(m.id)[3]='both';
  }
  if (studio==='photo') {
    const toolbar=readFileSync(process.argv[4],'utf8').match(/const TOOL_SECTIONS:[^=]*=\s*&\[([\s\S]*?)\n\];/);
    if (!toolbar) throw Error('Expected the pinned PhotoCraft toolbar source as the third argument.');
    for (const [,name] of toolbar[1].matchAll(/Tool::(\w+)/g)) {
      const id='toolbox.'+name;
      rows.set(id,[id,'Select '+name.replace(/([a-z])([A-Z])/g,'$1 $2'),'{}','palette']);
    }
  }
  catalog[studio]=[...rows.values()].sort((a,b)=>a[0]<b[0]?-1:a[0]>b[0]?1:0);
}
writeFileSync(new URL('./editor-catalog.json',import.meta.url),'{\n'+Object.entries(catalog).map(([k,rows])=>'  '+JSON.stringify(k)+': [\n'+rows.map(r=>'    '+JSON.stringify(r)).join(',\n')+'\n  ]').join(',\n')+'\n}\n');
