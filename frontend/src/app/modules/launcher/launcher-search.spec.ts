import {LauncherCollection,LauncherShortcut} from '../../../../generated/backend-api/thereabout';
import {greeting,localDate,searchShortcuts} from './launcher-search';

export const exampleCollection:LauncherCollection={
  groups:[{id:1,name:'Work tools',section:'Example',position:0},{id:2,name:'Reading',section:'Example',position:1}],
  shortcuts:[
    {id:1,groupId:1,title:'Calendar',description:'Plan your week',url:'https://example.com/calendar',emoji:'📅',position:0,iconVersion:0,hasIcon:false,iconState:'fallback'},
    {id:2,groupId:1,title:'Call notes',description:'Meeting journal',url:'https://example.com/notes',emoji:'',position:1,iconVersion:0,hasIcon:false,iconState:'fallback'},
    {id:3,groupId:2,title:'Zürich magazine',description:'Local stories',url:'https://example.org',emoji:'',position:0,iconVersion:1,hasIcon:true,iconState:'ready'}
  ]
};
describe('launcher search and local date',()=> {
  it('matches title, description, group and URL while prioritizing exact titles',()=> {
    const more:LauncherShortcut={...exampleCollection.shortcuts[0],id:4,title:'Shared calendar'};
    const collection={...exampleCollection,shortcuts:[more,...exampleCollection.shortcuts]};
    expect(searchShortcuts(collection,'calendar').map(s=>s.title)).toEqual(['Calendar','Shared calendar']);
    expect(searchShortcuts(collection,'meeting work').map(s=>s.title)).toEqual(['Call notes']);
    expect(searchShortcuts(collection,'example.org').map(s=>s.title)).toEqual(['Zürich magazine']);
    expect(searchShortcuts(collection,'Zurich')[0].id).toBe(3);
    expect(searchShortcuts(collection,'  ')).toEqual([]);
    expect(searchShortcuts(collection,'unknown')).toEqual([]);
  });
  it('uses local calendar dates and switches the greeting at the expected hours',()=> {
    expect(localDate(new Date(2026,8,25,0,5))).toBe('2026-09-25');
    expect(greeting(new Date(2026,8,25,5))).toBe('Good morning');
    expect(greeting(new Date(2026,8,25,12))).toBe('Good afternoon');
    expect(greeting(new Date(2026,8,25,18))).toBe('Good evening');
    expect(greeting(new Date(2026,8,25,2))).toBe('Good evening');
  });
});
