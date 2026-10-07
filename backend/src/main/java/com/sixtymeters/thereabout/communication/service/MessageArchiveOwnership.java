package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageArchiveOwnership {
    private final MessageRepository messages;

    @Transactional
    public boolean retainExisting(String source, Long receiverId, UserId user) {
        return messages.findFirstBySourceIdentifierAndReceiverIdOrderByIdAsc(source, receiverId).map(message -> {
            message.getArchiveUserIds().add(user.value());
            return true;
        }).orElse(false);
    }
}
