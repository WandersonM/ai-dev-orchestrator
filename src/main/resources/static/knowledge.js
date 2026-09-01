(()=>{
  const knowledgeTypes=['FACT','BUSINESS_RULE','DECISION','CONSTRAINT','INTEGRATION_RULE','ARCHITECTURE_DECISION','KNOWN_LEGACY_BEHAVIOR'];
  const confidences=['CONFIRMED','HIGH','MEDIUM','LOW'];

  function install(){
    const projectsView=document.querySelector('#projectsView');
    if(!projectsView||document.querySelector('#knowledgePanel'))return;
    projectsView.insertAdjacentHTML('beforeend',`
      <section id="knowledgePanel" class="panel top-gap">
        <div class="panel-head">
          <div><span class="section-kicker">PROJECT MEMORY</span><h2>Knowledge Base dos agentes</h2><p>Regras confirmadas, decisões, constraints e comportamentos legados que devem sobreviver aos cards.</p></div>
          <span id="knowledgeCount" class="pill">0 itens</span>
        </div>
        <div class="two-column knowledge-layout">
          <form id="knowledgeForm" class="stack-form">
            <div class="form-row">
              <label>Tipo<select id="knowledgeType">${knowledgeTypes.map(x=>`<option>${x}</option>`).join('')}</select></label>
              <label>Confiança<select id="knowledgeConfidence">${confidences.map(x=>`<option>${x}</option>`).join('')}</select></label>
            </div>
            <label>Regra / conhecimento<textarea id="knowledgeStatement" rows="5" required placeholder="Ex.: títulos com status PAGO nunca podem ter vencimento alterado."></textarea></label>
            <div class="form-row">
              <label>Origem<input id="knowledgeSourceType" value="HUMAN_DECISION" placeholder="TRELLO, DOMAIN_EXPERT, ADR..."></label>
              <label>Referência<input id="knowledgeSourceRef" placeholder="ERP-101, ADR-003..."></label>
            </div>
            <button class="btn primary" type="submit">Adicionar à memória do projeto</button>
          </form>
          <div>
            <div class="mini-note">Essa informação fica disponível para Domain Guardian, Architect, Devs, QA, Critic, Reviewer e Security através da tool <code>project_knowledge</code>.</div>
            <div id="knowledgeList" class="knowledge-list"></div>
          </div>
        </div>
      </section>`);
    document.querySelector('#knowledgeForm').addEventListener('submit',addKnowledge);
    document.querySelector('[data-view="projects"]')?.addEventListener('click',()=>setTimeout(loadKnowledge,0));
    document.querySelector('#projectSelect')?.addEventListener('change',()=>setTimeout(loadKnowledge,0));
    loadKnowledge();
  }

  async function loadKnowledge(){
    const root=document.querySelector('#knowledgeList');
    if(!root)return;
    if(!state.project){root.innerHTML='<span class="muted">Selecione um projeto.</span>';document.querySelector('#knowledgeCount').textContent='0 itens';return;}
    const list=await safe(`/api/projects/${state.project.id}/knowledge`,[]);
    document.querySelector('#knowledgeCount').textContent=`${list.length} ${list.length===1?'item':'itens'}`;
    root.innerHTML=list.length?list.map(k=>`<article class="knowledge-card"><div class="knowledge-head"><div><span class="pill ${k.confidence==='CONFIRMED'?'ok':'info'}">${esc(k.type)}</span><span class="pill">${esc(k.confidence)}</span></div><button class="btn small ghost" data-supersede="${k.id}">Arquivar</button></div><p>${esc(k.statement)}</p><small>${esc(k.sourceType||'')} ${k.sourceRef?'· '+esc(k.sourceRef):''}</small></article>`).join(''):'<span class="muted">Nenhum conhecimento persistente cadastrado.</span>';
    root.querySelectorAll('[data-supersede]').forEach(button=>button.onclick=()=>supersede(button.dataset.supersede));
  }

  async function addKnowledge(event){
    event.preventDefault();
    if(!state.project)return toast('Selecione um projeto',true);
    const body={type:document.querySelector('#knowledgeType').value,statement:document.querySelector('#knowledgeStatement').value.trim(),sourceType:document.querySelector('#knowledgeSourceType').value.trim()||'HUMAN_DECISION',sourceRef:document.querySelector('#knowledgeSourceRef').value.trim()||null,confidence:document.querySelector('#knowledgeConfidence').value,createdBy:'control-plane'};
    try{
      await api(`/api/projects/${state.project.id}/knowledge`,{method:'POST',body:JSON.stringify(body)});
      document.querySelector('#knowledgeStatement').value='';document.querySelector('#knowledgeSourceRef').value='';
      toast('Conhecimento adicionado à memória do projeto');await loadKnowledge();
    }catch(e){toast(e.message,true)}
  }

  async function supersede(id){
    try{await api(`/api/projects/${state.project.id}/knowledge/${id}/supersede`,{method:'POST',body:JSON.stringify({actor:'control-plane'})});toast('Conhecimento arquivado');await loadKnowledge()}catch(e){toast(e.message,true)}
  }

  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install);else install();
})();
