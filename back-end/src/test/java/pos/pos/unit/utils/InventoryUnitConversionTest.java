package pos.pos.unit.utils;

import org.junit.jupiter.api.Test;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.inventory.service.InventoryUnitConversion;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InventoryUnitConversionTest {

    @Test
    void convertsMassToTheInventoryBaseUnit() {
        assertThat(InventoryUnitConversion.convert(new BigDecimal("250"), InventoryUnit.GRAM, InventoryUnit.KILOGRAM))
                .isEqualByComparingTo("0.250000000");
        assertThat(InventoryUnitConversion.convert(new BigDecimal("1"), InventoryUnit.POUND, InventoryUnit.GRAM))
                .isEqualByComparingTo("453.592370000");
    }

    @Test
    void convertsMetricVolumeUnits() {
        assertThat(InventoryUnitConversion.convert(new BigDecimal("1500"), InventoryUnit.MILLILITER, InventoryUnit.LITER))
                .isEqualByComparingTo("1.500000000");
    }

    @Test
    void leavesMatchingUnitsUnchanged() {
        assertThat(InventoryUnitConversion.convert(new BigDecimal("2.125"), InventoryUnit.CUP, InventoryUnit.CUP))
                .isEqualByComparingTo("2.125");
    }

    @Test
    void rejectsUnknownOrIncompatibleConversions() {
        assertThat(InventoryUnitConversion.canConvert(InventoryUnit.CUP, InventoryUnit.MILLILITER)).isFalse();
        assertThat(InventoryUnitConversion.canConvert(InventoryUnit.EACH, InventoryUnit.PACK)).isFalse();
        assertThat(InventoryUnitConversion.canConvert(null, null)).isFalse();
        assertThat(InventoryUnitConversion.canConvert(InventoryUnit.GRAM, null)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() ->
                InventoryUnitConversion.convert(BigDecimal.ONE, InventoryUnit.GRAM, InventoryUnit.LITER));
    }


    @Test
    void everyMassUnitPairRoundTripsWithinConversionPrecision() {
        var massUnits = java.util.List.of(
                InventoryUnit.GRAM, InventoryUnit.KILOGRAM, InventoryUnit.OUNCE, InventoryUnit.POUND
        );
        BigDecimal quantity = new BigDecimal("12.345678");
        for (InventoryUnit from : massUnits) {
            for (InventoryUnit to : massUnits) {
                assertThat(InventoryUnitConversion.canConvert(from, to)).isTrue();
                BigDecimal roundTrip = InventoryUnitConversion.convert(
                        InventoryUnitConversion.convert(quantity, from, to), to, from
                );
                assertThat(roundTrip).isCloseTo(quantity,
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
            }
        }
    }

    @Test
    void volumeConversionsAndAllSameUnitConversionsAreExact() {
        assertThat(InventoryUnitConversion.convert(BigDecimal.ONE, InventoryUnit.LITER, InventoryUnit.MILLILITER))
                .isEqualByComparingTo("1000");
        assertThat(InventoryUnitConversion.convert(BigDecimal.ONE, InventoryUnit.MILLILITER, InventoryUnit.LITER))
                .isEqualByComparingTo("0.001");

        BigDecimal quantity = new BigDecimal("123.456789123");
        for (InventoryUnit unit : InventoryUnit.values()) {
            assertThat(InventoryUnitConversion.canConvert(unit, unit)).isTrue();
            assertThat(InventoryUnitConversion.convert(quantity, unit, unit)).isSameAs(quantity);
        }
    }

}
