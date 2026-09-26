package com.sixtymeters.thereabout.launcher;

import com.sixtymeters.thereabout.shared.icons.WebsiteIconFetcher;

import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Statement;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LauncherStore {
    private final JdbcTemplate db;
    public record Icon(byte[] bytes, String type, long version) {}
    public record PendingIcon(long id, String url, long version) {}

    @Transactional(readOnly = true)
    public GenLauncherCollection collection() {
        var groups = db.query("SELECT * FROM launcher_group ORDER BY position,id", (r,n) ->
                GenLauncherGroup.builder().id(r.getLong("id")).section(r.getString("section"))
                        .name(r.getString("name")).position(r.getInt("position")).build());
        var shortcuts = db.query("""
                SELECT s.id,s.group_id,s.title,s.url,s.description,s.emoji,s.position,s.icon_version,
                       s.icon_data IS NOT NULL AS has_icon,s.icon_attempted
                FROM launcher_shortcut s JOIN launcher_group g ON s.group_id=g.id
                ORDER BY g.position,g.id,s.position,s.id
                """, (r,n) -> GenLauncherShortcut.builder().id(r.getLong("id")).groupId(r.getLong("group_id"))
                .title(r.getString("title")).url(r.getString("url")).description(r.getString("description"))
                .emoji(r.getString("emoji")).position(r.getInt("position")).iconVersion(r.getLong("icon_version"))
                .hasIcon(r.getBoolean("has_icon")).iconState(!r.getBoolean("icon_attempted") ? GenLauncherShortcut.IconStateEnum.PENDING
                        : r.getBoolean("has_icon") ? GenLauncherShortcut.IconStateEnum.READY : GenLauncherShortcut.IconStateEnum.FALLBACK).build());
        return GenLauncherCollection.builder().groups(groups).shortcuts(shortcuts).build();
    }

    @Transactional
    public GenLauncherCollection createGroup(GenLauncherGroupInput input) {
        lockGroups();
        insertGroup(input.getSection(), input.getName());
        return collection();
    }

    private long insertGroup(String section, String name) {
        return insert("INSERT INTO launcher_group(section,name,position) VALUES (?,?,?)",
                required(section,160), required(name,160), nextGroupPosition());
    }

    @Transactional
    public GenLauncherCollection updateGroup(long id, GenLauncherGroupInput input) {
        lockGroups(); requireGroup(id);
        db.update("UPDATE launcher_group SET section=?,name=? WHERE id=?", required(input.getSection(),160), required(input.getName(),160), id);
        return collection();
    }

    @Transactional
    public GenLauncherCollection deleteGroup(long id) {
        lockGroups(); requireGroup(id);
        if (!shortcutIds(id).isEmpty()) throw new ThereaboutException(HttpStatus.CONFLICT, "Move or remove the shortcuts before deleting this group.");
        db.update("DELETE FROM launcher_group WHERE id=?", id);
        return collection();
    }

    @Transactional
    public GenLauncherCollection createShortcut(GenLauncherShortcutInput input) {
        lockGroups(); requireGroup(input.getGroupId());
        insertShortcut(input.getGroupId(), input.getTitle(), input.getUrl(), input.getDescription(), input.getEmoji());
        return collection();
    }

    private void insertShortcut(long group, String title, String url, String description, String emoji) {
        db.update("INSERT INTO launcher_shortcut(group_id,title,url,description,emoji,position) VALUES (?,?,?,?,?,?)",
                group,required(title,200),validUrl(url),optional(description,500),optional(emoji,32),nextShortcutPosition(group));
    }

    @Transactional
    public GenLauncherCollection updateShortcut(long id, GenLauncherShortcutInput input) {
        lockGroups(); requireGroup(input.getGroupId());
        var previous = shortcut(id);
        String url = validUrl(input.getUrl());
        int position = previous.getGroupId().equals(input.getGroupId()) ? previous.getPosition() : nextShortcutPosition(input.getGroupId());
        db.update("UPDATE launcher_shortcut SET group_id=?,title=?,url=?,description=?,emoji=?,position=? WHERE id=?",
                input.getGroupId(),required(input.getTitle(),200),url,optional(input.getDescription(),500),optional(input.getEmoji(),32),position,id);
        if (!previous.getUrl().equals(url)) db.update("""
                UPDATE launcher_shortcut SET icon_data=NULL,icon_type=NULL,icon_attempted=FALSE,icon_version=icon_version+1
                WHERE id=? AND icon_source='AUTO'
                """, id);
        return collection();
    }

    @Transactional
    public GenLauncherCollection deleteShortcut(long id) {
        lockGroups(); requireShortcut(id);
        db.update("DELETE FROM launcher_shortcut WHERE id=?", id);
        return collection();
    }

    @Transactional
    public GenLauncherCollection orderGroups(List<Long> ids) {
        var existing = lockGroups();
        checkOrder(ids,existing);
        for (int i=0;i<ids.size();i++) db.update("UPDATE launcher_group SET position=? WHERE id=?",i,ids.get(i));
        return collection();
    }

    @Transactional
    public GenLauncherCollection orderShortcuts(long group, List<Long> ids) {
        lockGroups(); requireGroup(group);
        checkOrder(ids,shortcutIds(group));
        for (int i=0;i<ids.size();i++) db.update("UPDATE launcher_shortcut SET position=? WHERE id=?",i,ids.get(i));
        return collection();
    }

    @Transactional
    public GenLauncherCollection importCollection(GenLauncherImport input) {
        if (!lockGroups().isEmpty()) throw new ThereaboutException(HttpStatus.CONFLICT,"Import is only available for an empty launcher.");
        if (input.getGroups()==null || input.getGroups().isEmpty() || input.getGroups().size()>100 ||
                input.getGroups().stream().mapToInt(g -> g.getShortcuts()==null ? 0 : g.getShortcuts().size()).sum()>5000)
            throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Invalid import size.");
        for (var group : input.getGroups()) {
            long id = insertGroup(group.getSection(),group.getName());
            if (group.getShortcuts()==null) throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Each group needs a shortcuts list.");
            for (var shortcut : group.getShortcuts()) insertShortcut(id,shortcut.getTitle(),shortcut.getUrl(),shortcut.getDescription(),shortcut.getEmoji());
        }
        return collection();
    }

    public Optional<Icon> icon(long id) {
        return db.query("SELECT icon_data,icon_type,icon_version FROM launcher_shortcut WHERE id=? AND icon_data IS NOT NULL",
                (r,n) -> new Icon(r.getBytes(1),r.getString(2),r.getLong(3)),id).stream().findFirst();
    }

    @Transactional
    public GenLauncherCollection saveCustomIcon(long id, byte[] bytes) {
        requireShortcut(id);
        String type = WebsiteIconFetcher.imageType(bytes);
        if (bytes.length>1_048_576 || type==null) throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Choose a PNG, JPEG, GIF, WebP or ICO image up to 1 MB.");
        db.update("UPDATE launcher_shortcut SET icon_data=?,icon_type=?,icon_source='CUSTOM',icon_attempted=TRUE,icon_version=icon_version+1 WHERE id=?",bytes,type,id);
        return collection();
    }

    @Transactional
    public GenLauncherCollection refreshIcon(long id) {
        requireShortcut(id);
        db.update("UPDATE launcher_shortcut SET icon_source='AUTO',icon_attempted=FALSE,icon_version=icon_version+1 WHERE id=?",id);
        return collection();
    }

    public List<PendingIcon> pendingIcons() {
        return db.query("SELECT id,url,icon_version FROM launcher_shortcut WHERE icon_attempted=FALSE AND icon_source='AUTO' ORDER BY id LIMIT 4",
                (r,n) -> new PendingIcon(r.getLong(1),r.getString(2),r.getLong(3)));
    }

    public void finishIcon(PendingIcon pending, WebsiteIconFetcher.Image image) {
        // A slow fetch must never overwrite a newer URL, upload, or refresh.
        if (image==null) db.update("UPDATE launcher_shortcut SET icon_attempted=TRUE WHERE id=? AND url=? AND icon_version=? AND icon_source='AUTO'",
                pending.id(),pending.url(),pending.version());
        else db.update("""
                UPDATE launcher_shortcut SET icon_data=?,icon_type=?,icon_attempted=TRUE,icon_version=icon_version+1
                WHERE id=? AND url=? AND icon_version=? AND icon_source='AUTO'
                """, image.bytes(),image.type(),pending.id(),pending.url(),pending.version());
    }

    public static String validUrl(String value) {
        String url=required(value,4096);
        try {
            URI uri=URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                    uri.getHost()==null || uri.getUserInfo()!=null || uri.getPort()>65535 || uri.getPort()==0) throw new IllegalArgumentException();
            return url;
        } catch (IllegalArgumentException e) { throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Enter a complete http:// or https:// URL without credentials."); }
    }

    private GenLauncherShortcut shortcut(long id) {
        return collection().getShortcuts().stream().filter(s -> s.getId()==id).findFirst()
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND,"Shortcut not found."));
    }
    private void requireShortcut(long id) {
        if (db.queryForList("SELECT id FROM launcher_shortcut WHERE id=?",Long.class,id).isEmpty())
            throw new ThereaboutException(HttpStatus.NOT_FOUND,"Shortcut not found.");
    }
    private void requireGroup(Long id) {
        if (id==null || db.queryForList("SELECT id FROM launcher_group WHERE id=?",Long.class,id).isEmpty())
            throw new ThereaboutException(HttpStatus.NOT_FOUND,"Group not found.");
    }
    private List<Long> lockGroups() { return db.queryForList("SELECT id FROM launcher_group ORDER BY position,id FOR UPDATE",Long.class); }
    private List<Long> shortcutIds(long group) { return db.queryForList("SELECT id FROM launcher_shortcut WHERE group_id=? ORDER BY position,id",Long.class,group); }
    private int nextGroupPosition() { return Objects.requireNonNull(db.queryForObject("SELECT COALESCE(MAX(position),-1)+1 FROM launcher_group",Integer.class)); }
    private int nextShortcutPosition(long group) { return Objects.requireNonNull(db.queryForObject("SELECT COALESCE(MAX(position),-1)+1 FROM launcher_shortcut WHERE group_id=?",Integer.class,group)); }
    private long insert(String sql, Object... values) {
        var keys=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private static void checkOrder(List<Long> ids,List<Long> existing) {
        if(ids==null || ids.size()!=existing.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(existing)))
            throw new ThereaboutException(HttpStatus.CONFLICT,"The collection changed. Refresh before reordering.");
    }
    private static String required(String value,int max) {
        String text=optional(value,max);
        if(text.isEmpty()) throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Required fields cannot be empty.");
        return text;
    }
    private static String optional(String value,int max) {
        String text=value==null?"":value.trim();
        if(text.length()>max) throw new ThereaboutException(HttpStatus.BAD_REQUEST,"A field is too long.");
        return text;
    }
}
