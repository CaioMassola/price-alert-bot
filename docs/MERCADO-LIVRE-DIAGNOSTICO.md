# Mercado Livre: investigação de 28/09/2026

## Evidências

**Resultado no bot implantado:** `/api/stores` confirmou Mercado Livre `OK` com cinco anúncios reais persistidos em 28/09/2026 às 14:19:47 UTC (IDs locais 39–43). A busca inicial `iphone 15 128` retornou ofertas do modelo azul entre R$ 4.899 e R$ 5.572. Preço original ausente; nenhuma promoção é afirmada somente com esses valores. O ciclo de coleta e persistência está validado, mas envio de alerta Mercado Livre depende de desconto efetivo e ainda não foi validado.

- O access token anterior estava expirado. A renovação oficial retornou HTTP 200; ambos os tokens e a validade foram salvos no `.env` sem imprimir segredos.
- Com o novo token, `/users/me` retornou 200.
- `/sites/MLB/search?q=teclado&limit=1` continuou retornando 403. Request ID: `fb49ab72-defa-4013-b889-c02f65a7292b`.
- `/products/MLB27172671/items?limit=2` retornou 200, com dois anúncios reais, preços de R$ 3.500 e R$ 3.800 no instante da consulta. Esses valores são evidência histórica do teste, não ofertas garantidas.
- Na investigação anterior, `/products/search` e detalhes de catálogo já retornavam 200, mas `buy_box_winner` vinha vazio. Isso não significava ausência de ofertas: faltava consultar o recurso de publicações concorrentes.

## Conclusão e limites

O 403 persiste com autenticação válida; outros recursos oficiais respondem na mesma rede. Não foi identificada a política interna que nega a busca geral. Não há evidência suficiente para afirmar bloqueio geral de IP ou falta de escopo. Não foi localizado anúncio oficial de desligamento universal de `/sites/MLB/search?q=...`.

A documentação de busca de itens descreve substituições para consultas por vendedor e consultas múltiplas, não uma substituição equivalente para busca geral por palavra-chave. A solução implementada tem cobertura de catálogo, não de todo o marketplace.

## Mudança no bot

Com token configurado, a descoberta passa a consultar `/products/search`, `/products/{id}` e `/products/{id}/items`. Os IDs e históricos continuam sendo por anúncio/vendedor. O link do catálogo inclui `wid` com o anúncio correspondente. Ofertas usadas e com quantidade mínima maior que uma unidade são excluídas. Preço original ausente continua ausente: não fabricamos desconto.

O endpoint de competição é usado como evidência de oferta ativa; a resposta não informa quantidade exata de estoque. Frete e condições do checkout podem alterar o custo final. Links de catálogo rastreados usam o catálogo; links diretos de anúncio continuam dependendo de `/items/{id}` e podem ser restritos. Quando um `wid` específico não está na amostra, o bot não substitui por outro vendedor. A consulta é limitada à primeira página, com até cinco produtos/ofertas por ciclo e intervalo de 15 segundos entre requests.

O token está renovado para esta validação. A renovação automática persistente ainda não está implementada; ao expirar, é necessária renovação e recarga do serviço. O script `check-mercadolivre.ps1` testa a busca geral legada para diagnóstico: seu 403 não representa, sozinho, o estado do novo coletor de catálogo. Para o coletor, consulte `/api/stores`.

## Referências

- [Busca de itens](https://developers.mercadolivre.com.br/itens-e-buscas)
- [Busca e detalhes de catálogo](https://developers.mercadolivre.com.br/buscador-de-produtos)
- [Publicações concorrentes de uma página de produto](https://developers.mercadolibre.com.ar/es_ar/mercadoenvios-modo-1/competencia-en-catalogo)
- [Relato reproduzível de 403 desde abril de 2025](https://github.com/mercadolibre/golang-restclient/issues/9) — relato de usuário, não comunicado oficial de política de acesso.

## Testes

Build Docker: 36 testes, 34 executados com sucesso e dois testes opcionais de PostgreSQL ignorados. Novos testes verificam coleta sem `buy_box_winner`, identidade por vendedor, exclusão de usados/atacado, preservação do vendedor escolhido, permalink vazio, continuidade após ofertas 404 e interrupção em caso de acesso negado. A API real retornou permalink vazio para um produto ativo; nesse caso usa-se a URL canônica HTTPS `/p/{catalog_id}`. Alguns produtos de catálogo retornam 404 na lista de ofertas; eles são ignorados na descoberta, sem fabricar dados. A busca `iphone 15 128` foi adicionada antes das buscas existentes para a validação real.
