const el=id=>document.getElementById(id);
function read(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback}catch{return fallback}}
function write(key,data){localStorage.setItem(key,JSON.stringify(data))}
function make(tag,text){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e}
function action(text,fn){const b=make('button',text);b.onclick=fn;return b}
function gameMeta(id){return read('meta:'+id,{})}
function updateMeta(id,fields){write('meta:'+id,Object.assign(gameMeta(id),fields))}
function photos(mode,operation){return new Promise((resolve,reject)=>{const req=indexedDB.open('lume-photos',1);req.onupgradeneeded=()=>req.result.createObjectStore('shots',{keyPath:'time'});req.onerror=()=>reject(req.error);req.onsuccess=()=>{const db=req.result,tx=db.transaction('shots',mode),result=operation(tx.objectStore('shots'));tx.oncomplete=()=>{db.close();resolve(result.result)};tx.onerror=()=>{db.close();reject(tx.error)}}})}
