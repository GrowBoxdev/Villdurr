package com.growboxit.villdurr

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var villageView: VillageView
    private lateinit var resourceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        villageView = findViewById(R.id.villageView)
        resourceText = findViewById(R.id.resourceText)

        villageView.resourceListener = { gold, elixir ->
            resourceText.text = "GOLD: $gold | ELIXIR: $elixir"
        }

        findViewById<Button>(R.id.btnGoldmine).setOnClickListener {
            villageView.setMode(Mode.PLACE_GOLDMINE)
        }
        findViewById<Button>(R.id.btnElixir).setOnClickListener {
            villageView.setMode(Mode.PLACE_ELIXIR)
        }
        findViewById<Button>(R.id.btnWall).setOnClickListener {
            villageView.setMode(Mode.PLACE_WALL)
        }
        findViewById<Button>(R.id.btnDemolish).setOnClickListener {
            villageView.setMode(Mode.DEMOLISH)
        }

        val prefs = getSharedPreferences("villdurr_save", Context.MODE_PRIVATE)
        val saved = prefs.getString("state", "") ?: ""
        villageView.restore(saved)
    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("villdurr_save", Context.MODE_PRIVATE)
        prefs.edit().putString("state", villageView.serialize()).apply()
    }
}
