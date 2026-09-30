package com.sixtymeters.thereabout.calendar.service;

import com.sixtymeters.thereabout.access.UserContext;
import com.sixtymeters.thereabout.calendar.data.CalendarStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sixtymeters.thereabout.config.ThereaboutException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service @RequiredArgsConstructor
public class CalendarUserService {
    private final CalendarStore store;
    private final UserContext users;
    @Transactional
    public void assign(long calendarId, List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())
            throw new ThereaboutException(HttpStatus.BAD_REQUEST,"Choose valid unique user IDs");
        store.update("UPDATE calendar_calendar SET id=id WHERE id=?",calendarId);
        if (store.calendars().stream().noneMatch(c -> c.id()==calendarId)) throw new ThereaboutException(HttpStatus.NOT_FOUND,"Calendar not found");
        ids.forEach(users::require);
        store.replaceUserIds(calendarId,ids);
    }
}
