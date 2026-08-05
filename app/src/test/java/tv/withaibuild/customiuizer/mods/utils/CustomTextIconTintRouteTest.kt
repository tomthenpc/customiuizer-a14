package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomTextIconTintRouteTest {

    private val context = object : Application() {}
    private val classLoader = FakeView::class.java.classLoader!!

    @Before
    fun setUp() {
        CustomTextIconTintRoute.releaseAll()
    }

    @After
    fun tearDown() {
        CustomTextIconTintRoute.releaseAll()
    }

    @Test
    fun registerAddsViewToDispatcherAndReleasesOnDetach() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)

        assertTrue("View should be added to dispatcher", view in dispatcher.registered)
        assertEquals(1, CustomTextIconTintRoute.trackedCount())

        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        assertFalse("View should be removed from dispatcher", view in dispatcher.registered)
        assertEquals(0, CustomTextIconTintRoute.trackedCount())
    }

    @Test
    fun registerIsIdempotentForSameView() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)

        assertEquals(1, dispatcher.registered.size)
        assertEquals(1, CustomTextIconTintRoute.trackedCount())
    }

    @Test
    fun releaseAllRemovesAllRegistrations() {
        val dispatcher = FakeDarkIconDispatcher()
        val view1 = FakeView(context, attached = true)
        val view2 = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view1, classLoader, "left", dispatcher)
        CustomTextIconTintRoute.register(view2, classLoader, "right", dispatcher)

        assertEquals(2, dispatcher.registered.size)
        assertEquals(2, CustomTextIconTintRoute.trackedCount())

        CustomTextIconTintRoute.releaseAll()

        assertTrue(dispatcher.registered.isEmpty())
        assertEquals(0, CustomTextIconTintRoute.trackedCount())
    }

    @Test
    fun registerWhenAlreadyAttachedImmediatelyRegisters() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "left", dispatcher)

        assertTrue(view in dispatcher.registered)
        assertEquals(1, CustomTextIconTintRoute.trackedCount())
    }

    @Test
    fun registerWhenDetachedDoesNotRegisterUntilAttached() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = false)

        CustomTextIconTintRoute.register(view, classLoader, "right", dispatcher)

        assertTrue(dispatcher.registered.isEmpty())

        view.attached = true
        view.dispatchOnAttachStateChangeListenerAttached()

        assertTrue(view in dispatcher.registered)
    }

    private class FakeDarkIconDispatcher {
        val registered = mutableListOf<View>()

        @Suppress("unused")
        fun addDarkReceiver(view: View) {
            registered.add(view)
        }

        @Suppress("unused")
        fun removeDarkReceiver(view: View) {
            registered.remove(view)
        }
    }

    private class FakeView(context: Context, var attached: Boolean) : View(context) {
        private val listeners = mutableListOf<View.OnAttachStateChangeListener>()

        override fun isAttachedToWindow() = attached

        override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            listeners.add(listener)
        }

        override fun removeOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            listeners.remove(listener)
        }

        fun simulateDetach() {
            attached = false
        }

        fun dispatchOnAttachStateChangeListenerAttached() {
            listeners.forEach { it.onViewAttachedToWindow(this) }
        }

        fun dispatchOnAttachStateChangeListenerDetaches() {
            val copy = listeners.toList()
            copy.forEach { it.onViewDetachedFromWindow(this) }
        }
    }
}
