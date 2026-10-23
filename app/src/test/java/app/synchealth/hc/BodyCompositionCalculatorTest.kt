package app.synchealth.hc

import app.synchealth.hc.scale.BodyCompositionCalculator
import app.synchealth.hc.scale.Sex
import app.synchealth.hc.scale.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyCompositionCalculatorTest {

    @Test
    fun typicalMale() {
        val c = BodyCompositionCalculator.calculate(75.0, 500, UserProfile(175.0, 30, Sex.MALE))
        assertEquals(22.5, c.bodyFatPercent, 0.5)
        assertEquals(75.0 * (1 - c.bodyFatPercent / 100), c.leanBodyMassKg, 0.001)
        assertTrue(c.boneMassKg in 2.0..4.0)
        assertTrue(c.bodyWaterMassKg in 35.0..45.0)
        assertTrue(c.bmrKcalPerDay in 1500.0..2000.0)
    }

    @Test
    fun higherImpedanceMeansMoreFat() {
        val p = UserProfile(160.0, 40, Sex.FEMALE)
        val low = BodyCompositionCalculator.bodyFatPercent(60.0, 450, p)
        val high = BodyCompositionCalculator.bodyFatPercent(60.0, 650, p)
        assertTrue(high > low)
    }
}
