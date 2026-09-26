package com.sixtymeters.thereabout.shared.icons;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteIconFetcherTest {
    @Test void rejectsLocalReservedAndTransitionAddresses() throws Exception {
        for(String host:List.of("127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","192.0.2.1","198.18.0.1","224.0.0.1","::1","fc00::1","fe80::1","2001:db8::1","2002:7f00:1::","::ffff:127.0.0.1"))
            assertThat(WebsiteIconFetcher.publicAddress(InetAddress.getByName(host))).as(host).isFalse();
        assertThat(WebsiteIconFetcher.publicAddress(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(WebsiteIconFetcher.publicAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }
    @Test void rejectsUnsafeSchemesCredentialsPortsAndLiteralHostsBeforeConnecting() {
        for(String url:List.of("file:///etc/passwd","https://user:pass@example.org/icon","https://example.org:8443/icon","http://127.0.0.1/icon","http://[::1]/icon"))
            assertThatThrownBy(()->WebsiteIconFetcher.validateDestination(URI.create(url))).isInstanceOf(java.io.IOException.class);
    }
    @Test void extractsOnlyIconHintsAndResolvesRelativeLinks() {
        String html="<link rel='stylesheet' href='/style.css'><link href='/app.png?a=1&amp;b=2' rel='shortcut icon'><link rel=apple-touch-icon href=touch.png>";
        assertThat(WebsiteIconFetcher.iconLinks(html.getBytes(StandardCharsets.UTF_8),URI.create("https://example.org/")))
                .containsExactly(URI.create("https://example.org/app.png?a=1&b=2"),URI.create("https://example.org/touch.png"));
        assertThat(WebsiteIconFetcher.imageType("<html>bad icon</html>".getBytes())).isNull();
        assertThat(WebsiteIconFetcher.imageType("<svg onload='test()'/>".getBytes())).isNull();
    }
    @Test void largeHomepagesKeepTheirHeadWhileOversizedImagesAreRejected() throws Exception {
        String html = "<link rel='apple-touch-icon' href='/app.png'>" + " ".repeat(1_500_000);
        byte[] prefix = WebsiteIconFetcher.readBytes(new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), true);
        assertThat(prefix).hasSize(1_048_576);
        assertThat(WebsiteIconFetcher.iconLinks(prefix, URI.create("https://bank.example/")))
            .containsExactly(URI.create("https://bank.example/app.png"));
        assertThatThrownBy(() -> WebsiteIconFetcher.readBytes(new java.io.ByteArrayInputStream(new byte[1_048_577]), false))
            .isInstanceOf(java.io.IOException.class);
    }

    @Test void blockedWebsitesUsePublicCacheWithoutSendingPrivatePaths() throws Exception {
        var fetcher = spy(new WebsiteIconFetcher());
        URI origin = URI.create("https://bank.example/");
        URI fallback = WebsiteIconFetcher.fallbackIcon("bank.example");
        byte[] png = {(byte)137,80,78,71,13,10,26,10,0,0,0,0};
        try {
            doReturn(new WebsiteIconFetcher.Download(origin, new byte[0], null, 403)).when(fetcher).download(origin, true);
            doReturn(new WebsiteIconFetcher.Download(origin, new byte[0], null, 403)).when(fetcher).download(origin.resolve("/favicon.ico"), false);
            doReturn(new WebsiteIconFetcher.Download(fallback, png, null, 200)).when(fetcher).download(fallback, false);
            var icon = fetcher.fetch("https://bank.example/private?account=12345");
            assertThat(icon).isNotNull();
            assertThat(icon.bytes()).containsExactly(png);
            assertThat(fallback.toString()).isEqualTo("https://www.google.com/s2/favicons?domain=bank.example&sz=128");
            verify(fetcher).download(fallback, false);
        } finally { fetcher.close(); }
    }

    @Test void blockedNetworkDestinationsNeverReachThePublicCache() throws Exception {
        var fetcher = spy(new WebsiteIconFetcher());
        URI origin = URI.create("https://internal.example/");
        try {
            doThrow(new java.io.IOException("Not public")).when(fetcher).download(origin, true);
            doThrow(new java.io.IOException("Not public")).when(fetcher).download(origin.resolve("/favicon.ico"), false);
            assertThat(fetcher.fetch(origin.toString())).isNull();
            verify(fetcher, never()).download(WebsiteIconFetcher.fallbackIcon("internal.example"), false);
        } finally { fetcher.close(); }
    }

}
