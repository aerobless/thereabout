package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.transport.mapper.IdentityMapper;
import com.sixtymeters.thereabout.finance.service.FinanceWriteCoordinator;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.persistence.criteria.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

/** Shared identity use cases. Browser admission remains admin-only; MCP admits authenticated machine/person clients. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityOperations {
  private static final UserId RECEIPT_SCOPE = new UserId(1);
  private static final IdentityMapper MAPPER = IdentityMapper.INSTANCE;
  private final IdentityRepository identities;
  private final IdentityInApplicationRepository applications;
  private final IdentityService people;
  private final IdentityInApplicationService links;
  private final GroupMembershipService memberships;
  private final FinanceWriteCoordinator writes;

  public GenIdentityPage list(GenIdentityQuery input) {
    var page = identities.findAll((root, query, cb) -> {
      var clauses = new ArrayList<Predicate>();
      if (input.getIsGroup() != null) clauses.add(cb.equal(root.get("isGroup"), input.getIsGroup()));
      if (input.getIdentityId() != null) clauses.add(cb.equal(root.get("id"), input.getIdentityId()));
      var app = root.join("identityInApplications", JoinType.LEFT);
      applicationFilters(input, app, cb, clauses);
      if (input.getLinked() != null) clauses.add(input.getLinked() ? cb.isNotNull(app.get("id")) : cb.isNull(app.get("id")));
      var text = needle(input.getQ());
      if (text != null) clauses.add(cb.or(like(cb, root.get("firstName"), text), like(cb, root.get("lastName"), text),
          cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), text, '!'),
          like(cb, root.get("relationship"), text), like(cb, app.get("identifier"), text), like(cb, app.get("usernameHint"), text)));
      query.distinct(true);
      return cb.and(clauses.toArray(Predicate[]::new));
    }, paging(input));
    return new GenIdentityPage().items(page.getContent().stream().map(MAPPER::mapToGenIdentity).toList())
        .total(page.getTotalElements()).page(page.getNumber()).pageSize(page.getSize());
  }

  public GenApplicationIdentityPage applications(GenIdentityQuery input) {
    var page = applications.findAll((root, query, cb) -> {
      var clauses = new ArrayList<Predicate>();
      if (input.getIsGroup() != null) clauses.add(cb.equal(root.get("isGroup"), input.getIsGroup()));
      applicationFilters(input, root, cb, clauses);
      var identity = root.join("identity", JoinType.LEFT);
      if (input.getIdentityId() != null) clauses.add(cb.equal(identity.get("id"), input.getIdentityId()));
      if (input.getLinked() != null) clauses.add(input.getLinked() ? cb.isNotNull(identity.get("id")) : cb.isNull(identity.get("id")));
      var text = needle(input.getQ());
      if (text != null) clauses.add(cb.or(like(cb, root.get("identifier"), text), like(cb, root.get("usernameHint"), text),
          like(cb, identity.get("relationship"), text), cb.like(cb.lower(cb.concat(cb.concat(identity.get("firstName"), " "), identity.get("lastName"))), text, '!')));
      return cb.and(clauses.toArray(Predicate[]::new));
    }, paging(input));
    return new GenApplicationIdentityPage().items(page.getContent().stream().map(MAPPER::mapToGenIdentityInApplication).toList())
        .total(page.getTotalElements()).page(page.getNumber()).pageSize(page.getSize());
  }
  private static void applicationFilters(GenIdentityQuery input, From<?, ?> app, CriteriaBuilder cb, List<Predicate> clauses) {
    if (input.getApplication() != null && !input.getApplication().isBlank())
      clauses.add(cb.equal(app.get("application"), MAPPER.displayNameToEnum(input.getApplication())));
  }
  private static Predicate like(CriteriaBuilder cb, Expression<String> field, String text) { return cb.like(cb.lower(field), text, '!'); }
  private static String needle(String q) {
    return q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }
  private static Pageable paging(GenIdentityQuery input) {
    int page = input.getPage() == null ? 0 : input.getPage(), size = input.getPageSize() == null ? 50 : input.getPageSize();
    require(page >= 0 && page <= 99999 && size >= 1 && size <= 200, "Invalid pagination");
    return PageRequest.of(page, size, Sort.by("id"));
  }
  public GenIdentity get(long id) { return MAPPER.mapToGenIdentity(requireIdentity(id)); }
  public GenIdentityInApplication application(long id) { return MAPPER.mapToGenIdentityInApplication(requireApplication(id)); }
  private IdentityEntity requireIdentity(long id) { return identities.findById(id).orElseThrow(() -> missing("Identity")); }
  private IdentityInApplicationEntity requireApplication(long id) { return applications.findById(id).orElseThrow(() -> missing("Application identity")); }

  @Transactional
  public GenIdentity create(GenIdentityCreateInput input) {
    var entity = IdentityEntity.builder().firstName(input.getFirstName()).lastName(input.getLastName())
        .isGroup(Boolean.TRUE.equals(input.getIsGroup())).relationship(input.getRelationship()).build();
    return create(input.getRequestKey(), input, entity);
  }

  @Transactional
  public GenIdentity create(GenIdentity input) {
    return create(input.getRequestKey(), input, MAPPER.mapToIdentityEntity(input));
  }

  private GenIdentity create(String requestKey, Object input, IdentityEntity entity) {
    return writes.write(RECEIPT_SCOPE, "identities.create", requestKey, input, GenIdentity.class, () -> {
      var saved = people.createIdentity(entity);
      identities.flush();
      var result = get(saved.getId());
      writes.audit(RECEIPT_SCOPE, "identities.create", saved.getId(), null, result);
      return result;
    });
  }

  @Transactional
  public GenIdentity update(GenIdentityUpdateInput input) {
    var change = IdentityEntity.builder().firstName(input.getFirstName()).lastName(input.getLastName())
        .isGroup(Boolean.TRUE.equals(input.getIsGroup())).relationship(input.getRelationship()).build();
    return update(input.getId(), input.getVersion(), input.getRequestKey(), input, change);
  }

  @Transactional
  public GenIdentity update(long id, GenIdentity input) {
    input.setId(java.math.BigDecimal.valueOf(id));
    return update(id, input.getVersion(), input.getRequestKey(), input, MAPPER.mapToIdentityEntity(input));
  }

  private GenIdentity update(long id, Long expectedVersion, String requestKey, Object input, IdentityEntity change) {
    return writes.write(RECEIPT_SCOPE, "identities.update", requestKey, input, GenIdentity.class, () -> {
      var entity = identities.findForUpdateById(id).orElseThrow(() -> missing("Identity"));
      version(expectedVersion, entity.getMembershipVersion());
      var before = get(id);
      people.updateIdentity(id, change);
      identities.flush();
      var after = get(id);
      writes.audit(RECEIPT_SCOPE, "identities.update", id, before, after);
      return after;
    });
  }
  @Transactional
  public Boolean delete(GenIdentityVersionedInput input) {
    return writes.write(RECEIPT_SCOPE, "identities.delete", input.getRequestKey(), input, Boolean.class, () -> {
      var entity = identities.findForUpdateById(input.getId()).orElseThrow(() -> missing("Identity"));
      version(input.getVersion(), entity.getMembershipVersion()); var before = get(entity.getId());
      people.deleteIdentity(entity.getId()); identities.flush();
      writes.audit(RECEIPT_SCOPE, "identities.delete", input.getId(), before, null); return true;
    });
  }
  @Transactional
  public GenIdentityInApplication link(GenIdentityLinkInput input) {
    return writes.write(RECEIPT_SCOPE, "identities.applications.link", input.getRequestKey(), input, GenIdentityInApplication.class, () -> {
      version(input.getVersion(), requireApplication(input.getId()).getVersion());
      var target = identities.findForUpdateById(input.getIdentityId()).orElseThrow(() -> missing("Identity"));
      version(input.getIdentityVersion(), target.getMembershipVersion()); var before = application(input.getId());
      links.linkAppIdentity(input.getId(), input.getIdentityId()); applications.flush(); var after = application(input.getId());
      writes.audit(RECEIPT_SCOPE, "identities.applications.link", input.getId(), before, after); return after;
    });
  }
  @Transactional
  public GenIdentityInApplication unlink(GenIdentityVersionedInput input) {
    return writes.write(RECEIPT_SCOPE, "identities.applications.unlink", input.getRequestKey(), input, GenIdentityInApplication.class, () -> {
      version(input.getVersion(), requireApplication(input.getId()).getVersion()); var before = application(input.getId());
      links.unlinkAppIdentity(input.getId()); applications.flush(); var after = application(input.getId());
      writes.audit(RECEIPT_SCOPE, "identities.applications.unlink", input.getId(), before, after); return after;
    });
  }
  public GenGroupMembers members(long id) { return memberships.get(id); }
  @Transactional
  public GenGroupMembers saveMembers(GenIdentityMembershipInput input) {
    return writes.write(RECEIPT_SCOPE, "identities.members.save", input.getRequestKey(), input, GenGroupMembers.class, () -> {
      var before = memberships.get(input.getId());
      var after = memberships.save(input.getId(), new GenGroupMembers().version(input.getVersion()).userIds(input.getUserIds()));
      writes.audit(RECEIPT_SCOPE, "identities.members.save", input.getId(), before, after); return after;
    });
  }
}
