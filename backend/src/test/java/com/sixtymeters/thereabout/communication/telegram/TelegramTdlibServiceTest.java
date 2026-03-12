package com.sixtymeters.thereabout.communication.telegram;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.MessageEntity;
import com.sixtymeters.thereabout.communication.data.MessageRepository;
import com.sixtymeters.thereabout.communication.data.TelegramConnectionEntity;
import com.sixtymeters.thereabout.communication.data.TelegramConnectionRepository;
import com.sixtymeters.thereabout.communication.data.TelegramSyncCheckpointEntity;
import com.sixtymeters.thereabout.communication.data.TelegramSyncCheckpointRepository;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramTdlibServiceTest {

    @Mock
    private TelegramConnectionRepository connectionRepository;

    @Mock
    private TelegramSyncCheckpointRepository checkpointRepository;

    @Mock
    private TelegramMessageMapper messageMapper;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpleTelegramClient client;

    private TelegramTdlibService service;
    private TelegramConnectionEntity connection;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.setHistoryBatchSize(100);
        properties.setResyncThrottleMs(0);

        service = new TelegramTdlibService(
                properties,
                connectionRepository,
                checkpointRepository,
                messageMapper,
                messageRepository
        );

        connection = TelegramConnectionEntity.builder()
                .id(1L)
                .authStatus("READY")
                .enabled(true)
                .phoneNumber("+41790000000")
                .build();

        @SuppressWarnings("unchecked")
        AtomicReference<SimpleTelegramClient> clientRef =
                (AtomicReference<SimpleTelegramClient>) ReflectionTestUtils.getField(service, "clientRef");
        clientRef.set(client);

        when(connectionRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(connection));
        lenient().when(checkpointRepository.save(any(TelegramSyncCheckpointEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(messageRepository.saveAll(anyIterable()))
                .thenAnswer(invocation -> {
                    List<MessageEntity> saved = new ArrayList<>();
                    invocation.<Iterable<MessageEntity>>getArgument(0).forEach(saved::add);
                    return saved;
                });
        lenient().when(messageMapper.toMessageEntity(any(TdApi.Message.class), any(), any(), any(), any(Boolean.class), any(), any()))
                .thenAnswer(invocation -> messageEntity("telegram-" + ((TdApi.Message) invocation.getArgument(0)).id));
        lenient().when(messageMapper.toMessageEntityWithSourcePrefix(any(TdApi.Message.class), any(), any(), any(), any(Boolean.class), any(), any()))
                .thenAnswer(invocation -> {
                    TdApi.Message message = invocation.getArgument(0);
                    String prefix = invocation.getArgument(1);
                    return messageEntity(prefix + message.id);
                });
        lenient().doNothing().when(messageMapper).clearCache();
    }

    @Test
    void continuesBackfillAfterShortBatchUntilHistoryIsActuallyEmpty() {
        AtomicBoolean mainLoaded = new AtomicBoolean(false);
        List<Long> requestedFromIds = new ArrayList<>();

        when(checkpointRepository.findByConnectionAndChatId(connection, 100L)).thenReturn(Optional.empty());
        stubClientSend(function -> {
            if (function instanceof TdApi.LoadChats loadChats) {
                if (loadChats.chatList instanceof TdApi.ChatListMain && mainLoaded.compareAndSet(false, true)) {
                    pushChatPosition(100L, new TdApi.ChatListMain(), 900L);
                }
                return new TdApi.Error(404, "done");
            }
            if (function instanceof TdApi.GetChat getChat) {
                return privateChat(getChat.chatId, "Main Chat");
            }
            if (function instanceof TdApi.GetUser getUser) {
                return user(getUser.userId, "Alice", "Test");
            }
            if (function instanceof TdApi.GetChatHistory getChatHistory) {
                requestedFromIds.add(getChatHistory.fromMessageId);
                if (getChatHistory.fromMessageId == 0) {
                    return messages(createMessages(100L, 700L, 650L, 51));
                }
                if (getChatHistory.fromMessageId == 650L) {
                    return messages(message(100L, 600L, 42L));
                }
                if (getChatHistory.fromMessageId == 600L) {
                    return messages();
                }
                throw new AssertionError("Unexpected history request fromMessageId=" + getChatHistory.fromMessageId);
            }
            throw new AssertionError("Unexpected TDLib call: " + function.getClass().getSimpleName());
        });

        service.runFullBackfill(TelegramTdlibService.BackfillMode.RESUME);

        ArgumentCaptor<TelegramSyncCheckpointEntity> checkpointCaptor = ArgumentCaptor.forClass(TelegramSyncCheckpointEntity.class);
        verify(checkpointRepository, times(3)).save(checkpointCaptor.capture());
        assertThat(requestedFromIds).containsExactly(0L, 650L, 600L);
        assertThat(checkpointCaptor.getAllValues())
                .extracting(TelegramSyncCheckpointEntity::getLastMessageId, TelegramSyncCheckpointEntity::getBackfillComplete)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(650L, false),
                        org.assertj.core.groups.Tuple.tuple(600L, false),
                        org.assertj.core.groups.Tuple.tuple(600L, true)
                );
    }

    @Test
    void marksCheckpointCompleteWhenHistoryStopsMakingProgress() {
        AtomicBoolean mainLoaded = new AtomicBoolean(false);

        when(checkpointRepository.findByConnectionAndChatId(connection, 100L)).thenReturn(Optional.empty());
        stubClientSend(function -> {
            if (function instanceof TdApi.LoadChats loadChats) {
                if (loadChats.chatList instanceof TdApi.ChatListMain && mainLoaded.compareAndSet(false, true)) {
                    pushChatPosition(100L, new TdApi.ChatListMain(), 900L);
                }
                return new TdApi.Error(404, "done");
            }
            if (function instanceof TdApi.GetChat getChat) {
                return privateChat(getChat.chatId, "Main Chat");
            }
            if (function instanceof TdApi.GetUser getUser) {
                return user(getUser.userId, "Alice", "Test");
            }
            if (function instanceof TdApi.GetChatHistory getChatHistory) {
                if (getChatHistory.fromMessageId == 0) {
                    return messages(message(100L, 500L, 42L));
                }
                if (getChatHistory.fromMessageId == 500L) {
                    return messages(message(100L, 500L, 42L));
                }
                throw new AssertionError("Unexpected history request fromMessageId=" + getChatHistory.fromMessageId);
            }
            throw new AssertionError("Unexpected TDLib call: " + function.getClass().getSimpleName());
        });

        service.runFullBackfill(TelegramTdlibService.BackfillMode.RESUME);

        ArgumentCaptor<TelegramSyncCheckpointEntity> checkpointCaptor = ArgumentCaptor.forClass(TelegramSyncCheckpointEntity.class);
        verify(checkpointRepository, times(2)).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getAllValues())
                .extracting(TelegramSyncCheckpointEntity::getLastMessageId, TelegramSyncCheckpointEntity::getBackfillComplete)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(500L, false),
                        org.assertj.core.groups.Tuple.tuple(500L, true)
                );
    }

    @Test
    void forceRebuildDeletesOldCheckpointsBeforeBackfillingAgain() {
        AtomicBoolean mainLoaded = new AtomicBoolean(false);
        AtomicBoolean checkpointsDeleted = new AtomicBoolean(false);
        List<Long> requestedFromIds = new ArrayList<>();
        TelegramSyncCheckpointEntity existingCheckpoint = TelegramSyncCheckpointEntity.builder()
                .connection(connection)
                .chatId(100L)
                .lastMessageId(999L)
                .backfillComplete(true)
                .build();

        when(checkpointRepository.deleteByConnection(connection)).thenAnswer(invocation -> {
            checkpointsDeleted.set(true);
            return 1L;
        });
        when(checkpointRepository.findByConnectionAndChatId(connection, 100L)).thenAnswer(invocation ->
                checkpointsDeleted.get() ? Optional.empty() : Optional.of(existingCheckpoint));

        stubClientSend(function -> {
            if (function instanceof TdApi.LoadChats loadChats) {
                if (loadChats.chatList instanceof TdApi.ChatListMain && mainLoaded.compareAndSet(false, true)) {
                    pushChatPosition(100L, new TdApi.ChatListMain(), 900L);
                }
                return new TdApi.Error(404, "done");
            }
            if (function instanceof TdApi.GetChat getChat) {
                return privateChat(getChat.chatId, "Main Chat");
            }
            if (function instanceof TdApi.GetUser getUser) {
                return user(getUser.userId, "Alice", "Test");
            }
            if (function instanceof TdApi.GetChatHistory getChatHistory) {
                requestedFromIds.add(getChatHistory.fromMessageId);
                if (getChatHistory.fromMessageId == 0) {
                    return messages(message(100L, 500L, 42L));
                }
                if (getChatHistory.fromMessageId == 500L) {
                    return messages();
                }
                throw new AssertionError("Unexpected history request fromMessageId=" + getChatHistory.fromMessageId);
            }
            throw new AssertionError("Unexpected TDLib call: " + function.getClass().getSimpleName());
        });

        service.runFullBackfill(TelegramTdlibService.BackfillMode.FORCE_REBUILD);

        verify(checkpointRepository).deleteByConnection(connection);
        assertThat(requestedFromIds).startsWith(0L);
    }

    @Test
    void backfillsMainAndFolderChatsButIgnoresArchiveAndDeduplicatesChatIds() {
        AtomicBoolean mainLoaded = new AtomicBoolean(false);
        AtomicBoolean folderLoaded = new AtomicBoolean(false);

        pushChatFolders(7);
        when(checkpointRepository.findByConnectionAndChatId(eq(connection), anyLong())).thenReturn(Optional.empty());

        stubClientSend(function -> {
            if (function instanceof TdApi.LoadChats loadChats) {
                if (loadChats.chatList instanceof TdApi.ChatListMain && mainLoaded.compareAndSet(false, true)) {
                    pushChatPosition(100L, new TdApi.ChatListMain(), 900L);
                    pushChatPosition(300L, new TdApi.ChatListArchive(), 800L);
                }
                if (loadChats.chatList instanceof TdApi.ChatListFolder folder && folder.chatFolderId == 7
                        && folderLoaded.compareAndSet(false, true)) {
                    pushChatPosition(100L, new TdApi.ChatListFolder(7), 700L);
                    pushChatPosition(200L, new TdApi.ChatListFolder(7), 600L);
                }
                return new TdApi.Error(404, "done");
            }
            if (function instanceof TdApi.GetChat getChat) {
                return privateChat(getChat.chatId, "Chat " + getChat.chatId);
            }
            if (function instanceof TdApi.GetChatHistory getChatHistory) {
                return messages();
            }
            throw new AssertionError("Unexpected TDLib call: " + function.getClass().getSimpleName());
        });

        service.runFullBackfill(TelegramTdlibService.BackfillMode.RESUME);

        verify(client, times(1)).send(argThatGetChat(100L));
        verify(client, times(1)).send(argThatGetChat(200L));
        verify(client, times(0)).send(argThatGetChat(300L));
    }

    @Test
    void continuesShortBatchPaginationForUpgradedBasicGroupHistory() {
        AtomicBoolean mainLoaded = new AtomicBoolean(false);
        List<Long> basicGroupFromIds = new ArrayList<>();

        when(checkpointRepository.findByConnectionAndChatId(connection, 200L)).thenReturn(Optional.empty());
        when(checkpointRepository.findByConnectionAndChatId(connection, 99L)).thenReturn(Optional.empty());

        stubClientSend(function -> {
            if (function instanceof TdApi.LoadChats loadChats) {
                if (loadChats.chatList instanceof TdApi.ChatListMain && mainLoaded.compareAndSet(false, true)) {
                    pushChatPosition(200L, new TdApi.ChatListMain(), 900L);
                }
                return new TdApi.Error(404, "done");
            }
            if (function instanceof TdApi.GetChat getChat) {
                if (getChat.chatId == 200L) {
                    return supergroupChat(200L, 5000L, "Upgraded Group");
                }
                if (getChat.chatId == 99L) {
                    return privateChat(99L, "Legacy Group");
                }
                throw new AssertionError("Unexpected GetChat request chatId=" + getChat.chatId);
            }
            if (function instanceof TdApi.GetSupergroupFullInfo) {
                TdApi.SupergroupFullInfo fullInfo = new TdApi.SupergroupFullInfo();
                fullInfo.upgradedFromBasicGroupId = 99L;
                return fullInfo;
            }
            if (function instanceof TdApi.CreateBasicGroupChat createBasicGroupChat) {
                assertThat(createBasicGroupChat.basicGroupId).isEqualTo(99L);
                return privateChat(99L, "Legacy Group");
            }
            if (function instanceof TdApi.GetUser getUser) {
                return user(getUser.userId, "Alice", "Test");
            }
            if (function instanceof TdApi.GetChatHistory getChatHistory) {
                if (getChatHistory.chatId == 99L) {
                    basicGroupFromIds.add(getChatHistory.fromMessageId);
                    if (getChatHistory.fromMessageId == 0) {
                        return messages(createMessages(99L, 700L, 650L, 51));
                    }
                    if (getChatHistory.fromMessageId == 650L) {
                        return messages(message(99L, 600L, 42L));
                    }
                    if (getChatHistory.fromMessageId == 600L) {
                        return messages();
                    }
                }
                if (getChatHistory.chatId == 200L) {
                    return messages();
                }
                throw new AssertionError("Unexpected history request chatId=" + getChatHistory.chatId + ", fromMessageId=" + getChatHistory.fromMessageId);
            }
            throw new AssertionError("Unexpected TDLib call: " + function.getClass().getSimpleName());
        });

        service.runFullBackfill(TelegramTdlibService.BackfillMode.RESUME);

        assertThat(basicGroupFromIds).containsExactly(0L, 650L, 600L);
    }

    @Test
    void readyStateDoesNotStartAutomaticBackfill() throws InterruptedException {
        TdApi.UpdateAuthorizationState update = new TdApi.UpdateAuthorizationState();
        update.authorizationState = new TdApi.AuthorizationStateReady();

        ReflectionTestUtils.invokeMethod(service, "onAuthorizationState", update);
        Thread.sleep(200);

        assertThat(service.getResyncStatus()).isEqualTo("IDLE");
        verify(connectionRepository).save(any(TelegramConnectionEntity.class));
        verifyNoInteractions(checkpointRepository, client, messageRepository);
    }

    private void stubClientSend(Function<TdApi.Function<?>, TdApi.Object> resolver) {
        doAnswer(invocation -> CompletableFuture.completedFuture(resolver.apply(invocation.getArgument(0))))
                .when(client)
                .send(any(TdApi.Function.class));
    }

    private void pushChatFolders(int... folderIds) {
        TdApi.ChatFolderInfo[] infos = new TdApi.ChatFolderInfo[folderIds.length];
        for (int i = 0; i < folderIds.length; i++) {
            TdApi.ChatFolderInfo info = new TdApi.ChatFolderInfo();
            info.id = folderIds[i];
            infos[i] = info;
        }
        TdApi.UpdateChatFolders update = new TdApi.UpdateChatFolders();
        update.chatFolders = infos;
        ReflectionTestUtils.invokeMethod(service, "onChatFolders", update);
    }

    private void pushChatPosition(long chatId, TdApi.ChatList chatList, long order) {
        TdApi.UpdateChatPosition update = new TdApi.UpdateChatPosition(
                chatId,
                new TdApi.ChatPosition(chatList, order, false, null)
        );
        ReflectionTestUtils.invokeMethod(service, "onChatPosition", update);
    }

    private MessageEntity messageEntity(String sourceIdentifier) {
        return MessageEntity.builder()
                .type("text")
                .source(CommunicationApplication.TELEGRAM)
                .sourceIdentifier(sourceIdentifier)
                .timestamp(LocalDateTime.of(2026, 3, 12, 12, 0))
                .build();
    }

    private TdApi.Chat privateChat(long chatId, String title) {
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = chatId;
        chat.title = title;
        chat.type = new TdApi.ChatTypePrivate(42L);
        return chat;
    }

    private TdApi.Chat supergroupChat(long chatId, long supergroupId, String title) {
        TdApi.Chat chat = new TdApi.Chat();
        chat.id = chatId;
        chat.title = title;
        chat.type = new TdApi.ChatTypeSupergroup(supergroupId, false);
        return chat;
    }

    private TdApi.User user(long userId, String firstName, String lastName) {
        TdApi.User user = new TdApi.User();
        user.id = userId;
        user.firstName = firstName;
        user.lastName = lastName;
        return user;
    }

    private TdApi.Messages messages(TdApi.Message... messages) {
        TdApi.Messages result = new TdApi.Messages();
        result.totalCount = messages.length;
        result.messages = messages;
        return result;
    }

    private TdApi.Message[] createMessages(long chatId, long newestMessageId, long oldestMessageId, int count) {
        TdApi.Message[] messages = new TdApi.Message[count];
        long currentId = newestMessageId;
        for (int i = 0; i < count; i++) {
            messages[i] = message(chatId, currentId, 42L);
            if (i < count - 2) {
                currentId -= 1;
            } else {
                currentId = oldestMessageId;
            }
        }
        return messages;
    }

    private TdApi.Message message(long chatId, long messageId, long userId) {
        TdApi.Message message = new TdApi.Message();
        message.chatId = chatId;
        message.id = messageId;
        message.date = 1;
        message.senderId = new TdApi.MessageSenderUser(userId);
        return message;
    }

    private TdApi.Function<?> argThatHistory(long chatId, long fromMessageId) {
        return org.mockito.ArgumentMatchers.argThat(function ->
                function instanceof TdApi.GetChatHistory getChatHistory
                        && getChatHistory.chatId == chatId
                        && getChatHistory.fromMessageId == fromMessageId);
    }

    private TdApi.Function<?> argThatGetChat(long chatId) {
        return org.mockito.ArgumentMatchers.argThat(function ->
                function instanceof TdApi.GetChat getChat && getChat.chatId == chatId);
    }
}
