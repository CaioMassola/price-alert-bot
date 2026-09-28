# Acesso ao Mercado Livre e à Amazon

**Atualização de 28/09/2026:** a integração de descoberta foi alterada para o catálogo oficial e suas ofertas concorrentes. Consulte [o diagnóstico atualizado](MERCADO-LIVRE-DIAGNOSTICO.md). As seções datadas abaixo registram a investigação anterior da busca geral, que continua retornando 403.

## Diagnóstico em 23/09/2026

Investigação complementar: com o mesmo token, `/users/me` e `/users/{proprio_usuario}/items/search?limit=1` responderam 200 (nenhum anúncio próprio). `/products/search?status=active&site_id=MLB` também respondeu 200, nas buscas por teclado e iPhone 15 128. Detalhes de três produtos responderam 200, mas todos trouxeram `buy_box_winner: null`, sem preço de oferta utilizável: MLB48021480, MLB27172671 e MLB27172667. Isso confirma acesso parcial à API, mas não valida monitoramento de preços pelo catálogo e não determina a causa da negativa na busca de anúncios. Referências: https://developers.mercadolivre.com.br/buscador-de-produtos e https://developers.mercadolivre.com.br/itens-e-buscas. Os cabeçalhos de autenticação já estão presentes no cliente; não foi usada simulação de navegador. O script de verificação agora informa a pausa de 15 segundos e não atribui automaticamente o 403 à falta de permissões; sintaxe PowerShell validada.

Atualização após OAuth: autorização concluída, access token e refresh token salvos localmente. `/users/me` autenticado com sucesso, mas `/sites/MLB/search?q=teclado&limit=1` retornou HTTP 403 mesmo com o token. Portanto, a busca de ofertas do Mercado Livre continua bloqueada; não é possível atribuir a causa somente às permissões sem diagnóstico adicional. O serviço do bot não foi recriado com esse token, pois a validação de busca falhou. Túnel temporário encerrado após a autorização; será preciso gerar e cadastrar nova URI para futuras autorizações. Renovação automática ainda não implementada.

O Mercado Livre retornou HTTP 403 com corpo `forbidden` na busca sem credenciais. A Amazon retornou HTTP 503 e uma página intitulada “Amazon.com.br Algo deu errado”. Essas respostas não contêm preços que possam ser coletados. Corrigir seletores HTML não resolve essas respostas.

A KaBuM continua operando no Docker. Não há uso de proxies, identidades rotativas, cookies privados ou solução de CAPTCHA.

## Callback local preparado

O auxiliar `node scripts/mercadolivre-oauth.mjs` recebe somente o retorno OAuth na raiz de `127.0.0.1:8765`. Não encaminha chamadas para a API do bot. Dois testes de segurança passam com `node --test scripts/mercadolivre-oauth.test.mjs` (estado inválido/expirado, isolamento de rotas e reutilização de código).

O usuário autorizou explicitamente o trânsito do código pela Cloudflare. O túnel temporário está ativo no container `milize-oauth-tunnel`, apontando somente para o callback local. A URI foi salva em `MERCADO_LIVRE_REDIRECT_URI` no `.env`. Validação pública: raiz HTTP 200, `/api/products` HTTP 404, retorno com estado inválido HTTP 400. O endereço pode mudar ao reiniciar o túnel; nesse caso, atualizar tanto o cadastro quanto o `.env`. Para encerrar: `docker stop milize-oauth-tunnel`. A autorização real ainda depende da criação da aplicação pelo usuário.

Depois de disponibilizar o túnel e criar a aplicação, configurar no `.env` `MERCADO_LIVRE_CLIENT_ID`, `MERCADO_LIVRE_CLIENT_SECRET` e `MERCADO_LIVRE_REDIRECT_URI` (raiz HTTPS exata do túnel). Executar `node scripts/mercadolivre-oauth.mjs authorize` e abrir o link produzido no navegador. O estado expira em dez minutos e é usado uma única vez. Manter PKCE desmarcado nesta versão. O callback troca o código no endpoint oficial, salva os tokens no `.env` sem imprimi-los e não reinicia o bot automaticamente. A renovação automática ainda não está implementada. Após autorizar, validar a busca e recriar o serviço para carregar o token.

## Mercado Livre: próximo passo

1. Entre com sua própria conta no [portal de desenvolvedores](https://developers.mercadolivre.com.br/devcenter).
2. Siga o [cadastro oficial de aplicação](https://developers.mercadolivre.com.br/crie-uma-aplicacao-no-mercado-livre). Nome sugerido: **MilizeOfertas**. Informe a finalidade real: consultar preços de produtos para alertas de ofertas no seu canal Discord.
3. O cadastro exige uma URI de redirecionamento HTTPS. O callback local descrito acima está implementado; falta disponibilizar e testar seu endereço HTTPS antes de cadastrá-lo. Não use o webhook Discord como retorno.
4. Após cadastrar a aplicação, implemente/configure o fluxo oficial [OAuth com autorização do usuário](https://developers.mercadolivre.com.br/autenticacao-e-autorizacao). App ID, Client Secret e Access Token são campos distintos; não coloque Client Secret no lugar de Access Token.
5. Salve o token emitido em `MERCADO_LIVRE_ACCESS_TOKEN`, no `.env`, sem compartilhá-lo no chat.
6. Execute `scripts/check-mercadolivre.ps1`. A verificação consulta a identidade autorizada e a busca de produtos separadamente, imprimindo apenas estados HTTP e contagens.
7. Só depois de confirmar a autorização, recrie o serviço para carregar a configuração: `docker compose up -d --force-recreate price-alert-api`.

O código atual já tem integração autenticada com `/sites/MLB/search` e `/items/{id}`. Ter uma aplicação e um token não garante acesso a todos os recursos: a loja pode restringir a consulta. Um teste bem-sucedido em `/users/me` também não prova acesso à busca. Renovação automática do token ainda não está implementada.

## Amazon: aprovação antes de prometer funcionamento

1. Acesse o [Amazon Associados Brasil](https://associados.amazon.com.br/).
2. Confira se o canal/site informado é aceito pelo programa. Não declare um site, público ou vendas que você não possui.
3. A [documentação da Creators API](https://associados.amazon.com.br/creatorsapi/docs/en-us/onboarding/register-for-creators-api) exige aceitação final no programa e vendas qualificadas antes de liberar o cadastro da API.
4. Se aprovado e habilitado, o titular acessa **Ferramentas → Creators API → Create Application** e obtém Credential ID, Secret e Version.
5. Antes de usar a API neste projeto, é necessário implementar a integração oficial e verificar as condições de exibição e retenção dos dados para o canal e o histórico de preços.

**O código atual da Amazon usa apenas páginas públicas; não tem cliente Creators API.** Não existem variáveis de credenciais Amazon prontas para configurar. Uma conta comum de comprador Amazon não é acesso à API. O acesso depende de aprovação da Amazon, não pode ser liberado apenas alterando o bot.

## Consultar o diagnóstico

`GET /api/stores` fornece o estado de cada loja, com `detail` e `nextStep`. A ausência de erro no banco ou no Discord não implica que todas as lojas estejam funcionando.
