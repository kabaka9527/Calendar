package com.shiftcalendar

import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.shiftcalendar.databinding.ActivityMainBinding
import com.shiftcalendar.ui.calendar.CalendarFragment
import com.shiftcalendar.ui.shiftrule.ShiftRuleFragment
import com.shiftcalendar.ui.shifttype.ShiftTypeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val calendarFragment = CalendarFragment()
    private val shiftTypeFragment = ShiftTypeFragment()
    private val shiftRuleFragment = ShiftRuleFragment()
    private val fragmentManager = supportFragmentManager
    private var activeFragment: Fragment = calendarFragment
    private var prefersReducedMotion: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检测用户是否启用了减弱动效
        prefersReducedMotion = checkReducedMotion()

        // 处理安全区域（刘海屏、挖孔屏、手势导航栏）
        setupWindowInsets()

        setupBottomNavigation()
        loadInitialFragment()
    }

    /**
     * 处理安全区域边距，确保内容不被系统栏遮挡
     */
    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0 // 底部由 BottomNavigationView 自行处理
            )
            insets
        }
    }

    /**
     * 检测用户是否开启了"移除动画"无障碍设置
     */
    private fun checkReducedMotion(): Boolean {
        return try {
            val duration = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            duration == 0.0f
        } catch (e: Exception) {
            false
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_calendar -> switchFragment(calendarFragment)
                R.id.nav_shifts -> switchFragment(shiftTypeFragment)
                R.id.nav_rules -> switchFragment(shiftRuleFragment)
            }
            true
        }
    }

    private fun loadInitialFragment() {
        fragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, shiftRuleFragment, "rule")
            .hide(shiftRuleFragment)
            .add(R.id.fragmentContainer, shiftTypeFragment, "shift")
            .hide(shiftTypeFragment)
            .add(R.id.fragmentContainer, calendarFragment, "calendar")
            .commit()
    }

    private fun switchFragment(fragment: Fragment) {
        if (fragment == activeFragment) return
        val transaction = fragmentManager.beginTransaction()
        if (!prefersReducedMotion) {
            transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }
        transaction.hide(activeFragment).show(fragment).commit()
        activeFragment = fragment
    }
}