const el=id=>document.getElementById(id);
function read(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}}
function write(key,data){localStorage.setItem(key,JSON.stringify(data))}
function make(tag,text){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e}
function action(text,fn){const b=make('button',text);b.onclick=fn;return b}
function gameMeta(id){return read('meta:'+id,{})}
function updateMeta(id,fields){write('meta:'+id,Object.assign(gameMeta(id),fields))}

const iconPaths = {
 gear: 'M10 3h4l.65 2.34 1.4.81 2.35-.6 2 3.46-1.7 1.73v1.64l1.7 1.73-2 3.46-2.35-.6-1.4.81L14 21h-4l-.65-2.22-1.4-.81-2.35.6-2-3.46 1.7-1.73v-1.64L3.6 10l2-3.46 2.35.6 1.4-.81z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',
 screen: 'M3 4h18v13H3z M12 17v3 M8 20h8',
 sound: 'M3 9h4l5-4v14l-5-4H3z M15 8a6 6 0 0 1 0 8 M18 5a10 10 0 0 1 0 14',
 controller: 'M8 7h8q4 0 5 6l.5 3q.4 4-3 2l-3-3h-7l-3 3q-3.4 2-3-2l.5-3q1-6 5-6z M6 11h4 M8 9v4 M16 11h.1 M18 13h.1',
 palette: 'M12 3a9 9 0 1 0 0 18q3 0 2-2.5t2-3h1a4 4 0 0 0 4-4Q20 3 12 3z M7 12h.1 M9 8h.1 M14 7h.1 M17 10h.1',
 sliders: 'M3 6h3m4 0h11 M3 12h10m4 0h4 M3 18h5m4 0h9 M6 4v4h4V4z M13 10v4h4v-4z M8 16v4h4v-4z',
 library: 'M3 4h4v16H3z M10 4h3v16h-3z M15 5l3-1 4 15-3 1z',
 guide: 'M12 6q-4-3-9-2v14q5-1 9 2 4-3 9-2V4q-5-1-9 2z M12 6v14',
 restore: 'M4 4v6h6 M4 10a8 8 0 1 1 0 5',
 backup: 'M4 4h13l3 3v13H4z M8 4v6h8V4 M8 20v-6h8v6',
 search: 'M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0z M15 15l6 6',
 document: 'M5 3h10l4 4v14H5z M14 3v5h5 M8 12h8 M8 16h6'
};
function sectionIcon(name) {
 const ns='http://www.w3.org/2000/svg';
 const svg=document.createElementNS(ns,'svg'),path=document.createElementNS(ns,'path');
 svg.setAttribute('class','section-icon');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');svg.setAttribute('focusable','false');
 path.setAttribute('d',iconPaths[name]);svg.append(path);return svg;
}

// One palette controls the whole interface, including text and console buttons.
const themes={
 ameixa:{name:'Ameixa',colors:['#120f1e','#251d37','#514263','#fff0d7','#c8b9d2','#ffbd76','#28172a']},
 noite:{name:'Meia-noite',colors:['#080d19','#182438','#364a68','#edf4ff','#a9bcd6','#91bfff','#11253e']},
 roxo:{name:'Roxo',colors:['#130d22','#291b42','#634786','#f5ecff','#c8b4df','#c59aff','#28153f']},
 ciano:{name:'Ciano',colors:['#071b20','#10343d','#2d6370','#e2fbff','#a4cbd3','#68e4ee','#072a30']},
 dourado:{name:'Dourado',colors:['#19150c','#302817','#6c5931','#fff3d5','#d0bd92','#f6c861','#2c210a']},
 azul:{name:'Azul oceano',colors:['#081727','#132d49','#315a82','#edf6ff','#adc5df','#70b9ff','#102b48']},
 jade:{name:'Jade',colors:['#0b1d18','#18382c','#39634c','#eaf9e9','#b1cdb5','#8fe2ad','#132f21']},
 brasa:{name:'Brasa',colors:['#23120e','#3e231a','#80503a','#fff1df','#ddbda7','#ffab70','#341b0c']},
 rubi:{name:'Rubi',colors:['#240f19','#401d2b','#814258','#fff0f2','#d6b2bd','#ff91ad','#3d1321']},
 rosa:{name:'Rosa',colors:['#21121f','#3a2338','#75536f','#fff0fb','#d4bad0','#f6a7d6','#381b2d']},
 circuito:{name:'Circuito',pattern:'repeating-linear-gradient(135deg,transparent 0 13px,#48d9aa28 13px 15px,transparent 15px 26px),repeating-linear-gradient(45deg,#092722 0 28px,#104138 28px 30px)',colors:['#071e1b','#143630','#356558','#e6fff3','#a3cdbd','#83f1b8','#0a2d1b']},
 aurora:{name:'Aurora',pattern:'radial-gradient(ellipse at 12% 100%,#096d7999,transparent 60%),radial-gradient(ellipse at 80% 10%,#7048a299,transparent 65%),linear-gradient(135deg,#0c2034,#21132e)',colors:['#101b31','#252641','#514d73','#eff2ff','#c0c3db','#a7dcec','#14283a']},
 carbono:{name:'Fibra de carbono',pattern:'repeating-linear-gradient(45deg,#20232a 0 4px,#14171c 4px 8px,#1c2026 8px 12px)',colors:['#121417','#26282e','#555960','#fcf4df','#cdc4ad','#f5c554','#2a210b']},
 grafite:{name:'Grafite',colors:['#14171c','#272d35','#515c69','#f0f3f7','#bbc4d0','#becbdd','#1c2735']}
};
function applyTheme(name){
 const theme=themes[name]||themes.ameixa;
 ['--base','--panel','--line','--paper','--muted','--accent','--on-accent'].forEach((key,i)=>document.documentElement.style.setProperty(key,theme.colors[i]));
 document.documentElement.style.setProperty('--mint',theme.colors[5]);document.documentElement.style.setProperty('--ink',theme.colors[3]);
 document.documentElement.style.setProperty('--console-surface',theme.pattern||'none');
 document.documentElement.dataset.theme=themes[name]?name:'ameixa';
}
applyTheme(read('fire-game-theme','ameixa'));
