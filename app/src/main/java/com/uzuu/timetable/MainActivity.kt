package com.uzuu.timetable

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.NavController
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var drawerLayout: DrawerLayout

    private companion object {
        const val DEBUG_TAG = "DEBUG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(DEBUG_TAG, "MainActivity.onCreate: start")

        // Initialize Firebase
        initializeFirebase()

        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: Toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        val navView: NavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.home_fragment,
                R.id.class_search_fragment,
                R.id.add_class_fragment,
                R.id.settings_fragment,
            ),
            drawerLayout,
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setNavigationItemSelectedListener { menuItem ->
            Log.d(DEBUG_TAG, "Navigation item clicked: ${menuItem.itemId}")
            val handled = when (menuItem.itemId) {
                R.id.menu_search_class -> {
                    navigateTo(navController, R.id.class_search_fragment)
                    true
                }
                R.id.menu_add_class -> {
                    navigateTo(navController, R.id.add_class_fragment)
                    true
                }
                R.id.menu_settings -> {
                    navigateTo(navController, R.id.settings_fragment)
                    true
                }
                else -> false
            }

            if (handled) {
                menuItem.isChecked = true
                drawerLayout.closeDrawers()
            }

            handled
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun navigateTo(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id != destinationId) {
            Log.d(DEBUG_TAG, "Navigating to destination: $destinationId")
            navController.navigate(destinationId)
        }
    }

    private fun initializeFirebase() {
        try {
            Log.d(DEBUG_TAG, "Initializing Firebase")
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d(DEBUG_TAG, "Firebase persistence enabled")
        } catch (e: Exception) {
            Log.d(DEBUG_TAG, "Firebase persistence init skipped/failed: ${e.message}", e)
        }
        
        // Sign in anonymously to enable database writes
        val auth = FirebaseAuth.getInstance()
        Log.d(DEBUG_TAG, "FirebaseAuth currentUser=${auth.currentUser?.uid ?: "null"}")
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(DEBUG_TAG, "Anonymous authentication successful: ${task.result?.user?.uid}")
                    } else {
                        Log.d(DEBUG_TAG, "Anonymous authentication failed: ${task.exception?.message}", task.exception)
                    }
                }
        } else {
            Log.d(DEBUG_TAG, "Anonymous auth already available: ${auth.currentUser?.uid}")
        }
    }
}
