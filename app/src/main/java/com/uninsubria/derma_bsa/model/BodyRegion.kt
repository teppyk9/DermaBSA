package com.uninsubria.derma_bsa.model

enum class PasiRegion(val label: String, val peso: Float) {
    TESTA("Testa", 0.1f),
    ARTI_SUPERIORI("Arti superiori", 0.2f),
    TRONCO("Tronco", 0.3f),
    ARTI_INFERIORI("Arti inferiori", 0.4f)
}

data class BodyRegion(
    val id: String,
    val label: String,
    val bsaPercent: Float,
    val pasiRegion: PasiRegion
)

// Pesi proporzionali da adult_back_front_percentage
private val HEAD_W  = floatArrayOf(3.5f, 3.5f, 1.0f, 1.0f)           // head_f, head_b, neck_f, neck_b
private val TRUNK_W = floatArrayOf(16.5f, 13.0f, 1.0f, 2.5f, 2.5f)   // torso_f, torso_b, groin, glut_l, glut_r
private val ARM_W   = floatArrayOf(2.0f, 2.0f, 1.5f, 1.5f, 1.25f, 1.25f) // ua_f, ua_b, fa_f, fa_b, h_f, h_b
private val LEG_W   = floatArrayOf(4.5f, 4.5f, 4.0f, 4.0f, 1.5f, 1.5f)  // th_f, th_b, ll_f, ll_b, ft_f, ft_b

private fun FloatArray.scaleTo(total: Float): FloatArray {
    val s = sum()
    return FloatArray(size) { i -> this[i] * total / s }
}

/**
 * Restituisce la lista dei distretti anatomici con percentuali BSA calibrate
 * per l'età del paziente, seguendo la Lund-Browder modificata (body_age).
 *
 * Totali per fascia d'età (body_age):
 *   1–4 anni  : testa 19%, busto 32%, per braccio 9.5%, per gamba 15%
 *   5–9 anni  : testa 15%, busto 32%, per braccio 9.5%, per gamba 17%
 *  10–14 anni : testa 13%, busto 32%, per braccio 9.5%, per gamba 18%
 *   ≥15 anni  : testa  9%, busto 37%, per braccio  9%,  per gamba 18%
 *
 * I totali vengono poi distribuiti ai sotto-distretti proporzionalmente
 * ai rapporti fronte/retro della tavola adulti (adult_back_front_percentage).
 */
fun regioniPerEta(ageYears: Int): List<BodyRegion> {
    val (testaTot, bustoTot, braccioTot, gambaTot) = when {
        ageYears <= 4  -> listOf(19f, 32f, 9.5f, 15f)
        ageYears <= 9  -> listOf(15f, 32f, 9.5f, 17f)
        ageYears <= 14 -> listOf(13f, 32f, 9.5f, 18f)
        else           -> listOf(9f,  37f, 9.0f, 18f)
    }

    val h = HEAD_W.scaleTo(testaTot)
    val t = TRUNK_W.scaleTo(bustoTot)
    val a = ARM_W.scaleTo(braccioTot)
    val l = LEG_W.scaleTo(gambaTot)

    return listOf(
        BodyRegion("head_front",            "Testa (fronte)",               h[0], PasiRegion.TESTA),
        BodyRegion("head_back",             "Testa (retro)",                h[1], PasiRegion.TESTA),
        BodyRegion("neck_front",            "Collo (fronte)",               h[2], PasiRegion.TESTA),
        BodyRegion("neck_back",             "Collo (retro)",                h[3], PasiRegion.TESTA),

        BodyRegion("torso_front",           "Busto anteriore",              t[0], PasiRegion.TRONCO),
        BodyRegion("torso_back",            "Busto posteriore",             t[1], PasiRegion.TRONCO),
        BodyRegion("groin",                 "Inguine",                      t[2], PasiRegion.TRONCO),
        BodyRegion("gluteus_left",          "Gluteo sinistro",              t[3], PasiRegion.TRONCO),
        BodyRegion("gluteus_right",         "Gluteo destro",                t[4], PasiRegion.TRONCO),

        BodyRegion("upper_arm_left_front",  "Braccio sup. sx (fronte)",     a[0], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("upper_arm_left_back",   "Braccio sup. sx (retro)",      a[1], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("forearm_left_front",    "Avambraccio sx (fronte)",      a[2], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("forearm_left_back",     "Avambraccio sx (retro)",       a[3], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("hand_left_front",       "Mano sx (fronte)",             a[4], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("hand_left_back",        "Mano sx (retro)",              a[5], PasiRegion.ARTI_SUPERIORI),

        BodyRegion("upper_arm_right_front", "Braccio sup. dx (fronte)",     a[0], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("upper_arm_right_back",  "Braccio sup. dx (retro)",      a[1], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("forearm_right_front",   "Avambraccio dx (fronte)",      a[2], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("forearm_right_back",    "Avambraccio dx (retro)",       a[3], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("hand_right_front",      "Mano dx (fronte)",             a[4], PasiRegion.ARTI_SUPERIORI),
        BodyRegion("hand_right_back",       "Mano dx (retro)",              a[5], PasiRegion.ARTI_SUPERIORI),

        BodyRegion("thigh_left_front",      "Coscia sx (fronte)",           l[0], PasiRegion.ARTI_INFERIORI),
        BodyRegion("thigh_left_back",       "Coscia sx (retro)",            l[1], PasiRegion.ARTI_INFERIORI),
        BodyRegion("lower_leg_left_front",  "Gamba inf. sx (fronte)",       l[2], PasiRegion.ARTI_INFERIORI),
        BodyRegion("lower_leg_left_back",   "Gamba inf. sx (retro)",        l[3], PasiRegion.ARTI_INFERIORI),
        BodyRegion("foot_left_front",       "Piede sx (fronte)",            l[4], PasiRegion.ARTI_INFERIORI),
        BodyRegion("foot_left_back",        "Piede sx (retro)",             l[5], PasiRegion.ARTI_INFERIORI),

        BodyRegion("thigh_right_front",     "Coscia dx (fronte)",           l[0], PasiRegion.ARTI_INFERIORI),
        BodyRegion("thigh_right_back",      "Coscia dx (retro)",            l[1], PasiRegion.ARTI_INFERIORI),
        BodyRegion("lower_leg_right_front", "Gamba inf. dx (fronte)",       l[2], PasiRegion.ARTI_INFERIORI),
        BodyRegion("lower_leg_right_back",  "Gamba inf. dx (retro)",        l[3], PasiRegion.ARTI_INFERIORI),
        BodyRegion("foot_right_front",      "Piede dx (fronte)",            l[4], PasiRegion.ARTI_INFERIORI),
        BodyRegion("foot_right_back",       "Piede dx (retro)",             l[5], PasiRegion.ARTI_INFERIORI),
    )
}
