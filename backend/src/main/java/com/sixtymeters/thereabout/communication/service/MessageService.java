package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.communication.data.MessageSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String DEFAULT_SORT = "timestamp,desc";

    private final MessageRepository messageRepository;

    public List<MessageEntity> getMessagesByDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(23, 59, 59, 999999999);
        return messageRepository.findAllByTimestampBetween(from, to);
    }

    public Page<MessageEntity> getMessagePage(int page, int size, String sort, String search,
                                               LocalDate dateFrom, LocalDate dateTo, String source,
                                               String sender, String receiver) {
        Sort sortObj = parseSort(sort != null && !sort.isBlank() ? sort.trim() : DEFAULT_SORT);
        Pageable pageable = PageRequest.of(page, size, sortObj);
        Specification<MessageEntity> spec = Specification.where(MessageSpecification.searchInBodyOrSubject(search))
                .and(MessageSpecification.timestampBetween(dateFrom, dateTo))
                .and(MessageSpecification.sourceEquals(parseSource(source)))
                .and(MessageSpecification.senderNameContains(sender))
                .and(MessageSpecification.receiverNameContains(receiver));
        return messageRepository.findAll(spec, pageable);
    }

    private static CommunicationApplication parseSource(String source) {
        if (source == null || source.isBlank()) return null;
        for (CommunicationApplication app : CommunicationApplication.values()) {
            if (app.getDisplayName().equalsIgnoreCase(source.trim())) return app;
        }
        return null;
    }

    private static Sort parseSort(String sortParam) {
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return desc ? Sort.by(field).descending() : Sort.by(field).ascending();
    }
}
