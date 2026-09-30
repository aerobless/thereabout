package com.sixtymeters.thereabout.launcher;

import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.api.LauncherApi;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class LauncherController implements LauncherApi {
    private final LauncherStore store;
    private final com.sixtymeters.thereabout.access.UserContext users;
    @Override public ResponseEntity<GenLauncherCollection> getLauncher() { return response(store.collection(users.current())); }
    @Override public ResponseEntity<GenLauncherCollection> createLauncherGroup(GenLauncherGroupInput input) { return response(store.createGroup(users.current(), input)); }
    @Override public ResponseEntity<GenLauncherCollection> updateLauncherGroup(Long id, GenLauncherGroupInput input) { return response(store.updateGroup(users.current(), id,input)); }
    @Override public ResponseEntity<GenLauncherCollection> deleteLauncherGroup(Long id) { return response(store.deleteGroup(users.current(), id)); }
    @Override public ResponseEntity<GenLauncherCollection> createLauncherShortcut(GenLauncherShortcutInput input) { return response(store.createShortcut(users.current(), input)); }
    @Override public ResponseEntity<GenLauncherCollection> updateLauncherShortcut(Long id, GenLauncherShortcutInput input) { return response(store.updateShortcut(users.current(), id,input)); }
    @Override public ResponseEntity<GenLauncherCollection> deleteLauncherShortcut(Long id) { return response(store.deleteShortcut(users.current(), id)); }
    @Override public ResponseEntity<GenLauncherCollection> reorderLauncherGroups(GenLauncherOrder order) { return response(store.orderGroups(users.current(), order.getIds())); }
    @Override public ResponseEntity<GenLauncherCollection> reorderLauncherShortcuts(Long id, GenLauncherOrder order) { return response(store.orderShortcuts(users.current(), id,order.getIds())); }
    @Override public ResponseEntity<GenLauncherCollection> importLauncher(GenLauncherImport input) { return response(store.importCollection(users.current(), input)); }
    @Override public ResponseEntity<GenLauncherCollection> refreshLauncherIcon(Long id) { return response(store.refreshIcon(users.current(), id)); }
    @Override public ResponseEntity<GenLauncherCollection> uploadLauncherIcon(Long id, MultipartFile file) {
        if(file.getSize()>1_048_576) throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Icons must be at most 1 MB.");
        try { return response(store.saveCustomIcon(users.current(), id,file.getBytes())); }
        catch(IOException e) { throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Unable to read image."); }
    }
    @Override public ResponseEntity<Resource> getLauncherIcon(Long id) {
        var icon=store.icon(users.current(), id).orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND,"No stored icon."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(icon.type()))
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore()).eTag("\""+id+"-"+icon.version()+"\"")
                .body(new ByteArrayResource(icon.bytes()));
    }
    private static ResponseEntity<GenLauncherCollection> response(GenLauncherCollection collection) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(collection);
    }
}
