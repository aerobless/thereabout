import {LauncherCollection, LauncherShortcut} from '../../../../generated/backend-api/thereabout';

export function localDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
export function greeting(date: Date): string {
  const hour=date.getHours();
  return hour>=5 && hour<12 ? 'Good morning' : hour>=12 && hour<18 ? 'Good afternoon' : 'Good evening';
}
const normalize=(value: string) => value.normalize('NFKD').replace(/\p{M}/gu,'').toLocaleLowerCase();

export function searchShortcuts(collection: LauncherCollection, query: string): LauncherShortcut[] {
  const text=normalize(query.trim());
  if(!text) return [];
  const tokens=text.split(/\s+/);
  const groups=new Map(collection.groups.map(g=>[g.id,`${g.section} ${g.name}`]));
  return collection.shortcuts.map((shortcut,index)=> {
    const name=normalize(shortcut.title);
    const haystack=normalize(`${shortcut.title} ${shortcut.description} ${groups.get(shortcut.groupId) ?? ''} ${shortcut.url}`);
    const match=tokens.every(token=>haystack.includes(token));
    const rank=name===text?0:name.startsWith(text)?1:tokens.every(token=>name.includes(token))?2:3;
    return {shortcut,index,rank,match};
  }).filter(item=>item.match).sort((a,b)=>a.rank-b.rank || a.index-b.index).map(item=>item.shortcut);
}

export function shortcutDomain(url: string): string {
  try { return new URL(url).host.replace(/^www\./,''); } catch { return ''; }
}
