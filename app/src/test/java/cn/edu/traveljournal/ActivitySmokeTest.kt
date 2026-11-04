package cn.edu.traveljournal

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** JVM Android lifecycle check only; not a rendered-device visual acceptance test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ActivitySmokeTest {
    @Test fun nativeActivityCanStartAndClose(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try{
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val content=controller.get().findViewById<View>(android.R.id.content)
            assertNotNull(content)
            assertEquals("cn.edu.traveljournal",controller.get().packageName)
        }finally{controller.pause().stop().destroy()}
    }
}
