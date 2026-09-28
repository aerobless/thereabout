package com.sixtymeters.thereabout.access;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without Cloudflare only this machine may use the application, and never through a rebound foreign name. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalAccessTest {
    @Autowired MockMvc mvc;

    static RequestPostProcessor from(String address, String host) {
        return request -> {
            request.setRemoteAddr(address);
            request.setServerName(host);
            return request;
        };
    }

    @Test
    void onlyLoopbackRequestsForALoopbackHostAreTheLocalUser() throws Exception {
        mvc.perform(get("/backend/api/v1/identity").with(from("127.0.0.1", "localhost"))).andExpect(status().isOk());
        mvc.perform(get("/backend/api/v1/identity").with(from("172.19.0.6", "localhost"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/backend/api/v1/identity").with(from("127.0.0.1", "rebound.example"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/finances/accounts").with(from("172.19.0.6", "localhost"))).andExpect(status().isUnauthorized());
    }
}
