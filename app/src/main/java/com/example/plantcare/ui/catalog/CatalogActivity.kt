package com.example.plantcare.ui.catalog

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.plantcare.AllPlantsFragment
import com.example.plantcare.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * CatalogActivity (F2.4)
 *
 * Fullscreen host for [AllPlantsFragment] — the curated 500+ plants
 * browser that used to live as the "Alle Pflanzen" top tab.
 *
 * Launched from:
 *  - [com.example.plantcare.ui.util.AddPlantChoiceBottomSheet] when the
 *    user picks "Aus Katalog wählen" inside Mein Garten.
 *  - F2.4-onwards: any future call site that wants the catalog (e.g.
 *    the `openCatalog=true` Intent extra that PlantsInRoomActivity
 *    forwards through MainActivity).
 *
 * Back returns to the previous screen (Mein Garten in the common path).
 * No state held here beyond the Fragment.
 */
class CatalogActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.plantcare.format.FontScaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        val toolbar: MaterialToolbar = findViewById(R.id.catalogToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.catalogContainer, AllPlantsFragment())
                .commit()
        }
    }
}
