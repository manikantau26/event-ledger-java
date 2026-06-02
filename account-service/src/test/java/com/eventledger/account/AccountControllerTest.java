package com.eventledger.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void appliesTransactionsAndCalculatesBalance() throws Exception {
        mockMvc.perform(post("/accounts/acct-test/transactions")
                        .header("X-Trace-Id", "trace-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-credit-test",
                                  "type": "CREDIT",
                                  "amount": 200.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/accounts/acct-test/transactions")
                        .header("X-Trace-Id", "trace-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-debit-test",
                                  "type": "DEBIT",
                                  "amount": 50.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T13:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/accounts/acct-test/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(150.0)));
    }
}