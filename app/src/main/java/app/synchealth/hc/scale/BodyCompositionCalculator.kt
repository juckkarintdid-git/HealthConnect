package app.synchealth.hc.scale

import app.synchealth.hc.model.BodyComposition

enum class Sex { MALE, FEMALE }

data class UserProfile(val heightCm: Double, val age: Int, val sex: Sex)

/**
 * Estimates body composition from weight and bioimpedance with the formulas openScale uses for
 * Xiaomi scales. Results are close to, not identical with, what Zepp Life shows.
 */
object BodyCompositionCalculator {

    fun calculate(weightKg: Double, impedance: Int, profile: UserProfile): BodyComposition {
        val fat = bodyFatPercent(weightKg, impedance, profile)
        val water = waterPercent(fat)
        return BodyComposition(
            bodyFatPercent = fat,
            leanBodyMassKg = weightKg * (1 - fat / 100),
            boneMassKg = boneMass(weightKg, impedance, profile),
            bodyWaterMassKg = weightKg * water / 100,
            bmrKcalPerDay = bmr(weightKg, profile),
        )
    }

    private fun lbmCoefficient(weight: Double, impedance: Int, p: UserProfile): Double {
        var lbm = (p.heightCm * 9.058 / 100) * (p.heightCm / 100)
        lbm += weight * 0.32 + 12.226
        lbm -= impedance * 0.0068
        lbm -= p.age * 0.0542
        return lbm
    }

    fun bodyFatPercent(weight: Double, impedance: Int, p: UserProfile): Double {
        val female = p.sex == Sex.FEMALE
        val lbmSub = when {
            female && p.age <= 49 -> 9.25
            female -> 7.25
            else -> 0.8
        }
        var coeff = 1.0
        if (!female && weight < 61) {
            coeff = 0.98
        } else if (female && weight > 60) {
            coeff = 0.96
            if (p.heightCm > 160) coeff *= 1.03
        } else if (female && weight < 50) {
            coeff = 1.02
            if (p.heightCm > 160) coeff *= 1.03
        }
        val fat = (1.0 - ((lbmCoefficient(weight, impedance, p) - lbmSub) * coeff) / weight) * 100
        return fat.coerceIn(5.0, 75.0)
    }

    private fun waterPercent(fatPercent: Double): Double {
        val water = (100 - fatPercent) * 0.7
        return (water * if (water < 50) 1.02 else 0.98).coerceIn(35.0, 75.0)
    }

    private fun boneMass(weight: Double, impedance: Int, p: UserProfile): Double {
        val base = if (p.sex == Sex.FEMALE) 0.245691014 else 0.18016894
        var bone = (base - lbmCoefficient(weight, impedance, p) * 0.05158) * -1
        bone += if (bone > 2.2) 0.1 else -0.1
        return bone.coerceIn(0.5, 8.0)
    }

    private fun bmr(weight: Double, p: UserProfile): Double {
        val bmr = if (p.sex == Sex.MALE) {
            877.8 + weight * 14.916 - p.heightCm * 0.726 - p.age * 8.976
        } else {
            864.6 + weight * 10.2036 - p.heightCm * 0.39336 - p.age * 6.204
        }
        return bmr.coerceIn(500.0, 5000.0)
    }
}
