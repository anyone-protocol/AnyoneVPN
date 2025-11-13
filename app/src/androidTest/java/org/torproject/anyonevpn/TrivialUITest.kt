package org.torproject.anyonevpn


import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import io.anyone.anyonevpn.AnyoneVpnActivity
import io.anyone.anyonevpn.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TrivialUITest {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(AnyoneVpnActivity::class.java)

    @Test
    fun trivialUITest() {

        val bottomNav = onView(
                allOf(withId(R.id.bottom_navigation),
                        isDisplayed()))

        //textView.check(matches(withText("CONFIGURE")))

    }
}
