package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.service.MessageService;
import com.sixtymeters.thereabout.communication.transport.mapper.MessageMapper;
import com.sixtymeters.thereabout.generated.api.MessageApi;
import com.sixtymeters.thereabout.generated.model.GenMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
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
}
