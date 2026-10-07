package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.generated.model.GenGroupMembers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

@Service
@RequiredArgsConstructor
public class GroupMembershipService {
  private final IdentityRepository identities;
  private final com.sixtymeters.thereabout.access.UserContext users;
  @Transactional(readOnly = true)
  public GenGroupMembers get(long id) {
    var group = identities.findById(id).filter(IdentityEntity::isGroup).orElseThrow(() -> missing("Group"));
    return new GenGroupMembers().version(group.getMembershipVersion()).userIds(group.getMemberUserIds().stream().sorted().toList());
  }
  @Transactional
  public GenGroupMembers save(long id, GenGroupMembers input) {
    var group = identities.findForUpdateById(id).filter(IdentityEntity::isGroup).orElseThrow(() -> missing("Group"));
    version(input.getVersion(), group.getMembershipVersion());
    require(input.getUserIds() != null && input.getUserIds().size() <= 100, "Choose up to 100 users");
    input.getUserIds().forEach(user -> { require(user != null, "User ID required"); users.require(user); });
    group.getMemberUserIds().clear(); group.getMemberUserIds().addAll(input.getUserIds());
    identities.flush(); return get(id);
  }
}
