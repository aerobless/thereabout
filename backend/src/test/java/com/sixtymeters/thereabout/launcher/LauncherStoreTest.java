package com.sixtymeters.thereabout.launcher;

import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties={"thereabout.launcher.fetch-icons=false","thereabout.calendar.worker-enabled=false"})
@Transactional
class LauncherStoreTest {
    @Autowired LauncherStore store;
    @Autowired MockMvc mvc;
    private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private long group(String name) {
        return store.createGroup(GenLauncherGroupInput.builder().section("Example section").name(name).build()).getGroups().stream()
                .filter(g->g.getName().equals(name)).findFirst().orElseThrow().getId();
    }
    private GenLauncherShortcutInput link(long group,String title,String url) {
        return GenLauncherShortcutInput.builder().groupId(group).title(title).url(url).description("Example description").emoji("🔖").build();
    }
    @Test void managesShortcutsAndGroupsWithoutLosingTargetsOrOrder() {
        long a=group("Group A"),b=group("Group B");
        var one=store.createShortcut(link(a,"One","https://example.com/find?q=one%20two#saved")).getShortcuts().getFirst();
        var two=store.createShortcut(link(a,"Two","http://192.0.2.1:8080/app")).getShortcuts().getLast();
        assertThat(store.orderShortcuts(a,List.of(two.getId(),one.getId())).getShortcuts()).extracting(GenLauncherShortcut::getTitle).containsExactly("Two","One");
        assertThat(store.orderGroups(List.of(b,a)).getGroups()).extracting(GenLauncherGroup::getId).containsExactly(b,a);
        store.updateShortcut(one.getId(),link(b,"Renamed",one.getUrl()));
        var moved=store.collection().getShortcuts().stream().filter(s->s.getId().equals(one.getId())).findFirst().orElseThrow();
        assertThat(moved.getGroupId()).isEqualTo(b);
        assertThat(moved.getUrl()).isEqualTo("https://example.com/find?q=one%20two#saved");
        store.updateGroup(a,GenLauncherGroupInput.builder().section("Another section").name("Changed").build());
        store.deleteShortcut(two.getId());store.deleteGroup(a);
        assertThat(store.collection().getGroups()).hasSize(1);
        assertThat(store.collection().getShortcuts()).hasSize(1);
    }
    @Test void refusesNonEmptyGroupDeletionAndStaleReorder() {
        long group=group("Keep");
        store.createShortcut(link(group,"Saved","https://example.com"));
        assertThatThrownBy(()->store.deleteGroup(group)).isInstanceOf(ThereaboutException.class);
        assertThatThrownBy(()->store.orderShortcuts(group,List.of())).isInstanceOf(ThereaboutException.class);
        assertThat(store.collection().getShortcuts()).hasSize(1);
    }
    @Test void rejectsExecutableAndCredentialUrls() {
        for(String url:List.of("javascript:alert(1)","data:text/html,test","file:///tmp/private","https://name:password@example.com/","example.com"))
            assertThatThrownBy(()->LauncherStore.validUrl(url)).isInstanceOf(ThereaboutException.class);
        assertThat(LauncherStore.validUrl("http://192.0.2.1:8080/app#one")).isEqualTo("http://192.0.2.1:8080/app#one");
    }
    @Test void importsOnlyThroughRuntimeAndRejectsRepeatedImports() {
        // Run against an isolated test database; no personal collection is a fixture.
        assertThat(store.collection().getGroups()).isEmpty();
        var input=GenLauncherImport.builder().groups(List.of(GenLauncherImportGroup.builder().section("Example").name("Imported")
                .shortcuts(List.of(GenLauncherLink.builder().title("Demo").url("https://example.org/#demo").build())).build())).build();
        assertThat(store.importCollection(input).getShortcuts()).hasSize(1);
        assertThatThrownBy(()->store.importCollection(input)).isInstanceOf(ThereaboutException.class);
        assertThat(store.collection().getShortcuts()).hasSize(1);
    }
    @Test void iconUploadPersistsAndSlowFetchCannotOverwriteNewerUpload() throws Exception {
        long group=group("Icons");
        var shortcut=store.createShortcut(link(group,"Demo","https://example.org")).getShortcuts().getFirst();
        var pending=store.pendingIcons().getFirst();
        store.saveCustomIcon(shortcut.getId(),PNG);
        long version=store.icon(shortcut.getId()).orElseThrow().version();
        store.finishIcon(pending,null);
        store.finishIcon(pending,new LauncherIconFetcher.Image(new byte[]{1,2,3},"image/gif"));
        assertThat(store.icon(shortcut.getId()).orElseThrow().bytes()).isEqualTo(PNG);
        assertThat(store.icon(shortcut.getId()).orElseThrow().version()).isEqualTo(version);
        var response=mvc.perform(get("/backend/api/v1/launcher/shortcuts/{id}/icon",shortcut.getId())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Cache-Control")).contains("private");
        assertThat(response.getContentAsByteArray()).isEqualTo(PNG);
        assertThatThrownBy(()->store.saveCustomIcon(shortcut.getId(),"<svg onload='alert(1)'/>".getBytes())).isInstanceOf(ThereaboutException.class);
    }
    @Test void changingUrlQueuesNewIconButKeepsAnUploadedImage() {
        long group=group("URL changes");
        var shortcut=store.createShortcut(link(group,"Demo","https://example.org")).getShortcuts().getFirst();
        store.finishIcon(store.pendingIcons().getFirst(),new LauncherIconFetcher.Image(PNG,"image/png"));
        var changed=store.updateShortcut(shortcut.getId(),link(group,"Demo","https://example.com")).getShortcuts().getFirst();
        assertThat(changed.getHasIcon()).isFalse();
        assertThat(changed.getIconState()).isEqualTo(GenLauncherShortcut.IconStateEnum.PENDING);
        store.saveCustomIcon(shortcut.getId(),PNG);
        store.updateShortcut(shortcut.getId(),link(group,"Demo","https://example.net"));
        assertThat(store.icon(shortcut.getId()).orElseThrow().bytes()).isEqualTo(PNG);
    }
    @Test void apiValidatesInputAndDoesNotCacheCollection() throws Exception {
        var invalid=mvc.perform(post("/backend/api/v1/launcher/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"section\":\"Example\"}"))
                .andReturn().getResponse();
        assertThat(invalid.getStatus()).isEqualTo(400);
        var read=mvc.perform(get("/backend/api/v1/launcher")).andReturn().getResponse();
        assertThat(read.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
