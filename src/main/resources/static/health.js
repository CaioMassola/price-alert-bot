const refreshButton = document.querySelector('#refresh');
const health = document.querySelector('#health');
const discord = document.querySelector('#discord');
const stores = document.querySelector('#stores');
const storeError = document.querySelector('#store-error');
const names = { KABUM: 'KaBuM', MERCADO_LIVRE: 'Mercado Livre', AMAZON: 'Amazon', STEAM: 'Steam', EPIC: 'Epic Games' };
const states = { OK: 'Coleta funcionando', UNAVAILABLE: 'Coleta indisponível', ERROR: 'Erro na coleta', NOT_CHECKED: 'Aguardando coleta' };

async function getJson(url, allowUnhealthy = false) {
  const response = await fetch(url, { cache: 'no-store', signal: AbortSignal.timeout(8000) });
  if (!response.ok && !(allowUnhealthy && response.status === 503)) throw new Error('Consulta indisponível');
  return response.json();
}
function element(tag, text, className = '') {
  const node = document.createElement(tag);
  node.textContent = text;
  node.className = className;
  return node;
}
async function update() {
  if (refreshButton.disabled) return;
  refreshButton.disabled = true;
  try {
    const [healthResult, statusResult] = await Promise.allSettled([
      getJson('/actuator/health', true), getJson('/api/status')
    ]);
    const status = healthResult.status === 'fulfilled' ? healthResult.value.status : null;
    health.textContent = status === 'UP' ? 'API disponível' : status === 'DOWN' || status === 'OUT_OF_SERVICE' ? 'API com falha de saúde' : 'Não foi possível confirmar a saúde da API';
    health.className = `state ${status === 'UP' ? 'good' : 'bad'}`;
    stores.replaceChildren();
    if (statusResult.status === 'fulfilled' && Array.isArray(statusResult.value.stores)) {
      discord.textContent = 'Discord principal: ' + (statusResult.value.discordConfigured ? 'configurado' : 'não configurado') + '. Discord de jogos: ' + (statusResult.value.discordGamesConfigured ? 'configurado' : 'não configurado') + '. Isso não confirma a entrega de mensagens.';
      storeError.textContent = '';
      for (const store of statusResult.value.stores.filter(Boolean)) {
        const card = element('article', '');
        card.append(element('h2', names[store.store] || store.store));
        card.append(element('p', states[store.status] || 'Estado desconhecido', store.status === 'OK' ? 'good' : 'neutral'));
        card.append(element('p', `Produtos na última coleta: ${store.productCount ?? 0}`));
        if (store.detail) card.append(element('p', store.detail));
        card.append(element('small', store.checkedAt ? `Última coleta: ${new Date(store.checkedAt).toLocaleString('pt-BR')}` : 'Ainda não consultada nesta execução.'));
        stores.append(card);
      }
    } else {
      discord.textContent = 'Não foi possível consultar a configuração do Discord.';
      storeError.textContent = 'Não foi possível consultar as lojas. Tente atualizar novamente.';
    }
    document.querySelector('#checked').textContent = `Última tentativa de consulta: ${new Date().toLocaleString('pt-BR')}`;
  } finally { refreshButton.disabled = false; }
}
refreshButton.addEventListener('click', update);
update();
setInterval(update, 30000);
