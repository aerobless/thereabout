package com.sixtymeters.thereabout.communication.data;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MessageSpecification {

    private MessageSpecification() {
    }

    public static Specification<MessageEntity> searchInBodyOrSubject(String search) {
        if (!StringUtils.hasText(search)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (Root<MessageEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate bodyMatch = cb.and(cb.isNotNull(root.get("body")),
                    cb.like(cb.lower(root.get("body")), pattern));
            Predicate subjectMatch = cb.and(cb.isNotNull(root.get("subject")),
                    cb.like(cb.lower(root.get("subject")), pattern));
            return cb.or(bodyMatch, subjectMatch);
        };
    }

    public static Specification<MessageEntity> timestampBetween(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? dateTo.atTime(23, 59, 59, 999999999) : null;
        LocalDateTime fromVal = from;
        LocalDateTime toVal = to;
        return (root, query, cb) -> {
            if (fromVal != null && toVal != null) {
                return cb.between(root.get("timestamp"), fromVal, toVal);
            }
            if (fromVal != null) {
                return cb.greaterThanOrEqualTo(root.get("timestamp"), fromVal);
            }
            return cb.lessThanOrEqualTo(root.get("timestamp"), toVal);
        };
    }

    public static Specification<MessageEntity> sourceEquals(CommunicationApplication source) {
        if (source == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("source"), source);
    }

    public static Specification<MessageEntity> senderNameContains(String sender) {
        if (!StringUtils.hasText(sender)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + sender.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var senderJoin = root.join("sender", JoinType.LEFT);
            var identityJoin = senderJoin.join("identity", JoinType.LEFT);
            Predicate shortNameMatch = cb.like(cb.lower(cb.coalesce(identityJoin.get("shortName"), "")), pattern);
            Predicate identifierMatch = cb.like(cb.lower(senderJoin.get("identifier")), pattern);
            return cb.or(shortNameMatch, identifierMatch);
        };
    }

    public static Specification<MessageEntity> receiverNameContains(String receiver) {
        if (!StringUtils.hasText(receiver)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + receiver.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            var receiverJoin = root.join("receiver", JoinType.LEFT);
            var identityJoin = receiverJoin.join("identity", JoinType.LEFT);
            Predicate shortNameMatch = cb.like(cb.lower(cb.coalesce(identityJoin.get("shortName"), "")), pattern);
            Predicate identifierMatch = cb.like(cb.lower(receiverJoin.get("identifier")), pattern);
            return cb.or(shortNameMatch, identifierMatch);
        };
    }
}
