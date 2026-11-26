package com.appraisal.service;

import com.appraisal.domain.AppUser;
import com.appraisal.domain.Appraisal;
import com.appraisal.domain.Enums.CycleStatus;
import com.appraisal.domain.Enums.ReviewerRole;
import com.appraisal.domain.Enums.Role;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Who may do what (team-wide, role-based):
 * <ul>
 *   <li>Self: the employee.</li>
 *   <li>Peer: any other active member of the same team.</li>
 *   <li>TL: a user with the TL role who leads the team (home team or a managed team) - not for levels that skip the TL review.</li>
 *   <li>Manager: a user with the MANAGER role who leads the team.</li>
 *   <li>"Lead" actions (set level, lead comments, verify achievements, see results): TL / Manager of the team, or an admin.</li>
 * </ul>
 */
@Service
public class AccessService {

    public boolean leadsTeam(AppUser u, Long teamId) {
        return (u.hasRole(Role.TL) || u.hasRole(Role.MANAGER)) && u.leadTeamIds().contains(teamId);
    }

    public boolean isSelf(AppUser u, Appraisal a) {
        return a.getEmployee().getId().equals(u.getId());
    }

    public List<ReviewerRole> eligibleRoles(AppUser u, Appraisal a) {
        List<ReviewerRole> out = new ArrayList<>();
        if (!u.isActive()) return out;
        if (isSelf(u, a)) {
            out.add(ReviewerRole.SELF);
            return out;
        }
        boolean skipTl = a.getLevel() != null && a.getLevel().isSkipTlReview();
        if (u.hasRole(Role.MANAGER) && u.leadTeamIds().contains(a.getTeamId())) out.add(ReviewerRole.MANAGER);
        if (u.hasRole(Role.TL) && u.leadTeamIds().contains(a.getTeamId()) && !skipTl) out.add(ReviewerRole.TL);
        if (u.getTeam() != null && u.getTeam().getId().equals(a.getTeamId())) out.add(ReviewerRole.PEER);
        return out;
    }

    /** Lead actions on someone else's appraisal. */
    public boolean canLead(AppUser u, Appraisal a) {
        if (isSelf(u, a)) return false;
        return u.hasRole(Role.ADMIN) || leadsTeam(u, a.getTeamId());
    }

    /** Scores are visible to admins and the team's leads; employees see their own once the cycle is closed. */
    public boolean canViewResults(AppUser u, Appraisal a) {
        if (canLead(u, a)) return true;
        return isSelf(u, a) && a.getCycle().getStatus() == CycleStatus.CLOSED;
    }

    /** Can open the appraisal page at all (achievements / goals). */
    public boolean canView(AppUser u, Appraisal a) {
        return isSelf(u, a) || canLead(u, a);
    }
}
