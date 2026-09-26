package com.sixtymeters.thereabout.shared.icons;

import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Fetches only site icons, never authenticated pages or a bookmark's private path/query. */
@Component
public class WebsiteIconFetcher {
    public record Image(byte[] bytes,String type) {}
    record Download(URI uri,byte[] bytes,String location,int status) {}
    private static final int MAX_BYTES=1_048_576;
    private static final Pattern LINK=Pattern.compile("(?is)<link\\b[^>]{0,4096}>");
    private static final Pattern ATTRIBUTE=Pattern.compile("(?is)\\b(rel|href)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private final CloseableHttpClient client;

    public WebsiteIconFetcher() {
        var connections=PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new DnsResolver() {
                    @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                        InetAddress[] addresses=InetAddress.getAllByName(host);
                        if(addresses.length==0 || Arrays.stream(addresses).anyMatch(a -> !publicAddress(a))) throw new UnknownHostException("Icon host is not public.");
                        return addresses; // The HTTP connection uses these exact checked addresses.
                    }
                    @Override public String resolveCanonicalHostname(String host) { return host; }
                })
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(3)).setSocketTimeout(Timeout.ofSeconds(3)).build())
                .setMaxConnTotal(4).setMaxConnPerRoute(2).build();
        client=HttpClients.custom().setConnectionManager(connections).disableRedirectHandling().disableCookieManagement()
                .disableAutomaticRetries().setUserAgent("Thereabout/1.0 (website icon cache)")
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(3)).setConnectionRequestTimeout(Timeout.ofSeconds(3)).build()).build();
    }

    public Image fetch(String shortcutUrl) {
        try {
            URI shortcut=URI.create(shortcutUrl);
            URI origin=new URI(shortcut.getScheme(),null,shortcut.getHost(),shortcut.getPort(),"/",null,null);
            validateDestination(origin);
            // Check literal IPs too: HttpClient need not call the DNS resolver for them.
            var candidates=new LinkedHashSet<URI>();
            boolean blocked = false;
            try {
                var home=download(origin, true);
                blocked = home.status()==403 || home.status()==429;
                if(home.status()==200) candidates.addAll(iconLinks(home.bytes(),home.uri()));
                candidates.add(home.uri().resolve("/favicon.ico"));
            } catch(IOException ignored) { /* A favicon can be available when a homepage is protected. */ }
            candidates.add(origin.resolve("/favicon.ico"));
            for(URI candidate:candidates.stream().limit(4).toList()) {
                try {
                    var result=download(candidate, false);
                    String type=imageType(result.bytes());
                    if(result.status()==200 && type!=null) return new Image(result.bytes(),type);
                } catch(IOException | IllegalArgumentException ignored) { /* Keep the stored fallback. */ }
            }
            if (blocked) {
                // A blocked public website may still have an icon in Google's public favicon cache.
                // Send only the hostname, never a path, query, account name or other user data.
                var cached = download(fallbackIcon(origin.getHost()), false);
                String type = imageType(cached.bytes());
                if (cached.status()==200 && type!=null) return new Image(cached.bytes(), type);
            }
        } catch(IOException | URISyntaxException | IllegalArgumentException ignored) { /* Never log private bookmark URLs. */ }
        return null;
    }

    static URI fallbackIcon(String host) {
        return URI.create("https://www.google.com/s2/favicons?domain="
                + URLEncoder.encode(host, StandardCharsets.UTF_8) + "&sz=128");
    }

    Download download(URI initial, boolean homepage) throws IOException {
        URI uri=initial;
        for(int redirects=0;redirects<=3;redirects++) {
            validateDestination(uri);
            URI current=uri;
            Download result=client.execute(new HttpGet(uri),response -> {
                int status=response.getCode();
                if(status>=300 && status<400) return new Download(current,new byte[0],response.getFirstHeader("Location")==null?null:response.getFirstHeader("Location").getValue(),status);
                if(status!=200 || response.getEntity()==null) return new Download(current,new byte[0],null,status);
                try(var stream=response.getEntity().getContent()) {
                    byte[] bytes=readBytes(stream, homepage);
                    return new Download(current,bytes,null,status);
                }
            });
            if(result.location()==null) return result;
            uri=uri.resolve(result.location());
        }
        throw new IOException("Too many redirects.");
    }

    static byte[] readBytes(java.io.InputStream stream, boolean homepage) throws IOException {
        // Icon hints live in the document head; a large page need not be downloaded in full.
        byte[] bytes = stream.readNBytes(homepage ? MAX_BYTES : MAX_BYTES + 1);
        if (!homepage && bytes.length > MAX_BYTES) throw new IOException("Icon response is too large.");
        return bytes;
    }

    static List<URI> iconLinks(byte[] html,URI base) {
        List<URI> result=new ArrayList<>();
        var tags=LINK.matcher(new String(html,StandardCharsets.UTF_8));
        while(tags.find() && result.size()<3) {
            var attrs=ATTRIBUTE.matcher(tags.group());
            String rel="",href="";
            while(attrs.find()) {
                String value=attrs.group(2)!=null?attrs.group(2):attrs.group(3)!=null?attrs.group(3):attrs.group(4);
                if(attrs.group(1).equalsIgnoreCase("rel")) rel=value.toLowerCase(Locale.ROOT);
                else href=value.replace("&amp;","&");
            }
            if(!href.isBlank() && Arrays.stream(rel.split("\\s+")).anyMatch(r -> r.equals("icon") || r.equals("apple-touch-icon"))) {
                try { result.add(base.resolve(href)); } catch(IllegalArgumentException ignored) { /* Bad HTML hint. */ }
            }
        }
        return result;
    }

    static void validateDestination(URI uri) throws IOException {
        if(uri.getHost()==null || uri.getUserInfo()!=null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                (uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443)) throw new IOException("Unsupported icon destination.");
        String host=uri.getHost().replace("[","").replace("]","");
        if(host.contains(":") || host.matches("[0-9.]+")) {
            if(!publicAddress(InetAddress.getByName(host))) throw new IOException("Icon host is not public.");
        }
    }

    static boolean publicAddress(InetAddress address) {
        if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b=address.getAddress();
        if(b.length==4) {
            int a=b[0]&255,c=b[1]&255,d=b[2]&255;
            return a!=0 && a!=10 && a!=127 && a<224 && !(a==100 && c>=64 && c<=127) &&
                    !(a==169 && c==254) && !(a==172 && c>=16 && c<=31) && !(a==192 && (c==168 || c==0 || (c==88 && d==99))) &&
                    !(a==198 && (c==18 || c==19 || (c==51 && d==100))) && !(a==203 && c==0 && d==113);
        }
        // Only global unicast IPv6; exclude documentation and transition ranges.
        return (b[0]&0xe0)==0x20 && !((b[0]&255)==0x20 && (b[1]&255)==0x02) &&
                !((b[0]&255)==0x20 && (b[1]&255)==0x01 && ((b[2]&255)==0 && (b[3]&255)==0 || (b[2]&255)==0x0d && (b[3]&255)==0xb8));
    }

    public static String imageType(byte[] b) {
        if(b==null || b.length<12) return null;
        if((b[0]&255)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "image/png";
        if((b[0]&255)==0xff && (b[1]&255)==0xd8 && (b[2]&255)==0xff) return "image/jpeg";
        String start=new String(b,0,6,StandardCharsets.US_ASCII);
        if(start.equals("GIF87a") || start.equals("GIF89a")) return "image/gif";
        if(b[0]==0 && b[1]==0 && b[2]==1 && b[3]==0 && ((b[4]&255)+(b[5]&255)*256)>0) return "image/x-icon";
        if(new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF") && new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        return null; // In particular, never serve HTML or active SVG from this origin.
    }

    @PreDestroy public void close() throws IOException { client.close(); }
}
