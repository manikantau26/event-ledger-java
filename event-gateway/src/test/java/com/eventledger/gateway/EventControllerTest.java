package com.eventledger.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AccountClient accountClient;

    @Test
    void createsEventAndPreventsDuplicateBalanceUpdate() throws Exception {
        mockMvc.perform(post("/events")
                        .header("X-Trace-Id", "trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-test-001",
                                  "accountId": "acct-test",
                                  "type": "CREDIT",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-1"));

        mockMvc.perform(post("/events")
                        .header("X-Trace-Id", "trace-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-test-001",
                                  "accountId": "acct-test",
                                  "type": "CREDIT",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isOk());

        Mockito.verify(accountClient, times(1)).applyTransaction(any());
    }

    @Test
    void listsEventsInTimestampOrder() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-late",
                                  "accountId": "acct-order",
                                  "type": "CREDIT",
                                  "amount": 50.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T18:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-early",
                                  "accountId": "acct-order",
                                  "type": "DEBIT",
                                  "amount": 25.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events?account=acct-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId", is("evt-early")))
                .andExpect(jsonPath("$[1].eventId", is("evt-late")));
    }

    @Test
    void rejectsInvalidAmount() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-invalid",
                                  "accountId": "acct-test",
                                  "type": "CREDIT",
                                  "amount": 0,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns503WhenAccountServiceUnavailable() throws Exception {
        Mockito.doThrow(new AccountClient.AccountServiceUnavailableException("Account Service unavailable"))
                .when(accountClient)
                .applyTransaction(any());

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-fail",
                                  "accountId": "acct-test",
                                  "type": "CREDIT",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/events?account=acct-test"))
                .andExpect(status().isOk());
    }

    @Test
    void getsBalanceThroughAccountService() throws Exception {
        Mockito.when(accountClient.getBalance("acct-test"))
                .thenReturn(Map.of(
                        "accountId", "acct-test",
                        "balance", 150.00,
                        "currency", "USD"
                ));

        mockMvc.perform(get("/accounts/acct-test/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(150.0)));
    }
}