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
model/         entidades JPA (Group, Member, Expense, ExpenseShare, Settlement, SplitType)
repository/    interfaces Spring Data
service/       regras de negócio
  split/       estratégias de divisão + alocação de centavos
  balance/     simplificação de dívidas
controller/    endpoints REST
dto/           records de entrada e saída
exception/     exceções e handler global (RFC 7807)
config/        beans de infraestrutura (Clock)
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

### Relatório de gastos

`GET /api/groups/{id}/reports/spending?from=2026-11-01&to=2026-11-30` resume o período (datas
opcionais e inclusivas): total gasto, quantidade e média das despesas, total por mês e, para cada
membro, quanto pagou, quanto consumiu (soma das suas partes) e a porcentagem do total.

```json
{
  "total": 390.00, "expenseCount": 2, "averageExpense": 195.00,
  "months": [ { "month": "2026-11", "total": 390.00, "expenseCount": 2 } ],
  "members": [
    { "memberId": 1, "memberName": "Ana", "paid": 300.00, "consumed": 100.00, "percentage": 25.64 }
  ]
}
```

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `POST` | `/api/groups` | Cria grupo com membros |
| `GET` | `/api/groups` | Lista grupos |
| `GET` | `/api/groups/{id}` | Busca grupo |
| `POST` | `/api/groups/{id}/members` | Adiciona membro |
| `POST` | `/api/groups/{id}/expenses` | Registra despesa |
| `GET` | `/api/groups/{id}/expenses` | Lista despesas |
| `DELETE` | `/api/groups/{id}/expenses/{expenseId}` | Remove despesa |
| `GET` | `/api/groups/{id}/balances` | Saldo de cada membro |
| `GET` | `/api/groups/{id}/debts` | Transferências sugeridas (simplificadas) |
| `GET` | `/api/groups/{id}/reports/spending` | Relatório de gastos por período |
| `POST` | `/api/groups/{id}/settlements` | Registra um acerto |
| `GET` | `/api/groups/{id}/settlements` | Lista acertos |

Erros seguem a **RFC 7807** (`application/problem+json`):
`400` validação (com `errors` por campo) · `404` grupo/membro/despesa inexistente · `422` regra de negócio.

### Exemplo

```bash
curl -X POST http://localhost:8080/api/groups -H "Content-Type: application/json" \
  -d '{"name":"Viagem","members":["Ana","Bruno","Carla"]}'

curl -X POST http://localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Hotel","amount":300.00,"payerId":1,"splitType":"EQUAL",
       "shares":[{"memberId":1},{"memberId":2},{"memberId":3}]}'

curl -X POST http://localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" \
  -d '{"description":"Jantar","amount":90.00,"payerId":2,"splitType":"SHARES",
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
o simplificador de dívidas (com teste de propriedade em saldos aleatórios), os services com
Mockito, os controllers com `@WebMvcTest` e um fluxo completo contra o H2.
