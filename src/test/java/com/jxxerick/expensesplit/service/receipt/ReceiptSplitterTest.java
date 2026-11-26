package com.jxxerick.expensesplit.service.receipt;

import com.jxxerick.expensesplit.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptSplitterTest {

    @Test
    void splitsItemsAndSpreadsServiceAndDiscountByConsumption() {
        ReceiptBill bill = ReceiptSplitter.split(List.of(
                item("Pizza", "60.00", 1, 1L, 2L, 3L),
                item("Cerveja", "12.00", 2, 1L, 2L),
                item("Suco", "8.00", 1, 3L)), new BigDecimal("10"), new BigDecimal("10.00"));

        assertThat(bill.subtotal()).isEqualByComparingTo("92.00");
        assertThat(bill.serviceCharge()).isEqualByComparingTo("9.20");
        assertThat(bill.discount()).isEqualByComparingTo("10.00");
        assertThat(bill.total()).isEqualByComparingTo("91.20");
        assertThat(bill.members()).containsExactly(
                bill(1L, "32.00", "3.20", "3.48", "31.72"),
                bill(2L, "32.00", "3.20", "3.48", "31.72"),
                bill(3L, "28.00", "2.80", "3.04", "27.76"));
    }

    @Test
    void keepsMembersInTheOrderTheyFirstAppear() {
        ReceiptBill bill = ReceiptSplitter.split(List.of(
                item("Sobremesa", "15.00", 1, 3L),
                item("Prato", "40.00", 1, 1L, 3L)), null, null);

        assertThat(bill.members()).extracting(MemberBill::memberId).containsExactly(3L, 1L);
        assertThat(bill.members()).extracting(MemberBill::total)
                .containsExactly(new BigDecimal("35.00"), new BigDecimal("20.00"));
    }

    @Test
    void roundsServiceChargeHalfUpLikeThePrintedBill() {
        assertThat(ReceiptSplitter.serviceCharge(new BigDecimal("33.35"), new BigDecimal("10")))
                .isEqualByComparingTo("3.34");
        assertThat(ReceiptSplitter.serviceCharge(new BigDecimal("33.35"), null)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @CsvSource({"10.00, 10, 0.00", "0.10, 13, 0.01", "99.99, 12.5, 7.77", "1234.57, 10, 100.00"})
    void membersAlwaysAddUpToTheBill(String price, String servicePercent, String discount) {
        ReceiptBill bill = ReceiptSplitter.split(List.of(
                item("Rodízio", price, 1, 1L, 2L, 3L),
                item("Refri", "7.00", 3, 2L, 3L, 4L, 5L, 6L, 7L)), new BigDecimal(servicePercent), new BigDecimal(discount));

        BigDecimal sum = bill.members().stream().map(MemberBill::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(bill.total());
        assertThat(bill.members()).allSatisfy(m -> assertThat(m.total().signum()).isNotNegative());
    }

    @Test
    void personWhoseOnlyItemRoundedDownOwesNothing() {
        ReceiptBill bill = ReceiptSplitter.split(List.of(
                item("Bala", "0.01", 1, 1L, 2L),
                item("Café", "5.00", 1, 1L)), new BigDecimal("10"), null);

        assertThat(bill.members()).extracting(MemberBill::total)
                .containsExactly(new BigDecimal("5.51"), new BigDecimal("0.00"));
    }

    @Test
    void rejectsDiscountAboveConsumption() {
        assertThatThrownBy(() -> ReceiptSplitter.split(List.of(item("Prato", "30.00", 1, 1L)),
                new BigDecimal("10"), new BigDecimal("30.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode passar do consumo");
    }

    @Test
    void rejectsBillThatEndsUpFree() {
        assertThatThrownBy(() -> ReceiptSplitter.split(List.of(item("Prato", "30.00", 1, 1L)),
                null, new BigDecimal("30.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("zerada");
    }

    @Test
    void rejectsMemberRepeatedInTheSameItem() {
        assertThatThrownBy(() -> ReceiptSplitter.split(List.of(item("Prato", "30.00", 1, 1L, 1L)), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Membro 1 aparece duas vezes no item 'Prato'");
    }

    @Test
    void rejectsEmptyReceipt() {
        assertThatThrownBy(() -> ReceiptSplitter.split(List.of(), null, null))
                .isInstanceOf(BusinessException.class);
    }

    private static ReceiptItem item(String description, String unitPrice, int quantity, Long... memberIds) {
        return new ReceiptItem(description, new BigDecimal(unitPrice), quantity, List.of(memberIds));
    }

    private static MemberBill bill(Long memberId, String subtotal, String service, String discount, String total) {
        return new MemberBill(memberId, new BigDecimal(subtotal), new BigDecimal(service), new BigDecimal(discount),
                new BigDecimal(total));
    }
}
