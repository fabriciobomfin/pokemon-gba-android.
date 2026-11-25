// Visual composition uses the existing library actions and local data.
const brand=make('span');brand.className='brand-mark';brand.setAttribute('aria-hidden','true');brand.append(sectionIcon('controller'));document.querySelector('.mast img').replaceWith(brand);
el('import').prepend(sectionIcon('plus'));el('full-backup-open').prepend(sectionIcon('backup'));
const offline=make('span','OFFLINE READY');offline.className='offline-badge';document.querySelector('.home-intro').append(offline);
function decorateLibrary(){
 for(const card of el('list').querySelectorAll('.gamecard')){
  const art=card.querySelector('.game-cover');if(art&&!art.querySelector('img')&&!art.querySelector('.cover-monogram')){
   const text=art.querySelector('span'),name=text?.textContent||'FG';
   const monogram=make('b',name.replace(/[^\p{L}\p{N}]/gu,'').slice(0,2).toUpperCase());monogram.className='cover-monogram';
   art.prepend(monogram);art.dataset.variant=String([...name].reduce((n,c)=>n+c.charCodeAt(0),0)%4);
  }
 }
}
new MutationObserver(decorateLibrary).observe(el('list'),{childList:true});decorateLibrary();
for(const tile of el('gallery').children){
 const preview=tile.querySelector('.console-preview');const display=make('span');display.className='mini-display';display.append(make('span','FIRE GAME'));preview.prepend(display);
 const caption=make('small','CONSOLE EDITION');caption.className='theme-caption';tile.append(caption);
}
