package com.skyward.domain.balance;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real-time (synchronous REST) read of a member's points balance. */
@RestController
@RequestMapping("/members")
public class MemberBalanceController {

    private final MemberBalanceService balanceService;

    public MemberBalanceController(MemberBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable("id") UUID id) {
        return balanceService.balanceFor(id);
    }
}
