package com.jxxerick.expensesplit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private JsonNode postJson(String url, String body) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
