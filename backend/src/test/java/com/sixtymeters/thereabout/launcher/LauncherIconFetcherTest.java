package com.sixtymeters.thereabout.launcher;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LauncherIconFetcherTest {
    @Test void rejectsLocalReservedAndTransitionAddresses() throws Exception {
        for(String host:List.of("127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","192.0.2.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1","2001:db8::1","2002:7f00:1::","::ffff:127.0.0.1"))
            assertThat(LauncherIconFetcher.publicAddress(InetAddress.getByName(host))).as(host).isFalse();
        assertThat(LauncherIconFetcher.publicAddress(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(LauncherIconFetcher.publicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
    @Test void rejectsUnsafeSchemesCredentialsPortsAndLiteralHostsBeforeConnecting() {
        for(String url:List.of("file:///etc/passwd","https://user:pass@example.org/icon","https://example.org:8443/icon","http://127.0.0.1/icon","http://[::1]/icon"))
            assertThatThrownBy(()->LauncherIconFetcher.validateDestination(URI.create(url))).isInstanceOf(java.io.IOException.class);
    }
    @Test void extractsOnlyIconHintsAndResolvesRelativeLinks() {
        String html="<link rel='stylesheet' href='/style.css'><link href='/app.png?a=1&amp;b=2' rel='shortcut icon'><link rel=apple-touch-icon href=touch.png>";
        assertThat(LauncherIconFetcher.iconLinks(html.getBytes(StandardCharsets.UTF_8),URI.create("https://example.org/")))
                .containsExactly(URI.create("https://example.org/app.png?a=1&b=2"),URI.create("https://example.org/touch.png"));
        assertThat(LauncherIconFetcher.imageType("<html>bad icon</html>".getBytes())).isNull();
        assertThat(LauncherIconFetcher.imageType("<svg onload='test()'/>".getBytes())).isNull();
    }
}
