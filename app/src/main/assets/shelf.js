const info=JSON.parse(Android.info()),games=JSON.parse(Android.library());
el('help').onclick=()=>{show('Como jogar');detail.append(make('p','Importe uma ROM .gba ou um ZIP e toque em Jogar. A partida abre em tela cheia. Toque em 1× para alternar a velocidade.'),make('p','Salve pelo menu do jogo. Para guardar uma cópia, abra Mais e escolha Backup do último jogo.'))};
el('import').onclick=()=>Android.pick();el('continue').onclick=()=>Android.play();
el('saved').hidden=el('tools').hidden=el('progress').hidden=!info.available;
el('exp').onclick=()=>Android.backup(true);
el('imp').onclick=()=>{if(confirm('Restaurar o progresso do último jogo usando um backup?'))Android.backup(false)};
const dialog=el('modal'),detail=el('detail');
el('close').onclick=()=>dialog.close();
function show(title){detail.replaceChildren(make('h2',title));if(!dialog.open)dialog.showModal()}
function edit(id){
 const meta=gameMeta(id);show('Detalhes do jogo');
 const name=make('input');name.value=meta.name||games[id];name.maxLength=100;name.setAttribute('aria-label','Nome na biblioteca');
 const notes=make('textarea');notes.placeholder='Anotações da aventura';notes.value=meta.notes||'';notes.maxLength=5000;
 detail.append(name,notes,make('p','Sessões iniciadas: '+(meta.sessions||0)),make('p','Tempo jogado: '+Math.floor((meta.seconds||0)/60)+' min'),make('small','ID da ROM · '+id.slice(0,12)),action('Salvar',()=>{updateMeta(id,{name:name.value.trim()||games[id],notes:notes.value});dialog.close();render()}))
}
function render(){
 const ids=Object.keys(games),q=el('search').value.toLocaleLowerCase(),order=el('sort').value;
 el('romName').textContent=gameMeta(info.id).name||info.name;el('list').replaceChildren();
 const rows=ids.map(id=>({id,...gameMeta(id),name:gameMeta(id).name||games[id]})).filter(g=>g.name.toLocaleLowerCase().includes(q)&&(!el('fav').checked||g.star));
 rows.sort((a,b)=>order==='name'?a.name.localeCompare(b.name,'pt-BR'):order==='time'?(b.seconds||0)-(a.seconds||0):(b.last||0)-(a.last||0));
 for(const g of rows){const row=make('article');row.className='gamecard';const b=action(g.name,()=>Android.select(g.id));b.className='title';b.append(make('small',g.last?'Última sessão · '+new Date(g.last).toLocaleDateString('pt-BR'):'Importado'));const star=action(g.star?'★':'☆',()=>{updateMeta(g.id,{star:!g.star});render()});star.setAttribute('aria-label',g.star?'Remover favorito':'Favoritar');row.append(b,star,action('Detalhes',()=>edit(g.id)));el('list').append(row)}
 if(!rows.length)el('list').append(make('p',ids.length?'Nenhum jogo corresponde ao filtro.':'Sua biblioteca começa com a primeira ROM.'))
}
el('sort').value=read('shelfSort','recent');el('sort').onchange=()=>{write('shelfSort',el('sort').value);render()};el('search').oninput=el('fav').onchange=render;render();
let urls=[];dialog.onclose=()=>{urls.forEach(URL.revokeObjectURL);urls=[]};
el('shots').onclick=async()=>{show('Galeria local');const gallery=make('div');gallery.id='gallery';detail.append(gallery);try{const shots=await photos('readonly',store=>store.getAll());if(!shots.length)gallery.append(make('p','As capturas feitas durante o jogo aparecem aqui.'));for(const shot of shots.reverse()){const fig=make('figure'),img=make('img');img.src=URL.createObjectURL(shot.blob);urls.push(img.src);img.alt=shot.name;fig.append(img,make('figcaption',shot.name+' · '+new Date(shot.time).toLocaleString('pt-BR')),action('Excluir captura',async()=>{if(confirm('Excluir esta captura?')){await photos('readwrite',s=>s.delete(shot.time));fig.remove()}}));gallery.append(fig)}}catch{gallery.textContent='Falha ao abrir as capturas.'}};

for(const [selector,icon] of [['.shelf h1','library'],['#help','guide'],['#shots','gallery'],['#progress summary','backup'],['#tools summary','search'],['.utility a','document']]){
 document.querySelector(selector).prepend(sectionIcon(icon));
}
