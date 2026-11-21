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

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/groups` | Cria grupo com membros |
| `GET` | `/api/groups` | Lista grupos |
| `GET` | `/api/groups/{id}` | Busca grupo |
| `POST` | `/api/groups/{id}/members` | Adiciona membro |
| `POST` | `/api/groups/{id}/expenses` | Registra despesa |
| `GET` | `/api/groups/{id}/expenses` | Lista despesas (filtro opcional `?category=FOOD`) |
| `PUT` | `/api/groups/{id}/expenses/{expenseId}` | Edita despesa e recalcula a divisão |
| `DELETE` | `/api/groups/{id}/expenses/{expenseId}` | Remove despesa |
| `GET` | `/api/groups/{id}/balances` | Saldo de cada membro |
| `GET` | `/api/groups/{id}/debts` | Transferências sugeridas (simplificadas) |
| `GET` | `/api/groups/{id}/reports/spending` | Relatório de gastos por período |
| `POST` | `/api/groups/{id}/settlements` | Registra um acerto |
| `GET` | `/api/groups/{id}/settlements` | Lista acertos |
| `POST` | `/api/groups/{id}/recurring-expenses` | Cria despesa recorrente |
| `GET` | `/api/groups/{id}/recurring-expenses` | Lista despesas recorrentes |
| `POST` | `/api/groups/{id}/recurring-expenses/{rid}/pause` | Pausa |
| `POST` | `/api/groups/{id}/recurring-expenses/{rid}/resume` | Retoma (sem cobrar o período pausado) |
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
curl -X POST http://localhost:8080/api/groups -H "Content-Type: application/json" \
  -d '{"name":"Viagem","members":["Ana","Bruno","Carla"]}'

curl -X POST http://localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Hotel","amount":300.00,"payerId":1,"splitType":"EQUAL","category":"LODGING",
       "shares":[{"memberId":1},{"memberId":2},{"memberId":3}]}'

curl -X POST http://localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Jantar","amount":90.00,"payerId":2,"splitType":"SHARES","category":"FOOD",
       "shares":[{"memberId":2,"value":2},{"memberId":3,"value":1}]}'

curl http://localhost:8080/api/groups/1/debts
```

```json
[
  { "from": { "id": 3, "name": "Carla" }, "to": { "id": 1, "name": "Ana" }, "amount": 130.00 },
  { "from": { "id": 2, "name": "Bruno" }, "to": { "id": 1, "name": "Ana" }, "amount": 70.00 }
]
```

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
