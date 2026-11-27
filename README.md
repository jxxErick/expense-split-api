# expense-split-api

API REST para dividir despesas em grupo (viagens, república, rolês): registra quem pagou o quê,
divide cada gasto de quatro jeitos diferentes, calcula o saldo de cada pessoa e sugere o **menor
número de transferências** para todo mundo ficar quite.

## Stack

- Java 21 · Spring Boot 3.3
- Spring Web · Spring Data JPA · Bean Validation
- H2 (em memória)
- JUnit 5 · Mockito · AssertJ · MockMvc

## Estrutura

```
model/         entidades JPA (Group, Member, Expense, Settlement, RecurringExpense, Budget, Activity...)
repository/    interfaces Spring Data
service/       regras de negócio
  split/       estratégias de divisão + alocação de centavos
  receipt/     divisão de comanda por itens (taxa de serviço e desconto)
  balance/     simplificação de dívidas
  activity/    eventos do feed de atividades
controller/    endpoints REST
dto/           records de entrada e saída
exception/     exceções e handler global (RFC 7807)
config/        beans de infraestrutura (Clock, agendamento)
```

Os controllers só recebem e devolvem DTOs (`record`), os services concentram as regras e os
repositories só falam com o banco.

## O que tem de interessante

### Divisão sem perder centavo

R$ 100,00 dividido por 3 não dá 33,33 × 3. O `MoneyAllocator` usa o **método dos maiores restos**:
arredonda cada parte para baixo e distribui os centavos que sobraram para quem teve a maior
fração descartada. A soma das partes é sempre igual ao total.

| Tipo | `value` de cada participante | Exemplo (R$ 300) |
| --- | --- | --- |
| `EQUAL` | ignorado | 100 / 100 / 100 |
| `EXACT` | valor em reais (tem que somar o total) | 120 / 80 / 100 |
| `PERCENTAGE` | porcentagem (tem que somar 100) | 50% / 30% / 20% |
| `SHARES` | peso (ex.: casal = 2, solteiro = 1) | 200 / 100 |

Cada tipo é uma `SplitStrategy` (padrão **Strategy**); o `SplitCalculator` escolhe a
estratégia pelo `SplitType`. Para criar um tipo novo basta adicionar um `@Component`.

### Simplificação de dívidas

O saldo de cada membro é `pago - devido + enviado - recebido`. O `DebtSimplifier` pega esses
saldos e, de forma gulosa, faz o **maior devedor pagar o maior credor** até zerar todo mundo.
Cada passo zera pelo menos uma pessoa, então o resultado tem no máximo `n - 1` transferências,
não importa quantas despesas existam. Dívidas circulares (A deve B, B deve C, C deve A) somem.

### Acertos

Um acerto (`Settlement`) registra um pagamento entre dois membros. Ele só é aceito se quem paga
realmente deve, quem recebe realmente tem a receber, e o valor não passa de nenhuma das duas
dívidas, então ninguém troca de lado por engano.

**Quitar tudo de uma vez**: `POST /api/groups/{id}/settlements/settle-up` (corpo opcional
`{"paidAt": "2026-11-20"}`) registra **todas** as transferências do plano simplificado numa única
transação, deixando todos os saldos em zero. Cada acerto entra no feed de atividades; se o grupo já
está quite, a resposta é `422`.

### Saída de membros

`DELETE /api/groups/{id}/members/{memberId}` tira alguém do grupo sem apagar nada: o membro fica
**inativo** (`"active": false` nas respostas) e as despesas antigas continuam apontando para ele.

- só sai quem está com **saldo zero** (`422` com "Bruno ainda deve R$ 10.00..." se não estiver);
- quem participa de uma despesa recorrente ativa ou pausada precisa sair dela antes;
- o grupo nunca fica sem nenhum membro ativo;
- quem saiu não entra em despesas novas, e as despesas antigas em que ele aparece **não podem mais
  ser editadas nem excluídas** (isso reabriria um saldo que ele já fechou);
- adicionar de novo alguém com o mesmo nome **reativa** o mesmo membro (mesmo id), e o feed registra
  `MEMBER_LEFT` e `MEMBER_REJOINED`.

### Categorias e edição

Toda despesa tem uma `category` (`FOOD`, `GROCERIES`, `TRANSPORT`, `LODGING`, `LEISURE`, `BILLS`,
`OTHER`); se não vier no corpo, vira `OTHER`. A listagem aceita `?category=` para filtrar.

`PUT /expenses/{expenseId}` recebe o mesmo corpo do `POST`, recalcula a divisão do zero e troca
todas as partes de uma vez (as antigas somem por `orphanRemoval`). Se `spentAt` não vier, a data
original é mantida. Como a divisão é validada antes de mexer na despesa, um `PUT` inválido não
deixa nada pela metade.

### Relatório de gastos

`GET /api/groups/{id}/reports/spending?from=2026-11-01&to=2026-11-30` resume o período (datas
opcionais e inclusivas): total gasto, quantidade e média das despesas, total por mês, total por
categoria (do maior para o menor) e, para cada membro, quanto pagou, quanto consumiu (soma das
suas partes) e a porcentagem do total.

```json
{
  "total": 390.00, "expenseCount": 2, "averageExpense": 195.00,
  "months": [ { "month": "2026-11", "total": 390.00, "expenseCount": 2 } ],
  "categories": [
    { "category": "LODGING", "total": 300.00, "expenseCount": 1, "percentage": 76.92 },
    { "category": "FOOD", "total": 90.00, "expenseCount": 1, "percentage": 23.08 }
  ],
  "members": [
    { "memberId": 1, "memberName": "Ana", "paid": 300.00, "consumed": 100.00, "percentage": 25.64 }
  ]
}
```

### Despesas recorrentes

Aluguel, internet e streaming viram um **modelo** (`POST /recurring-expenses`) com frequência
`WEEKLY` ou `MONTHLY`, data da primeira cobrança e data final opcional. A divisão é validada na
criação (mesmas regras de uma despesa normal), então a geração nunca falha depois.

- O `RecurringExpenseScheduler` roda todo dia às 6h (`app.recurring.cron`) e transforma em despesa
  tudo o que venceu; `POST /recurring-expenses/run` faz o mesmo na hora para um grupo.
- A data de cada ocorrência é calculada **a partir da primeira** (`start.plusMonths(n)`), e não
  da anterior: um aluguel que começa em 31/01 vai para 28/02 e volta para 31/03, sem "escorregar".
- Se a aplicação ficou fora do ar, a próxima execução recupera as ocorrências perdidas (no máximo
  24 por modelo de uma vez).
- `pause`/`resume`: ao retomar, as ocorrências do período pausado são **puladas**, não cobradas.
- Ao passar da data final, o modelo vira `FINISHED`. Apagar o modelo não apaga as despesas geradas.

```json
{ "description": "Internet", "amount": 120.00, "payerId": 1, "splitType": "EQUAL", "category": "BILLS",
  "frequency": "MONTHLY", "startOn": "2026-12-05", "shares": [{"memberId": 1}, {"memberId": 2}] }
```

### Orçamentos

Cada grupo pode ter um limite mensal total e um por categoria: `PUT /budgets/TOTAL` ou
`PUT /budgets/FOOD` com `{"monthlyLimit": 800.00}` (cria ou atualiza). Um limite de categoria não
pode passar do total, e o total não pode ficar abaixo de uma categoria.

`GET /budgets/status?month=2026-11` (padrão: mês atual) mostra, do mais usado para o menos usado:

| Campo | O que é |
| --- | --- |
| `spent` / `remaining` / `percentUsed` | gasto no mês, quanto sobra (pode ficar negativo) e % do limite |
| `level` | `OK`, `WARNING` (a partir de 80%) ou `EXCEEDED` |
| `projectedSpend` / `projectedLevel` | **previsão** de fim de mês: média diária × dias do mês |
| `dailyAllowance` | quanto ainda dá para gastar por dia até o fim do mês (só no mês atual) |

Assim o grupo descobre no dia 10 que, nesse ritmo, vai estourar o orçamento de comida, e não no dia 30.

### Feed de atividades

Os services publicam um `GroupActivityEvent` (`ApplicationEventPublisher`) quando algo acontece
no grupo: grupo criado, membro novo, despesa criada/editada/removida e acerto registrado. O
`ActivityService` escuta esses eventos e grava uma linha imutável com um resumo pronto:

```json
{
  "items": [
    { "id": 12, "type": "EXPENSE_UPDATED", "summary": "Despesa 'Mercado' editada (R$ 80.00 → R$ 95.50)",
      "amount": 95.50, "referenceId": 7, "occurredAt": "2026-11-21T18:40:00Z" },
    { "id": 11, "type": "SETTLEMENT_RECORDED", "summary": "Bruno pagou R$ 100.00 para Ana",
      "amount": 100.00, "referenceId": 3, "occurredAt": "2026-11-21T18:35:00Z" }
  ],
  "nextCursor": 11
}
```

- O listener roda **dentro da transação** de quem publicou: se a operação falhar e der rollback,
  a atividade some junto. Os services não conhecem o feed (baixo acoplamento).
- A paginação é por **cursor** (`?before=<nextCursor>&limit=20`), não por página: itens novos
  chegando no topo não fazem o cliente ver repetido ou pular nada. Filtro opcional `?type=`.
- A atividade guarda só ids e o texto, então continua no histórico mesmo depois que a despesa é apagada.

### Despesas em outra moeda

Cada grupo tem uma moeda (`"currency": "EUR"` na criação, padrão `BRL`) e todos os saldos,
relatórios e orçamentos ficam nela. Uma despesa paga em outra moeda informa a moeda e a cotação
(quanto vale 1 unidade dela na moeda do grupo):

```json
{ "description": "Jantar em Nova York", "amount": 100.00, "currency": "USD", "exchangeRate": 5.4321,
  "payerId": 1, "splitType": "EQUAL", "shares": [{"memberId": 1}, {"memberId": 2}, {"memberId": 3}] }
```

- O valor convertido usa **arredondamento bancário** (`HALF_EVEN`), e a resposta guarda o que foi
  pago de verdade em `foreign: { currency, amount, exchangeRate }`.
- A divisão é validada **na moeda do recibo**: num `EXACT`, os valores somam 100 USD, não 543,21.
- Depois disso, `EQUAL`, `PERCENTAGE` e `SHARES` são divididos de novo sobre o total convertido
  (cada um fica com R$ 181,07). No `EXACT`, as partes convertidas mantêm a proporção pelo método
  dos maiores restos, então a soma bate com o total no centavo.
- Sem cotação para moeda estrangeira, ou com cotação numa despesa na moeda do grupo → `422`.
  Códigos são validados pelo ISO 4217 (`usd` vira `USD`, `XYZ` é recusado).
- O feed mostra os dois valores: `Ana pagou US$ 100.00 (R$ 543.21) em 'Jantar'`.
- Editar a despesa para a moeda do grupo limpa os dados da moeda estrangeira.

**Acertos em outra moeda**: quem paga a dívida em dólar manda `currency` e `exchangeRate` no acerto,
com as mesmas regras (ISO 4217, cotação obrigatória só para moeda estrangeira):

```json
{ "fromMemberId": 2, "toMemberId": 1, "amount": 9.31, "currency": "USD", "exchangeRate": 5.37 }
```

- O limite (o que um deve e o outro tem a receber) é conferido **depois** da conversão. Passou do
  limite, o `422` mostra os dois valores: `O valor máximo desse acerto é R$ 10.00 (US$ 1.85 nessa cotação)`.
- Um valor em dólar quase nunca cai no centavo exato da dívida (9,31 × 5,37 = 49,99 e
  9,32 × 5,37 = 50,05 para uma dívida de R$ 50,00). Se o convertido fica a **menos de um centavo da
  moeda estrangeira** do limite, o acerto quita a dívida exatamente, em vez de deixar R$ 0,01
  pendurado ou recusar o pagamento.
- A resposta e a listagem trazem `foreign: { currency, amount, exchangeRate }`, e o feed mostra
  `Bruno pagou US$ 9.31 (R$ 50.00) para Ana`.

**Recorrentes em outra moeda**: a assinatura cobrada em dólar vira um modelo com `currency` e
`exchangeRate`, validado com as mesmas regras de uma despesa (divisão na moeda do recibo, cotação
obrigatória só para moeda estrangeira):

```json
{ "description": "Streaming", "amount": 15.99, "currency": "USD", "exchangeRate": 5.25,
  "payerId": 1, "splitType": "EQUAL", "frequency": "MONTHLY", "startOn": "2026-12-01",
  "shares": [{"memberId": 1}, {"memberId": 2}, {"memberId": 3}] }
```

- Cada ocorrência gerada é uma despesa estrangeira normal (`foreign: { currency, amount, exchangeRate }`),
  convertida com a cotação **atual** do modelo.
- A cotação muda com `PUT /recurring-expenses/{rid}/exchange-rate` (`{ "exchangeRate": 5.40 }`): vale
  só para as próximas ocorrências; as despesas já geradas mantêm a cotação com que foram criadas.
  Num modelo na moeda do grupo, ou com uma cotação que converte abaixo de um centavo → `422`.
- A listagem mostra `amount` + `currency` (o que é cobrado) e `convertedAmount` (quanto a próxima
  ocorrência vai custar ao grupo).

### Idade das dívidas

`GET /groups/{id}/debts/aging` responde "quem está devendo **há quanto tempo**", como o aging de
contas a receber de uma empresa:

- Cada movimento que aumenta a dívida de alguém abre um **lote** datado naquele dia; cada pagamento
  (acerto enviado ou despesa que a pessoa pagou) quita os lotes **mais antigos primeiro** (FIFO).
- Crédito que a pessoa já tinha absorve dívida nova antes de abrir lote; dívidas do mesmo dia viram
  um lote só.
- O que sobra aberto é a dívida atual, distribuída em faixas: `UP_TO_30_DAYS`, `FROM_31_TO_60_DAYS`,
  `FROM_61_TO_90_DAYS` e `OVER_90_DAYS` — por membro e no total do grupo. A soma dos lotes de cada um
  bate com o saldo negativo de `/balances`.
- Só aparece quem deve, ordenado pela dívida mais antiga. `?asOf=2026-10-01` reconstrói a posição
  numa data passada (movimentos depois dela são ignorados); data no futuro → `422`.

```json
{ "asOf": "2026-11-27", "totalOwed": 65.00,
  "buckets": { "UP_TO_30_DAYS": 15.00, "FROM_31_TO_60_DAYS": 10.00, "FROM_61_TO_90_DAYS": 0.00, "OVER_90_DAYS": 40.00 },
  "members": [
    { "member": { "id": 2, "name": "Bruno" }, "owed": 50.00, "oldestSince": "2026-08-10", "oldestDays": 109,
      "openDebts": [ { "since": "2026-08-10", "days": 109, "amount": 40.00 },
                     { "since": "2026-10-05", "days": 53, "amount": 10.00 } ] } ] }
```

### Extrato do membro

`GET /api/groups/{id}/members/{memberId}/statement?from=2026-11-01&to=2026-11-30` mostra a vida
de uma pessoa no grupo como um extrato de banco: cada despesa em que ela pagou ou participou e
cada acerto enviado/recebido, do mais antigo para o mais novo, com o **saldo acumulado** linha a
linha.

- `effect` usa a mesma regra do saldo do grupo (`pago - parte + enviado - recebido`), então sem
  período o `closingBalance` bate exatamente com `/balances` (há teste garantindo isso).
- Tudo o que aconteceu antes de `from` vira o `openingBalance`; o que vem depois de `to` fica de fora.
- No mesmo dia, a despesa vem antes do acerto (a dívida nasce antes de ser paga).
- Uma despesa que a pessoa pagou e da qual também participou vira uma linha só.

```json
{
  "member": { "id": 2, "name": "Bruno" },
  "from": "2026-11-15", "to": "2026-11-30",
  "openingBalance": -30.00,
  "totalPaid": 20.00, "totalShare": 20.00, "totalSent": 30.00, "totalReceived": 0.00,
  "closingBalance": 0.00,
  "lines": [
    { "date": "2026-11-15", "type": "EXPENSE", "referenceId": 2, "description": "Táxi",
      "paid": 20.00, "share": 20.00, "effect": 0.00, "balance": -30.00 },
    { "date": "2026-11-20", "type": "SETTLEMENT_SENT", "referenceId": 3, "description": "Pagamento para Ana",
      "paid": 0.00, "share": 0.00, "effect": 30.00, "balance": 0.00 }
  ]
}
```

### Planilha do grupo (CSV)

`GET /api/groups/{id}/export` baixa o grupo inteiro numa planilha que o Excel em pt-BR abre direto
(`;` entre colunas, vírgula decimal e BOM UTF-8 para os acentos):

```csv
data;tipo;descrição;categoria;pago por;valor (BRL);moeda original;valor original;cotação;Ana;Bruno;Carla
2026-11-20;Despesa;Hotel;Hospedagem;Ana;540,00;USD;100,00;5,4;360,00;-180,00;-180,00
2026-11-26;Acerto;Bruno pagou Ana;;Bruno;180,00;USD;33,34;5,4;-180,00;180,00;
;Saldo;;;;;;;;180,00;0,00;-180,00
```

- uma linha por despesa ou acerto, da mais antiga para a mais nova (no mesmo dia, despesas antes
  dos acertos, igual ao extrato do membro);
- **uma coluna por membro** com o quanto aquela linha mexeu no saldo dele (`pagou - parte` numa
  despesa, `+enviado` / `-recebido` num acerto); célula vazia é quem não participou;
- a última linha soma as colunas e dá **os mesmos saldos** de `/balances` (há teste garantindo isso),
  então dá para conferir as contas no próprio Excel com um `SOMA`;
- despesas e acertos em outra moeda mostram moeda, valor original e cotação;
- descrições e nomes passam pela proteção contra **CSV injection**: texto que começa com `=`, `+`,
  `-` ou `@` ganha um `'` na frente para a planilha não executar como fórmula.

### Comanda por itens

No restaurante ninguém divide igual: um pediu suco, outro tomou três cervejas, a pizza foi de
todo mundo. `POST /api/groups/{id}/expenses/receipt` recebe a comanda **item a item**, cada um
com quem consumiu, mais a taxa de serviço e um desconto opcionais:

```json
{
  "description": "Pizzaria", "payerId": 2, "serviceChargePercent": 10, "discount": 10.00,
  "items": [
    { "description": "Pizza",   "unitPrice": 60.00, "memberIds": [1, 2, 3] },
    { "description": "Cerveja", "unitPrice": 12.00, "quantity": 2, "memberIds": [1, 2] },
    { "description": "Suco",    "unitPrice": 8.00,  "memberIds": [3] }
  ]
}
```

1. cada item é dividido **igualmente** entre quem consumiu (Pizza: 20 / 20 / 20);
2. a taxa de serviço é calculada sobre o consumo e arredondada como na conta impressa
   (10% de 92,00 = 9,20) e depois **rateada pelo que cada um consumiu**, não por cabeça;
3. o desconto é rateado do mesmo jeito, então ninguém recebe mais desconto do que consumiu.

Todos os passos usam o `MoneyAllocator`, então a soma das partes bate com o total no centavo
(há teste parametrizado garantindo). O resultado vira uma despesa `EXACT` comum, que entra no
saldo, no extrato e no feed como qualquer outra; sem `category`, ela vai como `FOOD`.

`POST .../expenses/receipt/preview` faz a mesma conta **sem salvar**, para a tela mostrar quanto
cada um vai pagar enquanto a comanda é montada:

```json
{
  "subtotal": 92.00, "serviceCharge": 9.20, "discount": 10.00, "total": 91.20,
  "members": [
    { "memberId": 1, "name": "Ana",   "subtotal": 32.00, "serviceCharge": 3.20, "discount": 3.48, "total": 31.72 },
    { "memberId": 2, "name": "Bruno", "subtotal": 32.00, "serviceCharge": 3.20, "discount": 3.48, "total": 31.72 },
    { "memberId": 3, "name": "Carla", "subtotal": 28.00, "serviceCharge": 2.80, "discount": 3.04, "total": 27.76 }
  ]
}
```

Regras: taxa de serviço de 0 a 30%, desconto até o valor consumido, a comanda não pode ficar
zerada, o mesmo membro não aparece duas vezes num item e só membros ativos participam. A comanda
é sempre na moeda do grupo.

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/groups` | Cria grupo com membros (moeda opcional, padrão `BRL`) |
| `GET` | `/api/groups` | Lista grupos |
| `GET` | `/api/groups/{id}` | Busca grupo |
| `POST` | `/api/groups/{id}/members` | Adiciona membro (ou reativa quem saiu) |
| `DELETE` | `/api/groups/{id}/members/{memberId}` | Membro sai do grupo (precisa de saldo zero) |
| `POST` | `/api/groups/{id}/expenses` | Registra despesa (aceita `currency` + `exchangeRate`) |
| `GET` | `/api/groups/{id}/expenses` | Lista despesas (filtro opcional `?category=FOOD`) |
| `PUT` | `/api/groups/{id}/expenses/{expenseId}` | Edita despesa e recalcula a divisão |
| `DELETE` | `/api/groups/{id}/expenses/{expenseId}` | Remove despesa |
| `POST` | `/api/groups/{id}/expenses/receipt` | Registra uma comanda dividida por itens |
| `POST` | `/api/groups/{id}/expenses/receipt/preview` | Simula a comanda sem salvar |
| `GET` | `/api/groups/{id}/balances` | Saldo de cada membro |
| `GET` | `/api/groups/{id}/debts` | Transferências sugeridas (simplificadas) |
| `GET` | `/api/groups/{id}/debts/aging?asOf=` | Idade das dívidas por faixa (FIFO) |
| `GET` | `/api/groups/{id}/members/{memberId}/statement?from=&to=` | Extrato do membro com saldo acumulado |
| `GET` | `/api/groups/{id}/reports/spending` | Relatório de gastos por período |
| `GET` | `/api/groups/{id}/export` | Planilha CSV com todas as despesas, acertos e saldos |
| `POST` | `/api/groups/{id}/settlements` | Registra um acerto (aceita `currency` + `exchangeRate`) |
| `GET` | `/api/groups/{id}/settlements` | Lista acertos |
| `POST` | `/api/groups/{id}/settlements/settle-up` | Registra todas as transferências do plano de uma vez |
| `POST` | `/api/groups/{id}/recurring-expenses` | Cria despesa recorrente |
| `GET` | `/api/groups/{id}/recurring-expenses` | Lista despesas recorrentes |
| `POST` | `/api/groups/{id}/recurring-expenses/{rid}/pause` | Pausa |
| `POST` | `/api/groups/{id}/recurring-expenses/{rid}/resume` | Retoma (sem cobrar o período pausado) |
| `PUT` | `/api/groups/{id}/recurring-expenses/{rid}/exchange-rate` | Atualiza a cotação das próximas ocorrências |
| `DELETE` | `/api/groups/{id}/recurring-expenses/{rid}` | Remove o modelo (as despesas ficam) |
| `POST` | `/api/groups/{id}/recurring-expenses/run` | Gera agora as ocorrências vencidas |
| `GET` | `/api/groups/{id}/budgets` | Lista orçamentos |
| `PUT` | `/api/groups/{id}/budgets/{escopo}` | Cria ou altera um orçamento (`TOTAL` ou categoria) |
| `DELETE` | `/api/groups/{id}/budgets/{escopo}` | Remove um orçamento |
| `GET` | `/api/groups/{id}/budgets/status?month=` | Uso dos orçamentos com previsão |
| `GET` | `/api/groups/{id}/activity?before=&type=&limit=` | Feed de atividades (cursor) |

Erros seguem a **RFC 7807** (`application/problem+json`):
`400` validação (com `errors` por campo) · `404` grupo/membro/despesa/orçamento inexistente · `422` regra de negócio.

### Exemplo

```bash
curl -X POST http://localhost:8081/api/groups -H "Content-Type: application/json" \
  -d '{"name":"Viagem","members":["Ana","Bruno","Carla"]}'

curl -X POST http://localhost:8081/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Hotel","amount":300.00,"payerId":1,"splitType":"EQUAL","category":"LODGING",
       "shares":[{"memberId":1},{"memberId":2},{"memberId":3}]}'

curl -X POST http://localhost:8081/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Jantar","amount":90.00,"payerId":2,"splitType":"SHARES","category":"FOOD",
       "shares":[{"memberId":2,"value":2},{"memberId":3,"value":1}]}'

curl http://localhost:8081/api/groups/1/debts
```

```json
[
  { "from": { "id": 3, "name": "Carla" }, "to": { "id": 1, "name": "Ana" }, "amount": 130.00 },
  { "from": { "id": 2, "name": "Bruno" }, "to": { "id": 1, "name": "Ana" }, "amount": 70.00 }
]
```

## Front-end

Há uma interface web em Angular que usa esta API e a [customer-api](https://github.com/jxxErick/customer-api):
**[customer-expense-web](https://github.com/jxxErick/customer-expense-web)** (despesas, saldos,
"quitar tudo", membros, atividade e relatório).

A API sobe na porta **8081** (a 8080 fica com a customer-api) e libera `http://localhost:4200` no
CORS; outra origem pode ser configurada em `CORS_ALLOWED_ORIGINS` (lista separada por vírgula).

## Como rodar

```bash
mvn spring-boot:run
```

```bash
mvn test
```

Os testes cobrem as estratégias de divisão, o alocador de centavos (incluindo casos de borda),
o simplificador de dívidas (com teste de propriedade em saldos aleatórios), as recorrências (datas sem deriva, pausa, limite de recuperação), a previsão dos orçamentos, o feed com cursor, os services com
Mockito, os controllers com `@WebMvcTest` e um fluxo completo contra o H2.
