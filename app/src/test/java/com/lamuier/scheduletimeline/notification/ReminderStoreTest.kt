package com.lamuier.scheduletimeline.notification

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.lamuier.scheduletimeline.data.ReminderState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderStoreTest {
    @Test fun unchangedStateSkipsCommitAndFailedPersistenceIsRetried() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val delegate = context.getSharedPreferences("ledger-test", Context.MODE_PRIVATE)
        delegate.edit().clear().commit()
        var commits = 0
        var failCommit = false
        val prefs = object : SharedPreferences by delegate {
            override fun edit(): SharedPreferences.Editor {
                val editor = delegate.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                        editor.putStringSet(key, values)
                        return this
                    }
                    override fun commit(): Boolean {
                        commits++
                        return !failCommit && editor.commit()
                    }
                }
            }
        }
        val store = ReminderStore(prefs)
        store.write(ReminderState())
        assertEquals(0, commits)
        val pending = ReminderState(pending = setOf("A"))
        store.write(pending); store.write(pending)
        assertEquals(1, commits)
        val handled = ReminderState(handled = setOf("A"))
        failCommit = true
        try { store.write(handled); fail("Must report failed persistence") } catch (_: IllegalStateException) { }
        failCommit = false
        store.write(handled)
        assertEquals(3, commits)
        assertEquals(handled, store.read())
        store.write(ReminderState())
        assertEquals(ReminderState(), store.read())
    }
}
