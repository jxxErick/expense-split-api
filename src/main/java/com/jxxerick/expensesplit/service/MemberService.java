package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.repository.RecurringExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

/** Leaving a group: soft removal, so the history and the old expenses stay intact. */
@Service
public class MemberService {

    private final GroupService groupService;
    private final BalanceService balanceService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public MemberService(GroupService groupService, BalanceService balanceService,
                         RecurringExpenseRepository recurringExpenseRepository, Clock clock,
                         ApplicationEventPublisher events) {
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Only members with a zero balance and no live recurring expense can leave,
     * so nobody walks away owing money or keeps being charged.
     */
    @Transactional
    public void leave(Long groupId, Long memberId) {
        Group group = groupService.getGroup(groupId);
        Member member = ExpenseService.memberOf(group, memberId);
        if (!member.isActive()) {
            throw new BusinessException("%s já saiu do grupo".formatted(member.getName()));
        }
        if (group.activeMemberCount() == 1) {
            throw new BusinessException("O grupo precisa de pelo menos um membro");
        }
        BigDecimal balance = balanceService.netBalances(group).get(memberId);
        if (balance.signum() != 0) {
            String position = balance.signum() < 0 ? "deve" : "tem a receber";
            throw new BusinessException("%s ainda %s %s; acerte as contas antes de sair".formatted(
                    member.getName(), position, GroupActivityEvent.money(group.getCurrency(), balance.abs())));
        }
        recurringExpenseRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .filter(recurring -> recurring.getStatus() != RecurringStatus.FINISHED)
                .filter(recurring -> involves(recurring, memberId))
                .findFirst()
                .ifPresent(recurring -> {
                    throw new BusinessException("%s participa da despesa recorrente '%s'".formatted(
                            member.getName(), recurring.getDescription()));
                });

        member.leave(clock.instant());
        events.publishEvent(GroupActivityEvent.memberLeft(group, member));
    }

    private static boolean involves(RecurringExpense recurring, Long memberId) {
        return recurring.getPayer().getId().equals(memberId)
                || recurring.getShares().stream().anyMatch(share -> memberId.equals(share.getMemberId()));
    }
}
