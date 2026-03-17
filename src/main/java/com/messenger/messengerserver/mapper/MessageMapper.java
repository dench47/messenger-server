package com.messenger.messengerserver.mapper;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.model.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageDto toDto(Message message) {
        if (message == null) {
            return null;
        }

        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp());
        dto.setIsRead(message.getIsRead());
        dto.setSenderUsername(message.getSender() != null ? message.getSender().getUsername() : null);
        dto.setReceiverUsername(message.getReceiver() != null ? message.getReceiver().getUsername() : null);
        dto.setType(message.getType() != null ? message.getType().toString() : "TEXT");
        dto.setStatus(message.getStatus() != null ? message.getStatus().toString() : "SENT");
        return dto;
    }

    // Можно добавить обратное преобразование, если понадобится
    public Message toEntity(MessageDto dto) {
        if (dto == null) {
            return null;
        }

        Message message = new Message();
        message.setId(dto.getId());
        message.setContent(dto.getContent());
        message.setTimestamp(dto.getTimestamp());
        message.setIsRead(dto.getIsRead());
        // Sender и Receiver нужно устанавливать отдельно через сервис
        // так как у них могут быть дополнительные зависимости
        return message;
    }
}