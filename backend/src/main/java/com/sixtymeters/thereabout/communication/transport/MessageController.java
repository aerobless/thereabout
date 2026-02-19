package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.service.MessageService;
import com.sixtymeters.thereabout.communication.transport.mapper.MessageMapper;
import com.sixtymeters.thereabout.generated.api.MessageApi;
import com.sixtymeters.thereabout.generated.model.GenMessage;
import com.sixtymeters.thereabout.generated.model.GenMessagePage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MessageController implements MessageApi {

    private static final MessageMapper MESSAGE_MAPPER = MessageMapper.INSTANCE;
    private final MessageService messageService;

    @Override
    public ResponseEntity<List<GenMessage>> getMessages(LocalDate date) {
        List<GenMessage> messages = messageService.getMessagesByDate(date).stream()
                .map(MESSAGE_MAPPER::mapToGenMessage)
                .collect(Collectors.toList());
        return ResponseEntity.ok(messages);
    }

    @Override
    public ResponseEntity<GenMessagePage> getMessageList(Optional<Integer> page, Optional<Integer> size,
                                                         Optional<String> sort, Optional<String> search,
                                                         Optional<LocalDate> dateFrom, Optional<LocalDate> dateTo,
                                                         Optional<String> source, Optional<String> sender,
                                                         Optional<String> receiver) {
        int pageNum = page.filter(p -> p >= 0).orElse(0);
        int sizeNum = size.filter(s -> s >= 1).orElse(20);
        String sortParam = sort.filter(s -> !s.isBlank()).orElse(null);
        String searchParam = search.filter(s -> !s.isBlank()).orElse(null);
        LocalDate dateFromVal = dateFrom.orElse(null);
        LocalDate dateToVal = dateTo.orElse(null);
        String sourceParam = source.filter(s -> !s.isBlank()).orElse(null);
        String senderParam = sender.filter(s -> !s.isBlank()).orElse(null);
        String receiverParam = receiver.filter(s -> !s.isBlank()).orElse(null);

        var messagePage = messageService.getMessagePage(pageNum, sizeNum, sortParam, searchParam,
                dateFromVal, dateToVal, sourceParam, senderParam, receiverParam);
        List<GenMessage> content = messagePage.getContent().stream()
                .map(MESSAGE_MAPPER::mapToGenMessage)
                .collect(Collectors.toList());
        GenMessagePage response = new GenMessagePage()
                .content(content)
                .totalElements((int) messagePage.getTotalElements());
        return ResponseEntity.ok(response);
    }
}
