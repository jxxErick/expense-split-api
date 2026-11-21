package com.jxxerick.expensesplit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole flow against the real H2 database: group, expenses, debts and settlement. */
@SpringBootTest
@AutoConfigureMockMvc
class ExpenseSplitFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void splitsExpensesAndSettlesDebts() throws Exception {
        JsonNode group = postJson("/api/groups", """
                {"name": "Viagem", "members": ["Ana", "Bruno", "Carla"]}
                """);
        long groupId = group.get("id").asLong();
        long ana = group.at("/members/0/id").asLong();
        long bruno = group.at("/members/1/id").asLong();
        long carla = group.at("/members/2/id").asLong();
        String base = "/api/groups/" + groupId;

        postJson(base + "/expenses", """
                {"description": "Hotel", "amount": 300.00, "payerId": %d, "splitType": "EQUAL",
                 "shares": [{"memberId": %d}, {"memberId": %d}, {"memberId": %d}]}
                """.formatted(ana, ana, bruno, carla));
        postJson(base + "/expenses", """
                {"description": "Jantar", "amount": 90.00, "payerId": %d, "splitType": "SHARES",
                 "shares": [{"memberId": %d, "value": 2}, {"memberId": %d, "value": 1}]}
                """.formatted(bruno, bruno, carla));

        // Ana: 300 - 100 = 200 | Bruno: 90 - (100 + 60) = -70 | Carla: 0 - (100 + 30) = -130
        mvc.perform(get(base + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(200.00))
                .andExpect(jsonPath("$[1].balance").value(-70.00))
                .andExpect(jsonPath("$[2].balance").value(-130.00));

        mvc.perform(get(base + "/debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].from.name").value("Carla"))
                .andExpect(jsonPath("$[0].amount").value(130.00));

        postJson(base + "/settlements", """
                {"fromMemberId": %d, "toMemberId": %d, "amount": 130.00}
                """.formatted(carla, ana));

        mvc.perform(get(base + "/debts"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].from.name").value("Bruno"))
                .andExpect(jsonPath("$[0].to.name").value("Ana"))
                .andExpect(jsonPath("$[0].amount").value(70.00));

        mvc.perform(post(base + "/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId": %d, "toMemberId": %d, "amount": 80.00}
                                """.formatted(bruno, ana)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O valor máximo desse acerto é R$ 70.00"));
    }

    @Test
    void updatingAnExpenseReplacesItsShares() throws Exception {
        JsonNode group = postJson("/api/groups", """
                {"name": "República", "members": ["Ana", "Bruno"]}
                """);
        long ana = group.at("/members/0/id").asLong();
        long bruno = group.at("/members/1/id").asLong();
        String base = "/api/groups/" + group.get("id").asLong();

        JsonNode expense = postJson(base + "/expenses", """
                {"description": "Luz", "amount": 100.00, "payerId": %d, "splitType": "EQUAL",
                 "spentAt": "2026-11-10", "shares": [{"memberId": %d}, {"memberId": %d}]}
                """.formatted(ana, ana, bruno));

        mvc.perform(put(base + "/expenses/" + expense.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Luz e água", "amount": 150.00, "payerId": %d, "splitType": "EXACT",
                                 "shares": [{"memberId": %d, "value": 150.00}]}
                                """.formatted(ana, bruno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spentAt").value("2026-11-10"))
                .andExpect(jsonPath("$.shares.length()").value(1));

        mvc.perform(get(base + "/balances"))
                .andExpect(jsonPath("$[0].balance").value(150.00))
                .andExpect(jsonPath("$[1].balance").value(-150.00));
    }

    @Test
    void recurringExpenseGeneratesOnlyOncePerOccurrence() throws Exception {
        JsonNode group = postJson("/api/groups", """
                {"name": "Apê", "members": ["Ana", "Bruno"]}
                """);
        long ana = group.at("/members/0/id").asLong();
        long bruno = group.at("/members/1/id").asLong();
        String base = "/api/groups/" + group.get("id").asLong();

        postJson(base + "/recurring-expenses", """
                {"description": "Internet", "amount": 120.00, "payerId": %d, "splitType": "SHARES",
                 "category": "BILLS", "frequency": "MONTHLY", "startOn": "%s",
                 "shares": [{"memberId": %d, "value": 2}, {"memberId": %d, "value": 1}]}
                """.formatted(ana, LocalDate.now(), ana, bruno));

        mvc.perform(post(base + "/recurring-expenses/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shares[0].amount").value(80.00));
        mvc.perform(post(base + "/recurring-expenses/run"))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get(base + "/recurring-expenses"))
                .andExpect(jsonPath("$[0].generatedCount").value(1))
                .andExpect(jsonPath("$[0].nextRunOn").value(LocalDate.now().plusMonths(1).toString()))
                .andExpect(jsonPath("$[0].participants[1].memberName").value("Bruno"));
        mvc.perform(get(base + "/balances"))
                .andExpect(jsonPath("$[1].balance").value(-40.00));
    }

    @Test
    void budgetsTrackTheCurrentMonth() throws Exception {
        JsonNode group = postJson("/api/groups", """
                {"name": "Férias", "members": ["Ana", "Bruno"]}
                """);
        long ana = group.at("/members/0/id").asLong();
        String base = "/api/groups/" + group.get("id").asLong();

        putJson(base + "/budgets/TOTAL", "{\"monthlyLimit\": 1000.00}").andExpect(status().isOk());
        putJson(base + "/budgets/food", "{\"monthlyLimit\": 200.00}").andExpect(status().isOk());
        putJson(base + "/budgets/FOOD", "{\"monthlyLimit\": 1500.00}").andExpect(status().isUnprocessableEntity());
        postJson(base + "/expenses", """
                {"description": "Restaurante", "amount": 180.00, "payerId": %d, "splitType": "EQUAL",
                 "category": "FOOD", "shares": [{"memberId": %d}]}
                """.formatted(ana, ana));

        mvc.perform(get(base + "/budgets/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgets.length()").value(2))
                .andExpect(jsonPath("$.budgets[0].scope").value("FOOD"))
                .andExpect(jsonPath("$.budgets[0].spent").value(180.00))
                .andExpect(jsonPath("$.budgets[0].level").value("WARNING"))
                .andExpect(jsonPath("$.budgets[1].scope").value("TOTAL"))
                .andExpect(jsonPath("$.budgets[1].remaining").value(820.00));
    }

    @Test
    void activityFeedTellsTheGroupHistoryNewestFirst() throws Exception {
        JsonNode group = postJson("/api/groups", """
                {"name": "Churrasco", "members": ["Ana", "Bruno"]}
                """);
        long ana = group.at("/members/0/id").asLong();
        long bruno = group.at("/members/1/id").asLong();
        String base = "/api/groups/" + group.get("id").asLong();

        JsonNode expense = postJson(base + "/expenses", """
                {"description": "Carne", "amount": 200.00, "payerId": %d, "splitType": "EQUAL",
                 "category": "FOOD", "shares": [{"memberId": %d}, {"memberId": %d}]}
                """.formatted(ana, ana, bruno));
        postJson(base + "/settlements", """
                {"fromMemberId": %d, "toMemberId": %d, "amount": 100.00}
                """.formatted(bruno, ana));
        mvc.perform(delete(base + "/expenses/" + expense.get("id").asLong())).andExpect(status().isNoContent());

        String firstPage = mvc.perform(get(base + "/activity").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("EXPENSE_DELETED"))
                .andExpect(jsonPath("$.items[1].summary").value("Bruno pagou R$ 100.00 para Ana"))
                .andReturn().getResponse().getContentAsString();
        long cursor = objectMapper.readTree(firstPage).get("nextCursor").asLong();

        mvc.perform(get(base + "/activity").param("before", String.valueOf(cursor)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].summary").value("Ana pagou R$ 200.00 em 'Carne'"))
                .andExpect(jsonPath("$.items[1].type").value("GROUP_CREATED"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(base + "/activity").param("type", "SETTLEMENT_RECORDED"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    private ResultActions putJson(String url, String body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode postJson(String url, String body) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
