package com.jxxerick.expensesplit.dto;

import java.math.BigDecimal;

public record TransferResponse(MemberResponse from, MemberResponse to, BigDecimal amount) {
}
