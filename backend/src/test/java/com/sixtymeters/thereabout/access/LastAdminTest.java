package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.UserRole;
import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class LastAdminTest {
    @Autowired JdbcTemplate db;
    @Autowired IdentityUserService users;
    @Test void concurrentDemotionsLeaveExactlyOneAdmin() throws Exception {
        var old=db.queryForList("SELECT id,role FROM identity");
        try {
            db.update("UPDATE identity SET role='USER' WHERE role='ADMIN'");
            for(long id:List.of(100021L,100022L)) db.update("INSERT INTO identity(id,short_name,role) VALUES(?,?,'ADMIN') ON DUPLICATE KEY UPDATE role='ADMIN'",id,"Admin fixture "+id);
            var gate=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var tasks=List.of(100021L,100022L).stream().map(id -> pool.submit(() -> {
                    gate.await();
                    try { users.changeRole(id,UserRole.USER);return 200; }
                    catch(org.springframework.web.server.ResponseStatusException ex) { return ex.getStatusCode().value(); }
                })).toList();
                gate.countDown();
                var results=new ArrayList<Integer>();
                for(var task:tasks) results.add(task.get(20,TimeUnit.SECONDS));
                assertThat(results).containsExactlyInAnyOrder(200,409);
            }
            assertThat(db.<Long>queryForObject("SELECT COUNT(*) FROM identity WHERE role='ADMIN'",Long.class)).isEqualTo(1);
        } finally {
            db.update("UPDATE identity SET role='USER' WHERE id IN (100021,100022)");
            old.forEach(row -> db.update("UPDATE identity SET role=? WHERE id=?",row.get("role"),row.get("id")));
        }
    }
}
