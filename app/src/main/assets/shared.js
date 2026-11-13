const el=id=>document.getElementById(id);
function read(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}}
function write(key,data){localStorage.setItem(key,JSON.stringify(data))}
function make(tag,text){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e}
function action(text,fn){const b=make('button',text);b.onclick=fn;return b}
function gameMeta(id){return read('meta:'+id,{})}
function updateMeta(id,fields){write('meta:'+id,Object.assign(gameMeta(id),fields))}
function photos(mode,operation){return new Promise((resolve,reject)=>{const req=indexedDB.open('lume-photos',1);req.onupgradeneeded=()=>req.result.createObjectStore('shots',{keyPath:'time'});req.onerror=()=>reject(req.error);req.onsuccess=()=>{const db=req.result,tx=db.transaction('shots',mode),result=operation(tx.objectStore('shots'));tx.oncomplete=()=>{db.close();resolve(result.result)};tx.onerror=()=>{db.close();reject(tx.error)}}})}

const iconPaths = {
 gear: 'M10 3h4l.65 2.34 1.4.81 2.35-.6 2 3.46-1.7 1.73v1.64l1.7 1.73-2 3.46-2.35-.6-1.4.81L14 21h-4l-.65-2.22-1.4-.81-2.35.6-2-3.46 1.7-1.73v-1.64L3.6 10l2-3.46 2.35.6 1.4-.81z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',
 screen: 'M3 4h18v13H3z M12 17v3 M8 20h8',
 sound: 'M3 9h4l5-4v14l-5-4H3z M15 8a6 6 0 0 1 0 8 M18 5a10 10 0 0 1 0 14',
 controller: 'M8 7h8q4 0 5 6l.5 3q.4 4-3 2l-3-3h-7l-3 3q-3.4 2-3-2l.5-3q1-6 5-6z M6 11h4 M8 9v4 M16 11h.1 M18 13h.1',
 palette: 'M12 3a9 9 0 1 0 0 18q3 0 2-2.5t2-3h1a4 4 0 0 0 4-4Q20 3 12 3z M7 12h.1 M9 8h.1 M14 7h.1 M17 10h.1',
 sliders: 'M3 6h3m4 0h11 M3 12h10m4 0h4 M3 18h5m4 0h9 M6 4v4h4V4z M13 10v4h4v-4z M8 16v4h4v-4z',
 library: 'M3 4h4v16H3z M10 4h3v16h-3z M15 5l3-1 4 15-3 1z',
 guide: 'M12 6q-4-3-9-2v14q5-1 9 2 4-3 9-2V4q-5-1-9 2z M12 6v14',
 gallery: 'M3 4h18v16H3z M4 17l5-5 4 4 3-3 5 5 M16 8h.1',
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
