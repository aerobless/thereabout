package com.sixtymeters.thereabout.calendar;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.calendar.Calendar;
import com.sixtymeters.thereabout.calendar.google.GoogleCalendarGateway;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarGatewayTest {
    @Test void incrementalRequestsKeepRecurrenceAndPagingParametersConsistent() throws Exception {
        List<String> requests=new ArrayList<>();
        GoogleCalendarGateway gateway=gateway(requests,"{\"items\":[],\"nextSyncToken\":\"next\"}");
        gateway.events("calendar","token","page");
        assertThat(requests).singleElement().asString().contains("singleEvents=false","showDeleted=true","syncToken=token","pageToken=page").doesNotContain("timeMin","timeMax");
    }
    @Test void primaryAccountUsesOnlyCalendarListScope() throws Exception {
        List<String> requests=new ArrayList<>();
        GoogleCalendarGateway gateway=gateway(requests,"{\"items\":[{\"id\":\"self@example.com\",\"primary\":true}]}");
        assertThat(gateway.account()).isEqualTo("self@example.com");
        assertThat(requests).singleElement().asString().contains("/users/me/calendarList");
    }
    @Test void invalidGrantPausesButTransientTokenEndpointFailureDoesNot() {
        var invalid=new HttpResponseException.Builder(400,"Bad Request",new HttpHeaders()).setContent("{\"error\":\"invalid_grant\"}").build();
        assertThat(GoogleCalendarGateway.credentialsFailed(new IOException("Refresh failed",invalid))).isTrue();
        var unavailable=new HttpResponseException.Builder(503,"Unavailable",new HttpHeaders()).setContent("unavailable").build();
        assertThat(GoogleCalendarGateway.credentialsFailed(new IOException("Refresh failed",unavailable))).isFalse();
    }
    private GoogleCalendarGateway gateway(List<String> requests,String json) {
        MockHttpTransport transport=new MockHttpTransport() {
            @Override public MockLowLevelHttpRequest buildRequest(String method,String url) {
                requests.add(method+" "+url);
                return new MockLowLevelHttpRequest(url).setResponse(new MockLowLevelHttpResponse().setStatusCode(200).setContentType("application/json").setContent(json));
            }
        };
        GoogleCalendarGateway gateway=new GoogleCalendarGateway();
        ReflectionTestUtils.setField(gateway,"client",new Calendar.Builder(transport,GsonFactory.getDefaultInstance(),null).setApplicationName("Thereabout-test").build());
        return gateway;
    }
}
